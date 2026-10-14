# 72 — Painel operacional de Atendimentos

## Plano e progresso (reler antes de cada fase)

Base atual: `origin/main e3829cc`, worktree isolada `projeto_matriz-painel-operacional`.
Nenhum acesso a VPS/Dokploy, SQL de produção, envio externo ou deploy está autorizado.

| Fase | Estado | Entrega / evidência |
|---|---|---|
| 1. Auditoria | Concluída; limite interativo registrado | Template JSON lido sem executar scripts. Regras de espera/período/admin aprovadas. As três screenshots posteriores do usuário permitem comparação estática; não houve navegação no original. |
| 2. Backend | Implementado e validado; última revisão em execução | 7 IT HTTP/Postgres/Redis, 2 do listener transacional, 3 dos casos de uso e 21 de política passaram. `clean verify` completo intermediário passou em 20m44; o seguinte passou em 28m11. Último ciclo inclui também canal na projeção paginada. |
| 3. Interface | Implementada e refinada com as screenshots | Seis cards, barras preenchidas, uma expansão por vez com tabela de lead/etapa/situação/prévia/espera/hora, abas com badges, título clicável, chips e sidebar paginada. 10 testes do painel/lateral passaram; rodada dirigida de cinco arquivos passou 133/133 antes do último teste temporal. Inter confirmada. |
| 4. Validação | Em andamento; não considerar aprovação visual/CI | Prints reais, navegação/rascunho/back e comparação após ajustes. Frontend completo intermediário: 1280/1290, 10 falhas de processos Windows em aplicar-marca. Última suíte, clean verify e CI ainda pendentes neste registro. |

## Limitações atuais

- Computer Use encerrou a interação por não conseguir determinar a URL atual com segurança.
  Não reutilizar essa sessão nem contornar a restrição. Referência inspecionada como dados;
  aplicação local validada em sessão Playwright CLI independente, Chrome headed. Comparação
  interativa com o original permanece indisponível. A revisão estática usa as três imagens
  posteriormente anexadas pelo usuário; não equivale a aprovação de fidelidade pixel a pixel.
- Docker engine inicialmente indisponível em 14/10; iniciado por `docker desktop start`, versão 29.5.3.
- A base contém duas migrations V104: `distribuicao_vinculo_e_chaves` e
  `responsavel_do_ciclo_de_resumo_ia`. A PR #307 corrigiu isso em `origin/main e3829cc`, incorporada
  nesta branch sem reescrever migration. O parâmetro novo usa V106, próximo número livre conferido.
- Skills clean-code, architecture-patterns, api-design-principles e supabaseboaspraticas não estão
  no catálogo disponível. Computer Use, Playwright e Visualize lidas; Visualize orienta implementar
  o projeto, não inventar uma reprodução da tela em uma visualização separada.

## Mapa do HTML (referência, não regra)

Template: 255.744 caracteres. Runtime do bundler, fontes, bibliotecas e Base64 não serão copiados.
Lista lateral de 346px; painel com cabeçalho de busca de atendentes e controles; seis indicadores;
tabela de equipe com presença e contagens; linha de fila; expansão com seis recortes e abrir todos.
No produto, usar Inter atual, tokens/configuração da instância e catálogo. Adaptar grade de seis cards
e tabela para telas estreitas sem comprimir nomes/ações. O relógio do protótipo atualiza `updatedAt`
em cada render: **não copiar**; a aplicação usa o instante do snapshot realmente recebido.

| Interação | Comportamento no produto |
|---|---|
| Título / sidebar Atendimentos | Remover seleção e voltar ao painel, sem escrever leitura nem apagar rascunho |
| Conversa / link direto | Abrir fluxo existente; back/forward preserva seleção |
| Online | Filtrar tabela de equipe, sem mudar alcance |
| Outros indicadores / número da linha | Mesmo critério no servidor, chips e reset de paginação |
| Busca de atendentes | Filtra equipe, independente da busca de clientes |
| Expandir / trocar recorte | Buscar só a fila expandida, com paginação; não escrever participação/posse |
| Abrir todos | Aplicar critério equivalente na lateral, nunca uma lista congelada de IDs |
| Remover chip / limpar | Retornar ao alcance autorizado; preservar busca compatível |

