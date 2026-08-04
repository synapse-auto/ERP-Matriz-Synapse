# Prompt E00 — Fundação do monorepo

> Cole no Claude Code rodando na raiz do repositório. `/docs` e `CLAUDE.md` precisam estar **presentes na pasta** (ficam fora do versionamento pelo `.gitignore`, mas o Claude Code lê do disco normalmente).

---

Estou iniciando o desenvolvimento do CRM descrito em `/docs`. Leia `CLAUDE.md` e `docs/01-arquitetura-geral.md` antes de começar.

Esta é a **Etapa E00 — Fundação do monorepo**. Objetivo: um repositório que sobe com um comando e onde todas as etapas seguintes vão encaixar sem retrabalho estrutural.

## O que construir

### 1. Estrutura Maven multi-módulo

Parent POM na raiz de `/backend` (Java 21, Spring Boot 3), com os módulos:

- `crm-shared-kernel` — value objects e tipos base compartilhados
- `crm-core` — Lead, Tag, Etapa, Lembrete, MensagemProgramada
- `crm-atendimento` — Atendimento, Mensagem, Canal, WebSocket
- `crm-automacao-config` — parâmetros da automação, feature flags
- `crm-campanhas` — campanhas e métricas
- `crm-equipe` — usuários, papéis, presença, horários
- `crm-relatorios` — read models
- `crm-app` — aplicação executável (única com `@SpringBootApplication`)

Cada módulo de domínio já deve nascer com os pacotes `domain/`, `application/`, `infrastructure/`, `interface/`.

**Importante:** `crm-shared-kernel` e os pacotes `domain` não devem ter dependência de Spring nem de JPA no POM. Configure isso desde já — é o que impede que a regra de dependência seja violada por acidente mais tarde.

### 2. Docker Compose

`/docker/docker-compose.yml` com Postgres 15, Redis 7 e RabbitMQ 3 (com management UI), volumes nomeados para persistência e healthchecks em cada serviço.

### 3. Configuração da aplicação

`application.yml` em `crm-app` com a estrutura de configuração da instância descrita em `docs/07-base-pai-multitenancy.md` §4 (bloco `synapse:`), com valores lidos de variáveis de ambiente e defaults de desenvolvimento. Crie também um `.env.example`.

Configure **dois DataSources** desde já (Bulkhead): `chatDataSource` com pool reservado para o caminho crítico de mensagens, e `generalDataSource` para o resto. Nesta etapa basta a configuração e os beans; o uso vem nas etapas seguintes.

### 4. Frontend (esqueleto apenas)

`/frontend` com Next.js 14+ (App Router, TypeScript, Tailwind) inicializado e shadcn/ui configurado. **Só o scaffold** — nenhuma tela nesta etapa.

### 5. CI

GitHub Actions: build Maven + testes com Testcontainers, e lint/build do frontend. Deve rodar em push e PR.

### 6. Qualidade de base

- `.gitignore` cobrindo Java, Node, IDEs e `.env`
- `.editorconfig`
- Spotless ou formatter configurado no Maven
- `README.md` com instruções de como subir o ambiente local

## Restrições

- Siga estritamente `CLAUDE.md`. Em especial: nenhum valor hardcoded, nenhuma dependência de framework no domínio.
- Não crie entidades, controllers ou lógica de negócio nesta etapa. Só a fundação.
- Não crie `tenant_id` em lugar nenhum — o modelo é instância isolada por cliente.

## Definição de pronto

- [ ] `docker compose up -d` sobe Postgres, Redis e RabbitMQ com healthcheck OK
- [ ] `mvn clean install` passa na raiz do backend
- [ ] A aplicação sobe e responde em `/health/liveness`
- [ ] `npm run build` passa no frontend
- [ ] O workflow do CI está verde
- [ ] `README.md` permite a alguém subir o projeto do zero

Ao terminar, faça um commit `chore: fundação do monorepo` e me mostre a árvore de diretórios resultante junto com qualquer decisão que você tomou que não estava especificada aqui.
