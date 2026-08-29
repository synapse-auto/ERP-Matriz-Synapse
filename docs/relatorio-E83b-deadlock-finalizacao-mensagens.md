# E83b — Correção do deadlock entre finalização e mensagens

Data: 28/08/2026. Correção da E83 existente, ainda sem autorização de
commit/push/merge/deploy. Não é implementação nova do webhook.

## 1. Commit e estado

- Worktree: `C:/Users/marcondes/Desktop/projeto_matriz-e83`.
- Branch: `codex/e83-webhook-avaliacao`.
- Base original da E83: `aed6f16d711ec39cc3cdfc62a93dc1653a435157`.
- HEAD ao iniciar esta correção: `0ba32863ec574fed70286fa56e1e6457dbd38c36`
  (`checkpoint before checking out fix/criar-template-whatsapp`). Working tree
  estava limpa: as 27 alterações da E83 já estavam nesse commit local, não
  como arquivos não rastreados. Nenhuma operação Git em andamento.
- `origin/main` havia avançado para `d5ba368` (7 commits alheios). Não
  incorporados. Worktrees irmãos (`main`, `hmlgc`, `fixtwo`, E80, E82, etc.)
  não foram tocados.
- **Commit desta correção não criado; push, merge, rebase e deploy não
  realizados.** CI **não verificado** (não há run deste SHA).
- Frontend não alterado.

Arquivos desta correção (além do checkpoint E83):

```text
M backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/persistencia/AtendimentoRepositorioJdbc.java
M backend/crm-app/src/test/java/com/synapse/crm/app/atendimento/WebhookAvaliacaoIT.java
M docs/35-runbook-webhook-avaliacao.md
M docs/relatorio-E83-webhook-avaliacao.md
A docs/relatorio-E83b-deadlock-finalizacao-mensagens.md
```

## 2. Definição de pronto e evidências

- ✅ **Deadlock reproduzido antes da correção.** Teste
  `recebimentoPausadoAntesDoContador_eFinalizacaoNaoEntramEmDeadlock`: POST
  autenticado de finalizar + cadeia real `POST /webhook/canal` +
  `ProcessadorDeWebhookEntrada.processarPendentes()`. Pausa determinística
  depois do INSERT de `mensagem` e antes do `UPDATE` do lead, só depois que a
  finalização obteve o lock deste lead. Com `FOR UPDATE`, 28/08/2026 22:49-03:
  HTTP 500, `PessimisticLockingFailureException` na query
  `SELECT ... FROM atendimento WHERE id = ? FOR UPDATE`, causa
  `PSQLException: ERROR: deadlock detected` (ciclo entre os pids 65 e 66,
  “while locking tuple (0,1) in relation atendimento”). Failsafe: 1 teste,
  1 falha, 0 erros/skips. Não houve retry, sleep nem aumento de timeout.
- ✅ **Regra de locks.** `porIdParaAlteracao` passou a `FOR NO KEY UPDATE`.
  O upsert `salvar` altera `atendente_id`, `status` e `finalizado_em` — nenhuma
  é a PK `atendimento.id` referenciada pela FK de `mensagem` (V5), nem pelas
  FKs de `mensagem_automacao_idempotencia` e `comando_automacao_idempotencia`.
  `idx_atendimento_atendente_status` não é único. A guarda
  `WHERE atendimento.status <> 'FINALIZADO'` permanece. Escritores entre si
  continuam serializados (`duasFinalizacoes…`, transferência nas duas ordens,
  envio manual).
- ✅ **Mesmo teste após a correção, sem retries ocultos.** 28/08/2026 22:51-03:
  `WebhookAvaliacaoIT#recebimentoPausadoAntesDoContador…` 1/1, 35,90 s,
  BUILD SUCCESS. Classe completa em seguida: **34/34**, 54,20 s, zero
  falhas/erros/skips.
- ✅ **Matriz de pontos de entrada (IDs próprios, latches, não condição global
  de lock):**
  1. Recebimento via webhook HTTP + `processarPendentes` iniciado antes da
     finalização HTTP: sem deadlock, 1 mensagem, contador `num_mensagens` +1,
     webhook `processado_em` preenchido e `tentativas = 0`, 1 intenção.
  2. Recebimento após finalização confirmada: abre outro atendimento; snapshot
     da pesquisa anterior inalterado; mensagem nova não grava no encerrado.
  3. `POST /internal/v1/atendimentos/{id}/responder` (X-Synapse-Token) ×
     finalização, partindo de `EM_IA`: ambos 200, 1 mensagem, 1 outbox de envio,
     0 intenção (sem responsável). Segunda resposta após o fechamento = 409;
     não há direito novo da IA de responder depois.
  4. `POST /internal/v1/atendimentos/{id}/mensagens-enviadas` × finalização:
     1 mensagem, idempotência por wamid na repetição, 0 outbox de envio (não é
     ordem de enviar outra).
  5. Transferência HTTP × recebimento webhook: responsável = Bruno, 1 mensagem,
     0 intenção, timeline de transferência.
  6. Lote HTTP × recebimento no mesmo lead: `finalizados:1` / `recusados:1`,
     0 intenção, atomicidade dos itens recusados preservada.
  7. Disputas E83 (duas finalizações, transferência nas duas ordens, envio
     manual) continuam verdes nesta classe.
  8. Negativos E83 reexecutados na mesma classe: RLS/colega, rollback de
     outbox, gestor ≠ responsável, lote de 1 item, sem responsável, lease
     concorrente/expirada, resultado tardio, esgotamento, timeout HTTP,
     isolamento do HTTP.
