# AGENTS.md — Synapse CRM (Base PAI)

Arquivo canônico de contexto do projeto. Leia por inteiro antes de qualquer tarefa.

---

## Formato do relatório final — leia primeiro

Ao terminar uma tarefa, o relatório é lido por um arquiteto que **não viu a sessão** e que vai usá-lo para calibrar a próxima etapa. Um relatório só com "feito, testes passando" força uma rodada extra de perguntas.

Inclua, nesta ordem:

**1. Commit e estado**
SHA, branch, e **confirmação de que o push chegou ao `origin`**. Quantidade de arquivos.

> **Toda tarefa termina com commit e push.** Não acumule commits locais: código que existe em um só lugar pode desaparecer, e o CI só roda no que chega ao GitHub. Se o push falhar, isso vai no relatório — não se resolve em silêncio.

**2. Definição de pronto, item a item**
Cada checkbox do prompt com ✅/⚠️/❌ e a **evidência** — não "testes passando", mas "AutenticacaoIT 9/9, incluindo o negativo que prova que atendente A não alcança lead de B".

**3. Decisões que o prompt não especificou**
Toda escolha de design que você fez sozinho, com o **porquê**. Ex.: "usei `@Order(HIGHEST_PRECEDENCE)` no aspecto para ficar fora do `@Transactional`, então quando `proceed()` retorna a transação já commitou — dispensa `AFTER_COMMIT`."

Isto é a parte mais importante do relatório. Decisão não relatada vira surpresa três etapas depois.

**4. Divergências entre a documentação e a realidade**
Onde o `docs/` estava errado, incompleto ou impossível de seguir como escrito. A documentação é atualizada a partir daqui.

**5. Bugs encontrados no caminho**
Defeitos pré-existentes que você descobriu, mesmo fora do escopo. **Especialmente os que nenhum teste pegaria.** Este projeto tem sete casos de proteção que existia e não protegia nada — cada um foi achado assim.

**6. O que ficou de fora, e por quê**
Gaps deliberados. Diga o que **não** funciona, para ninguém supor que funciona.

**7. O que precisa de decisão minha**
Perguntas em aberto, trade-offs que você não quis resolver sozinho.

Seja específico e honesto sobre incerteza. "Não consigo medir isso de forma confiável porque X" vale mais que um número inventado.

---

## Contexto do projeto

CRM B2B sob medida para a **Estrutural Vidros** (fábrica de vidros, Brasília-DF), construído como **Base PAI**: template reutilizável que servirá de fundação para todos os clientes futuros ("filhos") da Synapse.

**Dois clientes em toda decisão:** resolve o problema da Estrutural? *E* sobrevive ao próximo filho sem fork?

**Multi-tenancy modelo Silo:** instância isolada por cliente — deploy e banco próprios por filho. **Não existe `tenant_id` em nenhuma tabela.** O isolamento é físico. Não escreva código de isolamento entre clientes.

Um repositório só. Um filho novo é o mesmo código com configuração diferente: variáveis de ambiente, `tema.json`, `textos.json` e feature flags no banco.

---

## Regra de precedência absoluta

> **A aba Atendimentos não pode ficar indisponível entre 08:00 e 18:30.**

Precede qualquer outra decisão de produto ou técnica. O cliente veio de um CRM que cai; estabilidade é o principal motivador da compra.

Consequência prática: **nada** entra de forma síncrona e bloqueante no caminho de envio/recebimento de mensagem. Se uma operação pode falhar ou demorar, vai para fila, thread pool separado, ou fica atrás de circuit breaker.

---

## Stack

**Backend:** Java 21 · Spring Boot 3 · Spring Security (JWT) · Spring Data JPA · Flyway · PostgreSQL 15+ · Redis · RabbitMQ · Resilience4j · Testcontainers
**Frontend:** Next.js 14+ (App Router) · TypeScript · shadcn/ui · Tailwind · TanStack Query · Zustand
**Infra:** Docker Compose (dev) · Maven multi-módulo

