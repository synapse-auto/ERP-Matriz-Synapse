# 04. Architecture Decision Records (ADRs) e Contrato de API

> **Regra de evidência:** Um endpoint só é documentado aqui com evidência nomeada: o controller que o implementa e o teste que o cobre. Contrato planejado não mora neste documento.

## Parte A — ADRs

### ADR-001 — Monólito modular em vez de microsserviços no MVP

**Contexto:** volume esperado ~5 mil atendimentos/mês (RNF-CRM-08), equipe de desenvolvimento provavelmente pequena/média, prazo de entrega apertado para um CRM "sob medida".
**Decisão:** monólito modular (Clean Architecture por bounded context), banco único PostgreSQL.
**Consequências:** deploy e operação simples; módulos com fronteira clara (portas/interfaces) permitem extrair qualquer um como serviço separado depois, se o volume ou a equipe crescerem — sem reescrever regra de negócio, só trocar o adaptador de infraestrutura.

### ADR-002 — Integração da Automação sem bloquear o caminho humano

**Contexto:** RNF-CRM-01 é a "ultra-regra": a aba Atendimentos não pode parar entre 08:00–18:30, mesmo que a Automação/IA falhe.
**Decisão:** envio e recebimento humanos não chamam a Automação. Consultas e comandos síncronos do n8n ficam em `/internal/v1`, autenticados por `X-Synapse-Token`/`ROLE_SERVICO`. O serviço lista atendimentos não encerrados por última atividade, sem usar a visão de usuário e sem devolver conversa; pode responder, transferir, devolver para IA, finalizar, criar lembrete para o responsável, sobrescrever o resumo atual e aplicar tags do catálogo. Um scheduler separado, também em contexto `SERVICO`, lê o toggle BOOLEAN por instância `atendimento.finalizar_inativos.habilitado` (V72, default `false`) antes de qualquer seleção; somente quando ligado finaliza em lotes os atendimentos `EM_ATENDIMENTO` sem interação além do limiar configurado por instância e reutiliza a mesma transição de finalização. A resposta da IA grava `Remetente.IA` e a intenção na outbox na mesma transação, sem chamada ao provedor durante o request; transferência explícita aceita apenas usuário ativo com papel `ATENDENTE` ou `SUBGESTOR` e pode reatribuir um atendimento ainda aberto (`EM_IA` ou `EM_ATENDIMENTO`) quando o cliente indicar o destino. A rota de próximo humano permanece estrita a `EM_IA` e escolhe pela estratégia configurável `ia.distribuicao.sequencial` (menor carga por padrão ou recência sequencial). Finalização e demais escritas sujeitas a retry usam `Idempotency-Key` persistida. Telemetria e invalidação de configuração permanecem desacopladas.
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

### ADR-009 — Código interno do lead como coluna, não JSONB

**Contexto:** o atendente precisa de um identificador numérico do cliente visível no card da lista de Atendimentos e editável em Informações gerais. `dados_customizados` não entra em projeção de listagem (mesmo recorte de `notas` e `resumo_ia`).

**Decisão:** coluna `lead.codigo VARCHAR(20)`, nullable, CHECK só dígitos, sem unique. `VARCHAR` em vez de inteiro para preservar zeros à esquerda (`00421`). Campo ausente no PUT preserva; string vazia limpa. Quem alcança o lead (atendente dono, gestor, subgestor) edita — a visibilidade continua sendo a Authorization. A Agenda (`LeadResumo`) não carrega o campo.

