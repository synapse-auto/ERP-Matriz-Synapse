# 05. Matriz de Rastreabilidade — Requisitos → Artefatos Técnicos

**Regenerada na E15b, a partir do código, não da intenção.** É a segunda vez que esta matriz mentiu — RF-CRM-57–64 marcados ✅ na primeira rodada (E14) sem estar prontos, agora RF-CRM-54. As duas vezes o padrão foi o mesmo: alguém escreveu "artefato: `tabela_x`" achando que a tabela na migration já era a funcionalidade.

> **Regra a partir de agora:** um requisito só recebe ✅ com **evidência nomeada** — o controller ou serviço que o implementa, e o teste que o cobre. Sem as duas colunas preenchidas, o status é ❌ ou ⚠️ — nunca ✅. Schema (`CREATE TABLE`) sem `application`/`interfaces` por trás não é implementação; é intenção.
>
> `✅` = controller/serviço nomeado **e** teste nomeado, ambos existentes e verificados nesta rodada.
> `⚠️` = parcialmente coberto, cortado conscientemente (registrado em `docs/09`), ou fora do escopo deste documento (decisão de UI/infra) — nunca "leitura em andamento".
> `❌` = sem implementação, sem teste, ou os dois — mesmo que a tabela exista na migration.

## Papéis e permissões

| Requisito | Status | Implementação | Teste |
|---|---|---|---|
| RF-CRM-01 | ✅ | `AutenticacaoController` (`/api/v1/auth/login`), JWT via Spring Security | `AutenticacaoIT` |
| RF-CRM-02 | ✅ | `@PreAuthorize` por caso de uso (ex.: `AtualizarConfiguracaoAutomacaoUseCase`, `GestaoDeTagsUseCases`) | `AutenticacaoIT`, `TagsEEtapasIT` (nega 403 para ATENDENTE) |
| RF-CRM-03 | ✅ | `VisibilidadeLeadSpecification` + política RLS (`V12__rls_isolamento_lead.sql`) | `IsolamentoDeAgendaIT`, `RlsIsolamentoIT` |

## Estrutura geral (transversal)

| Requisito | Status | Implementação | Teste |
|---|---|---|---|
| RF-CRM-04/05 | ✅ | `FiltroDeLeadsController` (`/api/v1/leads/filtrar`, `/contagem`) | `FiltroModularIT` |
| RF-CRM-06 | ✅ (fora do escopo de backend) | `frontend/src/app/(shell)/agenda/page.tsx` (`onAbrirPainel`/`onAbrirAtendimento`) | Sem teste de backend — comportamento é só frontend, nenhum endpoint envolvido |
| RF-CRM-07 | ⚠️ **rebaixado de ✅** | Índices existem (`pg_trgm`, `idx_lead_telefone`, `idx_lead_cpf`) e `FiltroDeLeadsController` aceita critério de busca, mas **nenhum teste exercita busca por texto fim a fim** — não encontrei asserção sobre nome/telefone/CPF/tag em nenhum IT | Nenhum encontrado |

## Atendimentos

