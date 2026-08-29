# E83 — Entrega local do webhook de avaliação

Data: 28/08/2026. Escopo: versão aprovada do prompt E83, com as três regras de
produto já decididas. Este relatório distingue testes locais de operação real.

## 1. Commit e estado

- Worktree exclusivo: `C:/Users/marcondes/Desktop/projeto_matriz-e83`.
- Branch: `codex/e83-webhook-avaliacao`, criada da `origin/main` conferida após fetch.
- Base e HEAD, antes/depois: `aed6f16d711ec39cc3cdfc62a93dc1653a435157`.
- Estado inicial limpo; estado final: 27 arquivos da E83/E83b (12 modificados e 15 novos),
  sem staging. Nenhuma alteração de outro worktree foi incorporada ou descartada.
- **Commit não criado; push, merge e deploy não realizados**, conforme restrição
  explícita do prompt. Portanto, esta implementação ainda não está no origin.
- A árvore original estava em `feat/novo-contato-whatsapp`, HEAD `8c068c9`, diferente
  do `dca54e2` citado no levantamento. Foi preservada; não serviu de base para a E83.
- **CI não verificado**: não existe run deste conteúdo não commitado.

Arquivos (caminhos relativos ao worktree acima):

```text
M .env.example
M README.md
M backend/crm-app/src/main/resources/application.yml
A backend/crm-app/src/main/resources/db/migration/V44__reserva_webhook_avaliacao.sql
A backend/crm-app/src/test/java/com/synapse/crm/app/atendimento/WebhookAvaliacaoIT.java
A backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/application/AtendimentoParaAlteracao.java
M backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/application/AtendimentoRepositorio.java
M backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/application/FinalizarAtendimentoUseCase.java
M backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/application/FinalizarAtendimentosVisiveisUseCase.java
A backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/application/OutboxDeAvaliacao.java
A backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/application/SolicitacaoDeAvaliacao.java
M backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/application/TransferirAtendimentoUseCase.java
A backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/avaliacao/AvaliacaoExecutorConfig.java
A backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/avaliacao/AvaliacaoOutboxTransacoes.java
A backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/avaliacao/AvaliacaoWebhookHttp.java
A backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/avaliacao/AvaliacaoWebhookProperties.java
A backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/avaliacao/OutboxDeAvaliacaoJdbc.java
A backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/avaliacao/PrepararAvaliacaoDeEncerramento.java
A backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/avaliacao/PublicadorDeAvaliacao.java
M backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/persistencia/AtendimentoRepositorioJdbc.java
M backend/crm-atendimento/src/test/java/com/synapse/crm/atendimento/application/TransferirAtendimentoUseCaseTest.java
A backend/crm-atendimento/src/test/java/com/synapse/crm/atendimento/infrastructure/avaliacao/AvaliacaoWebhookTest.java
M backend/crm-core/src/main/java/com/synapse/crm/core/application/lead/LeadNoCaminhoDeMensagem.java
M backend/crm-core/src/main/java/com/synapse/crm/core/infrastructure/persistencia/lead/LeadNoCaminhoDeMensagemJdbc.java
M docker/dokploy-stack.yml
A docs/35-runbook-webhook-avaliacao.md
A docs/relatorio-E83-webhook-avaliacao.md
```

## 2. Definição de pronto e evidências

- ✅ **Decisões do bloco 0**: individual elegível gera pesquisa; lote (inclusive um
  item) e sem responsável não geram; o responsável, não o gestor executor, recebe a nota.
- ✅ **Intenção durável/idempotente**: `postIndividualDoGestor_capturaResponsavelContratoExatoEColetaInterna`
  exerce o POST autenticado, confirma finalização, uma linha de outbox, seis campos
  exatos e header capturados no servidor HTTP local. Repetição retorna 409 sem outra intenção.
  `chaveDuravelNaoReescreveSnapshot_eNovoAtendimentoDoMesmoLeadPodeGerarPesquisa` prova
  que a mesma chave não muda o snapshot e um novo atendimento pode gerar outra pesquisa.
- ✅ **Lote e atribuição**: `loteReal_mesmoComUmItem_naoCriaPesquisaNemRetroatividade`
  executado com 1 e 3 itens; confirma eventos preservados e nenhuma pesquisa, inclusive
  após repetição individual. `loteComItemRecusado_preservaOsOutrosSemPesquisa` e
  `loteComFalhaNoSegundoItem_fazRollbackSemNotificacao` cobrem recusa e rollback.
  `semResponsavelOuTelefone_naoCriaPesquisaNemAtribuiQuemClicou` cobre sem dono,
  sem telefone e telefone inválido, sem impedir encerramento.