**Consequências:** todo filho da Base PAI ganha o campo; não é coluna de ramo (obra, convênio, imóvel). Dado específico de um cliente continua em `campo_customizado`. Busca/filtro por código e unicidade ficam de fora até decisão de produto.

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
| GET | `/api/v1/atendimentos/busca?leadId=...` ou `?telefone=...` | Busca pontualmente o cartão representativo mais recente de um lead visível; exige exatamente um parâmetro e normaliza telefone pelo contrato canônico | Atendente | `PainelDeAtendimentosController` · `PainelDeAtendimentosControllerIT` |
| GET | `/api/v1/atendimentos/{id}/mensagens` | Histórico paginado por cursor, com resumo de reações agregado em lote | Atendente | `AtendimentoMensagensController` · `HistoricoMensagensCursorIT` · `ReacoesDeMensagemIT` |
| GET | `/api/v1/atendimentos/{id}/mensagens/{mensagemId}` | Busca pontual de mensagem citada, limitada ao histórico do lead do atendimento visível; mídia somente por URL assinada | Atendente | `AtendimentoMensagensController` · `ListarHistoricoMensagensUseCase` |
| PUT | `/api/v1/atendimentos/{id}/mensagens/{mensagemId}/reacao` | Define a reação do usuário autenticado (`enviadoEm` na query ancora a partição). Idempotente para o mesmo emoji | Atendente | `AtendimentoMensagensController` · `ReacoesDeMensagemIT` |
| DELETE | `/api/v1/atendimentos/{id}/mensagens/{mensagemId}/reacao` | Remove a própria reação. Idempotente | Atendente | `AtendimentoMensagensController` · `ReacoesDeMensagemIT` |
| GET | `/api/v1/atendimentos/inbox` | Inbox unificada paginada por recência; item `CLIENTE` inclui `leadCodigo` | Atendente | `InboxUnificadaController` |
| POST | `/api/v1/atendimentos/novo-contato` | Inicia conversa WhatsApp: cria ou reusa lead visível do telefone, abre atendimento humano e envia texto livre ou template | Atendente | `AtendimentoAcoesController` · `NovoContatoIT` |
| POST | `/api/v1/atendimentos/mensagens` | Envia texto; o `atendimentoId` do clique ancora o ciclo e o envio humano transfere a responsabilidade elegível | Atendente | `AtendimentoAcoesController` · `AtendimentoAcoesControllerIT` |
| POST | `/api/v1/atendimentos/{id}/mensagens/midia` | Envia áudio, imagem, vídeo ou documento | Atendente | `AtendimentoAcoesController` · `AnexoMidiaIT` |
| POST | `/api/v1/atendimentos/{id}/transferir` | Transfere para atendente ou devolve à IA conforme a autorização | Atendente | `AtendimentoAcoesController` · `AtendimentoAcoesControllerIT` |
| POST | `/api/v1/atendimentos/{id}/convidar` | Cria convite idempotente para atendente ativo; preserva o responsável e entrega o cartão em Pendentes ao destinatário | Responsável, participante ativo ou gestor | `AtendimentoAcoesController` · `AtendimentoAcoesControllerIT` |
| POST | `/api/v1/atendimentos/{id}/finalizar` | Encerra atendimento | Atendente | `AtendimentoAcoesController` · `AtendimentoAcoesControllerIT` |
| GET | `/api/v1/atendimentos/{id}/avaliacao` | Lê a nota 1–5 da conversa visível | Atendente | `AtendimentoAcoesController` · `AvaliacaoAtendimentoIT` |
| POST | `/api/v1/atendimentos/{id}/avaliacao` | Grava uma única nota 1–5 no atendente dono, só após finalizar | Atendente | `AtendimentoAcoesController` · `AvaliacaoAtendimentoIT` |
| GET | `/api/v1/leads/{id}/timeline` | Linha do tempo de eventos | Atendente | `TimelineDoLeadController` · `LeadFichaIT` |

Na visão `FINALIZADOS`, a inbox aceita o parâmetro opcional `atendenteId`. Gestores, subgestores e
administradores podem omiti-lo (todos) ou informar um responsável específico. Para atendentes, a
tela sempre envia o próprio UUID; uma tentativa explícita de informar outro responsável é recusada
com `403` antes da consulta. A aplicação do filtro ocorre no read model paginado (`a.atendente_id`),
depois da autorização da `ListarAtendimentosVisiveisUseCase`; não há filtragem de finalizados no
frontend. Chamadas legadas sem o parâmetro preservam a regra existente de balcão de reativação.

#### Envio resiliente no navegador

Os endpoints de novo contato, texto, template, mídia e encaminhamento aceitam o header opcional
`Idempotency-Key`. A interface sempre envia uma chave UUID por ação; o backend reserva essa chave
para o par usuário/lead/atendimento no mesmo transaction boundary que grava a mensagem e a
transactional outbox. Repetir a mesma chave retorna a mesma `EnvioResposta` (HTTP 200), enquanto
duas chaves diferentes continuam representando mensagens distintas, mesmo com conteúdo igual.