| Requisito | Status | Implementação | Teste |
|---|---|---|---|
| RF-CRM-08/09 | ✅ | `AtendimentoMensagensController`, `PainelDeAtendimentosController`, WebSocket (`tempo-real`) | `TempoRealIT`, `PainelDeAtendimentosControllerIT`, `AnexoMidiaIT` |
| RF-CRM-10/11 | ✅ | `PainelDeAtendimentosController` (`GET /atendimentos?visao=`) | `PainelDeAtendimentosControllerIT` |
| RF-CRM-12 | ✅ | `MensagemRapidaController` | `MensagensRapidasIT` |
| RF-CRM-13 | ❌ **rebaixado de ✅** (estava junto de RF-CRM-12) | `arquivo_banco` existe só na migration (`V4__crm_core.sql`) — nenhum domain/application/interfaces em nenhum módulo | Nenhum (só `SchemaMigracoesIT`, que testa a tabela, não a feature) |
| RF-CRM-14 a 16 | ✅ | `TimelineDoLeadController`, `LembreteController`, `MensagemProgramadaController` | `PainelDoLeadIT`, `LembretesIT`, `MensagensProgramadasIT` |
| RF-CRM-17 a 19 | ✅ | `LeadController`, `EtapaController` | `LeadFichaIT` |
| RF-CRM-20/21/22 | ✅ | `PainelDeAtendimentosController` (`?visao=ATIVOS/PENDENTES/POTENCIAIS/TODOS`) | `PainelDeAtendimentosControllerIT` |
| RF-CRM-65 | ✅ | `AtendimentoAcoesController` (`/transferir`, `/finalizar`) | `AtendimentoAcoesControllerIT` |
| RF-CRM-66 | ✅ | `CanalController` + tabela `canal` (não hardcoded) | `CanalWhatsAppIT` |
| RF-CRM-67 | ✅ | `WebhookCanalController` atualiza `status_entrega` a partir do webhook Meta | `CanalWhatsAppIT` |
| RF-CRM-68 | ✅ | `mensagem.midia_metadados JSONB` + `EnviarMidiaUseCase` | `AnexoMidiaIT` |
| RF-CRM-69 | ✅ | `MensagemProgramadaController` (composer aciona a mesma API) | `MensagensProgramadasIT` |
| RF-CRM-70/71 | ✅ | `EtapaController`, contadores denormalizados em `lead` | `ContadoresDoLeadIT` |

## Agenda de Contatos

| Requisito | Status | Implementação | Teste |
|---|---|---|---|
| RF-CRM-23 a 26 | ✅ | `lead` + `etapa_atendimento`, `frontend/src/app/(shell)/agenda/page.tsx` (lista) | `LeadFichaIT`, `FiltroModularIT` |
| RF-CRM-27 (Kanban) | ⚠️ | Modelo de dados suporta (etapa com `ordem`), mas não encontrei view Kanban no frontend construído (ver E15, lista de divergências visuais) | Nenhum |
| RF-CRM-28/29 (import/export CSV) | ❌ **rebaixado de ✅** | Nenhum endpoint `/leads/importar` ou `/leads/exportar` em `LeadController` nem `FiltroDeLeadsController` — a doc antiga citava rotas que não existem | Nenhum |

## Dashboard e Relatórios

| Requisito | Status | Implementação | Teste |
|---|---|---|---|
| RF-CRM-31/33 (Dashboard, visão única) | ❌ **achado novo** | `docs/09` §2 diz que a Dashboard consolidada permanece no escopo — não encontrei controller nem rota de frontend (`app/(shell)/dashboard` não existe). A flag `dashboard` está `TRUE` no seed, então o item **aparece no menu e leva ao Placeholder** — o mesmo problema que a E15 corrigiu para Tags, ainda não corrigido aqui. Ver "Bugs encontrados" no relatório da E15b. | Nenhum |
| RF-CRM-32 | ⚠️ (mantido) | Cortado conscientemente — `docs/09` linha 4, aguarda priorização de indicadores com o cliente | N/A — decisão de produto |
| RF-CRM-79 (Relatórios) | ⚠️ (mantido, evidência revisada) | Cortado conscientemente — `docs/09` linha 1. Único código do módulo `crm-relatorios` é `AuditLogController` (auditoria administrativa, não relatório de negócio) | `AuditoriaIT` cobre só o audit log |

## Follow-up / Automação (configuração)

