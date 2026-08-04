# 05. Matriz de Rastreabilidade — Requisitos → Artefatos Técnicos

Checklist de conferência: cada requisito do documento original é cruzado com o artefato onde foi endereçado. `✅` = coberto nesta rodada de modelagem; `⚠️` = coberto parcialmente / decisão adiada conscientemente (não é lacuna esquecida — é escopo de produto ou detalhamento de fase 2).

## Papéis e permissões

| Código | Resumo | Artefato | Status |
|---|---|---|---|
| RF-CRM-01 | Autenticação individual | `usuario` (senha_hash) + JWT (Spring Security) — doc 01 §1/§7 | ✅ |
| RF-CRM-02 | RBAC por papel | `papel_usuario` enum + `@PreAuthorize` por caso de uso — doc 01 §7 | ✅ |
| RF-CRM-03 | Isolamento de agenda | `VisibilidadeLeadSpec` + `idx_lead_atendente` — doc 01 §7, doc 03 | ✅ |

## Estrutura geral (transversal)

| Código | Resumo | Artefato | Status |
|---|---|---|---|
| RF-CRM-04/05 | Filtros modulares + contador | `filtro_modular` (JSONB) — ADR-003, doc 03 | ✅ |
| RF-CRM-06 | Clique/duplo clique no lead | Comportamento de frontend (Next.js); não requer modelo de dados adicional | ✅ (fora do escopo de dados) |
| RF-CRM-07 | Busca (nome/telefone/CPF/tag) | Índices `pg_trgm`, `idx_lead_telefone`, `idx_lead_cpf` — doc 03 | ✅ |

## Atendimentos

| Código | Resumo | Artefato | Status |
|---|---|---|---|
| RF-CRM-08/09 | Chat estilo WhatsApp, tipos de mídia | `atendimento` + `mensagem` + WebSocket — doc 01 §4, doc 02 §3, doc 03 | ✅ |
| RF-CRM-10/11 | Card do lead + filtros na lista | `lead` + `filtro_modular` (contexto=ATENDIMENTOS) | ✅ |
| RF-CRM-12/13 | Mensagens rápidas / anexos do Banco de Arquivos | `mensagem_rapida`, `arquivo_banco` — doc 02 §2/§6 | ✅ |
| RF-CRM-14 a 16 | Painel lateral do lead (info, timeline, ações) | `lead` + `evento_timeline` + `lembrete` + `mensagem_programada` | ✅ |
| RF-CRM-17 a 19 | Ficha/status/etapa do lead | `lead`, `status_basico_lead`, `etapa_atendimento` — doc 02 §2 | ✅ |
| RF-CRM-20/21/22 | Visões por papel + reflexo de transferências | Endpoint `GET /atendimentos?visao=` + evento `automation.events.transferir-lead` — doc 04 Parte C/E | ✅ |
| RF-CRM-65 | Cabeçalho do atendimento (transferir/finalizar) | `POST /atendimentos/{id}/transferir` e `/finalizar` — doc 04 | ✅ |
| RF-CRM-66 | Multicanal desde o lançamento | Tabela `canal` como atributo, não hardcoded — doc 01 §2.2, doc 03 | ✅ |
| RF-CRM-67 | Status de entrega/leitura | `status_entrega` enum em `mensagem` — doc 03 | ✅ |
| RF-CRM-68 | Anexos ricos (card com metadados) | `mensagem.midia_metadados JSONB` — doc 03 (adicionado na revisão) | ✅ |
| RF-CRM-69 | Agendar mensagem pelo composer | Integra com `mensagem_programada` — doc 02 §2 | ✅ |
| RF-CRM-70/71 | Etapa como stepper / contadores do lead | `etapa_atendimento.ordem`, `lead.num_atendimentos/num_mensagens` (denormalizados) — doc 03 §3 nota 9 | ✅ |

## Agenda de Contatos

| RF-CRM-23 a 29 | Lista, agenda por atendente, Kanban, import/export CSV, automação em massa | `lead`, `etapa_atendimento` (Kanban), endpoints `/leads/importar` e `/exportar` — doc 02 §2, doc 04 Parte C | ✅ |

## Dashboard e Relatórios

| Código | Resumo | Artefato | Status |
|---|---|---|---|
| RF-CRM-31 a 33 | Filtro de data, amplitude de indicadores, mini-dashboard gestor | Módulo `relatorios` citado como *read-model* — doc 01 §2.1 | ⚠️ Pendência: ainda não detalhamos as *views*/consultas agregadas específicas nem se serão *materialized views* ou consultas on-demand. Recomendo uma rodada de modelagem dedicada a Dashboard/Relatórios assim que a lista exata de indicadores for priorizada com o cliente — o volume de "mostrar milhares de informações sobre tudo" (RF-CRM-32) é vago o suficiente para exigir isso. |
| RF-CRM-79 | Relatórios operacionais separados do Dashboard | Mesmo módulo `relatorios`, endpoints com filtro de período + exportação | ⚠️ Mesma pendência acima |