Para texto e template, a interface também envia `atendimentoId` junto de `leadId`. Ele ancora o
clique na conversa que estava aberta: se ela foi finalizada ou substituída antes de o request obter
o lock do lead, o endpoint devolve `409` e não cria mensagem, outbox nem um novo ciclo. A ausência
do campo permanece aceita somente para clientes legados; a tela de Atendimentos sempre o informa.
O upload usa o `{id}` da rota como a mesma âncora e remove o objeto recém-gravado no storage quando
esse atendimento já não pode receber a mídia.
Em um envio humano aceito, a RN-CRM-06 é aplicada na mesma transação: `lead.atendente_responsavel_id`
e `atendimento.atendente_id` passam ao remetente e ambos ficam no estado humano antes de a mensagem
e a outbox serem gravadas. Participação ativa dá alcance colaborativo, mas não preserva a posse
quando o participante envia.

O histórico de mensagens e o evento WebSocket `MENSAGEM` devolvem a mesma chave. Quando o navegador
perde a resposta, a UI mantém a mensagem otimista pendente e consulta o histórico por essa
identidade em três tentativas (1 s, 2 s e uma tentativa final imediata). Encontrar a chave substitui
o otimista pelo registro real; apenas 4xx definitivo ou o esgotamento sem confirmação exibe
`FALHOU`/`Reenviar`. O status de transporte não é apresentado como erro informado pelo provedor.
Eventos WebSocket sem `mensagemId` são ignorados, e `STATUS` só pode alterar a mensagem cujo id
real (ou chave idempotente) corresponde ao evento.

### CRM Core

| Método | Rota | Descrição | Papel mínimo | Evidência |
|---|---|---|---|---|
| GET | `/api/v1/leads` | Lista leads sob a `VisibilidadeLeadSpecification` (sem `codigo`, notas, resumo ou JSONB) | Atendente | `LeadController` · `PainelDoLeadIT` |
| GET | `/api/v1/leads/{id}` | Ficha completa, inclusive `codigo`, `notas`, `resumoIa` e `resumoIaAtualizadoEm`; os campos longos não saem na listagem | Atendente (visível) | `LeadController` · `LeadFichaIT` |
| GET | `/api/v1/leads/{id}/agenda` | Ficha completa a partir de um lead retornado pela Agenda; aplica o contexto colaborativo da Agenda sem alterar a visibilidade da rota comum | Atendente autenticado (Agenda) | `LeadController` · `IsolamentoDeAgendaIT` |
| PUT | `/api/v1/leads/{id}` | Atualização parcial da ficha. `notas` é compartilhada entre usuários autorizados; `codigo` ausente preserva; `""` limpa; letra ou >20 dígitos vira 400 (`Codigo invalido`). `nome` ausente preserva; vazio ou só espaços vira 400 (`Nome invalido`) — o nome não se apaga | Atendente (visível) | `LeadController` · `LeadFichaIT` |
| POST | `/api/v1/leads/filtrar` | Executa a árvore de critérios AND/OR | Atendente | `FiltroDeLeadsController` · `FiltroModularIT` |
| POST | `/api/v1/leads/filtrar/contagem` | Conta o resultado da mesma árvore de critérios | Atendente | `FiltroDeLeadsController` · `FiltroModularIT` |
| POST | `/api/v1/lembretes` | Cria lembrete | Atendente | `LembreteController` · `LembretesIT` |
| POST | `/api/v1/mensagens-programadas` | Agenda mensagem | Atendente | `MensagemProgramadaController` · `MensagensProgramadasIT` |

| GET/PUT | `/api/v1/automacao/fidelizacao/configuracao[/{chave}]` | Lê/atualiza configuração de aniversário | Gestor/Admin | `RegrasAutomacaoController` · `RegrasAutomacaoIT` |
| GET/POST | `/api/v1/automacao/fidelizacao/datas-festivas` | Lista/cadastra datas festivas dinâmicas | Gestor/Admin | `RegrasAutomacaoController` · `RegrasAutomacaoIT` |
| PUT/PATCH/DELETE | `/api/v1/automacao/fidelizacao/datas-festivas/{id}[/{ativo}]` | Edita, ativa/desativa ou remove data festiva | Gestor/Admin | `RegrasAutomacaoController` · `RegrasAutomacaoIT` |

### Automação — Configuração (consumida pelo serviço de Automação)