| Requisito | Status | Implementação | Teste |
|---|---|---|---|
| RF-CRM-34 a 38 | ❌ **rebaixado de ✅** | `regra_follow_up`, `mensagem_festiva`, `configuracao_resumo_ia` existem só como migration (`V7__automacao_config.sql`). Nenhum domain/application/interfaces usa essas tabelas — nem escrita nem leitura, nem no contrato `/internal/v1`. Confirmado na E15/E15b; ver `docs/09` recomendado para atualização com esta descoberta | Nenhum |
| RF-CRM-38a a 38e | ✅ **evidência renovada na E15b** | `configuracao_automacao` + `ConfiguracaoAutomacaoController` — agora com `GET` (E15b §1, autenticado por JWT, `hasAnyRole('GESTOR','SUBGESTOR')`) além do `PUT` que já existia | `ContratoAutomacaoIT` (`PainelAdmin`, `Configuracao`) |
| RF-CRM-72/73 | ❌ **rebaixado de ✅** | `regra_fidelizacao` existe só como migration — mesmo caso de RF-CRM-34-38, nenhum caso de uso de escrita, campo `ativo` nunca é alterado por nenhum código | Nenhum |
| RF-CRM-74 (disponibilidade individual) | ✅ | `disponibilidade_atendente_ia` é escrita por `EquipeRepositorioJdbc::atualizarPresenca` (fica `TRUE` quando o atendente marca presença `ONLINE`) e lida por `AtendentesDisponiveisInternalController` | `EquipeEPresencaIT` |
| RF-CRM-75 (rotinas por dia) | ❌ **rebaixado de ✅** | `rotina_disponibilidade` e `rotina_disponibilidade_atendente` existem só como migration — mesmo achado de `horario_trabalho` (RF-CRM-54): esta é a tabela que a seção "Rotinas pré-definidas" do protótipo de Automação usaria, e não tem código nenhum atrás | Nenhum |
| RF-CRM-76 (telemetria) | ⚠️ **rebaixado de ✅** | Escrita real: `RegistrarEventoDeAutomacaoUseCase` incrementa `status_automacao_telemetria` via `POST /internal/v1/eventos`. **Mas** não existe leitura em lugar nenhum (nem `/api/v1`, nem `/internal/v1`) e não encontrei nenhum `SimpMessagingTemplate` publicando em `/topic/automacao/status` — esse tópico WS citado na doc antiga não existe no código | Nenhum — `/internal/v1/eventos` não é exercitado por nenhum IT |

## Campanhas

| Requisito | Status | Implementação | Teste |
|---|---|---|---|
| RF-CRM-39 a 44 | ❌ **rebaixado de ✅** | O módulo `crm-campanhas` inteiro só tem `package-info.java` em cada camada — zero domain, application, infrastructure ou interfaces. Coerente com o corte de `docs/09` (flag `campanhas = false`), mas a doc antiga marcava ✅ mesmo assim | Nenhum |

## Chat interno

| Requisito | Status | Implementação | Teste |
|---|---|---|---|
| RF-CRM-45/78 | ⚠️ **parcial — E44** | Conversas diretas de texto, API autenticada, RLS, leitura individual e entrega pela fila pessoal (`crm-equipe`, `V37`). Grupos, mídia, busca, reações e links para atendimento continuam fora do escopo. | `ChatInternoUseCaseTest` (6), `RedisSubscriberDeAtendimentoTest.chat_interno_entrega_apenas_aos_participantes_destinatarios` |

## Equipe, Tags, Mensagens Rápidas, Horários, Arquivos, Lembretes, Mensagens Programadas

| Requisito | Status | Implementação | Teste |
|---|---|---|---|
| RF-CRM-46/47 | ✅ | `UsuarioController`, `AvaliacaoController` | `EquipeEPresencaIT` |
| RF-CRM-48/49 (CRUD, cor/ícone) | ✅ | `TagController` (`/api/v1/tags`, CRUD completo) | `TagsEEtapasIT` |
| RF-CRM-50/77 (métricas, mini-dashboard) | ❌ **rebaixado de ✅** | Nenhum endpoint retorna contagem de leads por tag — `LeadTagRepositorio` não expõe agregação nenhuma. Confirmado ao construir `/tags` na E15: a tela real saiu sem mini-dashboard por este motivo exato | Nenhum |
| RF-CRM-51 a 53 | ✅ | `MensagemRapidaController` | `MensagensRapidasIT` |
| RF-CRM-54 (Horários) | ❌ **rebaixado de ✅** (achado pela E15, mas esta é a primeira vez que `docs/05` reflete isso — a E15 corrigiu `docs/09` e o menu, não esta matriz) | `horario_trabalho` só como migration — zero código de aplicação; agora com registro em `docs/09` §1.1 e flag `horarios = false` | Nenhum |
| RF-CRM-55/56 (Banco de Arquivos) | ❌ **rebaixado de ✅** | `arquivo_banco` só como migration — mesmo achado de RF-CRM-13 | Nenhum |
| RF-CRM-57 a 64 | ✅ | `LembreteController`, `MensagemProgramadaController` | `LembretesIT`, `MensagensProgramadasIT` |

## Configurações globais e perfil