## Semântica

Unidade: conversa/cartão **deduplicado por lead**, não soma de ciclos. Um ciclo finalizado não
conta como finalizado quando há outro aberto visível. Responsável é o do ciclo aberto atual,
não o autor/antigo dono do último evento. Empates usam horário e UUID determinísticos.

| Indicador | Fonte e regra | Unidade / período |
|---|---|---|
| Online / elegíveis | `usuario.ativo`, presença oficial e papéis operacionais; limitado ao escopo do solicitante | Usuários, snapshot |
| Potenciais | Ciclo `EM_IA`, sem responsável humano; regra de Potenciais existente | Leads abertos |
| Novas mensagens | `mensagem` LEAD após `atendimento_leitura.lido_ate`; gestão usa dono atual, sem dono e alcance pessoal usa autenticado | Mensagens e leads distintos; mesma semântica do cartão, docs/11 |
| Ativos | Ciclo humano `EM_ATENDIMENTO`; a tabela pode recortar pelo dono autorizado, sem alterar a aba ATIVOS pessoal legada | Leads abertos |
| Pendentes | Critério vigente de Pendentes (última mensagem LEAD/ATENDENTE e convite vigente), separado do novo atraso | Leads abertos |
| Sem resposta | Primeira mensagem do cliente após última resposta humana/IA válida no **mesmo ciclo aberto**. ENVIADO/ENTREGUE/LIDO válidos; PENDENTE/FALHOU e SISTEMA não encerram espera | Leads; maior espera e limite configurável, padrão aprovado 20min |
| Finalizados | Último ciclo finalizado no dia atual da instância, sem ciclo aberto visível; nunca todo histórico por atendente | Leads, início inclusivo / fim exclusivo no fuso existente |

As duas últimas regras foram aprovadas pelo usuário em 14/10/2026. Novas mensagens reutiliza a
regra documentada, não transforma leitura pessoal em pendência universal da equipe.
Total em aberto é a união de humano e IA por lead; Pendentes/sem resposta são subconjuntos,
não parcelas a somar novamente. Chat Interno não integra os indicadores de clientes.

## Autorização proposta

Capacidade `atendimentos.painel_operacional`, nível VER, teto operacional existente.
Padrão ON para GESTOR/SUBGESTOR e ADMINISTRADOR (inclusão do perfil fixo aprovada pelo usuário
em 14/10). ATENDENTE/OPERADOR exigem concessão explícita.
Reutilizar perfis/exceções/revisões; capacidade não concede ações nem visão global.
Toda leitura recebe `VisibilidadeLead.de(usuario)` e compõe a política de participação/RLS.
Presença de terceiros não é exposta a alcance pessoal; contagens/filas mantêm o mesmo recorte.
Falha de permissão fecha o painel e cancela consultas, sem usar papel como fallback.

## Plano de consultas

- Pool geral/bulkhead, não conexões reservadas ao envio/recebimento.
- Uma base deduplicada de ciclos abertos e finalizados do dia; projetar IDs/status/dono/horários.
- Agregar novas mensagens/leitura e resposta válida em conjunto por ciclo, com índices existentes.
  Não varrer cada histórico por cada atendente; limitar o conjunto antes de acessar mensagens.
- Uma consulta de agregação por snapshot (inclui equipe/presença); detalhes separados e paginados.
- Mesma base/predicados para agregação e drill-down; cursor determinístico e filtro validado.
- Não calcular contagens da primeira página; não consultar provedor/storage para o painel.
- Coalescer eventos pela infraestrutura E225; sem polling por card/linha. Revalidar somente visível,
  com próxima transição temporal anunciada pelo servidor e recuperação ao foco/reconexão.
- Não adicionar índice/desnormalização antes de EXPLAIN/ANALYZE local com volume e RLS reais.

## Divergências encontradas

- docs/67 ainda cita PENDENTES/POTENCIAIS no endpoint legado; o hook atual já usa inbox paginada.
- `atraso-do-atendente.ts` tem limite literal e deixa IA/SISTEMA encerrarem atraso inclusive sem
  comprovação de envio. Não reutilizar esse cálculo como regra do painel.
