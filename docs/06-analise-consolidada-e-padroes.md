# 06. Análise Consolidada e Catálogo de Padrões de Projeto

Análise cruzada de **Requisitos do CRM (client-facing)** + **Requisitos Internos (Base PAI / Synapse)**, com os padrões de projeto recomendados para cada problema concreto.

---

## Parte A — Análise consolidada dos dois documentos

### A.1 O que muda com os Requisitos Internos

O documento interno não adiciona apenas "detalhes técnicos" — ele **reordena as prioridades** do projeto. Três mudanças estruturais:

**1. O produto tem dois clientes, não um.** O cliente pagante é a Estrutural Vidros; o cliente estratégico é a própria Synapse, que vai reusar essa base em todos os projetos futuros. Toda decisão precisa passar por dois filtros: "resolve o problema da Estrutural?" e "sobrevive ao próximo filho sem fork?".

**2. Estabilidade deixou de ser um RNF e virou uma regra de precedência.** O texto é explícito: a continuidade da aba Atendimentos "precede qualquer outra decisão de produto". Na prática isso significa que qualquer feature que introduza risco no caminho crítico de envio/recebimento de mensagem deve ser isolada arquiteturalmente ou adiada — não negociada.

**3. Apareceram requisitos que ainda não estavam modelados:**

| Novo requisito | Impacto no que já foi modelado |
|---|---|
| Log de auditoria amplo com filtros (transferências, tags, alterações) | `evento_timeline` cobre só eventos por lead. Precisa de uma tabela `audit_log` genérica e transversal. |
| Troca do número principal de atendimento | Confirma a decisão de `canal` como tabela (não hardcode), mas exige modelar credenciais do canal e o efeito da troca no histórico. |
| Alerta automático de indisponibilidade | Novo componente: watchdog/heartbeat que notifica o grupo do cliente antes da reclamação. |
| Documentação de endpoints para a Automação ("mudar no máximo URL e token") | Formaliza o contrato `/internal/v1/*` como **interface estável e versionada** — é o que permite reusar a mesma Automação em todos os filhos. |
| Degradação controlada em caso de queda | Exige *fallbacks* explícitos por módulo, não só "tentar de novo". |
| Front-end configurável (cores, textos, nomes de cards) | Design tokens + catálogo de textos vindos de configuração, não constantes no código. |

### A.2 Tensões que precisam de decisão consciente

Aponto três conflitos reais entre os documentos — não são erros, são trade-offs que alguém precisa arbitrar:

**Tensão 1 — "Base PAI completa desde já" × prazo de 11 dias com dev solo.**
Modularidade total e prazo curto puxam em direções opostas. Modularizar custa tempo *agora* e economiza *depois*. Ver `08-plano-execucao.md` para o recorte que proponho: investir a modularidade onde ela é cara de reverter (fronteiras de módulo, config vs. hardcode, contrato da Automação) e aceitar acoplamento temporário onde é barato refatorar (telas internas, relatórios).

**Tensão 2 — "Nada hardcoded" × "robustez / mínimo de bugs".**
Configurabilidade extrema aumenta o espaço de estados possíveis do sistema, e espaço de estados é onde bugs moram. Um sistema onde tudo pode mudar em runtime é, por definição, mais difícil de testar do que um onde as coisas são fixas. Mitigação: configuração **tipada e validada** (não string solta), com faixas declaradas no banco (já previsto em `configuracao_automacao`) e testes que rodam contra os valores-limite, não só o *happy path*.

**Tensão 3 — "não reinventar a roda / copiar templates" × "design único, sem cara de template de IA".**
Também opostos, mas conciliáveis: reusar **comportamento** (shadcn/Radix para acessibilidade, foco, teclado, estados) e customizar **aparência** (design tokens próprios). O que dá "cara de template" é usar as cores e o espaçamento padrão do shadcn, não usar os componentes dele.

### A.3 O que o modelo de instância-por-cliente simplifica