| Método | Rota | Descrição | Consumidor | Evidência |
|---|---|---|---|---|
| GET | `/internal/v1/automation-config` | Todos os parâmetros tipados atuais | Serviço de Automação | `AutomationConfigInternalController` · `ContratoAutomacaoIT` |
| GET | `/internal/v1/automation-config/{chave}` | Parâmetro específico | Serviço de Automação | `AutomationConfigInternalController` · `ContratoAutomacaoIT` |
| GET | `/internal/v1/automation-config/ev05` | Configuração independente de resumo e preenchimento do EV-05 | Serviço de Automação | `AutomationConfigInternalController` |
| GET | `/internal/v1/ev05/candidatos` | IDs paginados de atendimentos `EM_ATENDIMENTO`, sem conteúdo sensível | Serviço de Automação | `Ev05AutomacaoInternalController` |
| GET | `/internal/v1/ev05/atendimentos/{id}/contexto` | Contexto limitado para análise, somente atendimento elegível | Serviço de Automação | `Ev05AutomacaoInternalController` |
| GET/POST | `/internal/v1/ev05/leads/{id}/resumo` | Situação e escrita idempotente do resumo | Serviço de Automação | `Ev05AutomacaoInternalController` |
| POST | `/internal/v1/ev05/leads/{id}/resumo-status` | Evolução idempotente do ciclo sob demanda (`PROCESSANDO`, `CONCLUIDO`, `FALHOU`) | Serviço de Automação | `Ev05AutomacaoInternalController` · `AtualizarStatusResumoIaUseCase` |
| GET/POST | `/internal/v1/ev05/leads/{id}/preenchimento` | Situação e preenchimento idempotente de dados vazios | Serviço de Automação | `Ev05AutomacaoInternalController` |
| GET | `/internal/v1/regras/follow-up` | Snapshot das regras de follow-up | Serviço de Automação | `AutomationConfigInternalController` · `ContratoInternalV1IT` |
| GET | `/internal/v1/regras/fidelizacao` | Snapshot das regras de fidelização | Serviço de Automação | `AutomationConfigInternalController` · `ContratoInternalV1IT` |
| POST | `/internal/v1/eventos` | Recebe telemetria idempotente da Automação | Serviço de Automação | `AutomationConfigInternalController` · `ContratoAutomacaoIT` |
| GET | `/internal/v1/atendentes/disponiveis` | Lista atendentes elegíveis à distribuição | Serviço de Automação | `AtendentesDisponiveisInternalController` · `ContratoInternalV1IT` |
| GET | `/internal/v1/atendimentos/atendentes?nome=...` | Resolve por nome (case-insensitive, substring) os atendentes ativos ATENDENTE/SUBGESTOR para uma transferência explícita; devolve todos os candidatos | Serviço de Automação | `TransferenciaAutomacaoInternalController` · `TransferenciaAutomacaoIT` |
| GET | `/internal/v1/atendimentos/em-andamento` | Página de atendimentos não encerrados, filtrável por última atividade, sem conteúdo ou histórico | Serviço de Automação | `AtendimentosAutomacaoInternalController` · `ContratosInternosAutomacaoIT` |
| POST | `/internal/v1/atendimentos/{id}/responder` | Responde como IA, grava mensagem e outbox sem transferir o lead | Serviço de Automação | `TransferenciaAutomacaoInternalController` · `ComandosAutomacaoIT` |
| POST | `/internal/v1/atendimentos/{id}/transferir` | Reatribui atendimento aberto para atendente ativo informado no corpo (pedido explícito); `FINALIZADO` é bloqueado | Serviço de Automação | `TransferenciaAutomacaoInternalController` · `ComandosAutomacaoIT` |
| PATCH | `/internal/v1/atendimentos/{id}/modo-ia` | Devolve atendimento e lead para a IA | Serviço de Automação | `TransferenciaAutomacaoInternalController` · `ComandosAutomacaoIT` |
| POST | `/internal/v1/atendimentos/{id}/transferir-proximo-humano` | Escolhe o primeiro atendente disponível por nome e id; somente para atendimento `EM_IA` | Serviço de Automação | `TransferenciaAutomacaoInternalController` · `ComandosAutomacaoIT` |
| POST | `/internal/v1/atendimentos/{id}/finalizar` | Finaliza atendimento e lead, com origem `AUTOMACAO`, sem chamar provedor | Serviço de Automação | `TransferenciaAutomacaoInternalController` · `ComandosAutomacaoIT` |
| POST | `/internal/v1/atendimentos/{id}/lembretes` | Cria, com `Idempotency-Key`, lembrete para o responsável humano atual | Serviço de Automação | `AtendimentosAutomacaoInternalController` · `ContratosInternosAutomacaoIT` |
| POST | `/internal/v1/atendimentos/{id}/resumo` | Sobrescreve o resumo atual da IA, limitado por configuração | Serviço de Automação | `AtendimentosAutomacaoInternalController` · `ContratosInternosAutomacaoIT` |
| POST | `/internal/v1/atendimentos/{id}/avaliacao` | Grava CSAT 1–5 no atendente dono da conversa já finalizada | Serviço de Automação | `AtendimentosAutomacaoInternalController` · `AvaliacaoAtendimentoIT` |
| GET | `/internal/v1/tags` | Lista o catálogo fechado de tags da instância | Serviço de Automação | `TagsAutomacaoInternalController` · `ContratosInternosAutomacaoIT` |
| POST | `/internal/v1/leads/{id}/tags` | Aplica idempotentemente tag existente, auditada como Automação | Serviço de Automação | `TagsAutomacaoInternalController` · `ContratosInternosAutomacaoIT` |
| PUT | `/api/v1/automacao/config/{chave}` | Atualiza parâmetro e invalida o cache | Gestor/Subgestor | `ConfiguracaoAutomacaoController` · `ContratoAutomacaoIT` |
| GET | `/api/v1/automacao/telemetria` | Snapshot cumulativo do estado da Automação | Gestor/Subgestor | `StatusAutomacaoTelemetriaController` · `StatusAutomacaoTelemetriaControllerIT` |