- ✅ **Suíte completa do reator.** `cd backend; .\mvnw.cmd spotless:apply clean verify`,
  Java 21.0.12, Docker/Testcontainers ativos, código de saída 0, **23:01:49 -03:00**,
  **5min58s**. Spotless check em todos os módulos (0 arquivos reformatados).
  ArchUnit `ArquiteturaTest` **8/8**. Surefire **157** testes unitários; Failsafe
  **418** ITs (`failsafe-summary.xml`: completed=418, errors=0, failures=0,
  skipped=0, flakes=0). Total **575**, zero falhas. `WebhookAvaliacaoIT` **34/34**
  neste reator (33,31 s). `AvaliacaoAtendimentoIT` 6/6, `RlsIsolamentoIT` 11/11,
  `OpenApiIT` 3/3. `git diff --check` limpo.
  Isto não é CI: **CI não verificado** (sem run no GitHub).
- ✅ **HTTP da avaliação fora da transação; lease/retry/circuito não
  alterados.** Nenhuma variável Dokploy nova. As 14 opcionais da E83
  permanecem.
- ✅ **Sem commit/push/deploy; sem n8n/WhatsApp reais; sem segredo no diff.**

### O que esta IT exercita e o que não exercita

Exercitados: finalizar individual HTTP, finalizar-lote HTTP, transferir HTTP,
webhook de entrada HTTP + job `processarPendentes`, responder da automação
HTTP, registrar mensagem já enviada HTTP, isolamento transacional do caso de
uso de recebimento, publisher de avaliação pelo ponto `@Scheduled`.

Não exercitados nesta correção: WhatsApp/n8n reais, frontend, workflow, payload
Meta com várias mensagens (E32), smoke RLS em homologação.

## 3. Decisões técnicas

1. **`FOR NO KEY UPDATE` só em `porIdParaAlteracao`.** O checkpoint local da E83
   já trazia esse modo (mistura antecipada da correção, sem a matriz). Esta
   etapa restaurou `FOR UPDATE` só para o vermelho, reproduziu `40P01` na IT
   real, e restaurou `FOR NO KEY UPDATE` com o comentário das colunas/FK/upsert.
   `LeadNoCaminhoDeMensagemJdbc` continua `FOR UPDATE`.
2. **Não inverter a ordem para atendimento → lead.** O envio manual trava o
   lead primeiro; inverter só a finalização recriaria ciclo com ele.
3. **Pausa no spy da porta real `registrarInteracao`**, depois do INSERT JDBC
   verdadeiro, sem mock que inventa `40P01`. Coordenação por `CountDownLatch`
   no lead desta fixture, não por `pg_stat_activity` global.
4. **Não capturar deadlock como sucesso.** O vermelho foi HTTP 500 com a
   exceção real; o verde é 200 + efeitos persistidos.

Skills: `coding-standards`, `backend-patterns`, `api-design` e
`database-migrations` lidas antes da edição. Não havia
`supabaseboaspraticas` / `architecture-patterns` / `clean-code` neste
ambiente; a correção segue hexagonal, porta JDBC e migration intocada.

## 4. Divergências

- O prompt E83b descreveu working tree com 27 arquivos não commitados em
  `aed6f16`. A realidade era o checkpoint local `0ba3286` (mesmo conteúdo E83,
  já commitado localmente para outro checkout). Trabalho preservado; não
  rebaseado em `origin/main`.
- `docs/13` continua em E58.
- O diagnóstico JDBC em schema reduzido (três trials `FOR UPDATE` = `40P01`,
  três `FOR NO KEY UPDATE` = COMMIT) **não** é IT do CRM nem incidente de
  produção. A evidência desta etapa é a Failsafe acima.
- `PublicadorDeRepasseWebhookOperacoes` ainda faz HTTP em transação; fora do
  escopo.

## 5. Bugs encontrados

- O `AfterEach` da IT da E83 não dava `reset` no spy de
  `LeadNoCaminhoDeMensagem` nem limpava `webhook_entrada` /
  tabelas de idempotência. Vazamento de stub quebraria testes seguintes.
  Corrigido nesta classe.
- O caminho de recebimento já aberto não trava o lead antes do INSERT; o teste
  de envio manual da E83 não cobria isso. Confirmado.
- Constraint canônica de telefone vs ACL (E83): não alterada.

## 6. Fora / limitações

Sem publicação, sem n8n, sem WhatsApp, sem alteração de workflow, payload,
headers, lease, circuito, autorização ou CSAT 1–5. Sem edição de migration
aplicada. Sem Java > 21.

## 7. Pendências do operador

- Autorizar commit/push desta correção junto com a E83, se quiser publicar.
- **Dokploy: nenhuma variável nova.** As 14 `AUTOMACAO_AVALIACAO_*` da E83
  continuam opcionais (`${VAR:-}`). Ativação permanece etapa separada.
- Antes de ativar: URL/header/segredo seguros, Dylan confirmar deduplicação
  persistente por modo + `atendimento_id` (não afirmado aqui), rotacionar a
  credencial que foi compartilhada no chat. Não repetida neste relatório.
- **CI não verificado.**