### Java 21 é fixo — não aceite upgrade automático

`maven.compiler.release` é **21**. Ferramentas de modernização vão propor Java 25. **Recuse:**

- Spring Boot 3.5 suporta até Java 24; Java 25 exigiria Spring Boot 4
- **ArchUnit 1.3 não lê bytecode 69 (Java 25) e importa zero classes** — todas as regras de arquitetura param de verificar, silenciosamente
- O CI roda Temurin 21

Se o build falhar com `failed to check any classes`, é isso. Reproduza com:
```
cd backend && ./mvnw clean install -Dmaven.compiler.release=21
```

---

## Estrutura do monorepo

```
/backend
  /crm-shared-kernel      value objects comuns, tipos base
  /crm-core               Lead, Tag, Etapa, Lembrete, MensagemProgramada, Banco de Arquivos
  /crm-atendimento        Atendimento, Mensagem, Canal, WebSocket
  /crm-automacao-config   parâmetros da automação, feature flags
  /crm-campanhas          campanhas e métricas
  /crm-equipe             usuários, papéis, presença, horários, chat interno
  /crm-relatorios         read models (dashboard/relatórios)
  /crm-app                aplicação executável (composição + config)
/frontend                 Next.js
/docs                     documentação de arquitetura (01 a 12)
/design                   protótipo e design tokens
/docker                   compose e infra local
```

São **8 módulos**. `chat_interno_*` vive em `crm-equipe`; `arquivo_banco` em `crm-core`.

### Camadas dentro de cada módulo

```
domain/          entidades, VOs, regras puras — SEM Spring, SEM JPA, SEM anotações de framework
application/     casos de uso, portas (interfaces)
infrastructure/  adaptadores: repositórios JPA, clientes HTTP, produtores de fila
interfaces/      controllers REST, handlers WebSocket, DTOs
```

> `interfaces` no plural porque `interface` é palavra reservada do Java.

**Regra de dependência:** sempre de fora para dentro. `domain` não importa nada de `application`, `infrastructure` ou `interfaces`. Se precisou importar `jakarta.persistence` em `domain`, está errado.

---

## Padrões obrigatórios

| Padrão | Onde | Por quê |
|---|---|---|
| **Hexagonal / Ports & Adapters** | Todos os módulos | Trocar canal/fila/IA sem tocar em domínio |
| **Specification** | Toda consulta de Lead | Regra de visibilidade em UM lugar só |
| **Composite + Interpreter** | Filtros modulares | Árvore de critérios AND/OR aninhados |
| **Transactional Outbox** | Todo evento publicado em fila | Atomicidade estado + evento |
| **Adapter (ACL)** | Integrações externas | Payload do WhatsApp não entra no domínio |
| **Domain Events** | Fatos com múltiplas reações | `@TransactionalEventListener(AFTER_COMMIT)` |
| **Feature Toggle** | Módulos opcionais por filho | Deploy contínuo sem branch longa |
| **Decorator/AOP** | Auditoria, métricas | `@Auditable` — não espalhar log nos casos de uso |
| **Bulkhead** | Pools de conexão/thread | Relatório pesado não rouba conexão do chat |
| **Circuit Breaker** | Toda chamada de saída | Degradação controlada |

**Tipos selados com `switch` exaustivo** onde uma variante nova precisa quebrar o build: `VisibilidadeLead`, `Criterio`, `ValorDeFiltro`, `ConteudoDeEnvio`.

---

## Regras de negócio sensíveis — cuidado ao mexer

**Os atendentes trabalham por comissão e disputam leads.** Vazamento de lead entre atendentes não é bug técnico, é incidente comercial na casa do cliente.

- `RN-CRM-01` — Atendente vê **apenas** seus leads (Ativos, Pendentes) mais os em status `IA` (Potenciais). Gestor/subgestor veem todos.
- `RN-CRM-02` — Lead atribuído a um atendente pertence a ele.
- `RN-CRM-06` — Enviar mensagem manual **transfere** o lead para quem enviou. **Sem exceção de papel** — vale para gestor e subgestor.

