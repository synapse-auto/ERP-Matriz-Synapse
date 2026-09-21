# E201 — Auditoria de reaproveitamento de consultas caras

## Escopo e limite da evidência

Esta auditoria cobre o item 2 do plano pós-incidente de CPU de 21/09: localizar consultas com
`JOIN`, subconsulta ou agregação materialmente cara que são reaproveitadas por um chamador que usa
somente uma fração do resultado. A revisão foi feita sobre `origin/main` no commit `0c7502c`, após a
correção E199, nos módulos `crm-atendimento`, `crm-core`, `crm-equipe`, `crm-campanhas` e
`crm-relatorios`.

O levantamento é estático. Não há `pg_stat_statements` configurado nos arquivos de stack e não foi
feita medição nas instâncias `matriz_hml` ou `fmnaprod`. Portanto, “frequência” abaixo significa o
ponto de entrada e a multiplicidade inferidos do código; não representa chamadas por minuto nem
tempo real de execução. Nenhuma consulta foi alterada nesta etapa.

## Resultado priorizado

| Prioridade | Módulo | Consulta reaproveitada | Resultado descartado | Frequência conhecida | Custo aparente e próximo passo |
|---|---|---|---|---|---|
| P1 — medir primeiro | `crm-relatorios` | `AgregacaoDeVendasRepositorioJdbc.agregar(...)` | `DashboardVisaoGeralRepositorioJdbc` usa a agregação do período anterior somente por `total()` e `statusAoVivo(...)` repete a agregação do dia somente por `total()` | Cada `GET /api/v1/dashboard/visao-geral` executa três agregações de vendas; duas não usam o ranking. O frontend não configura polling, mas refaz a consulta ao montar ou mudar o filtro | Alto: o CTE faz `DISTINCT ON` em `evento_timeline`, junta `lead`, depois junta e agrupa por `usuario`. Medir as duas assinaturas no `pg_stat_statements`; se confirmado, criar uma operação canônica de total que preserve a mesma definição de venda sem agrupar por responsável |
| P1 — medir primeiro | `crm-atendimento` | `AtendimentosEmAndamentoRepositorioJdbc.porLeadEmAtendimento(...)` | `Ev05LeadUseCase.exigirAtendimentoElegivel(...)` usa apenas existência; caminhos idempotentes usam, em alguns ramos, somente `atendimentoId` | Chamado pelos endpoints EV-05 de estado e escrita. A frequência real é definida pelo n8n e não está no repositório; pode multiplicar pelo número de candidatos de cada ciclo | Médio/alto sob fan-out: a consulta junta `usuario` e executa uma lateral sobre `mensagem` mesmo quando o chamador só precisa saber se existe. Medir por `queryid`; depois separar projeções de existência/id e de ciclo completo sem duplicar a regra `EM_ATENDIMENTO` |
| P2 — medir depois | `crm-atendimento` | `HistoricoDeMensagensRepositorioJdbc.doAtendimento(...)` | `ContextoEv05UseCase` conserva apenas `mensagemId`, nome do remetente, tipo, conteúdo e horário | Uma chamada por geração de contexto do resumo; fluxo sob demanda, sem polling no CRM | Médio: por mensagem carrega dados de atendimento, responsável, erro, referência, idempotência, mídia e opções, com seis junções. Criar read model estreito para contexto somente se a medição mostrar impacto; preservar a autorização pelo atendimento e a ordenação atual |
| P3 — ganho pequeno | `crm-equipe` | `EquipeRepositorioJdbc.porId(...)` usando `BASE` | `ObterFotoDeUsuarioUseCase` usa somente `fotoReferencia` | Uma busca por URL de avatar; o frontend usa cache TanStack por URL, reduzindo repetição | Baixo: busca por PK e `LEFT JOIN` 1:1, mas lê 12 colunas, inclusive `senha_hash`, e disponibilidade da IA. Uma porta `fotoReferencia(usuarioId)` reduziria projeção e exposição interna, sem urgência de CPU |
| P3 — ganho pequeno | `crm-atendimento` | `ParticipacaoAtendimentoRepositorioJdbc.pedido(...)` com `p.* JOIN usuario` | `GerenciarParticipacaoAtendimentoUseCase.responder(...)` usa `atendimentoId`, `solicitanteId` e `solicitadoEm`; nome e vários campos do pedido não participam do comando | Apenas ao aprovar ou recusar entrada; interação humana e pontual | Baixo: uma linha por PK. Se tocado por outra necessidade, substituir `p.*` por projeção explícita do comando e remover o `JOIN usuario`; não justifica PR isolado de performance |

### 1. Agregação de vendas usada como contagem

A consulta de `agregar(...)` primeiro encontra a primeira transição para `GANHO` de cada lead,
depois resolve usuário, papel e agrupamento por responsável. Isso é necessário para
`vendasAtual`, porque o dashboard usa `porAtendente` e `semResponsavel`. Não é necessário nos dois
chamadores abaixo:

- `vendasAnterior`: somente `total()` entra no comparativo;
- `vendasHoje` em `statusAoVivo`: somente `total()` entra na resposta.

