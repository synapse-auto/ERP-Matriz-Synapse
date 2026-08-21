# 04. Architecture Decision Records (ADRs) e Contrato de API

> **Regra de evidência:** Um endpoint só é documentado aqui com evidência nomeada: o controller que o implementa e o teste que o cobre. Contrato planejado não mora neste documento.

## Parte A — ADRs

### ADR-001 — Monólito modular em vez de microsserviços no MVP

**Contexto:** volume esperado ~5 mil atendimentos/mês (RNF-CRM-08), equipe de desenvolvimento provavelmente pequena/média, prazo de entrega apertado para um CRM "sob medida".
**Decisão:** monólito modular (Clean Architecture por bounded context), banco único PostgreSQL.
**Consequências:** deploy e operação simples; módulos com fronteira clara (portas/interfaces) permitem extrair qualquer um como serviço separado depois, se o volume ou a equipe crescerem — sem reescrever regra de negócio, só trocar o adaptador de infraestrutura.

### ADR-002 — Integração da Automação sem bloquear o caminho humano

**Contexto:** RNF-CRM-01 é a "ultra-regra": a aba Atendimentos não pode parar entre 08:00–18:30, mesmo que a Automação/IA falhe.
**Decisão:** envio e recebimento humanos não chamam a Automação. Os comandos síncronos do n8n ficam em `/internal/v1`, autenticados por `X-Synapse-Token`/`ROLE_SERVICO`: `responder`, `transferir`, `modo-ia` e `transferir-proximo-humano`. A resposta da IA grava `Remetente.IA` e a intenção na outbox na mesma transação, sem chamada ao provedor durante o request; transferência explícita aceita apenas usuário ativo com papel `ATENDENTE`, e a rota de próximo humano escolhe por nome e id. Cada escrita exige `Idempotency-Key` persistida. Telemetria e invalidação de configuração permanecem desacopladas.
**Consequências:** falha ou lentidão da IA não bloqueia o atendimento humano. A Automação recebe sucesso ou falha imediata, não pode distribuir comissão por um UUID arbitrário e suas ações aparecem na timeline/auditoria como `ator_tipo = AUTOMACAO`, com `ator_id` nulo.

### ADR-003 — Filtros modulares como JSONB genérico

**Contexto:** RF-CRM-04/05/40 pedem o mesmo mecanismo de filtro combinável reaproveitado em três telas diferentes, com contagem em tempo real.
**Decisão:** representar o filtro como uma árvore de condições em `JSONB` (`filtro_modular.criterios`), interpretada por um *query builder* no backend, em vez de modelar cada tipo de filtro como tabela própria.
**Consequências:** novos critérios de filtro não exigem migração de schema; contrapartida é validar o JSON na aplicação (schema de critérios permitidos) para evitar injeção de condições arbitrárias.

### ADR-004 — Configuração de automação como chave-valor tipado

**Contexto:** RF-CRM-38a-e exigem que todo parâmetro numérico/temporal da automação seja editável no CRM e aplicado sem novo deploy (RN-CRM-07).
**Decisão:** tabela `configuracao_automacao` chave-valor com tipo, unidade e faixa (min/max) declarados por linha, cache Redis com invalidação por evento.
**Consequências:** adicionar um parâmetro novo é uma migração de dados (INSERT), não uma migração de schema nem deploy de código; exige disciplina de nomeação de chaves e validação de tipo na camada de aplicação.

### ADR-005 — Particionamento da tabela `mensagem` por mês

**Contexto:** `mensagem` é a tabela de maior volume de escrita do sistema (toda troca em toda conversa).
**Decisão:** particionar por `RANGE (enviado_em)`, uma partição por mês, com automação de criação de partições futuras.
**Consequências:** consultas do mês corrente permanecem rápidas independente do histórico acumulado; exige job de manutenção (criar partição do próximo mês com antecedência) e cuidado ao fazer *joins* que cruzem partições.

### ADR-006 — Multi-tenancy por instância isolada