| Requisito | Status | Implementação | Teste |
|---|---|---|---|
| RF-CRM-80 (preferências) | ❌ **rebaixado de ✅** | Nenhuma tabela `preferencia_usuario`, nenhum controller — a doc antiga citava um artefato que não existe em lugar nenhum do schema nem do código | Nenhum |
| RF-CRM-81 (perfil e presença) | ✅ | `UsuarioController` (`PATCH /usuarios/me/presenca`) | `EquipeEPresencaIT` |

## Requisitos não funcionais

| Requisito | Status | Implementação | Teste |
|---|---|---|---|
| RNF-CRM-01 (estabilidade) | ⚠️ parcial | `/health/critical` verifica banco-chat, fila efetiva, canal, WebSocket, partições e outbox; liveness continua sem dependências. Watchdog externo ainda precisa ser provisionado e exercitado na homologação | `SaudeCriticaController` + `SaudeCriticaIT`, `SaudeBancoIndisponivelIT`, `SaudeCanalInvalidoIT`; runbook `docs/15` |
| RNF-CRM-03 (modularidade) | ✅ | `configuracao_automacao`, `filtro_modular` (JSONB), feature flags | `ContratoAutomacaoIT` (flags), `FiltroModularIT` |
| RNF-CRM-06 (intuitividade) | ⚠️ (mantido) | Decisão de produto/UI, fora do escopo deste documento | N/A |
| RNF-CRM-08 (performance) | ⚠️ **rebaixado de ✅** | Particionamento real (`V5__atendimento.sql`), índices reais. **Nenhum teste de carga/performance no repositório** — a meta de "~5 mil atendimentos/mês" não tem evidência empírica, só schema compatível com o volume | Nenhum teste de carga encontrado |
| RNF-CRM-10 (auditoria) | ✅ | `AuditoriaAspect` + `@Auditable` (`crm-shared-kernel`) | `AuditoriaIT`, `AuditoriaDeAcoesSensiveisTest` (teste de arquitetura — prova que ação sensível sem `@Auditable` reprova) |
| RNF-CRM-12 (responsividade) | ⚠️ (mantido) | Decisão de frontend, fora do escopo deste documento | N/A |

## Regras de negócio

| Requisito | Status | Implementação | Teste |
|---|---|---|---|
| RN-CRM-01 | ✅ | `VisibilidadeLeadSpecification` | `IsolamentoDeAgendaIT` |
| RN-CRM-02 | ✅ | `lead.atendente_responsavel_id` | `IsolamentoDeAgendaIT` |
| RN-CRM-03 | ✅ | Autorização em `GestaoDeTagsUseCases` | `TagsEEtapasIT` (atendente recebe 403 em criar/editar/excluir tag) |
| RN-CRM-04 | ✅ | Filtro por `atendente_id` em `MensagemRapidaController` (`?minhas=true`) | `MensagensRapidasIT::gestor` (prova que `minhas=true` restringe mesmo o gestor vendo tudo por padrão) |
| RN-CRM-05 | ✅ (fora do escopo de backend) | Frontend — mesma evidência de RF-CRM-06 | Nenhum teste de backend |
| RN-CRM-06 | ✅ | `EnviarMensagemUseCase` (atualiza `atendimento.atendente_id` ao enviar) | `AtendimentoTest` (unitário), `AtendimentoAcoesControllerIT` |
| RN-CRM-07 | ✅ | `configuracao_automacao` + `/internal/v1/automation-config` | `ContratoAutomacaoIT` |

## Requisitos Internos (Base PAI / Synapse)

Esta seção mistura decisões de arquitetura (verificáveis por código) com narrativa de posicionamento (não verificável por controller+teste). Aplicando a mesma régua: o que não aponta para um artefato concreto vira ⚠️, não ✅ — não é mentira, é categoria errada de afirmação para esta tabela.