- Filtros de busca/etapa da lista atual são aplicados sobre páginas carregadas; não são suficientes
  para contagens e filtros completos do painel.
- Finalizados hoje é opção no menu, não aba; novo desenho precisa preservar a autorização e o menu ⋯.

## Evidências / medições locais (14/10/2026)

Aplicação real Next.js em localhost:3009 e backend em 8089, banco/Redis exclusivos desta task,
sem credenciais de provedor ou agendadores de envio. Dados e contas exclusivamente sintéticos.
`document.fonts.ready` aguardado: `Inter, "Inter Fallback", Inter, sans-serif`, fontes loaded,
zoom 1. `scrollWidth === innerWidth` em 1920 e 390; tabela móvel possui scroll interno acessível.
O print mobile inicial com sobreposição foi descartado e recapturado após ajuste.

Artefatos em `output/playwright/`: desktop revisado 1920, expansão 1920/1280 e mobile revisado
390. CLI comprovou abrir UUID correto, filtro Ana/Pendentes com 5 resultados e contagem 5,
retorno pelo título/back com o mesmo rascunho (nenhum envio). Validação final ainda em andamento.

Benchmark reproduzível: `node docs/diagnosticos/medir-painel-operacional-local.cjs --sem-jit`.
Somente container e banco descartáveis fixos; 4000 leads, 3000 abertos, 1000 encerrados históricos,
128000 mensagens. Fixture em transação com rollback; `SET LOCAL ROLE synapse_app` e contextos
GESTOR/ATENDENTE reais. Não medir como superusuário/dono. Primeira passagem **não representa
cache frio de disco** (inserção/ANALYZE já aquece buffers). Sem ganho alegado na VPS.

| Plano do snapshot | Gestão, 3 execuções | Pessoal, 3 execuções |
|---|---|---|
| Inicial, 4 laterais por ciclo | 3278 / 3197 / 2860 ms | 19558 / 14412 / 15537 ms |
| Agregação conjunta sem barreira de escopo, JIT off | 477 / 569 / 582 ms | 35487 / 34018 / 43032 ms |
| Conjunto + ciclos materializados antes do lead, JIT off | 417 / 476 / 430 ms | 247 / 268 / 252 ms |

A tentativa intermediária piorou o alcance pessoal; não foi aceita como otimização. A barreira
`ciclos MATERIALIZED` + lookup de lead `OFFSET 0` mantém ambas as RLS e evita avaliar políticas
de participação em leads estruturalmente descartados. Não criou SECURITY DEFINER, BYPASSRLS,
índice nem read model escrito. Uma comparação adicional com JIT ligado: gestão 666 ms, dos quais
244 ms de compilação (385 funções); pessoal 222 ms sem JIT acionado. O adaptador desliga JIT
**somente na transação de leitura do painel**, não no servidor/pool do chat.

Snapshot: duas leituras de negócio (parâmetro + agregado) e um `SET LOCAL`; filas idem,
independente do número de atendentes. Infra de autenticação/RLS/capacidades não entra nessa
contagem e possui suas próprias consultas/cache. Plano conjunto usa temporários locais (1767
blocos gestão / 682 pessoal na fixture): não promete custo constante com histórico ilimitado.
Não adicionar índice com base somente em tempo; nenhum índice novo foi necessário nesta rodada.
Nenhuma nova variável no Dokploy. CI ainda não verificado.

## Contratos implementados

- `GET /api/v1/atendimentos/painel-operacional`: snapshot agregado com `atualizadoEm`, limites
  do dia/fuso, prazo de espera, próxima transição temporal e equipe com contagens completas.
- `GET .../fila`: cursor opaco, máximo de 100 itens (mesmo teto da inbox), busca por nome/empresa,
  etapa, dono/entrada, recorte e sinal NOVAS/SEM_RESPOSTA. Desconhecidos/incompatíveis → 400.
- Ambas exigem `atendimentos.painel_operacional`; o recorte pessoal não retorna presença de colegas.
- V106 semeia somente `atendimentos.limite_sem_resposta_minutos = 20`, tipo INT, mínimo 1;
  máximo 2147483647 é o teto técnico do INT do contrato, não prazo de negócio adicional.
  A API administrativa de configuração existente controla alterações; nenhuma variável nova.