**Contexto:** o CRM é a "Base PAI" — template reutilizável para vários clientes ("filhos"). O requisito interno "mudar no máximo a URL e o token permanente de filho para filho" indica deploys independentes.
**Decisão:** cada cliente recebe deploy e banco próprios; nenhuma tabela tem `tenant_id`; o core é distribuído como biblioteca versionada e cada filho é um repositório fino que a consome.
**Consequências:** isolamento físico elimina a classe de bugs mais perigosa de SaaS multi-tenant e simplifica o schema; em troca, o custo se desloca para operação (N deploys, N backups) e para a estratégia de propagação de versão, detalhada em `07-base-pai-multitenancy.md`.

### ADR-007 — WebSocket com Redis pub/sub como backplane

**Contexto:** múltiplos atendentes simultâneos (RNF-CRM-08) exigem mais de uma instância do backend; WebSocket é *stateful* por natureza.
**Decisão:** Redis pub/sub replica eventos de WebSocket entre instâncias, permitindo que uma mensagem publicada em qualquer instância chegue ao cliente conectado em outra.
**Consequências:** dependência operacional adicional (Redis em alta disponibilidade), mas viabiliza escalar o backend horizontalmente sem *sticky sessions* rígidas.

### ADR-008 — Venda ganha como transição de etapa no período

**Contexto:** o estado atual do lead não responde quando a venda aconteceu. Contar hoje os leads numa etapa chamada "Fechado" repetiria para sempre vendas antigas e ainda acoplaria a métrica ao nome escolhido por cada filho da Base PAI. Uma reabertura também pode produzir mais de uma transição para ganho no mesmo intervalo por correção operacional.

**Decisão:** cada etapa declara um resultado estável (`EM_ANDAMENTO`, `GANHO` ou `PERDIDO`) e somente uma etapa pode ser `GANHO`. "Vendas fechadas no período" é a quantidade de leads distintos que tiveram ao menos um `ETAPA_ALTERADA` cujo `resultado_novo` era `GANHO` dentro do intervalo. Reabrir e fechar o mesmo lead no mesmo período conta uma vez; fechar novamente num período posterior conta novamente, representando uma venda recorrente. O crédito comercial usa `responsavel_id`, snapshot do dono do lead na transição; `ator_id` identifica separadamente quem executou a mudança.

**Consequências:** nomes como "Fechado", "Concluído" ou "Vendido" não entram em consultas nem regras. O histórico começa no deploy da migration que introduziu `ETAPA_ALTERADA`; períodos anteriores retornam zero. Não existe reconstrução pelo `audit_log`, porque sua retenção é de manutenção e não pode governar uma métrica comercial.

---

## Parte B — Convenções gerais de API

- **Versionamento:** prefixo `/api/v1/...`; mudanças incompatíveis sobem para `/api/v2` mantendo v1 ativo durante transição.
- **Autenticação:** `Authorization: Bearer <JWT>`; access token curto (15 min) + refresh token opaco e rotativo (7 dias) via `POST /api/v1/auth/refresh` (`AutenticacaoController`, `AutenticacaoIT`).
- **Paginação:** *offset-based* (`?page=0&size=20`) nas listagens de gestão; *cursor-based* (`?cursor=...`) na lista de mensagens de um atendimento (evita duplicar/pular itens quando novas mensagens chegam durante a rolagem).
- **Erros:** respostas de erro HTTP usam o `ProblemDetail` do Spring (RFC 7807). Não há catálogo público de URIs de tipos de erro; portanto, este documento não inventa exemplos de `type` ou `instance` que o código não produza.
- **Autorização:** cada endpoint declara o(s) papel(éis) mínimo(s) exigido(s); a checagem de "é dono deste recurso" (ex.: `RN-CRM-01`) é feita no *use case*, não apenas por papel.

## Parte C — Endpoints REST por módulo (contratos em operação)

### Atendimento

