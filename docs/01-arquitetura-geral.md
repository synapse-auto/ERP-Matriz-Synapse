# 01. Arquitetura Geral — CRM Estrutural Vidros

## 1. Stack tecnológica

| Camada | Escolha | Justificativa |
|---|---|---|
| Frontend | **Next.js 14+ (App Router, TypeScript)** | SSR/streaming para dashboard e relatórios pesados, rotas de API leves para BFF, ecossistema React maduro para UI densa (Kanban, chat, filtros modulares). |
| Estado/dados no front | TanStack Query + Zustand | Cache de servidor (leads, atendimentos) separado de estado de UI (filtros abertos, aba ativa). |
| Tempo real | WebSocket (STOMP sobre SockJS ou WS nativo) | RF-CRM-08/RNF-CRM-01 exigem chat com atualização instantânea e alta disponibilidade. |
| Backend | **Java 21 + Spring Boot 3** | Ecossistema maduro para RBAC, mensageria, WebSocket, integrações; boa aderência a Clean/Hexagonal Architecture. |
| Persistência | PostgreSQL 15+ | Já definido pelo cliente; JSONB cobre bem os "filtros modulares" e os parâmetros de automação sem explosão de tabelas. |
| Acesso a dados | Spring Data JPA (agregados) + jOOQ ou SQL nativo pontual (relatórios/consultas analíticas) | JPA para escrita/transações de domínio; SQL direto para os relatórios pesados da aba Relatórios/Dashboard, evitando N+1 e mantendo performance (RNF-CRM-08). |
| Migrações | Flyway | Versionamento de schema auditável, alinhado a squads pequenos. |
| Filas/eventos | RabbitMQ (ou Amazon SQS gerenciado) | Desacopla CRM ↔ Automação; garante que a "Automação" não derruba a aba Atendimentos (RNF-CRM-01). |
| Cache/pub-sub | Redis | Presença de usuários (RF-CRM-81/78), pub/sub de WebSocket entre instâncias, cache de `automation_config`. |
| Armazenamento de arquivos | S3-compatível (AWS S3 ou MinIO on-prem) | Banco de Arquivos (RF-CRM-55) e mídia de mensagens não devem morar no Postgres como BLOB. |
| Autenticação | JWT (access + refresh) via Spring Security | RF-CRM-01/02, sessão segura e stateless, compatível com múltiplas instâncias. |
| Observabilidade | Logs estruturados (JSON) + métricas (Micrometer/Prometheus) + healthchecks dedicados à aba Atendimentos | RNF-CRM-01 é a "ultra-regra" do produto — precisa de alarme dedicado, não genérico. |

## 2. Estilo arquitetural: Monólito modular em Clean/Hexagonal Architecture

**Decisão:** começar como **monólito modular** (um único deploy, banco único), organizado internamente em módulos por *bounded context*, cada um seguindo Clean Architecture (Domain → Application → Infrastructure → Interface).

Por quê não microsserviços desde o dia 1: volume de ~5 mil atendimentos/mês (RNF-CRM-08) não justifica a complexidade operacional de microsserviços; um monólito bem modularizado entrega a mesma capacidade de evolução com custo de operação muito menor, e os módulos já nascem com fronteiras claras para extração futura (ex.: Automação pode virar serviço separado quando o volume justificar).

### 2.1 Módulos (bounded contexts)

```
crm-backend/
├── atendimento/        # Conversas, mensagens, canais, WebSocket gateway
├── crm-core/            # Leads, tags, agenda de contatos, lembretes, mensagens programadas
├── automacao-config/    # Painel de configuração da automação (fonte da verdade dos parâmetros)
├── campanhas/           # Campanhas, filtros de público, métricas por mensagem
├── equipe/              # Usuários, papéis, presença, avaliações, horários
├── chat-interno/        # Chat entre atendentes/gestores
├── arquivos/            # Banco de Arquivos (metadados; binário no object storage)
├── relatorios/          # Read-models e consultas agregadas para Dashboard e Relatórios
└── shared-kernel/        # Value objects comuns (Papel, StatusPresenca, FiltroModular, Auditoria)
```

Cada módulo expõe **portas** (interfaces) e é consumido pelos demais só por elas — nunca acessando a tabela de outro módulo diretamente. Isso é o que viabiliza RNF-CRM-03 (nada hardcoded / modular) e facilita testes.

### 2.2 Camadas dentro de cada módulo

- **Domain** — entidades, agregados, value objects, regras de negócio puras (sem Spring, sem JPA).
- **Application** — casos de uso (`CriarLeadUseCase`, `TransferirAtendimentoUseCase`), orquestra domínio + portas.
- **Infrastructure** — adaptadores: repositórios JPA, cliente WhatsApp, produtor RabbitMQ, cliente S3.
- **Interface** — controllers REST, handlers WebSocket, DTOs/mappers.

A regra de dependência é sempre "de fora para dentro": Interface e Infrastructure dependem de Application/Domain, nunca o contrário. Isso é o que permite trocar de canal (WhatsApp → outro canal) ou de fila (RabbitMQ → SQS) sem tocar em regra de negócio — atendendo diretamente RF-CRM-66 (arquitetura multicanal desde o lançamento).