Boa notícia para o prazo: com deploy isolado por cliente, **o schema não precisa de `tenant_id` em lugar nenhum**, não há RLS multi-tenant para escrever, não há risco de vazamento entre clientes, e cada filho pode estar em uma versão diferente da base. Isso elimina a classe inteira de bugs mais perigosa de SaaS multi-tenant.

O custo se desloca para **operação e propagação de versão**: N deploys, N bancos, N pipelines, e a pergunta "como levo uma correção do pai para os 8 filhos?" precisa de resposta desde o dia 1. É o que o `07-base-pai-multitenancy.md` resolve.

---

## Parte B — Catálogo de padrões de projeto

Cada padrão abaixo está amarrado a um requisito concreto. Marquei com 🔥 os que considero **inegociáveis para o dia 7**, ⏳ os que podem esperar a fase 2.

### B.1 Padrões arquiteturais

#### 🔥 Modular Monolith + Hexagonal (Ports & Adapters)
**Problema:** `RNF-CRM-03` (ultra-modularidade), reuso pai→filho, e a necessidade de trocar canal/fila/IA sem tocar em regra de negócio.
**Aplicação:** cada módulo (`atendimento`, `crm-core`, `campanhas`...) expõe portas; adaptadores de infraestrutura ficam nas bordas. O que é específico de um filho vira **adaptador**, não *if* espalhado pelo domínio.
**Custo no prazo:** baixo se adotado desde o primeiro commit; altíssimo se retrofitado depois. Por isso é dia 1.

#### 🔥 Transactional Outbox
**Problema:** `RNF-CRM-01` + integração por fila. Sem ele existe o cenário: o banco grava a transferência do lead, a publicação na fila falha, e o CRM e a Automação divergem silenciosamente — exatamente o tipo de bug que só aparece em produção.
**Aplicação:** o *use case* grava o evento numa tabela `outbox` na **mesma transação** da mudança de estado; um publisher assíncrono lê a outbox e publica na fila, marcando como enviado.
**Por que é inegociável:** é a diferença entre "geralmente consistente" e "consistente". Custa ~meio dia de implementação e elimina uma categoria inteira de bugs de produção.

#### 🔥 Bulkhead (isolamento de recursos)
**Problema:** "a aba Atendimentos precede qualquer outra decisão de produto".
**Aplicação:** pools de thread e pools de conexão **separados** para o caminho crítico (mensagens) e para o resto (relatórios, campanhas, importação de CSV). Um relatório pesado ou uma importação de 10 mil leads não pode consumir as conexões que o chat precisa.
**Concretamente:** dois `DataSource` no Spring (`chatDataSource` com pool reservado, `generalDataSource`), e `@Async` com executors distintos.

#### 🔥 Circuit Breaker + Fallback (Resilience4j)
**Problema:** "degradar de forma controlada e recuperar rapidamente".
**Aplicação:** toda chamada de saída (API do WhatsApp, serviço de IA, storage) com breaker; ao abrir, o sistema entra em modo degradado explícito (ex.: enfileira o envio e mostra "enviando…" em vez de travar a UI) em vez de propagar exceção até a tela.

#### 🔥 Health Check API + Heartbeat/Watchdog
**Problema:** "avisar o cliente no grupo antes de reclamarem".
**Aplicação:** endpoint `/health/critical` que valida especificamente o caminho de mensagens (banco acessível, fila conectada, canal WhatsApp autenticado, WebSocket aceitando conexão) — separado do health genérico. Um watchdog externo (não no mesmo processo, senão cai junto) faz *polling* e dispara a notificação.
**Detalhe que importa:** o watchdog precisa rodar fora da mesma máquina/deploy do CRM. Um monitor que morre junto com o monitorado não é um monitor.