## Follow-up / Automação (configuração)

| Código | Resumo | Artefato | Status |
|---|---|---|---|
| RF-CRM-34 a 38 | Central de mensagens automáticas, tempos/textos, festivas, aniversário, gatilho de resumo | `regra_follow_up`, `mensagem_festiva`, `configuracao_resumo_ia` — doc 02 §5, doc 03 | ✅ |
| RF-CRM-38a a 38e | Painel de configuração como fonte da verdade | `configuracao_automacao` chave-valor tipado — ADR-004 | ✅ |
| RF-CRM-72/73 | Fidelização e ativação individual por regra | `regra_fidelizacao` (campo `ativo` em todas as regras) — doc 02 §5 | ✅ |
| RF-CRM-74/75 | Disponibilidade de atendentes / rotinas por dia | `disponibilidade_atendente_ia`, `rotina_disponibilidade(_atendente)` — doc 02 §1, doc 03 | ✅ |
| RF-CRM-76 | Telemetria da automação | `status_automacao_telemetria` (singleton) + tópico WS `/topic/automacao/status` — doc 02 §5, doc 04 Parte D | ✅ |

## Campanhas

| RF-CRM-39 a 44 | Gestão de campanhas, filtro de público, ativação, métricas e comparativo | `campanha`, `campanha_mensagem`, `campanha_mensagem_metrica`, `filtro_modular` — doc 02 §4, doc 03 | ✅ |

## Chat interno

| RF-CRM-45/78 | Chat interno + rail de presença | `chat_interno_conversa/participante/mensagem`, `status_presenca` — doc 02 §6, doc 03 | ✅ |

## Equipe, Tags, Mensagens Rápidas, Horários, Arquivos, Lembretes, Mensagens Programadas

| Código(s) | Resumo | Artefato | Status |
|---|---|---|---|
| RF-CRM-46/47 | Gestão de usuários + mini-dashboard de avaliações | `usuario`, `avaliacao` — doc 02 §1 | ✅ |
| RF-CRM-48 a 50/77 | Tags compartilhadas, métricas, mini-dashboard, cor/ícone | `tag`, `lead_tag` (com `idx_lead_tag_tag` para métricas) — doc 03 | ✅ |
| RF-CRM-51 a 53 | Mensagens rápidas pessoais/visão do gestor | `mensagem_rapida` — doc 02 §2 | ✅ |
| RF-CRM-54 | Horários da IA e atendentes | `horario_trabalho` — doc 02 §1, doc 03 | ✅ |
| RF-CRM-55/56 | Banco de Arquivos + envio rápido | `arquivo_banco` — doc 02 §6, doc 03 | ✅ |
| RF-CRM-57 a 64 | Lembretes e mensagens programadas (central, visão por papel, origem no lead, histórico) | `lembrete`, `mensagem_programada` (+ `GET /atendimentos` para histórico) — doc 02 §2, doc 04 | ✅ |

## Configurações globais e perfil

| Código | Resumo | Artefato | Status |
|---|---|---|---|
| RF-CRM-80 | Área de configurações (preferências, aparência, ajuda) | `preferencia_usuario` (chave-valor por usuário) — doc 03 §5 | ✅ Resolvido na 2ª rodada |
| RF-CRM-81 | Perfil e presença | `usuario.status_presenca`, endpoint `PATCH /usuarios/me/presenca` — doc 03, doc 04 | ✅ |

## Requisitos não funcionais

| Código | Resumo | Artefato | Status |
|---|---|---|---|
| RNF-CRM-01 | Estabilidade da aba Atendimentos (ultra-regra) | ADR-002 (fila assíncrona), health check dedicado, réplicas ≥ 2 — doc 01 §3/§6 | ✅ |
| RNF-CRM-03 | Modularidade / nada hardcoded | `configuracao_automacao`, `filtro_modular` (JSONB), etapas/canais como tabelas — docs 01–03 | ✅ |
| RNF-CRM-06 | Intuitividade | Decisão de produto/UI (design system Next.js) | ⚠️ Fora do escopo deste documento técnico — trate em um guia de UI/UX à parte |
| RNF-CRM-08 | Performance/concorrência (~5 mil atendimentos/mês) | Particionamento, índices parciais, connection pooling, WebSocket+Redis — doc 01 §6, doc 03 §3 | ✅ |
| RNF-CRM-10 | Auditoria | `evento_timeline` append-only — doc 02 §2, doc 03 §3 | ✅ |
| RNF-CRM-12 | Responsividade | Decisão de frontend (fora do escopo de dados/backend) | ⚠️ Fora do escopo deste documento técnico |