## 3. Integração CRM ↔ Automação

O documento de requisitos é explícito: **o CRM não executa a automação, apenas a configura** (RN-CRM-07). A separação técnica reflete isso:

- O módulo `automacao-config` expõe uma **API interna somente-leitura** (`GET /internal/v1/automation-config`) que a Automação consulta em tempo de execução — nunca lê direto do banco do CRM (evita acoplamento de schema).
- Alterações de configuração (RF-CRM-38b: "efeito sem novo deploy") invalidam o cache Redis local depois do commit. A Automação obtém o valor vigente na próxima leitura da API interna; não existe evento RabbitMQ de configuração no contrato atual.
- A entrega de uma conversa da IA para atendimento humano usa `POST /internal/v1/atendimentos/{id}/transferir`, autenticado por `X-Synapse-Token` na rede interna. O CRM escolhe o atendente elegível com menor carga aberta; a Automação não informa o destinatário. A resposta síncrona confirma imediatamente se a conversa foi entregue, sem criar usuário técnico nem contornar as regras comerciais.
- Integrações assíncronas de telemetria e configuração continuam isoladas do caminho de mensagens humanas. Se a Automação cair, nenhuma operação humana da aba Atendimentos depende dela para enviar ou receber mensagens, preservando a **RNF-CRM-01 (ultra-regra)**.
- Circuit breaker (Resilience4j) em qualquer chamada síncrona do CRM em direção a serviços externos de IA, para que uma falha externa nunca trave o envio/recebimento de mensagens.

## 4. Tempo real (aba Atendimentos e Chat Interno)

- WebSocket gateway no backend Java, com Redis como *pub/sub* backplane — permite múltiplas instâncias do backend atrás de um load balancer sem perder mensagens em tempo real (necessário para RNF-CRM-08, concorrência de vários atendentes simultâneos).
- O handshake STOMP ocorre em `/ws?token=<JWT>`. Eventos de atendimento são entregues apenas pela fila pessoal `/user/queue/atendimento.{id}`, após autorização por carteira; revogações usam `/user/queue/revogacoes`. Não há broadcast de dados de lead em `/topic`.
- Status de entrega/leitura (RF-CRM-67) é modelado como estado da mensagem (`ENVIADO → ENTREGUE → LIDO`), atualizado por webhook do canal (WhatsApp) e propagado via WebSocket.

## 5. Filtros modulares (requisito transversal)

RF-CRM-04/05/40 pedem o **mesmo mecanismo de filtro combinável** em Atendimentos, Agenda de Contatos e Campanhas. Decisão: modelar como uma estrutura de critérios em **JSONB** (`filtro_modular.criterios`), com um pequeno DSL interno (lista de condições com campo/operador/valor + composição AND/OR). O backend interpreta essa árvore e traduz para SQL dinamicamente (usando `jOOQ` ou *query builder* próprio), com contagem em tempo real (RF-CRM-05) via `COUNT(*)` na mesma condição antes de salvar. Ver `03-modelo-dados-postgres.md`.

## 6. Requisitos não funcionais → decisões técnicas

| Requisito | Decisão técnica |
|---|---|
| RNF-CRM-01 (estabilidade 08:00–18:30) | Fila assíncrona isolando Automação; health check dedicado ao módulo `atendimento`; deploy com *rolling update* (zero downtime); réplicas mínimas = 2. |
| RNF-CRM-03 (nada hardcoded) | Tabela `automation_config` chave-valor tipada; filtros em JSONB; textos/cores de UI vindos de configuração, não de constantes no frontend. |
| RNF-CRM-06 (intuitividade) | Decisão de produto/UI — Next.js com design system próprio (fora do escopo deste documento técnico, ver guia de UI separado). |
| RNF-CRM-08 (~5 mil atendimentos/mês, concorrência) | Connection pooling (HikariCP), índices dedicados (ver doc 03), paginação em todas as listagens, WebSocket com Redis pub/sub para escalar horizontalmente. |
| RNF-CRM-10 (auditoria) | Tabela `evento_timeline` (append-only) + `updated_by`/`updated_at` em tabelas sensíveis. |
| RNF-CRM-12 (responsividade) | Next.js com layout responsivo; desktop como alvo primário (decisão de produto). |

## 7. Papéis e RBAC

Quatro papéis (RF-CRM-02): **Atendente, Subgestor, Gestor, Administrador**. RBAC implementado como:

- Enum `papel_usuario` no banco + anotações `@PreAuthorize` no Spring Security por caso de uso (não por rota genérica — cada *use case* declara quem pode chamá-lo).
- Regra de isolamento de agenda (RN-CRM-01) aplicada na camada de Application via *specification* (`VisibilidadeLeadSpec`) que filtra por `atendente_responsavel_id` quando o papel é `ATENDENTE`, e libera visão ampla para `GESTOR`/`SUBGESTOR` — nunca deixada para o frontend decidir.

## 8. Próximos documentos

Ver `02-modelo-dominio-classes.md` para o diagrama de classes e `03-modelo-dados-postgres.md` para o schema físico.