#### 🔥 CQRS "light" (separação leitura/escrita, sem event sourcing)
**Problema:** `RF-CRM-32` ("milhares de informações") e `RF-CRM-79` (relatórios) contra `RNF-CRM-08` (fluidez).
**Aplicação:** escrita via agregados JPA; leitura de dashboard/relatórios via SQL direto (jOOQ/JdbcTemplate) em *read models* dedicados, possivelmente *materialized views* atualizadas periodicamente.
**Importante:** CQRS aqui significa só "duas rotas de acesso a dados", **não** event sourcing, **não** bancos separados. A versão completa seria suicídio no prazo.

#### ⏳ Saga / Processo de longa duração
**Problema:** campanhas com envio ao longo de dias, follow-ups em cadeia.
**Recomendação para o prazo:** implementar como *jobs* idempotentes agendados com estado em tabela, não como framework de saga. A complexidade de uma saga completa não se paga aqui — o fluxo é linear e tolera reprocessamento.

---

### B.2 Padrões táticos (DDD + GoF)

#### 🔥 Specification Pattern
**Problema:** `RN-CRM-01` (isolamento de agenda) + `RF-CRM-04/05/40` (filtros modulares reusados em três telas).
**Aplicação:** `VisibilidadeLeadSpec` compõe com `FiltroUsuarioSpec`; ambas viram `Predicate`/condição SQL. A regra "atendente só vê os leads dele" fica em **um lugar só**, aplicada em toda consulta de lead por composição — não replicada em cada endpoint.
**Por que importa tanto aqui:** o documento interno avisa que os atendentes trabalham por comissão e disputam leads. Um vazamento de lead entre atendentes não é um bug técnico, é um problema comercial na casa do cliente. Centralizar essa regra em uma Specification é o que impede que o oitavo endpoint escrito às pressas esqueça o filtro.

#### 🔥 Composite + Interpreter
**Problema:** a árvore de critérios do filtro modular (`{"op":"AND","cond":[...]}` com aninhamento).
**Aplicação:** `Criterio` como interface; `CriterioSimples` (folha: campo/operador/valor) e `CriterioComposto` (nó: AND/OR + filhos) — Composite clássico. O Interpreter percorre a árvore e emite a condição SQL.
**Ganho:** adicionar um novo operador ou campo filtrável é uma classe nova, sem tocar no resto. É exatamente o "sistema moldável" que cativou o cliente.

#### 🔥 Strategy
**Problema:** múltiplos comportamentos intercambiáveis: canais (`WHATSAPP` e futuros), tipos de regra de automação (follow-up / fidelização / festiva), operadores de filtro.
**Aplicação:** `CanalGateway` como interface com implementação por canal, resolvida por `Map<TipoCanal, CanalGateway>` injetado pelo Spring. Novo canal = nova classe + linha na tabela `canal`, zero alteração no domínio.

#### 🔥 Repository + Unit of Work
**Problema:** consistência transacional e testabilidade do domínio.
**Aplicação:** interfaces de repositório declaradas no domínio, implementadas na infraestrutura; `@Transactional` na fronteira do *use case* como Unit of Work implícito.

#### 🔥 Adapter (Anti-Corruption Layer)
**Problema:** o formato de payload do WhatsApp (ou de qualquer canal/provedor) não pode contaminar o modelo de domínio.
**Aplicação:** `WhatsAppMessageAdapter` traduz o webhook cru para `MensagemRecebida` do domínio. Quando o provedor mudar o formato — e vai mudar — a mudança fica confinada a uma classe.

#### 🔥 Domain Events + Observer
**Problema:** um mesmo fato ("lead transferido") precisa disparar N reações: timeline, notificação WebSocket, lembrete automático, log de auditoria, métrica.
**Aplicação:** o agregado registra `LeadTransferidoEvent`; *listeners* independentes reagem. Sem isso, o `TransferirLeadUseCase` vira um método de 200 linhas que precisa ser alterado toda vez que alguém quer mais uma reação.
**Cuidado:** listeners devem ser assíncronos (`@TransactionalEventListener(phase = AFTER_COMMIT)`) para não colocar a notificação no caminho crítico da transação.