Autenticação das rotas `/internal/v1`: header `X-Synapse-Token` com o token permanente da instância (`SynapseTokenAuthenticationFilter`, `ContratoInternalV1IT`). Namespace e formato são idênticos em todos os filhos — só URL e token mudam. A Automação não usa `/api/v1/atendimentos?visao=TODOS`: essa é uma rota JWT de usuário e `visao` representa propriedade/papel humano.

O documento OpenAPI publicado em `/v3/api-docs`, `/v3/api-docs.yaml` e no Swagger UI é gerado a partir de todo o pacote `com.synapse.crm`, com escopo explícito para `/api/v1/**`, `/internal/v1/**`, `/health/**` e `/webhook/**`. Assim, a documentação inclui os endpoints públicos, o contrato interno da Automação, o ciclo de Resumo por IA (incluindo configuração e recursos disponíveis) e os probes de saúde. As operações internas recebem também as tags `Interno` e `Automação`; as operações EV-05, a configuração/recursos e o endpoint JWT de solicitação recebem `Resumo por IA`. Headers `Idempotency-Key` obrigatórios aparecem como parâmetros de cabeçalho, com a regra de replay descrita na operação.

A presença no Swagger é somente documentação: não libera rota nem substitui a cadeia de segurança. Operações públicas protegidas continuam exigindo `Authorization: Bearer <JWT>`; `/internal/v1/**` continua exigindo `X-Synapse-Token` e `ROLE_SERVICO`; o webhook continua usando sua assinatura própria. O contrato do resumo por IA está nas operações públicas `/api/v1/atendimentos/{atendimentoId}/resumo-ia` (solicitação/estado), `/api/v1/automacao/config/resumo-ia` (configuração) e `/api/v1/automacao/config/recursos-ia`, além das internas `/internal/v1/ev05/atendimentos/{atendimentoId}/contexto`, `/internal/v1/ev05/leads/{leadId}/resumo` e `/internal/v1/ev05/leads/{leadId}/resumo-status`. `OpenApiIT` verifica a descoberta, tags, schemes, headers e ciclo; `ContratoInternalV1IT` continua protegendo a forma consumida pela Automação.

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
| PUT | `/api/v1/chat-interno/conversas/{id}/mensagens/{mensagemId}/reacao` | Define a reação do participante. Papel amplo não fura conversa privada | Participante | `ChatInternoController` · `ReacoesDeChatInternoIT` |
| DELETE | `/api/v1/chat-interno/conversas/{id}/mensagens/{mensagemId}/reacao` | Remove a própria reação do participante | Participante | `ChatInternoController` · `ReacoesDeChatInternoIT` |

### Relatórios — Dashboard Visão Geral

Um único `GET` devolve todos os KPIs, funil, ranking e horário de pico. Papéis: `GESTOR`, `SUBGESTOR`, `ADMINISTRADOR`. Atendente recebe 403.