> `RN-CRM-01` e `RN-CRM-06` **compõem**: a transferência só acontece dentro do recorte de visibilidade. Atendente não rouba lead de colega porque não o alcança — ele assume lead sem dono. Quem alcança lead de terceiros é gestor/subgestor, e para esses a transferência é intencional.

Um atendente **não pode escolher para quem** transferir um lead Potencial — só devolver para a IA ou assumir para si. Distribuir é prerrogativa da IA ou do gestor.

Essas regras vivem em `VisibilidadeLeadSpecification`, nas políticas RLS e nos casos de uso. **Nunca** deixe filtragem para o frontend. **Nunca** escreva query de lead que não passe pela Specification.

---

## Proibido

- ❌ Valor de configuração hardcoded (tempos, textos, cores, nomes de cards, limites). Tudo vem de `configuracao_automacao`, `feature_flag`, `tema.json` ou `textos.json`.
- ❌ `if (cliente == "estrutural")` ou condicional por tenant no core. Se precisou, faltou ponto de extensão — crie o ponto de extensão. Extensão por **capacidade** (`synapse.extensoes.X.habilitado`), nunca por nome de cliente.
- ❌ **Tabela ou coluna específica do ramo do cliente.** Nada de `orcamento_vidro`, `medida_vao`. Campo que só um cliente precisa vai para `lead.dados_customizados` (JSONB) + `campo_customizado`.
- ❌ `SELECT *` ou entidade inteira em tela de listagem. `resumo_ia`, `notas` e `dados_customizados` **nunca** em lista.
- ❌ Publicar em fila fora da outbox.
- ❌ Chamada externa síncrona sem circuit breaker.
- ❌ String literal de UI em componente React. Tudo do catálogo de textos.
- ❌ Cor hardcoded no frontend. Só design tokens / CSS variables.
- ❌ **Dado mockado no frontend.** Endpoint que não existe ⇒ estado vazio de verdade, não conteúdo inventado. Nada de controle fantasma: ou funciona, ou não aparece.
- ❌ Editar arquivo do core para atender um filho específico.
- ❌ Commitar segredo. Credenciais em variável de ambiente; o banco guarda `token_ref` (referência), nunca o token.
- ❌ Editar migration já aplicada. Correção é migration nova.

---

## Convenções

**Nomenclatura:** domínio em **português** (`Lead`, `Atendimento`, `EtapaAtendimento`). Termos técnicos de framework em inglês (`Repository`, `UseCase`, `Config`).

**Casos de uso:** uma classe por caso de uso, nome imperativo (`TransferirAtendimentoUseCase`). Um método público `executar(...)`.

**Repositórios — padrão obrigatório:**
- Porta sem `findAll`/`findById` cru; métodos que exigem a Specification como parâmetro
- Implementação JPA **pacote-privada**
- Regra ArchUnit genérica: nada fora de `..infrastructure.persistencia..` depende de `*JpaRepository`

**Autorização:** cada caso de uso declara quem pode chamá-lo (`@PreAuthorize`). Por papel **e** por propriedade do recurso.

**Migrations:** Flyway, `V{n}__{descricao}.sql`.

**Commits:** Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `chore:`).

**API:** REST em `/api/v1/...`; contrato da Automação em `/internal/v1/...` (autenticado por `X-Synapse-Token`, versionado, coberto por teste de contrato — quebrar esse contrato quebra a Automação de todos os filhos).

**Erros:** RFC 7807 (Problem Details).

---

## Testes