#### 🔥 Feature Toggle
**Problema:** Base PAI — cada filho habilita módulos diferentes; e entrega incremental para a subgestora antes da virada.
**Aplicação:** tabela `feature_flag (chave, habilitado, descricao)` consultada por um `FeatureService` cacheado, exposta ao frontend em `GET /api/v1/config/features`. Permite entregar código incompleto em produção desligado — essencial quando o prazo é curto e você quer *deploy* contínuo sem *branch* de longa duração.

#### 🔥 Decorator (cross-cutting)
**Problema:** auditoria ampla ("logs de muitas ações do CRM"), métricas e cache sem poluir os *use cases*.
**Aplicação:** AOP do Spring (`@Auditable`) ou decoradores explícitos em torno dos *use cases*. O caso de uso continua sabendo só de regra de negócio; o log de auditoria acontece em volta dele.
**Por que Decorator e não "chamar o logger no meio do método":** com 60+ casos de uso e um dev solo com prazo curto, a chance de esquecer o log em alguns é ~100%. Um aspecto aplicado por anotação não esquece.

#### ⏳ Chain of Responsibility
**Problema:** pipeline de processamento de mensagem recebida (validar → identificar lead → aplicar automação → persistir → notificar).
**Recomendação:** útil, mas dá para começar com um *use case* sequencial bem escrito e extrair a chain quando o pipeline crescer. Não é dia 1.

#### ⏳ Null Object / Result Type
**Aplicação:** usar `Optional`/`Result` em vez de exceções para fluxo esperado (lead não encontrado). Bom para robustez, mas é disciplina de código, não decisão arquitetural.

---

### B.3 Padrões do frontend (Next.js)

#### 🔥 Design Tokens + Theme Provider
**Problema:** "cores do front-end devem poder mudar sem alterar código" + "design único, sem cara de template".
**Aplicação:** CSS variables definidas a partir de um JSON de tema carregado por configuração da instância (`GET /api/v1/config/tema`). O shadcn já é construído sobre CSS variables — usar isso a favor, sobrescrevendo os tokens em vez de editar componentes.

#### 🔥 Container/Presentational + Server Components
**Problema:** fluidez (`RNF` de ultra-responsividade).
**Aplicação:** Server Components para dados iniciais (lista de leads, dashboard), Client Components só onde há interatividade (chat, filtros). Reduz o JS enviado ao browser, que é o principal fator de "fluidez percebida".

#### 🔥 Optimistic UI
**Problema:** percepção de velocidade no chat.
**Aplicação:** a mensagem aparece na tela imediatamente com estado "enviando", reconciliada quando o servidor confirma. Sem isso, mesmo um backend rápido *parece* lento.

#### 🔥 i18n / Catálogo de textos
**Problema:** "textos e nomes de cards devem poder mudar".
**Aplicação:** nenhuma string literal nos componentes; tudo vem de um dicionário sobrescrevível por instância. Mesmo sem tradução de idioma, o mecanismo de i18n é a forma padrão e testada de resolver "textos configuráveis".

---

## Parte C — Priorização honesta

Se o prazo apertar (e vai), esta é a ordem em que eu abriria mão:

| Prioridade | Padrões | Racional |
|---|---|---|
| **Nunca cortar** | Hexagonal, Specification, Outbox, Feature Toggle, Design Tokens, Adapter | São caros ou impossíveis de retrofitar. Cortar aqui compromete a Base PAI inteira. |
| **Cortar por último** | Bulkhead, Circuit Breaker, Health Check dedicado, Domain Events | Protegem a promessa de estabilidade, que é o principal motivador de compra do cliente. |
| **Pode esperar fase 2** | CQRS com materialized views, Chain of Responsibility, Saga, Interpreter otimizado | Ganho real, mas o sistema funciona sem eles no volume atual. |

Ver `08-plano-execucao.md` para como isso se traduz em dias de trabalho até 7 de agosto.