| Método | Rota | Descrição | Papel mínimo | Evidência |
|---|---|---|---|---|
| GET | `/api/v1/atendimentos` | Lista atendimentos por visão operacional | Atendente | `PainelDeAtendimentosController` · `PainelDeAtendimentosControllerIT` |
| GET | `/api/v1/atendimentos/{id}/mensagens` | Histórico paginado por cursor | Atendente | `AtendimentoMensagensController` · `HistoricoMensagensCursorIT` |
| POST | `/api/v1/atendimentos/mensagens` | Envia mensagem de texto | Atendente | `AtendimentoAcoesController` · `AtendimentoAcoesControllerIT` |
| POST | `/api/v1/atendimentos/{id}/mensagens/midia` | Envia áudio, imagem, vídeo ou documento | Atendente | `AtendimentoAcoesController` · `AnexoMidiaIT` |
| POST | `/api/v1/atendimentos/{id}/transferir` | Transfere para atendente ou devolve à IA conforme a autorização | Atendente | `AtendimentoAcoesController` · `AtendimentoAcoesControllerIT` |
| POST | `/api/v1/atendimentos/{id}/finalizar` | Encerra atendimento | Atendente | `AtendimentoAcoesController` · `AtendimentoAcoesControllerIT` |
| GET | `/api/v1/leads/{id}/timeline` | Linha do tempo de eventos | Atendente | `TimelineDoLeadController` · `LeadFichaIT` |

### CRM Core

| Método | Rota | Descrição | Papel mínimo | Evidência |
|---|---|---|---|---|
| GET | `/api/v1/leads` | Lista leads sob a `VisibilidadeLeadSpecification` | Atendente | `LeadController` · `PainelDoLeadIT` |
| POST | `/api/v1/leads/filtrar` | Executa a árvore de critérios AND/OR | Atendente | `FiltroDeLeadsController` · `FiltroModularIT` |
| POST | `/api/v1/leads/filtrar/contagem` | Conta o resultado da mesma árvore de critérios | Atendente | `FiltroDeLeadsController` · `FiltroModularIT` |
| POST | `/api/v1/lembretes` | Cria lembrete | Atendente | `LembreteController` · `LembretesIT` |
| POST | `/api/v1/mensagens-programadas` | Agenda mensagem | Atendente | `MensagemProgramadaController` · `MensagensProgramadasIT` |

### Automação — Configuração (consumida pelo serviço de Automação)

| Método | Rota | Descrição | Consumidor | Evidência |
|---|---|---|---|---|
| GET | `/internal/v1/automation-config` | Todos os parâmetros tipados atuais | Serviço de Automação | `AutomationConfigInternalController` · `ContratoAutomacaoIT` |
| GET | `/internal/v1/automation-config/{chave}` | Parâmetro específico | Serviço de Automação | `AutomationConfigInternalController` · `ContratoAutomacaoIT` |
| GET | `/internal/v1/regras/follow-up` | Snapshot das regras de follow-up | Serviço de Automação | `AutomationConfigInternalController` · `ContratoInternalV1IT` |
| GET | `/internal/v1/regras/fidelizacao` | Snapshot das regras de fidelização | Serviço de Automação | `AutomationConfigInternalController` · `ContratoInternalV1IT` |
| POST | `/internal/v1/eventos` | Recebe telemetria idempotente da Automação | Serviço de Automação | `AutomationConfigInternalController` · `ContratoAutomacaoIT` |
| GET | `/internal/v1/atendentes/disponiveis` | Lista atendentes elegíveis à distribuição | Serviço de Automação | `AtendentesDisponiveisInternalController` · `ContratoInternalV1IT` |
| POST | `/internal/v1/atendimentos/{id}/responder` | Responde como IA, grava mensagem e outbox sem transferir o lead | Serviço de Automação | `TransferenciaAutomacaoInternalController` · `ComandosAutomacaoIT` |
| POST | `/internal/v1/atendimentos/{id}/transferir` | Transfere da IA para atendente ativo informado no corpo | Serviço de Automação | `TransferenciaAutomacaoInternalController` · `ComandosAutomacaoIT` |
| PATCH | `/internal/v1/atendimentos/{id}/modo-ia` | Devolve atendimento e lead para a IA | Serviço de Automação | `TransferenciaAutomacaoInternalController` · `ComandosAutomacaoIT` |
| POST | `/internal/v1/atendimentos/{id}/transferir-proximo-humano` | Escolhe o primeiro atendente disponível por nome e id | Serviço de Automação | `TransferenciaAutomacaoInternalController` · `ComandosAutomacaoIT` |
| PUT | `/api/v1/automacao/config/{chave}` | Atualiza parâmetro e invalida o cache | Gestor/Subgestor | `ConfiguracaoAutomacaoController` · `ContratoAutomacaoIT` |
| GET | `/api/v1/automacao/telemetria` | Snapshot cumulativo do estado da Automação | Gestor/Subgestor | `StatusAutomacaoTelemetriaController` · `StatusAutomacaoTelemetriaControllerIT` |