| Requisito interno | Status | Implementação | Teste |
|---|---|---|---|
| Robustez / mínimo de bugs em produção | ✅ | Testcontainers real em todo IT (`PostgresIT`), Outbox (`outbox_evento`) | Todos os `*IT.java` estendem `PostgresIT` — Postgres real, não H2/mock |
| Ultra rapidez e fluidez | ⚠️ **rebaixado de ✅** | Decisão de frontend (Server Components, Optimistic UI) — sem métrica nem teste de performance no repositório | Nenhum |
| Design único (sem cara de template de IA) | ⚠️ **rebaixado de ✅** | Decisão de design, não testável por controller | N/A |
| Referência de UX claude.ai | ⚠️ **rebaixado de ✅** | Decisão de design | N/A |
| Reuso de componentes robustos | ⚠️ **rebaixado de ✅** | Escolha de dependência (`shadcn/ui`, `@base-ui/react` em `package.json`) — não é "requisito" testável | N/A |
| Continuidade inegociável (precedência) | ⚠️ **rebaixado de ✅** | Circuit breaker real (`MetaCloudApiAdapter`); Bulkhead não localizado; "réplicas ≥ 2" é deploy, não código | Nenhum teste de resiliência sob carga |
| Resiliência / degradação controlada | ✅ | `MetaCloudApiAdapter` usa Resilience4j (`CircuitBreaker`) | Não encontrei teste que force o circuito a abrir — evidência de implementação, não de comportamento sob falha |
| Alerta automático de indisponibilidade | ⚠️ parcial | backend classifica `CRITICO`/`DEGRADADO`, aplica duas falhas, janela e destinos via `ALERTA_WEBHOOK`; queda total exige o Kuma externo ainda não provisionado | `MonitorarSaudeCriticaUseCaseTest` + `docs/15-operacao-watchdog-externo.md` |
| Substituição do número principal | ✅ | `canal_credencial` com `vigente_ate` — troca preserva histórico | `CanalWhatsAppIT::trocaDeNumero_preservaHistoricoEMensagemEmTransito` |
| Logs de administração com filtros ricos | ✅ | `AuditLogController` + `ConsultarAuditLogUseCase` | `AuditoriaIT` |
| Documentação de endpoints para a Automação | ✅ | OpenAPI gerado no build, 62 operações documentadas | `OpenApiIT` (cobertura de summary/description/tag/resposta + contagem exata) |
| Base PAI: ultra-modularidade, mínimo hardcode | ✅ | 8 módulos Maven + `feature_flag` tabela | `SeedDesenvolvimentoIT`, `ContratoAutomacaoIT` (flags) |
| Base PAI: reuso pai→filho sem fork | ⚠️ **rebaixado de ✅** | Estrutura de monorepo único suporta isso por design, mas não há um segundo filho real para provar — afirmação não testável hoje | N/A |
| Roadmap: mini front-end da Base PAI | ⚠️ (mantido, sem mudança) | Especulativo — `/internal/v1` autenticado por instância já existe, mas não há front-end algum construído para isso | N/A |
| Roadmap: sistema de Novidades via GitHub | ⚠️ (mantido, sem mudança) | Especulativo, nada implementado | N/A |

## Resumo da revisão (E15b)

**23 linhas rebaixadas de ✅** para ❌ ou ⚠️ nesta rodada (contagem no relatório da tarefa). Os achados mais graves, em ordem de gravidade:

1. **`chat_interno` esteve com a flag `TRUE`** e zero código por trás; a E44 corrigiu o contrato e entregou a fase direta de texto.
2. **`dashboard` também está com a flag `TRUE`**, sem controller nem rota de frontend — mesmo problema que motivou a E15 para Tags/Automação/Horários, não corrigido para Dashboard.
3. **Follow-up, Fidelização, Festivas, Aniversário, Rotinas** (RF-CRM-34-38, 72/73, 75) — toda a "Central de Automação" do documento de requisitos original é schema sem aplicação. Só a configuração geral (`configuracao_automacao`) tem código real, e só nesta rodada ganhou leitura por JWT.
4. **Campanhas** — módulo inteiro vazio, mas isso já era esperado e está registrado em `docs/09`.
5. **CSV import/export de leads** e **contagem de tags** — funcionalidades específicas citadas na doc antiga que nunca tiveram endpoint.

Nenhum requisito 🔴 crítico de segurança ficou sem cobertura — autenticação, RBAC, isolamento de agenda (RLS) e auditoria de ações sensíveis têm implementação e teste nomeados. A dívida está concentrada em **features de configuração/automação anunciadas mas nunca construídas**, não em risco de vazamento de dado entre atendentes.