- ✅ **Contrato e segredo privado**: POST local com Content-Type application/json,
  `crm-synapse-marc-auth` configurável e sem assinatura Meta. Payload sem token/URL.
  `AvaliacaoWebhookTest` confirma serialização/toString sem segredo, configuração
  incompleta inválida sem rede e payload inválido recusado. Credenciais dos testes
  são sintéticas; a credencial compartilhada na conversa não foi copiada.
- ✅ **Isolamento do n8n**: `httpBloqueado_naoReteveTransacaoNemImpedeMensagemNormal_eTimeoutRepeteMesmoId`
  confirma finalização antes do job, fake HTTP bloqueado por latch, zero conexões
  `idle in transaction`, envio de mensagem pelo publisher normal durante a espera,
  timeout finito e retry com o mesmo payload. Não é medição de latência em produção.
- ✅ **Concorrência/rollback/privacidade**: `duasFinalizacoesComLeituraAntiga_umaVencedoraUmaIntencao`
  força leituras antigas simultâneas: uma vence, a outra recebe 409. O teste de
  transferência concorrente executa as duas ordens de vitória; o de envio concorrente
  prova novo atendimento sem alterar o snapshot encerrado. O upsert recusa cópia antiga.
  `erroDepoisDeGravarIntencao_fazRollbackDaFinalizacaoEDaOutbox` injeta falha após INSERT.
  `colegaESemAutenticacao_naoMudamNadaNemEnfileiram` verifica HTTP negativo e a RLS no
  banco sob `synapse_app`, sem nova intenção ou HTTP externo.
- ✅ **Deadlock E83b**: `recebimentoPausadoAntesDoContador_eFinalizacaoNaoEntramEmDeadlock`
  pausa o recebimento real depois do INSERT de mensagem, aguarda a finalização obter o
  lock do lead e então libera as duas transações. Resultado 28/28, sem 40P01, sem perda
  ou duplicação, atendimento finalizado e uma intenção. O diagnóstico anterior em schema
  reduzido observou 40P01 com `FOR UPDATE`; ele não é apresentado como execução de produção.
- ✅ **Lease/retry**: ponto de entrada `publicarPendentes`, o próprio método `@Scheduled`,
  é chamado nos testes; agendamento global permanece desligado em `PostgresIT`.
  `leaseConcorrente_expiraRecuperaERecusaResultadosAntigos` força duas reservas,
  expiração e rejeita sucesso/falha atrasados. Falhas 429/500/503 respeitam backoff e
  esgotam; 301/401/403/422 esgotam na primeira tentativa, sem redirect/vazamento de
  resposta em logs. Morte repetida na reserva chega ao limite sem apagar a linha.
  Circuito aberto e payload corrompido não enviam HTTP.
- ✅ **CSAT preservado**: retorno real no endpoint interno com token correto, nota 5,
  responsável original; sem token, token errado e JWT humano recusados, nota 6 = 400,
  duplicata = 409. `AvaliacaoAtendimentoIT` existente também passou (6 testes).
- ✅ **Configuração/runbook**: defaults opcionais e documentação em quatro arquivos de
  configuração + `docs/35-runbook-webhook-avaliacao.md`. Ausência de configuração
  é observável na finalização; pausa não marca pendências como entregues.
- ✅ **Verificação completa local**: `mvnw.cmd spotless:apply clean verify` no reator
  inteiro terminou com BUILD SUCCESS; 569 testes, zero falhas/erros/ignorados.
  `git diff --check` passou. Não equivale a CI verde.
- ✅ **Frontend/contratos**: frontend e publishers legados não alterados. Nenhuma
  operação HTTP nova; `OpenApiIT` passou 3/3 e continua exigindo 135 operações.
- ✅ **CI/deploy honestos**: somente execução local com fake HTTP e Testcontainers;
  sem CI deste diff, publicação, pesquisa real ou alteração de workflow.

### Execuções

- Java `21.0.12`; Docker ativo; PostgreSQL real via Testcontainers. Migration V44
  aplicada no banco descartável da suíte, nunca em homologação/produção.
- Reator completo: `cd backend; .\mvnw.cmd spotless:apply clean verify`, código de
  saída 0, concluído às **16:50:08 -03:00** em **5min37s**. Todos os 8 módulos e
  o agregador terminaram SUCCESS. XMLs Surefire: **157 testes**; Failsafe:
  **412 testes**, zero falhas, erros, skips ou flakes. Spotless e ArchUnit incluídos.
  Evidência local em `backend/*/target/surefire-reports/TEST-*.xml` e
  `backend/crm-app/target/failsafe-reports/{TEST-*.xml,failsafe-summary.xml}`.