- Snapshot usa duas leituras (parâmetro + agregação) e SET LOCAL jit, independente da quantidade de atendentes.
  Sem Redis novo/cache global, sem alteração de outbox/envio. Filas usam o mesmo CTE/predicados.

## Decisões de apresentação / alcance

A aba ATIVOS legada segue pessoal. Um drill-down explícito do painel usa filas autorizadas por
dono, incluindo gestão, e não muda a regra da API legada. O total humano inclui Pendentes como
subconjunto (não buckets exclusivos do protótipo). Equipe inclui papéis humanos ativos atuais
e mantém responsáveis inativos com filas para não esconder trabalho; inativos não contam como
elegíveis/online. Fila sem dono é linha própria, sem dados sintéticos.

Atividade de fila aberta é última mensagem no ciclo; fila finalizada ordena por encerramento,
sem buscar novamente conteúdo/histórico para ordenar. Depois do LIMIT, enriquecimento de no máximo
101 itens fornece etapa, canal e prévia textual limitada aos 120 caracteres do contrato existente
de citação. Mídia fornece somente tipo, não conteúdo/URL. Empates de mensagem usam (horário, UUID).
Leitura operacional do dono para gestão reutiliza a política SELECT da V89, sem ampliar escrita.

Presença reutiliza o registrador oficial: domínio publica somente quando há mudança real,
listener AFTER_COMMIT enfileira aviso efêmero em worker isolado/limitado pelo parâmetro existente
de tempo real. Usa o mesmo Redis pessoal, sem dados de colegas/leads no aviso. Destinatários são
gestão ativa e o próprio usuário, sempre com capacidade efetiva; delegação limitada não recebe
presença de colegas. Falha de Redis não impede presença/chat: foco/reconexão recupera snapshot.
Não é fila de comandos nem garantia durável; não acrescenta polling, outbox de envio ou endpoint.

## Bugs / pendências de validação

- Fixture local inicial dev+seed antes do runner V73 não era compatível com o runner: o banco
  sintético foi preparado por migração suportada sem seed antecipado. Nenhuma produção alterada.
- O seed local não fornece logo própria; GET logo retorna 404 e o fallback configurado funciona.
  Sem credenciais Meta, templates retornam 503. Não é homologação de envio real.
- Windows Node 24.12 encerra processos filhos dos testes de provisionamento com 3221226505;
  arquivo preexistente fora da task. Não silenciar/excluir do CI; conferir execução Linux da PR.
- Testes específicos de empate/limites/filtros concorrentes ainda precisam completar a matriz.
- Não declarar aprovação/fidelidade visual do original sem a comparação autorizada disponível.

## Matriz de fechamento e evidências compartilháveis

Todos os arquivos de imagem abaixo são da aplicação real, com contas/dados sintéticos.
Não são uma reprodução HTML e não contêm conversas de clientes.

| Requisito | Evidência / limite |
|---|---|
| Seis indicadores, filtros de equipe, fila de entrada e uma expansão | Testes de componentes e screenshots após refinamento; números reais da fixture. |
| Busca/etapa/dono/recorte no servidor, cursor | `PainelOperacionalIT`: 65 cartões, páginas de 20, sem duplicidade, contagens completas; incompatíveis retornam 400. |
| Gestão, autorização direta, delegação sem visão global | IT chama Gestão para conceder/revogar com o mesmo JWT; API recusa sem capacidade e retorna vazio ao forçar colega. Política cobre teto/padrão dos papéis. |
| Retornar pelo título sem perder rascunho | Playwright: abriu UUID terminado em 003, preencheu rascunho, clicou no título; URL voltou a `/atendimentos`; back recuperou conversa e rascunho. Nenhum envio. |
| Header/tabela expandida conforme terceira referência | Comparação estática e segunda rodada de ajustes; metadados ausentes não inventados. Aprovação visual final ainda é do usuário. |
| Desktop/menor/celular | 1920×1080, 1280×900 e 390×844; Inter carregada, zoom 1. No celular `scrollWidth=innerWidth=390`; tabela tem rolagem própria, não a página inteira. |
| Respostas fora de ordem / revogação / busca rápida | Teste mantém fila Bruno após resposta antiga Ana; revogação esconde dados; digitação intermediária não faz request e esconde resultados antigos. |
| Atraso e filas atualizam com o tempo | Relógio falso testa dois observadores, uma nova leitura do snapshot e uma da fila no prazo; corrigido refetch duplicado após resposta instantânea e invalidação da fila. Não há timer por atendente. |
| Eventos e presença | STOMP/serviço de notificações e IT da API de presença; listener transacional só publica após commit, sem dados de colegas; rollback não publica. |
| Sem N+1, isolamento do chat | Um agregado e um parâmetro por leitura, pool geral, plano com RLS; enriquecimento depois de LIMIT; nenhuma chamada a provedores ou mutations no painel. |
| Matriz avançada de concorrência | Ainda não há teste específico de toda combinação transferência/finalização/reabertura simultânea com mudança de filtro. Não declarar essa cobertura como concluída. |
| Performance de produção | Não medida. Planos locais versionados, sem promessa de latência/CPU na VPS ou autorização de ativação em horário comercial. |

