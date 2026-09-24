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

## Atualização de remediação P1 — 22/09

As duas projeções P1 foram separadas sem alterar a definição de venda, os filtros de visibilidade ou
as migrations:

- o dashboard passou a usar `totalDeVendas(...)` para o período anterior e para `vendasHoje`; a
  agregação completa continua exclusiva de `vendasAtual`, que alimenta ranking e sem responsável;
- a elegibilidade de leitura EV-05 passou a usar `EXISTS` em `atendimento`, sem carregar
  responsável nem última mensagem. Os demais caminhos que precisam de `atendimentoId` e do marco
  da última mensagem continuam em `porLeadEmAtendimento(...)`.

Não houve acesso a `matriz_hml` para confirmar em `pg_stat_statements` se essas duas consultas são
responsáveis pelo pico atual. A remediação reduz trabalho comprovadamente descartado, mas não prova
causalidade nem substitui a investigação do endpoint de listagem externa do E200.

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

## E209 — CPU do Postgres da Estrutural em 24/09

### Evidência disponível e seus limites

Observado na instância (não reproduzido aqui): ~180 min com CPU da VPS em 100%, container
`erp-matriz-hml-oxj4cd_postgres` em 224,72% e 182,66%, memória perto de 915 MiB de 1 GiB, e em
`pg_stat_activity` o `SELECT COUNT(*)` da visão FINALIZADOS. Entre 16:12:58 e 16:17:23 os
contadores acumulados cresceram 898.840 varreduras de `usuario` e 7.805.651 buscas por índice em
`atendimento` — são **deltas** de 4 min 25 s, não totais.

Sem `pg_stat_statements` e sem métricas HTTP expostas (`management.endpoints` só publica
`health`/`info`), **não é possível atribuir as três horas a uma consulta nem saber a frequência
real de `/contagem` e da inbox em produção.** O que se afirma abaixo vem do código, de testes e da
bancada `docs/benchmarks/e209-painel/`.

Uma inferência que a evidência sustenta: a SQL de contagem não referencia `usuario`. Na bancada, só
as listagens produzem `seq_scan` em `usuario` (até 9.974 por página TODOS, pelo `LEFT JOIN usuario`
por linha). As 898.840 varreduras de `usuario` indicam, portanto, listagens do painel (ou outra
consulta que junte `usuario`) rodando em volume no período — não só o COUNT visto no snapshot.

### Causa confirmada no código

1. **Ouvinte global invalidando tudo a cada evento.** `NotificacoesTempoReal` fica no layout (todas
   as páginas) e invalidava `["atendimentos"]` em toda notificação de origem ATENDIMENTO, incluindo
   `ATENDIMENTO_ESTADO`, que o backend envia a **todos os gestores/subgestores/administradores** a
   cada mensagem recebida, enviada ou respondida pela IA (`ListarDestinatariosTempoRealUseCase`).
   Para lead com a IA, `NOVA_MENSAGEM` vai a todos os usuários ativos. Cada invalidação relia a
   contagem (5 COUNTs para gestão, incluindo FINALIZADOS), **todas** as páginas já carregadas da
   inbox e o `/estado` da conversa aberta.
2. **Mesmo evento invalidado duas vezes na tela de Atendimentos** (ouvinte global + ouvinte da
   página). `invalidateQueries` usa `cancelRefetch`: o navegador descarta a primeira resposta, mas o
   servidor executa as duas consultas.
3. **Página 1 da inbox custava a lista inteira.** O `ROW_NUMBER() OVER (PARTITION BY lead)` impede o
   `LIMIT` de descer; não lidas, atendimento ativo, dono, etapa e prévia eram calculados para cada
   atendimento da visão antes do corte.
4. **FINALIZADOS contado e descartado em toda chamada** a `/contagem`.
5. `mensagem` é particionada por mês: cada lateral "última mensagem" sonda o índice de todas as
   partições (default + meses), por atendimento.

**Hipótese não comprovada:** a soma 1–4, com vários gestores/abas e tráfego da IA, explica a
saturação. É coerente com os contadores, mas não há como provar volume sem telemetria. A automação
(E200) **não foi avaliada**: não há acesso aos logs do n8n nem registro de requisições no backend.

### O que mudou

| Frente | Antes | Depois |
|---|---|---|
| `/contagem` padrão | abas + FINALIZADOS | só abas; `?incluirFinalizados=true` mantém o total com a mesma equivalência |
| SQL da contagem | `ROW_NUMBER` + lateral de mensagem por atendimento, sem `JOIN lead` | `COUNT(DISTINCT a.lead_id)` com `JOIN lead` (RLS de lead) |
| Listagem/página | cartão completo para todo atendimento da visão | fase 1 estreita escolhe e pagina; fase 2 monta ≤ limite cartões |
| Refetch por evento | 1 (global) ou 2 (tela) invalidações amplas por evento | ≤ 1 imediato + 1 no fim de 2 s por aba; mesmo evento deduplicado |
| `/estado` por evento | relido em todo evento de qualquer lead | só quando o evento é daquele atendimento, ou urgente |
| Revogação | fechava o painel; lista só mudava no próximo evento | refetch urgente imediato da lista |

### Bancada — SQL (mesma massa, RLS real, média de 3 execuções)

Massa sintética: 20 mil leads, 40.104 atendimentos, 506 mil mensagens. `idx_msg` e `idx_at` são
buscas por índice por execução em `mensagem` (todas as partições) e `atendimento`.