- Nova suíte: `WebhookAvaliacaoIT` **28/28** (inclui a regressão determinística
  recebimento × finalização); `AvaliacaoWebhookTest` **19/19**.
- Regressões verificadas no reator: `AvaliacaoAtendimentoIT` 6/6,
  `CanalWhatsAppIT` 19/19 (classes aninhadas), `RepasseWebhookAutomacaoIT` 2/2,
  `RepasseWebhookAutomacaoDesabilitadoIT` 1/1, `RlsIsolamentoIT` 11/11,
  `OpenApiIT` 3/3, `SchemaMigracoesIT` 15/15 e `ArquiteturaTest` 8/8.
- O teste legado de repasse confirma corpo/assinatura byte a byte no destinatário
  fake e mensagem recebida mesmo com automação indisponível; não foi reescrito.
- Antes do reator, seleção dirigida passou 44 testes (24 IT da primeira versão,
  19 unitários novos e 1 regressão de transferência). A versão final acrescentou
  três casos de integração e asserções de sanitização ao reator completo; a E83b
  acrescentou a regressão determinística de recebimento × finalização.
- A primeira execução dirigida revelou duas questões nas fixtures: telefone curto
  barrado pela constraint antes da ACL, e relógio controlado anterior ao INSERT da
  outbox legada. Corrigidas as fixtures; não houve redução de asserção nem aumento
  de timeout. Nenhum `Thread.sleep` foi introduzido.
- Compose validado com `docker compose --env-file .env.example -f docker/dokploy-stack.yml config --quiet`
  e valores sintéticos, somente no processo, para variáveis obrigatórias preexistentes.
  A primeira tentativa sem eles recusou `N8N_DB_NAME`, já obrigatório antes da E83.
- Não executados: frontend (não alterado), workflow n8n real, WhatsApp real, deploy e CI.

## 3. Decisões de implementação

1. **Origem fechada no backend**: enum privado INDIVIDUAL/LOTE e entrada própria
   `executarEmLote`, com a mesma autorização e transação. Nenhum booleano HTTP novo.
   O evento de finalização existente continua sendo publicado nos dois casos.
2. **Locks lead → atendimento**, com releitura protegida pela RLS. O envio manual
   já adquire o lead primeiro; aplicar a ordem inversa à finalização criaria disputa
   circular. Transferência humana/automação usa o mesmo carregamento protegido,
   sem mudar distribuição/autorização. O upsert recebe guarda de estado terminal,
   para impedir que cópias antigas alterem responsável/instante vencedores.
3. **Outbox própria, tabela existente**: porta explícita e tipo
   `automacao.avaliacao.iniciar`. UUID determinístico de tipo + atendimento; não por
   lead/telefone. A captura do contato usa a porta existente sob a transação do chat.
   Falha ao persistir intenção reverte a finalização, com diagnóstico sem erro SQL bruto.
4. **V44** acrescenta UUID de reserva anulável e índice parcial da nova fila.
   O token distingue donos sucessivos da mesma intenção; token + prazo protegem a
   gravação do resultado. Não há backfill, alteração de RLS ou migration antiga.
   O DDL adquire locks normais de migration; esta execução não mede duração no banco real.
5. **Tentativas contadas na reserva**, não só ao retornar da rede. Assim, processo
   morrendo repetidamente não produz retry infinito. Expiração permite nova reserva;
   esgotamento mantém payload/diagnóstico inspecionáveis e nunca simula publicação.
6. **Worker isolado e fila limitada**: tick apenas agenda; a reserva ocorre quando o
   worker começa, não enquanto espera na fila. Rede fora da transação e fora do
   `ContextoDeServico`; contexto de serviço só envolve as curtas operações de banco.
   Executor não usa CallerRuns; não herda pool/limites do WhatsApp.
7. **HTTP com prazo total**, inclusive corpo, redirect desabilitado e circuit breaker
   próprio. 2xx só confirma recepção HTTP. 408/429/5xx/timeout/transporte são recuperáveis;
   outros status são permanentes. Não há cabeçalho novo de idempotência presumido.
8. **Configuração**: URL/token/header ausentes ou inválidos desabilitam a integração,
   preservando o encerramento. Limites explicitamente inválidos (ex.: lease ≤ timeout,
   concorrência zero) falham no binding para não operar sem limites seguros; os
   defaults são válidos. Toda opção continua dispensável para boot/deploy.
9. **Telefone**: ACL exige 10–15 dígitos ASCII, primeiro não zero, sem inferir país
   ou renormalizar o domínio. Não comprova existência/opt-in do número na Meta.
