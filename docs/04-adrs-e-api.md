# 04. Architecture Decision Records (ADRs) e Contrato de API

## Parte A — ADRs

### ADR-001 — Monólito modular em vez de microsserviços no MVP

**Contexto:** volume esperado ~5 mil atendimentos/mês (RNF-CRM-08), equipe de desenvolvimento provavelmente pequena/média, prazo de entrega apertado para um CRM "sob medida".
**Decisão:** monólito modular (Clean Architecture por bounded context), banco único PostgreSQL.
**Consequências:** deploy e operação simples; módulos com fronteira clara (portas/interfaces) permitem extrair qualquer um como serviço separado depois, se o volume ou a equipe crescerem — sem reescrever regra de negócio, só trocar o adaptador de infraestrutura.

### ADR-002 — Automação desacoplada por fila assíncrona

**Contexto:** RNF-CRM-01 é a "ultra-regra": a aba Atendimentos não pode parar entre 08:00–18:30, mesmo que a Automação/IA falhe.
**Decisão:** toda ação da Automação sobre o CRM chega por fila (RabbitMQ), nunca por chamada síncrona bloqueante; o CRM expõe API de configuração somente-leitura para a Automação consultar parâmetros.
**Consequências:** uma falha ou lentidão da IA nunca bloqueia envio/recebimento humano de mensagens; exige tratamento de fila (retries, dead-letter) e idempotência nos consumidores.

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

---

## Parte B — Convenções gerais de API

- **Versionamento:** prefixo `/api/v1/...`; mudanças incompatíveis sobem para `/api/v2` mantendo v1 ativo durante transição.
- **Autenticação:** `Authorization: Bearer <JWT>`; access token curto (15 min) + refresh token (7 dias) via `POST /api/v1/auth/refresh`.
- **Paginação:** *offset-based* (`?page=0&size=20`) nas listagens de gestão; *cursor-based* (`?cursor=...`) na lista de mensagens de um atendimento (evita duplicar/pular itens quando novas mensagens chegam durante a rolagem).
- **Erros:** formato padrão RFC 7807 (*Problem Details*):
  ```json
  {
    "type": "https://api.crm.estrutural/erros/lead-nao-encontrado",
    "title": "Lead não encontrado",
    "status": 404,
    "detail": "Nenhum lead com id 123 foi localizado",
    "instance": "/api/v1/leads/123"
  }
  ```
- **Autorização:** cada endpoint declara o(s) papel(éis) mínimo(s) exigido(s); a checagem de "é dono deste recurso" (ex.: `RN-CRM-01`) é feita no *use case*, não apenas por papel.

## Parte C — Endpoints REST por módulo (amostra representativa)

### Atendimento

| Método | Rota | Descrição | Papel mínimo | Requisito |
|---|---|---|---|---|
| GET | `/api/v1/atendimentos` | Lista atendimentos (filtra por `visao=ativos\|pendentes\|potenciais\|todos`) | Atendente | RF-CRM-20/21 |
| GET | `/api/v1/atendimentos/{id}/mensagens` | Histórico paginado (cursor) | Atendente | RF-CRM-08 |
| POST | `/api/v1/atendimentos/{id}/mensagens` | Envia mensagem (texto/áudio/imagem/documento) | Atendente | RF-CRM-09 |
| POST | `/api/v1/atendimentos/{id}/transferir` | Transfere para outro atendente/IA | Atendente | RF-CRM-65/RN-CRM-06 |
| POST | `/api/v1/atendimentos/{id}/finalizar` | Encerra atendimento | Atendente | RF-CRM-65 |
| GET | `/api/v1/leads/{id}/timeline` | Linha do tempo de eventos | Atendente | RF-CRM-15 |

### CRM Core

| Método | Rota | Descrição | Papel mínimo | Requisito |
|---|---|---|---|---|
| GET | `/api/v1/leads` | Lista/filtra leads (filtro modular via query ou `POST /leads/filtrar`) | Atendente | RF-CRM-04/23 |
| POST | `/api/v1/leads/filtrar/contagem` | Retorna contagem em tempo real do filtro montado | Atendente | RF-CRM-05 |
| POST | `/api/v1/leads/importar` | Importa CSV | Gestor | RF-CRM-26 |
| GET | `/api/v1/leads/exportar` | Exporta CSV | Gestor | RF-CRM-27 |
| POST | `/api/v1/lembretes` | Cria lembrete | Atendente | RF-CRM-57/59 |
| POST | `/api/v1/mensagens-programadas` | Agenda mensagem | Atendente | RF-CRM-61/62 |

### Campanhas