| Método | Rota | Descrição | Papel mínimo | Evidência |
|---|---|---|---|---|
| GET | `/api/v1/dashboard/visao-geral` | Visão consolidada (`ano`/`meses` **ou** `inicio`/`fim`, mais `origemInicio`/`origemFim`) | Gestor | `DashboardController` · `DashboardVisaoGeralIT` |

Parâmetros: `ano` + `meses` (1–12, vírgula; ausente = ano inteiro) **ou** `inicio`/`fim` (datas inclusivas do recorte diário; os dois juntos; quando presentes substituem ano/meses). `origemInicio`/`origemFim` (coorte opcional de `lead.criado_em`, os dois juntos). Chips "Hoje" e "7 dias" no celular enviam `inicio`/`fim` reais (hoje; últimos 7 dias inclusive). O comparativo usa a janela imediatamente anterior da mesma duração.

Fontes de cada métrica (todas em `DashboardVisaoGeralRepositorioJdbc`, vendas em `AgregacaoDeVendasRepositorioJdbc`):

| KPI / bloco | Fórmula | Tabelas | Coluna temporal |
|---|---|---|---|
| Atendimentos no período | `count(*)` | `atendimento` | `iniciado_em` |
| Atendimentos acumulados | `count(*)` até o fim do período | `atendimento` | `iniciado_em < fim` |
| Tempo médio | `avg(finalizado_em - iniciado_em)` só dos finalizados | `atendimento` | `iniciado_em` |
| Avaliação / satisfação | `avg(nota)`, escala 0–10; distribuição: `9–10`, `7–8`, `0–6` | `avaliacao` | `criado_em` |
| Atendentes online (faixa Agora) | online / total entre usuários ativos com papel `ATENDENTE` ou `SUBGESTOR` | `usuario` | estado atual de `status_presenca` |
| Resolução por IA | finalizados sem `LEAD_TRANSFERIDO_POR_ENVIO` nem `ATENDIMENTO_TRANSFERIDO` no histórico / finalizados no período | `atendimento` + `evento_timeline` | `atendimento.finalizado_em` |
| Vendas fechadas (payload + ranking; o card de KPI saiu da Visão Geral) | leads distintos com primeira transição `ETAPA_ALTERADA` cujo `dados.resultado_novo = GANHO` | `evento_timeline` + `lead` (+ `usuario` no ranking) | `evento_timeline.criado_em`; coorte opcional em `lead.criado_em` |
| Taxa de conversão | vendas do período / leads recebidos no mesmo recorte (ou no coorte de originação) | mesmas de vendas + `lead` | `lead.criado_em` no denominador |
| Funil | `count(lead)` por `etapa_atendimento`, snapshot da etapa **atual** (não da timeline) | `etapa_atendimento` + `lead` | `lead.criado_em` |
| Horário de pico | `count(*)` por hora de `enviado_em` no fuso do tenant | `mensagem` | `enviado_em` |
| Ranking de vendas (payload; a lista da Visão Geral passou a ser por avaliação) | mesmas vendas, agrupadas por `responsavel_id` do JSONB; sem responsável entra no total e numa nota de rodapé, não na lista | `evento_timeline` + `lead` + `usuario` | igual a vendas |
| Ranking de avaliação | `avg(nota)` e `count(*)` por `atendente_id`, ordenado por média | `avaliacao` + `usuario` | `avaliacao.criado_em` |

Leitura complementar na Equipe (não alimenta a Visão Geral): `GET /api/v1/equipe/avaliacoes` agrega `avaliacao` sem recorte de período.

O gráfico de satisfação usa a distribuição e a média das avaliações existentes; não representa NPS,
pois o sistema não registra uma pergunta de recomendação nem respostas identificadas como promotor,
neutro e detrator. A faixa ao vivo também não exibe “aguardando 1ª resposta” ou “esquecidos”: esses
indicadores ainda não têm definição/fonte confiável no read model.

Coleta: `POST /api/v1/atendimentos/{id}/avaliacao` (JWT, visibilidade RLS) e `POST /internal/v1/atendimentos/{id}/avaliacao` (`X-Synapse-Token`, para a Automação/WhatsApp). Escala **0–10** (desde V56), uma linha por atendimento (`uq_avaliacao_atendimento`). O atendente dono da conversa recebe o crédito. Conversa ainda aberta ou sem atendente → 422; segunda nota → 409.

### ADR — Uma reação por usuário (E84)

