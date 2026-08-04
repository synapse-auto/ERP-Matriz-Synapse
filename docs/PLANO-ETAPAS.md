# Plano de Etapas — Synapse CRM (Base PAI)

Plano de desenvolvimento em **14 etapas**. Etapas em vez de datas: uma etapa atrasada empurra a fila, mas não invalida o plano.

Cada etapa tem um prompt correspondente (`prompt-E{n}-{nome}.md`) pronto para colar no Claude Code.

**Como usar:** faça uma etapa por vez. Ao terminar, rode a checagem de pronto antes de avançar. Não comece a etapa seguinte com a anterior "quase pronta" — em projeto de prazo curto, débito acumulado no início custa o dobro no fim.

---

## Visão geral

| # | Etapa | Escopo | Bloqueia | Estimativa |
|---|---|---|---|---|
| E00 | Fundação do monorepo | Estrutura Maven, Docker, CI | Tudo | 0,5 dia |
| E01 | Schema e migrations | Flyway com o DDL completo | E02+ | 0,5 dia |
| E02 | Auth, RBAC e Specification | Login, papéis, visibilidade de lead | E03+ | 1 dia |
| E03 | CRM Core + Filtro Modular | Lead, Tag, Etapa, Composite/Interpreter | E04, E11 | 1,5 dia |
| E04 | Atendimento — domínio | Atendimento, Mensagem, casos de uso | E05, E06 | 1 dia |
| E05 | Canal WhatsApp (ACL) | Adapter, webhook, credencial versionada | E06 | 1 dia |
| E06 | Tempo real | WebSocket + Redis pub/sub | E12 | 1 dia |
| E06b | Campos customizados | Schema JSONB + metadados (Base PAI) | — | 1 h |
| E07 | Contrato `/internal/v1` + config | **Funde a antiga E08.** Outbox saiu para a E05 | E14 | 1,5 dia |
| E09a | Auditoria via AOP | `@Auditable` + consulta de log. **Logo após E07** | — | 0,5 dia |
| E09b | Saúde crítica e alerta | `/health/critical` + watchdog. **Antes do deploy de homologação** | — | 0,5 dia |
| E10 | Frontend — fundação | Next.js, design tokens, shell, login | E11+ | 1 dia |
| E11 | Frontend — Atendimentos | Chat, lista de conversas, optimistic UI | — | 1,5 dia |
| E12 | Frontend — lead e timeline | Aba lateral, ficha, eventos | — | 0,5 dia |
| E11b | Anexos no chat | Storage + upload/download de mídia. **Necessário para homologação** | — | 0,5 dia |
| E13 | CRUDs de suporte | Lembretes, msg programadas/rápidas, equipe, presença + **gaps da E11**: mensagem rápida por palavra-chave, agendar pelo composer, atalho de tags, cursor de paginação | — | 1,5 dia |
| E14 | Hardening e homologação | Testes, deploy, checklist final | — | 1 dia |

**Total:** ~13 dias de trabalho efetivo, contra **29 dias corridos até 25/08** (~21 úteis). Sobram ~8 dias de folga — o suficiente para contingência real *e* provavelmente Campanhas e um Dashboard inicial.

Não comprometa toda a folga com escopo novo agora. Segure ao menos 3 dias de contingência genuína e decida sobre o resto em **18/08**, com dados reais de progresso. Ver `08-plano-execucao.md` §1.

---

## Ordem de execução

```
E00 ─→ E01 ─→ E02 ─→ E03 ─┬─→ E04 ─→ E05 ─→ E06 ─┐
                          │                       │
                          └─→ E07 ─→ E08          │
                                                  │
E10 (pode começar em paralelo após E02) ─→ E11 ───┘─→ E12 ─→ E13 ─→ E14
                                                        │
E09 (transversal, encaixar quando houver folga) ────────┘
```

**Caminho crítico:** E00 → E01 → E02 → E03 → E04 → E05 → E06 → E11. Se algo atrasar aqui, o produto não existe. Proteja essa linha.

---

## Detalhamento