> ### Uma proteção que não pode falhar em silêncio não é proteção
>
> Sete incidentes no projeto, mesmo padrão — a proteção existia, o build passava, e nada estava protegido:
>
> 1. `ArquiteturaTest` com `DoNotIncludeJars` — os módulos chegam como JAR, as regras nunca rodaram
> 2. Políticas RLS escritas, mas o usuário era dono/superusuário — todo mundo via tudo, e os testes **positivos** passavam por isso
> 3. Upgrade para Java 25 fez o ArchUnit importar zero classes
> 4. `@Scheduled` chamando método transacional do próprio bean (auto-invocação) — o caminho de mensagens estava quebrado nas duas direções. A proteção *funcionou* e gritou em todo teste, mas nenhum teste chamava o método agendado e o erro virou ruído de fundo
> 5. Javadoc afirmando "vira 400" sem `@ExceptionHandler` que fizesse isso
> 6. `JwtAuthenticationToken` de um argumento nasce com `authenticated=false` — todo `@PreAuthorize` falhava silenciosamente
> 7. `@Scheduled` de um contexto de teste rodando contra o Postgres compartilhado e roubando linha de outbox de outro teste
>
> Regras derivadas:
>
> - Toda proteção nova nasce com um teste que a **viola de propósito** e confirma que ela reprova. Regra que nunca reprovou nada é decoração.
> - **Teste o ponto de entrada, não só o método interno.** Job agendado, handler de webhook e listener de fila precisam de teste que os chame como o runtime chama.
>   A armadilha: chamar `bean::metodoInterno` num `@Autowired` **parece** testar o ponto de entrada — é chamada externa legítima e funciona. Mas não exercita a auto-invocação escondida *dentro* do método anotado.
> - **Erro recorrente em log de teste é defeito, não paisagem.** Alarme que dispara sempre é indistinguível de nenhum alarme.
> - **Teste o negativo.** Provar que alguém *não* vê algo pega o que o teste positivo esconde.
> - **Espere por condição, nunca por tempo.** `Awaitility` com timeout, jamais `Thread.sleep` ou asserção imediata sobre efeito assíncrono.

- **Testcontainers** para integração — Postgres real, não mock nem H2
- Domínio: teste unitário puro, sem Spring context
- Caso de uso: teste com portas mockadas
- Fluxo de mensagem ponta a ponta: teste de integração obrigatório
- Contrato `/internal/v1`: teste de contrato no CI

---

## Escopo da primeira entrega

**Fora** (ver `docs/09`): aba de Relatórios, aba de Banco de Arquivos, aba de Campanhas, sub-abas da Dashboard e configurações de aparência na UI.

Três regras que **não** decorrem desse corte:

1. **O schema permanece completo.** Não remova tabelas das migrations.
2. **Corte por feature flag, não por remoção.** As abas não são construídas e as flags ficam `false`. Nada de `if` escondendo menu.
3. **Os design tokens permanecem.** O que sai é o *controle na tela de configurações*, não a arquitetura de tema.

Permanecem com ajuste: Dashboard como **visão única consolidada** e anexo no chat por **upload direto** — mas a infra de storage continua.

---

## Ambiente

- **Postgres nativo do Windows ocupa a 5432.** O container usa outra porta; use `POSTGRES_PORT` do `.env`, nunca assuma 5432.
- **Docker Desktop precisa estar rodando** para os Testcontainers.
- `JAVA_HOME` deve apontar para JDK 21. Existe JDK 17 e 25 na máquina.
- Só `pg_trgm` é exigida como extensão (`pgcrypto` foi removida — Postgres 13+ tem `gen_random_uuid()` nativo).

---

## Contexto de prazo

Entrega em **25/08/2026**, desenvolvedor solo. Homologação com a subgestora por volta de **11/08**. Prefira:

- Reusar componentes consagrados (shadcn/Radix, Tremor) a construir do zero
- Feature flag desligada em vez de branch de longa duração
- Simplicidade que funciona hoje a abstração que talvez sirva amanhã — **exceto** nos padrões obrigatórios acima, caros de retrofitar

Se uma decisão for barata de reverter, tome a mais rápida. Se for cara (fronteira de módulo, contrato de API, modelo de dados, config vs. hardcode), pare e faça direito.