Uma pessoa autenticada mantém no máximo uma reação por mensagem. `PUT` define ou substitui; repetir o mesmo emoji é idempotente e não duplica linha. `DELETE` remove a própria reação e também é idempotente. O DTO aceita só um grapheme Unicode (ZWJ, tom de pele, variation selector); texto comum, concatenação e payload grande viram 400 (RFC 7807) sem gravar. O resumo na listagem HTTP é `{emoji, quantidade, reagi}` agregado em **um** `IN` por página — proibido N+1. O evento de tempo real (depois do commit) leva o resumo público `{emoji, quantidade}` **mais** `atorId` e `emojiDoAtor` (nulo na remoção): o mínimo para a outra aba do mesmo usuário recalcular `reagi`, sem nomes, sem lista de reatores e sem `reagi` no fio. Autorização: a mesma visibilidade do atendimento (RN-CRM-01, 404) e participação no chat interno (403, inclusive gestor de fora). Tabelas separadas com FK real: `mensagem_reacao` usa a chave composta da partição; `chat_interno_mensagem_reacao` aponta para `chat_interno_mensagem`.

O seletor amplo usa `emoji-mart` 5.6.0 + `@emoji-mart/data` 1.2.1 (MIT), montado como Web Component (`em-emoji-picker`). O pacote `emoji-mart` **não declara peer de React** — internamente usa Preact — então não há `overrides` nem `@emoji-mart/react` (peer só até React 18). Dados versionados no bundle, `set: native`, sem CDN. A aparência segue o Unicode do sistema (Apple no iOS, Segoe/Noto no Windows); o CRM não serve assets da Apple nem do WhatsApp. O mesmo picker entra no composer de Atendimentos e no chat interno (busca, categorias e tom de pele); reações rápidas continuam sendo os seis atalhos do catálogo.

## Parte D — WebSocket (tempo real)

### ADR — Inbox unificada (E62)

A tela de Atendimentos usa `GET /api/v1/atendimentos/inbox` como read model discriminado:
`tipo=CLIENTE` mantém os campos e regras de `Atendimento`, enquanto `tipo=EQUIPE_INTERNA`
expõe somente a conversa interna da qual o usuário participa. A união, ordenação pela última
mensagem e cursor são feitos no backend com consultas keyset limitadas por fonte (não há carga
completa em memória), para não perder itens ao compor páginas. O contrato e a autorização HTTP
são exercitados por `InboxUnificadaControllerIT` (Postgres/Testcontainers), incluindo participante,
gestor não participante e 401. Item `CLIENTE` inclui `leadCodigo` (nulo omitido via `@JsonInclude`)
para o card da lista; conversas internas não têm o campo. Conversas
internas aparecem apenas em `TODOS`; os badges de status continuam contando apenas clientes.
O endpoint específico de chat interno permanece para compatibilidade. O botão **Novo atendimento** da lista abre `POST /api/v1/atendimentos/novo-contato`: nome e telefone obrigatórios; primeira mensagem (texto livre) **ou** template `{nome, idioma, parametros}`, nunca os dois. A janela de 24h da Meta só abre quando o **cliente** envia mensagem — texto livre em contato novo (ou com janela fechada) responde 422 `ForaDaJanelaException` **antes** de gravar o lead. Template pré-aprovado passa. Sem mensagem, a conversa abre em modo humano e o composer oferece os templates. Opt-in é responsabilidade do atendente (não há tabela de consentimento). Telefone de colega: RLS esconde a linha e o índice único bloqueia o insert — os dois casos viram o mesmo 404 (`RecursoDeAtendimentoIndisponivelException`), sem vazar a RN-CRM-01. Não usar `resolverPorTelefone` neste fluxo (cria lead em IA, sem dono; com JWT a RLS esconderia o lead do colega e duplicaria).

| Destino | Direção | Payload | Proteção | Evidência |
|---|---|---|---|---|
| `/ws?token=<JWT>` | Cliente → Servidor | Handshake STOMP | JWT validado antes do upgrade | `WebSocketConfig` · `TempoRealIT` |
| `/user/queue/atendimento.{id}` | Servidor → Cliente | Mensagem, status, transferência, finalização e reação | Assinatura autorizada pela visibilidade do atendimento | `AutorizacaoDeAssinaturaInterceptor` · `TempoRealIT` · `RelayDeTempoRealListener` |
| `/user/queue/revogacoes` | Servidor → Cliente | Atendimento cuja assinatura deixou de ser visível | Revalidação após transferência | `RedisSubscriberDeAtendimento` · `TempoRealIT` |