| Método | Rota | Descrição | Papel mínimo | Requisito |
|---|---|---|---|---|
| POST | `/api/v1/campanhas` | Cria campanha (nome, mensagens, filtro, datas) | Gestor | RF-CRM-39 |
| POST | `/api/v1/campanhas/{id}/ativar` | Ativa e define intervalo de envio | Gestor | RF-CRM-41 |
| GET | `/api/v1/campanhas/{id}/metricas` | Métricas por mensagem + comparativo | Gestor | RF-CRM-43/44 |

### Automação — Configuração (consumida pelo serviço de Automação)

| Método | Rota | Descrição | Consumidor |
|---|---|---|---|
| GET | `/internal/v1/automation-config` | Todos os parâmetros tipados atuais | Serviço de Automação |
| GET | `/internal/v1/automation-config/{chave}` | Parâmetro específico | Serviço de Automação |
| PUT | `/api/v1/automacao/config/{chave}` | Atualiza parâmetro (dispara invalidação de cache + evento) | Gestor/Subgestor |
| GET | `/api/v1/automacao/status` | Telemetria (mensagens enviadas, conexão, etc.) | Gestor |

Autenticação das rotas `/internal/v1`: header `X-Synapse-Token` com o token permanente da instância. Namespace e formato são idênticos em todos os filhos — só URL e token mudam. OpenAPI gerado no build e coberto por testes de contrato.

### Configuração da instância (consumida pelo frontend)

| Método | Rota | Descrição | Requisito |
|---|---|---|---|
| GET | `/api/v1/config/tema` | Design tokens (cores, tipografia, logo) | Base PAI / nada hardcoded |
| GET | `/api/v1/config/textos` | Catálogo de labels e nomes de cards | Base PAI / nada hardcoded |
| GET | `/api/v1/config/features` | Feature flags habilitadas para esta instância | Base PAI |
| GET | `/api/v1/canais/{id}/credenciais` | Credencial vigente do canal | Troca do número principal |
| POST | `/api/v1/canais/{id}/credenciais/trocar` | Troca o número ativo (valida antes, versiona a antiga) | Troca do número principal |
| GET | `/api/v1/audit-log` | Log de auditoria com filtros (ator, ação, entidade, lead, período) | Logs de administração |

### Saúde e monitoramento

| Método | Rota | Descrição |
|---|---|---|
| GET | `/health/critical` | Valida o caminho de mensagens: banco, fila, canal autenticado, WebSocket aceitando conexão. Consumido pelo watchdog externo. |
| GET | `/health/liveness` `/health/readiness` | Probes padrão de orquestrador |

### Equipe

| Método | Rota | Descrição | Papel mínimo | Requisito |
|---|---|---|---|---|
| GET/POST/PUT | `/api/v1/usuarios` | CRUD de atendentes/subgestores | Gestor | RF-CRM-46 |
| PATCH | `/api/v1/usuarios/me/presenca` | Atualiza presença (online/ausente/offline) | Atendente | RF-CRM-81 |
| GET | `/api/v1/equipe/avaliacoes` | Mini-dashboard de avaliações | Gestor | RF-CRM-47 |

## Parte D — WebSocket (tempo real)

| Tópico | Direção | Payload | Requisito |
|---|---|---|---|
| `/topic/atendimento/{id}` | Servidor → Cliente | Nova mensagem, mudança de status de entrega/leitura | RF-CRM-08/67 |
| `/topic/usuario/{id}/notificacoes` | Servidor → Cliente | Transferência recebida, lembrete disparado | RF-CRM-22/60 |
| `/topic/presenca` | Servidor → Cliente | Mudança de status de presença de qualquer usuário | RF-CRM-78/81 |
| `/topic/chat-interno/{conversaId}` | Servidor ↔ Cliente | Mensagens do chat interno | RF-CRM-45/78 |
| `/topic/automacao/status` | Servidor → Cliente | Atualização de telemetria da automação | RF-CRM-76 |

## Parte E — Fila assíncrona (CRM ↔ Automação)

| Fila/Evento | Publicador | Consumidor | Descrição |
|---|---|---|---|
| `automation.config.updated` | CRM | Automação | Configuração alterada, recarregar cache |
| `automation.events.transferir-lead` | Automação | CRM | IA solicita transferência de lead para atendente |
| `automation.events.follow-up-enviado` | Automação | CRM | Registra evento na timeline (RF-CRM-15) |
| `automation.events.resumo-gerado` | Automação | CRM | Atualiza `lead.resumo_ia` |
| `automation.events.campanha-metrica` | Automação | CRM | Atualiza `campanha_mensagem_metrica` |

Todos os consumidores devem ser **idempotentes** (checar se o evento já foi processado por `event_id`), já que filas garantem *at-least-once delivery*.