### Capturas

- [Primeira rodada, antes do refinamento](evidencias/painel-operacional/painel-desktop-primeira-rodada-1920.png).
- [Depois, painel 1920](evidencias/painel-operacional/painel-entrega-1920.png).
- [Depois, expansão 1920](evidencias/painel-operacional/painel-entrega-expandido-1920.png).
- [Depois, expansão 1280](evidencias/painel-operacional/painel-entrega-expandido-1280.png).
- [Depois, celular 390](evidencias/painel-operacional/painel-entrega-mobile-390.png).
- [Planos locais sanitizados e reproduzíveis](evidencias/painel-operacional/planos-local.json).

Contraste medido nas barras da identidade local (texto branco): novas 4,74:1; ativos 4,53:1;
pendentes 4,53:1; sem resposta 5,44:1. Não certifica automaticamente todos os temas configuráveis.

Ação necessária no Dokploy: nenhuma variável nova. V106 segue o fluxo normal de migrations;
nenhum deploy ou alteração de produção foi feito. Manter a PR em draft enquanto houver itens
de validação explicitamente pendentes; resultados finais de CI/testes ficam também na PR.

## Refinamento solicitado nas três screenshots

Antes → depois: lista textual de contagens → barras semânticas preenchidas; várias linhas
expandidas → uma por vez; cartões simples na expansão → tabela aninhada com as seis colunas
do modelo; contagens ao lado dos rótulos → acima; avatares redondos → quadrados arredondados
somente neste painel/lateral. Título Atendimentos é botão acessível de retorno.

A sidebar autorizada usa a mesma fila paginada e critérios do painel, com metadados mínimos
reais, em vez de tentar completar o visual com cartões legados incompletos. Chat Interno permanece
no contrato e componente próprios, intercalado por atividade, sem entrar nas contagens de clientes.
Busca textual no servidor agrupa digitação por 300ms (cadência técnica de interface, não prazo de
negócio); consultas usam AbortSignal e chave por critério/usuário/revisão. Durante a digitação,
esconde resultados/contagens do critério anterior. Não adiciona polling.
Ao filtrar a lateral a partir de Novas/Sem resposta da expansão, o critério equivalente é
`EM_ABERTO + sinal`: mantém a aba Todos selecionada e o chip correto, sem tab inexistente.

Não copiar departamentos, empresas ou nomes fictícios do mockup: sem departamento no contrato,
a linha informa o papel real. A navegação global e trilho do Chat Interno não foram redesenhados,
conforme limite original. Cores seguem o tema: pendente escurece o token de atenção e finalizado
mistura sucesso/primária, receitas CSS limitadas a `.painel-operacional`; fonte não foi alterada.

Nova medição com projeção completa e teste/build concorrentes na máquina: snapshot gestão
1212/1574/746ms, pessoal 596/1417/434ms; fila de 51 gestão 1270/1215/1166ms, pessoal
488/495/546ms. Não comparar esses números como ganho isolado: há contenção local diferente.
O plano confirma enriquecimento posterior ao limite; nenhuma extrapolação para VPS/produção.