### E00 — Fundação do monorepo
**Objetivo:** repositório que sobe com um comando e onde tudo o mais vai encaixar.
**Escopo:** parent POM Maven multi-módulo com os 8 módulos; Docker Compose (Postgres, Redis, RabbitMQ); `crm-app` executável; GitHub Actions com build + testes; `.gitignore`, `.editorconfig`.
**Pronto quando:** `docker compose up` sobe a infra, `mvn clean install` passa, a aplicação sobe e responde `/health/liveness`, o CI está verde.

### E01 — Schema e migrations
**Objetivo:** banco completo e versionado.
**Escopo:** DDL do `docs/03-modelo-dados-postgres.md` quebrado em migrations Flyway ordenadas; seed de desenvolvimento (etapas, canal, tags, usuário admin, feature flags).
**Pronto quando:** `flyway migrate` roda do zero sem erro, o seed popula, e um teste com Testcontainers valida que o schema sobe limpo.

### E02 — Auth, RBAC e Specification de visibilidade
**Objetivo:** a regra comercialmente mais sensível do sistema, feita certo antes de qualquer tela.
**Escopo:** login JWT (access + refresh), `@PreAuthorize` por caso de uso, `VisibilidadeLeadSpecification`, `UsuarioContext`.
**Pronto quando:** existe teste provando que atendente A **não** consegue acessar lead de atendente B por nenhuma rota, e que gestor consegue.

### E03 — CRM Core + Filtro Modular
**Objetivo:** o núcleo de dados e o mecanismo de filtro reusado em três telas.
**Escopo:** agregados Lead/Tag/Etapa, CRUDs, `Criterio` (Composite), `CriterioSqlInterpreter`, endpoint de contagem em tempo real.
**Pronto quando:** um filtro aninhado (`(etapa = X OR tag = Y) AND semRetorno > 30d`) retorna resultado e contagem corretos, com a Specification de visibilidade composta por cima.

### E04 — Atendimento (domínio)
**Objetivo:** o coração do produto.
**Escopo:** agregados Atendimento/Mensagem, casos de uso (enviar, receber, transferir, finalizar), domain events, atualização de contadores do lead.
**Pronto quando:** os casos de uso passam em teste unitário; transferir gera evento; enviar mensagem manual transfere o lead (RN-CRM-06).

### E05 — Canal WhatsApp (Anti-Corruption Layer)
**Objetivo:** integração externa sem contaminar o domínio.
**Escopo:** porta `CanalGateway`, adapter do provedor, webhook de entrada com validação de assinatura, `canal_credencial` versionada, fluxo de troca de número.
**Pronto quando:** mensagem enviada chega no WhatsApp real, mensagem recebida cria/atualiza atendimento, e trocar o número não quebra o histórico.

### E06 — Tempo real
**Objetivo:** o chat parecer instantâneo.
**Escopo:** WebSocket com Redis pub/sub, tópicos de atendimento/usuário/presença, status de entrega e leitura.
**Pronto quando:** duas sessões em instâncias diferentes recebem a mesma mensagem em < 1s.

### E07 — Outbox, fila e contrato `/internal/v1`
**Objetivo:** integração confiável com a Automação.
**Escopo:** tabela outbox + publisher, consumidores idempotentes, `/internal/v1/automation-config`, OpenAPI gerado no build, teste de contrato.
**Pronto quando:** matar o RabbitMQ no meio de uma transferência e religar resulta no evento publicado — nada se perde.

### E08 — Configuração de automação e feature flags
**Objetivo:** "nada hardcoded" virar realidade operacional.
**Escopo:** CRUD de `configuracao_automacao` com validação de faixa, `FeatureService` cacheado, invalidação por evento, endpoints de tema/textos/features.
**Pronto quando:** alterar um parâmetro no painel reflete na Automação sem redeploy, e desligar uma flag some a aba no frontend.

### E09 — Auditoria e resiliência
**Objetivo:** honrar a promessa de estabilidade e a exigência de logs.
**Escopo:** `@Auditable` via AOP gravando em `audit_log`, endpoint de consulta com filtros, `/health/critical`, watchdog externo, circuit breakers, bulkhead (DataSource e executors separados).
**Pronto quando:** derrubar o Postgres faz `/health/critical` falhar e o watchdog alertar; uma importação pesada não degrada o tempo de resposta do chat.