10. Skills clean-code, architecture-patterns, api-design-principles e
    supabaseboaspraticas não estavam disponíveis. Usados os padrões locais já
    existentes; nenhuma skill disponível era necessária para este escopo backend.

## 4. Divergências de documentação

- `docs/13` ainda resume E58. Não foi usado para inferir inexistência dos contratos recentes.
- O evento em memória já existente atendia timeline/auditoria/tempo real, mas não
  equivalia a intenção durável nem implementava o POST de avaliação.
- `PublicadorDeRepasseWebhookOperacoes` ainda envolve HTTP em transação. Foi
  preservado integralmente; o novo fluxo segue reserva/rede/resultado separados.
- A branch/HEAD do worktree original mudou desde a inspeção do prompt. A E83 usa
  somente a base remota confirmada e seu worktree dedicado.

## 5. Bugs/riscos encontrados

- **Corrigido neste escopo:** upsert irrestrito permitia a cópia antiga sobrescrever
  atendimento finalizado. Proteção de domínio sequencial não eliminava a corrida.
- **Corrigido no carregamento de finalização/transferência:** falta de coordenação
  com a ordem de bloqueio do envio. Há testes das duas ordens transferência × finalização.
- **Corrigido na E83b:** `FOR UPDATE` conflitava com o `KEY SHARE` da FK durante a
  mensagem recebida, formando ciclo lead → atendimento → lead. `FOR NO KEY UPDATE`
  mantém a serialização necessária do agregado e o teste determinístico reproduz o
  ponto de entrada sob transações reais; não houve retry, sleep ou timeout ampliado.
- **Fora do escopo:** outbox legada grava `Instant.now()` sem Clock injetado, enquanto
  o publisher usa Clock. Ficou evidente na fixture de envio simultâneo; o teste
  alinha o relógio após a escrita, sem esperar/reexecutar o publisher para mascarar.
- **Fora do escopo:** constraint canônica do telefone admite primeiro dígito zero;
  o fixture `000000000000` chega à ACL e é recusado sem bloquear encerramento.
  Não alterada a regra global de telefone nem reconciliados dados reais.
- Entrega pode repetir se n8n aceitar e a resposta se perder. Não foi verificado se
  o workflow possui deduplicação persistente; ativar sem ela pode duplicar pesquisa.

## 6. Fora do escopo / limitações

Nenhum webhook real foi chamado e nenhuma pesquisa foi enviada. Testes usam UUIDs,
telefones e segredos sintéticos, POSTs reais somente para servidores locais e canal
fake. Não comprovo entrega WhatsApp, coleta pelo workflow, opt-in ou template real.

Não alterados: frontend, contratos públicos/escala CSAT, políticas RLS, filas e
limites legados, workflow n8n, dados de produção ou permissões comerciais. Não criada
API/script de replay, survey retroativa ou migração de históricos. O repasse legado
em transação continua uma dívida separada. Nenhuma credencial foi rotacionada.

## 7. Decisões/ações necessárias do operador

- Autorizar separadamente commit/push e, depois, deploy/teste controlado se desejar
  publicar. As regras individuais/lote/atribuição já estão decididas, sem nova pergunta.
- **Antes de ativar no Dokploy**, configurar `AUTOMACAO_AVALIACAO_URL` (exemplo seguro:
  `https://automacao.example.test/webhook/avaliacao`), `AUTOMACAO_AVALIACAO_AUTH_HEADER`
  (`crm-synapse-marc-auth`) e `AUTOMACAO_AVALIACAO_TOKEN` (segredo no ambiente seguro,
  sem exemplo de valor no relatório). Sem isso o CRM sobe com avaliação desligada.
- As outras 11 variáveis novas e respectivos defaults estão na tabela completa do
  [runbook](35-runbook-webhook-avaliacao.md#configuração--ação-necessária-no-dokploy):
  TIMEOUT, RESERVA_EXPIRACAO, LOTE, CONCORRENCIA, FILA, MAXIMO_TENTATIVAS,
  BACKOFF_INICIAL, BACKOFF_MAXIMO, MINIMO_CHAMADAS_CIRCUITO, ESPERA_CIRCUITO e
  INTERVALO_MS, todas com prefixo `AUTOMACAO_AVALIACAO_`.
- Dylan precisa confirmar deduplicação persistente por **modo + atendimento_id
  antes do envio**. O CRM oferece pelo menos uma vez dentro da política de tentativas,
  não exatamente uma vez.
- Recomendada rotação coordenada da credencial anteriormente compartilhada; não
  executada. Pausa/retomada e qualquer backlog exigem inspeção e autorização própria.
- **CI não verificado; nenhuma run ou imagem nova publicada por esta etapa.**