## Regras de negócio

| Código | Resumo | Artefato | Status |
|---|---|---|---|
| RN-CRM-01 | Isolamento de agenda | `VisibilidadeLeadSpec` — doc 01 §7 | ✅ |
| RN-CRM-02 | Propriedade do lead | `lead.atendente_responsavel_id` | ✅ |
| RN-CRM-03 | Gestão de tags restrita | Autorização por papel no *use case* de tags | ✅ |
| RN-CRM-04 | Privacidade de listas pessoais | `mensagem_rapida`/`lembrete`/`mensagem_programada` filtrados por `atendente_id` na aplicação | ✅ |
| RN-CRM-05 | Interações de lead (clique/duplo clique) | Frontend | ✅ (fora do escopo de dados) |
| RN-CRM-06 | Envio manual transfere o lead | Lógica no *use case* `EnviarMensagemUseCase` (atualiza `atendimento.atendente_id`) | ✅ |
| RN-CRM-07 | Configuração governa a automação | ADR-004 + API `/internal/v1/automation-config` | ✅ |

## Requisitos Internos (Base PAI / Synapse) — 2ª rodada

| Requisito interno | Artefato | Status |
|---|---|---|
| Robustez / mínimo de bugs em produção | Testcontainers, testes de contrato, Outbox, config tipada e validada — doc 06 §B, doc 08 §4 | ✅ |
| Ultra rapidez e fluidez | Server Components, Optimistic UI, CQRS light, índices/particionamento — doc 06 §B.3, doc 03 §3 | ✅ |
| Design único (sem cara de template de IA) | Design tokens próprios sobre componentes shadcn/Radix — doc 06 §B.3 e §A.2 (tensão 3) | ✅ |
| Referência de UX claude.ai | Layout base com sidebar + rodapé de perfil/config — doc 08 (cronograma dia 2) | ✅ |
| Reuso de componentes robustos | shadcn/ui + Tremor; reusar comportamento, customizar aparência | ✅ |
| Continuidade inegociável (precedência) | Bulkhead, Circuit Breaker, fila assíncrona, réplicas ≥2 — doc 06 §B.1, ADR-002 | ✅ |
| Resiliência / degradação controlada | Circuit Breaker + Fallback explícito por módulo | ✅ |
| Alerta automático de indisponibilidade | `/health/critical` + watchdog externo — doc 06 §B.1, doc 07 §7 | ✅ |
| Substituição do número principal | `canal_credencial` versionada com índice único parcial — doc 03, doc 07 §5 | ✅ |
| Logs de administração com filtros ricos | `audit_log` + Decorator/AOP `@Auditable` — doc 03 §5, doc 06 §B.2 | ✅ |
| Documentação de endpoints para a Automação | OpenAPI gerado no build + testes de contrato + `/internal/v1` versionado — doc 07 §6 | ✅ |
| Base PAI: ultra-modularidade, mínimo hardcode | Hexagonal + feature flags + 5 camadas de customização — doc 06 §B, doc 07 §3 | ✅ |
| Base PAI: reuso pai→filho sem fork | Core versionado + repo fino por filho — doc 07 §2 | ✅ |
| Roadmap: mini front-end da Base PAI | Cai naturalmente de `/internal/v1` autenticado por instância — doc 07 §8 | ✅ (não exige mudança arquitetural) |
| Roadmap: sistema de Novidades via GitHub | Releases semânticas do core como fonte — doc 07 §8 | ✅ (não exige mudança arquitetural) |

## Resumo da revisão

Após a 2ª rodada, restam **2 pendências conscientes**, ambas 🟢 e nenhuma bloqueante:

1. **Dashboard/Relatórios detalhado** — depende da lista de indicadores ser priorizada com o cliente. Está fora do recorte do dia 7 (ver `08-plano-execucao.md` §2).
2. **RNF-CRM-06 e RNF-CRM-12** (intuitividade e responsividade) — são decisões de UI/UX, endereçadas no plano de frontend, não em modelagem de dados.

`RF-CRM-80` foi resolvido nesta rodada com `preferencia_usuario`. Nenhum requisito 🔴 crítico ou 🟠 alto — do documento do cliente ou do documento interno — ficou sem artefato técnico correspondente.