Uma eventual correção não deve copiar a definição de venda para outro SQL independente. A
definição “primeira transição do lead para GANHO no período/coorte” precisa continuar canônica,
idealmente por um fragmento/CTE compartilhado entre a projeção por atendente e a contagem.

### 2. Atendimento EV-05 completo usado como existência

`porLeadEmAtendimento(...)` devolve responsável e última mensagem por meio de `LEFT JOIN usuario`
e `LEFT JOIN LATERAL`. Os métodos de gravação precisam de parte desse estado para proteger o ciclo,
mas `estadoResumo(...)` e `estadoPreenchimento(...)` passam por
`exigirAtendimentoElegivel(...)`, que descarta o objeto e verifica somente presença.

Esse caso merece medição antes dos demais porque a automação pode consultar o estado para vários
candidatos. A frequência e o tamanho do lote não estão no código do CRM; pertencem ao workflow n8n.

### 3. Histórico completo usado como contexto reduzido

O histórico de chat é intencionalmente rico para a interface: autoria histórica, atendimento de
origem, responsável, falha de entrega, citação e chave idempotente. O endpoint de contexto EV-05
transforma cada item em cinco campos e não devolve URL de mídia ou metadados. Uma projeção dedicada
pode evitar junções e desserialização desnecessárias, mas só deve ser implementada preservando:

- a autorização obtida pelo atendimento de origem;
- o limite configurado e a ordenação mais recente primeiro;
- o nome do remetente, que ainda exige resolver `usuario` para mensagens humanas;
- o marco `contextoAte` exatamente igual ao comportamento atual.

## Resultado por módulo

### `crm-atendimento`

Três reaproveitamentos foram encontrados: estado completo do atendimento EV-05 usado como
existência/id, histórico completo usado como contexto reduzido e pedido de participação completo
usado por um comando estreito. O `listar()` do painel continua conhecido como consulta cara, mas
não entra nesta lista: os campos e laterais alimentam efetivamente o cartão. A otimização dele
permanece bloqueada até existir medição real, conforme o item 3 do plano E201.

A contagem do painel também não entra como pendência: no commit-base auditado, a E199 já separa
`CAMPOS_CONTAGEM`/`ORIGEM_CONTAGEM` da projeção de cartões.

### `crm-core`

Nenhum caso satisfaz os dois critérios do E201. As consultas JDBC com junções (`lembrete`,
`mensagem_programada`, tags e timeline) usam as colunas projetadas nos respectivos read models.

Há um ponto adjacente, fora do critério estrito: vários casos de uso chamam
`LeadRepositorioJpa.porId(...)` apenas para autorização e `lead.id()`. Isso materializa a entidade
`Lead` inteira, mas não reutiliza uma consulta com `JOIN` ou subconsulta cara construída para outro
fim. Deve ser reavaliado apenas se `pg_stat_statements` ou profiling apontar essa consulta, para não
transformar a auditoria em refatoração genérica.

### `crm-equipe`

Foi encontrado o reaproveitamento de `EquipeRepositorioJdbc.porId(...)` no endpoint de avatar.
As consultas mais complexas de chat interno e disponibilidade da IA usam seus agregados, contagens
e critérios de ordenação na resposta; não descartam uma projeção rica para obter apenas contagem ou
existência.

### `crm-campanhas`

O módulo contém somente `package-info.java`; não há repositório, endpoint ou consulta executável a
auditar nesta versão.

### `crm-relatorios`

Foi encontrado o reaproveitamento material da agregação por atendente em duas contagens de vendas.
As consultas de auditoria já separam `COUNT(*)` da projeção paginada. `avaliacoes(...)` devolve
contagem e média para período atual e anterior; no período anterior a contagem não é lida, mas ambos
os agregados são calculados na mesma varredura, então retirar a coluna não elimina `JOIN`, filtro ou
scan e não foi classificado como oportunidade material.

## Itens explicitamente não executados

1. **`pg_stat_statements`:** não habilitado. Os stacks usam `postgres:15-alpine` sem
   `shared_preload_libraries`; ativar exige decisão de configuração, restart e janela operacional em
   cada instância. Não foi feita alteração de infra nem restart.
2. **Otimização de `listar()` do painel:** não executada. A projeção é consumida pela tela e precisa
   de baseline real antes de qualquer mudança.
3. **E200:** independente desta auditoria; não integra este commit-base (`origin/main` ainda está em
   `0c7502c`).
4. **Medição de produção/homologação:** não executada. Sem acesso operacional e sem
   `pg_stat_statements`, não há números confiáveis de tempo médio, total ou chamadas/minuto.

## Plano de medição recomendado

Depois de habilitar `pg_stat_statements`, capturar ao menos um período representativo por instância
e registrar `calls`, `total_exec_time`, `mean_exec_time`, `rows` e a consulta normalizada. Priorizar:

1. CTE iniciado por `WITH vendas AS`;
2. consulta por `a.lead_id = $1 AND a.status = 'EM_ATENDIMENTO'` com lateral de mensagem;
3. histórico de contexto com junções em `mensagem_referencia` e `mensagem_envio_idempotencia`;
4. somente depois, a listagem completa do painel.

Não comparar CPU antes/depois sem carga equivalente. O critério para abrir uma correção deve ser
tempo total relevante ou alta multiplicidade, não apenas SQL visualmente grande.