Dados de lead não usam `/topic` de broadcast. Redis replica os eventos entre instâncias; a entrega final continua sendo uma fila pessoal do usuário autenticado.

### Chat interno — ações de mensagem (E176)

As ações abaixo são parte do contrato do chat interno e só podem ser chamadas por participante da
conversa. A origem e o destino de um encaminhamento são validados no backend; não há conversão para
mensagem de WhatsApp.

| Método | Rota | Regra | Evento |
|---|---|---|---|
| POST | `/api/v1/chat-interno/conversas/{id}/mensagens/{mensagemId}/responder` | Cria texto com referência segura à mensagem da mesma conversa | `CHAT_INTERNO_MENSAGEM` após commit |
| POST | `/api/v1/chat-interno/conversas/{id}/mensagens/{mensagemId}/encaminhar` | Copia mensagem para outra conversa interna da qual o usuário participa | `CHAT_INTERNO_MENSAGEM` após commit |
| DELETE | `/api/v1/chat-interno/conversas/{id}/mensagens/{mensagemId}` | Autor marca tombstone; conteúdo e mídia deixam de ser lidos | `CHAT_INTERNO_MENSAGEM_REMOVIDA` após commit |
| PATCH | `/api/v1/chat-interno/conversas/{id}/mensagens/{mensagemId}` | Autor atualiza somente mensagem `TEXTO`; mantém ID, data, reações e referências | `CHAT_INTERNO_MENSAGEM_EDITADA` após commit |

#### Mídia com legenda

`POST /api/v1/chat-interno/conversas/{id}/mensagens/midia` recebe `multipart/form-data` com a parte
obrigatória `arquivo`, a parte opcional `legenda` (texto livre, inclusive vazia) e o header obrigatório
`Idempotency-Key`. A imagem e a legenda são persistidas na **mesma** `chat_interno_mensagem`: a legenda
é o `conteudo` da mensagem e também fica em `midia_metadados.legenda`, permitindo exibição no histórico,
na lista de mídias e em eventos WebSocket sem criar uma segunda mensagem. Os limites e MIME aceitos
continuam vindo da configuração de anexos; não há limite ou texto fixado no frontend.

O retorno `201` contém a mensagem completa com URL de mídia assinada. Repetir a mesma chave para o
mesmo participante, conversa e bytes/legenda devolve `201` com a mesma mensagem, sem novo arquivo,
linha ou evento. Reutilizar a chave com outra conversa, usuário ou payload devolve `409`; arquivo
inválido, excedente ou sem participante devolve `400`/`403` em RFC 7807. O frontend só limpa preview
e legenda após o `201`; falha de upload preserva ambos para nova tentativa. O evento
`CHAT_INTERNO_MENSAGEM` é publicado uma única vez após a persistência.

`GET .../mensagens` retorna `removida=true` sem conteúdo, mídia ou prévia. Mensagens editadas incluem
`editadoEm`; o texto original não é exposto no contrato. Referências mantêm apenas
autor, tipo de conteúdo e resumo sanitizado; quando a origem é removida, recebem o marcador seguro
“mensagem removida”. A migration V65 adiciona o tombstone e o trigger de remoção das referências;
V66 adiciona `editadoEm` e atualiza a prévia quando o texto de origem é editado. A URL assinada de
mídia recusa mensagens removidas.

Para a navegação de citações, `GET /api/v1/chat-interno/conversas/{id}/mensagens/{mensagemId}` aplica a
mesma participação antes de consultar a mensagem e devolve a mídia apenas como URL assinada de curta
duração. No atendimento externo, a rota equivalente ancora a busca no atendimento visível e no mesmo
`lead_id`, permitindo citar mensagens de páginas anteriores sem uma busca global. O frontend usa a
resposta pontual somente para inserir a origem na janela atual, centralizar e destacar a bolha; falha
de autorização, conversa divergente, mensagem inexistente ou tombstone não expõe conteúdo nem mídia.

## Parte E — Contrato CRM ↔ Automação

Não há consumidor RabbitMQ da Automação no código atual. O contrato implementado é HTTP sobre a rede interna, autenticado por `X-Synapse-Token`; os controllers e testes de cada operação estão nomeados na tabela da Parte C. A fila do canal humano continua interna ao módulo de atendimento e não constitui contrato CRM ↔ Automação.