Autenticação das rotas `/internal/v1`: header `X-Synapse-Token` com o token permanente da instância (`SynapseTokenAuthenticationFilter`, `ContratoInternalV1IT`). Namespace e formato são idênticos em todos os filhos — só URL e token mudam. O OpenAPI é exposto em runtime e seu contrato interno reduzido é coberto por `OpenApiIT` e `ContratoInternalV1IT`.

### Configuração da instância (consumida pelo frontend)

| Método | Rota | Descrição | Evidência |
|---|---|---|---|
| GET | `/api/v1/config/features` | Feature flags da instância | `ConfigInstanciaController` · `ContratoAutomacaoIT` |
| GET | `/api/v1/audit-log` | Auditoria filtrável por ator, ação, entidade, lead e período | `AuditLogController` · `AuditoriaIT` |

### Saúde e monitoramento

| Método | Rota | Descrição | Evidência |
|---|---|---|---|
| GET | `/health/liveness` | Processo vivo; não testa dependências | `ProbesController` · `AplicacaoIT` e `SaudeBancoIndisponivelIT` |
| GET | `/health/readiness` | Prontidão da aplicação e acesso pelo pool geral | `ProbesController` · `AplicacaoIT` |
| GET | `/health/critical` | Seis sinais do caminho de mensagens, com componente e severidade (`UP`, `DEGRADED` ou `DOWN`) | `SaudeCriticaController` · `SaudeCriticaIT`, `SaudeBancoIndisponivelIT` e `SaudeCanalInvalidoIT` |

O componente `fila-outbox` mede a fila transacional que o backend realmente consome (`outbox_evento` + `PublicadorDaOutbox`), não o container RabbitMQ sem consumidor no código. `banco-chat`, `canal`, `websocket` e `particoes-mensagem` são críticos; acúmulo anormal da outbox é degradado. O endpoint raiz e os grupos internos do Actuator ficam em `/internal-health`; o healthcheck do container permanece em `/health/liveness`.

### Equipe

| Método | Rota | Descrição | Papel mínimo | Evidência |
|---|---|---|---|---|
| POST | `/api/v1/usuarios` | Cria usuário operacional | Gestor | `UsuarioController` · `EquipeEPresencaIT` |
| PUT | `/api/v1/usuarios/{id}` | Atualiza usuário operacional | Gestor | `UsuarioController` · `EquipeEPresencaIT` |
| PATCH | `/api/v1/usuarios/me/presenca` | Atualiza presença própria | Atendente | `UsuarioController` · `EquipeEPresencaIT` |
| GET | `/api/v1/equipe/avaliacoes` | Resumo de avaliações da equipe | Gestor | `AvaliacaoController` · `EquipeEPresencaIT` |

## Parte D — WebSocket (tempo real)

| Destino | Direção | Payload | Proteção | Evidência |
|---|---|---|---|---|
| `/ws?token=<JWT>` | Cliente → Servidor | Handshake STOMP | JWT validado antes do upgrade | `WebSocketConfig` · `TempoRealIT` |
| `/user/queue/atendimento.{id}` | Servidor → Cliente | Mensagem, status, transferência e finalização | Assinatura autorizada pela visibilidade do atendimento | `AutorizacaoDeAssinaturaInterceptor` · `TempoRealIT` |
| `/user/queue/revogacoes` | Servidor → Cliente | Atendimento cuja assinatura deixou de ser visível | Revalidação após transferência | `RedisSubscriberDeAtendimento` · `TempoRealIT` |

Dados de lead não usam `/topic` de broadcast. Redis replica os eventos entre instâncias; a entrega final continua sendo uma fila pessoal do usuário autenticado.

## Parte E — Contrato CRM ↔ Automação

Não há consumidor RabbitMQ da Automação no código atual. O contrato implementado é HTTP sobre a rede interna, autenticado por `X-Synapse-Token`; os controllers e testes de cada operação estão nomeados na tabela da Parte C. A fila do canal humano continua interna ao módulo de atendimento e não constitui contrato CRM ↔ Automação.