| Cenário | ms antes | ms depois | blocos antes | blocos depois | idx_at antes→depois | idx_msg antes→depois | seq `usuario` antes→depois |
|---|---:|---:|---:|---:|---|---|---|
| inbox pág. 1 TODOS (gestor) | 1.396,9 | 233,6 | 625.149 | 184.675 | 19.960→10.147 | 146.288→50.686 | 9.974→1 |
| inbox pág. 1 PENDENTES (gestor) | 2.113,7 | 123,9 | 1.141.963 | 130.952 | 84.710→6.908 | 301.605→38.258 | 1→1 |
| inbox pág. 1 FINALIZADOS (gestor) | 2.642,3 | 563,3 | 1.465.060 | 637.429 | 100.374→70.403 | 291.486→151.211 | 1→1 |
| inbox pág. 1 ATIVOS (atendente) | 72,0 | 43,1 | 47.035 | 17.362 | 1.850→1.273 | 10.868→4.454 | 738→51 |
| inbox pág. 1 FINALIZADOS (atendente) | 2.291,9 | 738,4 | 1.451.151 | 618.801 | 95.069→65.098 | 291.486→151.211 | 1→1 |
| listar PENDENTES (atendente, legado) | 2.397,2 | 95,2 | 801.318 | 31.315 | 70.163→1.108 | 203.672→8.424 | 1→1 |
| listar TODOS (gestor, legado) | 731,6 | 541,2 | 625.149 | 455.941 | 19.960→14.975 | 146.288→119.668 | 9.974→1 |
| contar FINALIZADOS (gestor) | 241,9 | 199,7 | 362.852 | 181.799 | 4→40.110 | 150.650→0 | 0→0 |
| `/contagem` gestor (soma) | 753,4 (5 SQL) | 270,2 (4 SQL) | 637.946 | 109.497 | | | |
| `/contagem` atendente (soma) | 670,5 (4 SQL) | 253,1 (3 SQL) | 472.158 | 29.095 | | | |

Resultados comparados byte a byte (`comparar.sh`): as 20 listagens/páginas e 8 das 9 contagens são
idênticas. A diferente é FINALIZADOS do **atendente**: 16.780 → 15.015, que é o tamanho da
listagem. A contagem da E199 perdera o `JOIN lead` e contava leads que um colega está atendendo
(ciclo antigo FINALIZADO visível, lead invisível pela RLS de `lead`) — divergência pré-existente
entre badge e lista.

Tempos têm ruído de máquina local (3 execuções); blocos e buscas por índice são determinísticos.
Não foi medido p95 HTTP nem CPU de produção.

### Frequência de refetch (teste `atualizacao-do-painel.test.ts`, QueryClient real)

| Cenário | Antes | Depois |
|---|---:|---:|
| 50 eventos em 1 s, tela de Atendimentos (2 ouvintes): execuções de `/contagem` | 100 | 2 |
| idem, `/estado` da conversa aberta (eventos de outros leads) | 100 | 0 |
| evento isolado | imediato | imediato |
| transferência/devolução/convite/revogação no meio de rajada | imediato | imediato |
| duas abas na tela de Atendimentos, N eventos | 2 × 2N | 2 × 2 por janela |

Estimativa de custo de banco por evento, **modelada** a partir das duas tabelas (gestor na aba
TODOS com uma página carregada): antes 2 × (753 + 1.397) ≈ 4,3 s de execução SQL por evento;
depois ≈ 0,5 s por janela de 2 s, independente do número de eventos. Com três gestores e um evento
por segundo, isso vai de ~13 s de CPU por segundo (acima de 4 vCPU) para ~0,75 s. É um modelo sobre
massa sintética, não medição de produção.

### Fora do repositório

- **n8n / E200.** A busca pontual já existe (`GET /api/v1/atendimentos/busca?leadId=` ou
  `?telefone=`, PR #196). Não há evidência de que a automação chame a listagem inteira — o
  `docs/04` afirma o contrário ("A Automação não usa `/api/v1/atendimentos?visao=TODOS`"), e o E200
  diz que sim. Nada foi alterado no fluxo. Para confirmar: habilitar log de acesso no Traefik (ou
  consultar o existente) e contar `GET /api/v1/atendimentos?visao=` sem cabeçalho de navegador; se
  houver, trocar no workflow o nó HTTP por `/busca` e validar 200/404 com um lead conhecido.
- **Janela de infraestrutura.** `pg_stat_statements` exige `shared_preload_libraries` e restart do
  Postgres — fora do horário 08:00–18:30, em cada instância, com `CREATE EXTENSION` depois.
- **RLS por linha.** As políticas chamam `app_papel()`/`app_usuario_id()` (inlinados em
  `current_setting`) em cada linha; na bancada isso é boa parte do custo restante da contagem.
  Envolver as chamadas em `(SELECT ...)` permitiria ao planejador avaliá-las uma vez, mas é mudança
  de RLS: exige migration própria, teste negativo e janela.
- **Última mensagem desnormalizada.** O custo restante da fase 1 (FINALIZADOS ~0,5 s) é a lateral
  por atendimento em todas as partições. `atendimento.ultima_mensagem_em` mantida na gravação
  eliminaria isso, mas toca o caminho de envio/recebimento e exige backfill: decisão separada.