### E10 — Frontend: fundação
**Objetivo:** identidade visual e shell da aplicação.
**Escopo:** Next.js + Tailwind + shadcn, design tokens a partir de `/api/v1/config/tema`, catálogo de textos, layout com sidebar e rodapé de perfil/config (referência claude.ai), login, TanStack Query.
**Pronto quando:** trocar o JSON de tema muda toda a aparência sem tocar em componente; nenhuma cor ou texto literal no código.

### E11 — Frontend: Atendimentos
**Objetivo:** a tela que o cliente vai usar 8 horas por dia.
**Escopo:** lista de conversas com filtros, conversa com todos os tipos de mídia, composer (anexo, mensagem rápida por palavra-chave, emoji, áudio, agendar), status de entrega, cabeçalho com transferir/finalizar, optimistic UI, reconexão de WebSocket.
**Pronto quando:** um atendente consegue trabalhar um lead do início ao fim sem sair da tela, e derrubar a rede por 10s reconecta sem perder mensagem.

### E12 — Frontend: aba lateral do lead e timeline
**Escopo:** painel com ficha completa, stepper de etapa, contadores, tags, resumo por IA, notas, timeline de eventos, ações de lembrete e mensagem programada.
**Pronto quando:** um clique abre a lateral, duplo clique abre o atendimento (RN-CRM-05).

### E13 — CRUDs de suporte
**Escopo:** lembretes, mensagens programadas, mensagens rápidas, equipe — back e front, com visão por papel (privado por atendente, gestor vê todos com coluna de origem). Banco de Arquivos ficou fora da primeira entrega.
**Pronto quando:** cada CRUD respeita `RN-CRM-04`.

### E15 — Dashboard consolidada
**Escopo:** visão única, sem sub-abas. Atendimentos do dia, leads por etapa (funil), desempenho por atendente, filtro de período. Read models via SQL direto, não JPA.
**Pronto quando:** o gestor abre a tela e entende a operação do dia sem precisar de outra aba.

### E14 — Hardening e homologação
**Escopo:** cobertura dos fluxos críticos, teste de carga leve no chat, revisão de índices com `EXPLAIN`, revisão de segredos, deploy de homologação, checklist do `docs/08-plano-execucao.md` §5.
**Pronto quando:** todos os itens do checklist estão verdes e a subgestora consegue usar o sistema sem acompanhamento.

---

## Etapa adicional

| # | Etapa | Escopo | Estimativa |
|---|---|---|---|
| E15 | Dashboard consolidada | Visão única (sem sub-abas): atendimentos do dia, leads por etapa, desempenho por atendente, filtro de período | 1 dia |

## Fora da primeira entrega

Campanhas, Relatórios, Banco de Arquivos, sub-abas da Dashboard e configurações de aparência na UI. Ver `09-escopo-primeira-entrega.md` — inclui as três regras do corte (schema permanece completo, corte por flag, tokens permanecem).

---

## Marcos de acompanhamento

| Data | Marco |
|---|---|
| ~03/08 | Login funcionando + isolamento de lead provado por teste |
| ~10/08 | **Mensagem real trocada com um cliente pelo CRM** |
| ~11/08 | Ambiente de homologação liberado para a subgestora |
| ~17/08 | Automação operando contra o CRM |
| 18/08 | Ponto de decisão sobre a folga |
| 25/08 | Entrega |

---

## Regras de disciplina

1. **Uma etapa por vez.** Etapa "quase pronta" é etapa não pronta.
2. **Commit ao fim de cada etapa**, com o CI verde.
3. **Não pule os testes das etapas E02, E04, E05 e E07** — são as que concentram o risco de bug em produção.
4. **Folga é para contingência, não para escopo.** Algo vai dar errado; um plano sem folga não é otimista, é ficcional.
5. **Feature flag em vez de branch longa.** Commit na main com a feature desligada.
6. **Libere para a subgestora cedo** (~11/08), sem esperar estar tudo pronto. O valor do acompanhamento dela é pegar desalinhamento antes que vire retrabalho.
