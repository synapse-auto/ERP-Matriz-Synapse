# Synapse CRM — Base PAI

CRM B2B construído como **Base PAI**: um template reutilizável que serve de fundação para cada
cliente ("filho") da Synapse. O primeiro filho é a Estrutural Vidros.

**Multi-tenancy por instância:** cada cliente tem deploy e banco próprios. O isolamento é físico —
não existe `tenant_id` em nenhuma tabela, e não deve passar a existir.

> **Regra de precedência do produto:** a aba Atendimentos não pode ficar indisponível entre 08:00 e
> 18:30. Nenhuma operação que possa falhar ou demorar entra de forma síncrona e bloqueante no
> caminho de envio/recebimento de mensagem. Ver `CLAUDE.md`.

---

## Pré-requisitos

| Ferramenta | Versão | Observação |
|---|---|---|
| JDK | **21+** | O bytecode é sempre `release 21`. Verificado pelo enforcer no build. |
| Maven | 3.9+ | Ou use o wrapper: `./mvnw` em `/backend`, sem instalar nada. |
| Docker | com Compose v2 | Sobe Postgres, Redis e RabbitMQ. Também usado pelos testes (Testcontainers). |
| Node.js | 20+ | Frontend. |

---

## Subir o ambiente local

### 1. Variáveis de ambiente

```bash
cp .env.example .env
```

Os defaults de `.env.example` já funcionam para desenvolvimento sem integrações externas. Os
campos de **segredo** (`WHATSAPP_*`, `AUTOMACAO_*`) ficam vazios de propósito porque pertencem a
cada instância; um default falso ou compartilhado seria pior que a funcionalidade falhar fechada.

### 2. Infraestrutura

```bash
cd docker && docker compose up -d
```

Sobe quatro serviços, cada um com healthcheck e volume nomeado:

| Serviço | Porta padrão | Credenciais de dev |
|---|---|---|
| PostgreSQL 15 | 5432 | `synapse` / `synapse`, banco `synapse_crm` |
| Redis 7 | 6379 | sem senha |
| RabbitMQ 3 | 5672 (AMQP), 15672 (UI) | `synapse` / `synapse` |
| MinIO | 9000 (S3), 9001 (UI) | `minioadmin` / `minioadmin` |

Aguarde o `(healthy)`:

```bash
docker compose ps
```

A UI do RabbitMQ fica em <http://localhost:15672>.

### 3. Backend

```bash
cd backend && ./mvnw clean install
```

Isso roda o ciclo inteiro: enforcer, compilação, testes de arquitetura (ArchUnit), testes de
integração com Testcontainers e verificação de formatação. **Precisa do Docker rodando.**

Para subir a aplicação:

```bash
java -jar backend/crm-app/target/crm-app-0.1.0-SNAPSHOT-exec.jar
```

Confira:

```bash
curl http://localhost:8080/health/liveness
```

Resposta esperada: `{"status":"UP"}`.

Para subir **com o banco populado** (etapas do funil, canal, usuários, tags, feature flags e
parâmetros da automação), use o perfil `dev`:

```bash
SPRING_PROFILES_ACTIVE=dev java -jar backend/crm-app/target/crm-app-0.1.0-SNAPSHOT-exec.jar
```

Ver [Migrations e seed](#migrations-e-seed) para o que entra e por que só no `dev`.

### 4. Frontend

```bash
cd frontend && npm install && npm run dev
```

Disponível em <http://localhost:3000>. Nesta etapa é apenas o scaffold — ainda não há telas.

---

## Estrutura

```
/backend                    monorepo Maven multi-módulo
  /crm-shared-kernel        value objects e tipos base — Java puro, sem framework
  /crm-core                 Lead, Tag, Etapa, Lembrete, MensagemProgramada
  /crm-atendimento          Atendimento, Mensagem, Canal, WebSocket
  /crm-automacao-config     parâmetros da automação, feature flags
  /crm-campanhas            campanhas e métricas
  /crm-equipe               usuários, papéis, presença, horários
  /crm-relatorios           read models
  /crm-app                  aplicação executável (único com @SpringBootApplication)
/frontend                   Next.js (App Router, TypeScript, Tailwind, shadcn/ui)
/docker                     compose da infra local
/docs                       documentação de arquitetura (fora do versionamento)
```

### Camadas dentro de cada módulo

```
domain/          entidades, VOs, regras puras — SEM Spring, SEM JPA
application/     casos de uso e portas (interfaces)
infrastructure/  adaptadores: repositórios JPA, clientes HTTP, produtores de fila
interfaces/      controllers REST, handlers WebSocket, DTOs
```

A dependência aponta sempre para dentro. `domain` não importa nada de `application`,
`infrastructure` ou `interfaces`.

Duas coisas fazem valer essa regra no build, e não no code review:

- **`maven-enforcer-plugin`** no `crm-shared-kernel` — falha se alguém adicionar Spring, JPA,
  Hibernate ou Jackson ao POM daquele módulo.
- **`ArquiteturaTest`** (ArchUnit, em `crm-app`) — falha se uma classe de `domain` importar
  framework ou outra camada. Roda em todo `mvn install`.

> O pacote se chama `interfaces`, no plural, porque `interface` é palavra reservada do Java e não
> pode nomear um pacote. Os documentos de arquitetura chamam a camada de `interface/`.

---

## Comandos úteis

| O quê | Comando |
|---|---|
| Build completo com testes | `cd backend && ./mvnw clean install` |
| Build sem testes | `cd backend && ./mvnw clean install -DskipTests` |
| Corrigir formatação | `cd backend && ./mvnw spotless:apply` |
| Só os testes de arquitetura | `cd backend && ./mvnw -pl crm-app test` |
| Parar a infra (mantém os dados) | `cd docker && docker compose down` |
| Zerar a infra (**apaga os dados**) | `cd docker && docker compose down -v` |
| Lint e build do frontend | `cd frontend && npm run lint && npm run build` |

---

## Configuração

Nada de configurável é constante no código. A configuração da instância vive no bloco `synapse:` de
`backend/crm-app/src/main/resources/application.yml`, e todo valor vem de variável de ambiente com
um default de desenvolvimento.

### Migrations e seed

As migrations Flyway vivem em `backend/crm-app/src/main/resources/db/migration`, quebradas por
assunto (`V1` extensões e ENUMs, `V2` equipe, … `V10` índices). **Nunca edite uma migration já
aplicada** — se estiver errada, crie a próxima.

O seed de desenvolvimento é uma migration repetível em uma pasta separada, `db/seed`, que só entra
em `spring.flyway.locations` no perfil `dev`. Sem o perfil, o Flyway nem enxerga o arquivo; a
proteção não depende de ninguém lembrar de nada. A lista de locations também não vem de variável de
ambiente, justamente para que nenhuma variável errada consiga semear um banco de produção.

Usuários criados pelo seed (todos com domínio `@dev.local`):

| E-mail | Senha | Papel |
|---|---|---|
| `admin@dev.local` | `admin123` | ADMINISTRADOR |
| `gestor@dev.local` | `gestor123` | GESTOR |
| `subgestor@dev.local` | `subgestor123` | SUBGESTOR |
| `ana@dev.local`, `bruno@dev.local` | `atendente123` | ATENDENTE |

### Partições de `mensagem`

`mensagem` é particionada por mês em `enviado_em`. Um `INSERT` numa faixa sem partição **falha**, e
falhar ali significa parar de enviar e receber mensagem — o que a regra de precedência proíbe. Três
mecanismos cuidam disso:

- A migration `V5` cria o mês corrente **mais 3 meses**, através de funções idempotentes.
- Um job mensal (`ManutencaoParticaoMensagem`, dia 1 às 03:00) recompõe a **janela inteira**, não só
  o próximo mês — assim o job pode falhar algumas vezes seguidas sem parar a operação.
- Na inicialização, a aplicação **se recusa a subir** se faltar partição para o mês corrente ou o
  próximo, com uma mensagem dizendo como corrigir.

Para inspecionar ou corrigir manualmente:

```sql
SELECT * FROM particoes_mensagem_faltantes(3);
SELECT garantir_particoes_mensagem(3);
```

Além disso existe uma partição `DEFAULT` (`mensagem_default`) como **último recurso**. Se todas as
salvaguardas falharem juntas, é melhor a linha cair nela — dívida recuperável — do que o `INSERT`
falhar e a mensagem do cliente se perder. O custo é conhecido: enquanto houver linha ali na faixa de
um mês, criar a partição daquele mês falha. Por isso ela **não é silenciosa**: um job diário (07:15,
antes do horário protegido) registra um `ERROR` com o marcador `[ALERTA_PARTICAO_DEFAULT]` se houver
qualquer linha. Rede de segurança sem alarme some do radar até a limpeza ficar cara.

```sql
SELECT mensagens_na_particao_default();
SELECT DISTINCT date_trunc('month', enviado_em) FROM mensagem_default;
```

## Isolamento de agenda (RN-CRM-01)

Atendentes trabalham por comissão e disputam leads. Um atendente enxergar o lead de outro não é bug
de tela, é problema comercial. O isolamento tem **duas camadas independentes**.

### Camada 1 — aplicação (E02)

Quatro barreiras, três delas de tempo de compilação:

1. `LeadRepositorio` **não tem `findAll()` nem `findById()` cru** — o vocabulário da porta não
   consegue expressar "todos os leads". A visibilidade também não é parâmetro: é derivada do
   `UsuarioContext` dentro do adaptador, então quem chama não escolhe o próprio nível de acesso.
2. `LeadJpaRepository`, o único capaz de ler sem filtro, é **pacote-privado**. Injetá-lo de outro
   pacote não compila.
3. `VisibilidadeLead` é um tipo **selado**; a tradução para SQL usa `switch` exaustivo. Um modo de
   visibilidade novo quebra o build até a tradução existir.
4. `ArquiteturaTest` reprova qualquer classe fora de `...persistencia.lead` que dependa do
   repositório JPA. **Validada por mutação** — introduzir a violação de propósito reprova o build.

### Camada 2 — RLS no banco (E02b)

Cobre o que a camada 1 não alcança: **SQL cru dos read models** (dashboard e relatórios usam
consulta direta, ver `docs/01` §2.2) e **acesso manual pelo `psql`**. Políticas em `lead`,
`atendimento`, `lembrete` e `mensagem_programada`.

A cada transação, nos dois pools, a aplicação executa `SET LOCAL ROLE synapse_app` e publica o
contexto com `set_config(..., is_local => true)` — o equivalente parametrizável de `SET LOCAL`.
Nunca `SET` de sessão: com PgBouncer em modo *transaction*, previsto para produção, o contexto
sobreviveria à transação e o próximo atendente herdaria a visão do anterior.

> **Por que trocar de role.** O usuário da aplicação é dono das tabelas (foi ele quem rodou as
> migrations), e dono ignora RLS a menos que a tabela use `FORCE`. Pior: **superusuário ignora
> sempre, mesmo com `FORCE`**. Enquanto a transação rodasse como dono, as políticas seriam
> decoração. Assumir uma role sem privilégio de dono faz a proteção parar de depender de como a
> string de conexão foi provisionada.

### Os três contextos

Quem chegar depois precisa saber por que uma consulta no `psql` não retorna nada:

| Contexto | Quem | O que acontece |
|---|---|---|
| **Requisição autenticada** | Atendente ou gestor via HTTP | `app.usuario_id` e `app.papel` preenchidos; a política filtra pelo papel |
| **Serviço** | Jobs `@Scheduled`, consumidor de fila, publisher da outbox | `app.papel = 'SERVICO'`; enxerga tudo. Marque o trecho com `ContextoDeServico.executarComo(...)` |
| **Sem contexto** | Bug, ou uma sessão `psql` comum | **Zero linhas.** Falha fechado |

Falhar fechado é deliberado: um bug deixa a tela vazia — visível, diagnosticável em segundos — em
vez de mostrar o lead de outro atendente, que ninguém percebe.

Para investigar no `psql`, assuma um contexto explicitamente:

```sql
BEGIN;
SET LOCAL ROLE synapse_app;
SELECT set_config('app.papel', 'SERVICO', true);
SELECT * FROM lead;
COMMIT;
```

Migrations continuam rodando como o dono das tabelas, fora do RLS — de propósito.

### Padrão obrigatório dos repositórios protegidos

`lead` já segue. `atendimento`, `lembrete` e `mensagem_programada` têm política de banco, mas
**ainda não têm repositório**; quando a E03 os criar, repita a estrutura:

1. Porta em `application` **sem** `findAll()`/`findById()` cru e **sem** a visibilidade como
   parâmetro.
2. Interface Spring Data em `infrastructure/persistencia/<agregado>/`, **pacote-privada**.
3. Adaptador `@Repository` no mesmo pacote, aplicando a Specification em **todos** os métodos —
   inclusive a consulta por id, que precisa filtrar no banco e não em memória.
4. Regra nova no `ArquiteturaTest`, no mesmo formato de `so_o_adaptador_conversa_com_o_jpa_de_lead`.
5. Teste de paridade entre a política SQL e a regra de domínio.

Sem os cinco, o agregado fica protegido só pela camada 2.

## Deploy

### Stack de homologação no Dokploy

O arquivo [`docker/dokploy-stack.yml`](docker/dokploy-stack.yml) descreve os sete serviços da
instância: PostgreSQL, Redis, RabbitMQ, MinIO, backend, frontend e n8n. Ele é para **Docker Stack
(Swarm)** e referencia somente imagens pré-compiladas. O CI publica backend e frontend no GHCR com
as tags `latest` e SHA curto; use sempre o SHA em `SYNAPSE_IMAGE_TAG`, porque ele identifica uma
versão exata e permite rollback sem rebuild. O n8n usa sua imagem oficial, sempre com versão exata
informada em `N8N_IMAGE_TAG`.

Backend e frontend usam `start-first`, healthcheck em `/health/liveness` e rollback automático. Os
cinco serviços com volume mantêm uma réplica no nó manager e não usam `start-first`: duas cópias
do PostgreSQL, RabbitMQ ou MinIO escrevendo simultaneamente no mesmo volume local corromperiam os
dados; no n8n, duas instâncias executariam os mesmos gatilhos. Todos os sete têm limite de memória.
Os defaults provisórios somam 5,25 GiB em regime e podem chegar a 7,25 GiB se backend e frontend
sobrepuserem as versões ao mesmo tempo; reserve ainda memória para
o sistema, Docker, Dokploy e Traefik ao dimensionar a VPS.

O Traefik recebe as rotas pelas labels versionadas em `deploy.labels`:

| Destino | Regra pública |
|---|---|
| Backend | `https://SYNAPSE_DOMINIO/api/v1`, `/webhook/canal`, `/ws` e `/health` |
| Frontend | Demais caminhos de `https://SYNAPSE_DOMINIO` |
| MinIO S3 | `https://MIDIA_DOMINIO` |
| n8n | `https://AUTOMACAO_DOMINIO` (editor e webhooks da Automação) |

`/internal/v1` não tem router público. O n8n o acessa apenas pela rede overlay `synapse-internal`,
em `http://synapse-backend-internal:8080/internal/v1`, e continua enviando `X-Synapse-Token` como
segunda camada. Expor esse namespace para uma Automação externa é exceção: exige router dedicado e
allowlist de IP além do token.

PostgreSQL, Redis, RabbitMQ e os consoles do RabbitMQ/MinIO não publicam porta no host. A rede
externa `dokploy-network` precisa existir (a instalação padrão do Dokploy a cria). Não cadastre
rotas equivalentes de novo na aba Domains: isso criaria routers concorrentes com as labels da
stack. O acesso ao GHCR privado é configurado no Registry do Dokploy, nunca no YAML.

No primeiro volume do Postgres, o script `docker/postgres-init/10-create-n8n-database.sh` cria um
banco e uma role exclusivos para o n8n. Ele não roda novamente em volume existente; alterar as
variáveis `N8N_DB_*` depois do provisionamento exige migração/rotação manual no banco.

#### Variáveis obrigatórias por instância

Nenhum valor desta tabela deve ser commitado. Cadastre-os no ambiente da stack no Dokploy:

| Variável | Descrição |
|---|---|
| `SYNAPSE_IMAGE_TAG` | SHA curto publicado pelo CI; use a mesma tag no backend e frontend. |
| `N8N_IMAGE_TAG` | Versão exata da imagem oficial do n8n; nunca use `latest`. |
| `TRAEFIK_ROUTER_PREFIX` | Identificador curto e único no servidor, sem espaços, por exemplo `estrutural-hml`. |
| `SYNAPSE_DOMINIO` | Host do CRM sem protocolo, por exemplo `hml.crm.exemplo.com`. |
| `MIDIA_DOMINIO` | Host separado do endpoint S3/MinIO, sem protocolo. |
| `AUTOMACAO_DOMINIO` | Host do editor e dos webhooks do n8n, sem protocolo. |
| `SYNAPSE_TENANT_CODIGO` | Código estável da instância; identifica o filho em logs e integrações. |
| `SYNAPSE_TENANT_NOME` | Nome exibido da empresa cliente. |
| `SYNAPSE_TIMEZONE` | Fuso IANA da instância, por exemplo `America/Sao_Paulo`. |
| `POSTGRES_DB` | Nome do banco isolado desta instância. |
| `POSTGRES_USER` | Usuário dono do schema e usado pelos dois pools da aplicação. |
| `POSTGRES_PASSWORD` | Senha forte do PostgreSQL; o backend recebe a mesma referência. |
| `N8N_DB_NAME` | Banco exclusivo do n8n, criado no primeiro boot do volume do Postgres. |
| `N8N_DB_USER` | Role exclusiva do n8n; não reutilize o usuário do CRM. |
| `N8N_DB_PASSWORD` | Senha forte da role exclusiva do n8n. |
| `N8N_ENCRYPTION_KEY` | Chave aleatória e estável usada pelo n8n para cifrar credenciais salvas. |
| `RABBITMQ_USER` | Usuário administrativo do RabbitMQ da instância. |
| `RABBITMQ_PASSWORD` | Senha forte do RabbitMQ. |
| `MINIO_ROOT_USER` | Access key do MinIO e do adaptador S3 do backend. |
| `MINIO_ROOT_PASSWORD` | Secret key forte do MinIO e do adaptador S3 do backend. |
| `SYNAPSE_JWT_SEGREDO` | Segredo HMAC dos tokens de usuário, com no mínimo 32 caracteres. |
| `SYNAPSE_TOKEN_INTERNO` | Segredo de `X-Synapse-Token` usado pelo n8n no contrato privado `/internal/v1`. |
| `AUTOMACAO_TOKEN` | Token permanente usado nas chamadas do CRM para a Automação. |
| `WHATSAPP_NUMERO` | Identificador do número de telefone na Meta Cloud API. |
| `WHATSAPP_TOKEN` | Token de acesso da Meta usado nas chamadas de saída. |
| `WHATSAPP_WEBHOOK_VERIFY_TOKEN` | Token escolhido pela instância para o desafio `GET` do webhook. |
| `WHATSAPP_WEBHOOK_SECRET` | App Secret da Meta, usado somente no HMAC dos `POST` do webhook. |

Os dois segredos do webhook são deliberadamente distintos e não podem ser reutilizados um no
lugar do outro.

#### Variáveis opcionais e capacidade

| Variável | Default da stack | Quando alterar |
|---|---:|---|
| `SYNAPSE_APP_NAME` | `synapse-crm` | Nome técnico em telemetria. |
| `WHATSAPP_PROVEDOR` | `meta-cloud` | Somente ao instalar outro adapter de canal. |
| `WHATSAPP_URL_BASE` | Graph API `v21.0` | Mudança versionada da API da Meta. |
| `ALERTA_WEBHOOK` | vazio | Webhook do canal operacional de alertas. |
| `MIDIA_S3_BUCKET` | `synapse-crm-midia` | Nome do bucket exclusivo deste filho. |
| `MIDIA_S3_EXPIRACAO_LEITURA` | `5m` | Validade das URLs assinadas de anexos. |
| `FEATURE_CAMPANHAS` | `false` | Só ligar quando a aba de Campanhas entrar no escopo. |
| `FEATURE_CHAT_INTERNO`, `FEATURE_FIDELIZACAO` | `true` | Corte de capacidade por filho. |
| `BACKEND_REPLICAS`, `FRONTEND_REPLICAS` | `1` | Escala horizontal; o Redis já é o backplane do WebSocket. |
| `BACKEND_MEMORY_LIMIT` | `1536M` | Limite de memória por réplica do Spring Boot. |
| `FRONTEND_MEMORY_LIMIT` | `512M` | Limite de memória por réplica do Next.js. |
| `POSTGRES_MEMORY_LIMIT` | `1G` | Limite do banco. |
| `REDIS_MEMORY_LIMIT` | `256M` | Limite do cache/backplane. |
| `RABBITMQ_MEMORY_LIMIT` | `512M` | Limite do broker. |
| `MINIO_MEMORY_LIMIT` | `512M` | Limite do storage de mídia. |
| `N8N_MEMORY_LIMIT` | `1G` | Limite provisório da Automação; revisar na E14a com a capacidade contratada. |
| `JAVA_TOOL_OPTIONS` | heap em 70% do cgroup + exit em OOM | Ajuste fino da JVM sem reconstruir a imagem. |

Roteiro no Dokploy:

1. Cadastre o GHCR privado no Registry.
2. Crie uma aplicação Docker Compose em modo Docker Stack apontando para
   `docker/dokploy-stack.yml` na branch `main`.
3. Cadastre todas as variáveis obrigatórias e revise os limites para o tamanho real da VPS.
4. Confira o Preview Compose: nenhuma porta de banco/broker deve estar publicada e os healthchecks
   de backend/frontend devem terminar em `/health/liveness`.
5. Aponte os três registros DNS para a VPS e faça o deploy. Para rollback do CRM, troque apenas
   `SYNAPSE_IMAGE_TAG` pelo SHA anterior e redeploye.

### Extensões do PostgreSQL

O schema usa **`pg_trgm`** (busca por nome). Em Postgres gerenciado (RDS, Cloud SQL, Azure Database
etc.) habilitar extensão costuma exigir privilégio que o usuário da aplicação não tem, e nesse caso
a `V1` falha. **Verifique isso antes do primeiro deploy de homologação** — se for necessário,
habilite a extensão fora da migration.

`pgcrypto` **não** é mais necessária: `gen_random_uuid()` é nativa desde o PostgreSQL 13 e o projeto
exige 15+. Uma extensão a menos é um obstáculo a menos no deploy gerenciado.

### Bulkhead: dois pools de conexão

A aplicação abre **dois pools sobre o mesmo banco**:

| Pool | Bean | Para quê |
|---|---|---|
| `synapse-geral` | `generalDataSource` (`@Primary`) | Todo o resto da aplicação. |
| `synapse-chat` | `chatDataSource` | Reservado ao caminho crítico de mensagens. |

O pool geral é o `@Primary` de propósito: quem esquecer de qualificar cai nele, então o esquecimento
degrada um relatório em vez de consumir a reserva do chat. Usar o pool do chat exige pedir por ele,
com `@Qualifier("chatDataSource")`. O timeout do chat é curto — falhar rápido é melhor que
enfileirar o atendente.

Nesta etapa existem apenas os beans; ligar o caminho de mensagens ao `chatDataSource` é a etapa E09.

---

## Problemas comuns

**`FATAL: autenticação do tipo senha falhou para o usuário "synapse"`**

Já existe um PostgreSQL instalado na máquina ocupando a porta 5432, e a conexão está indo para ele
em vez de ir para o container. Confirme com:

```bash
netstat -ano | findstr :5432
```

Se aparecer mais de um processo, use outra porta no `.env`:

```
POSTGRES_PORT=55432
SYNAPSE_DB_URL=jdbc:postgresql://localhost:55432/synapse_crm
```

E recrie o container com `cd docker && docker compose up -d`.

**Os testes de integração falham sem erro claro**

Testcontainers precisa do Docker rodando. Verifique com `docker info`. Para pular só os testes de
integração: `./mvnw clean install -DskipITs`.

**`mvn` reclama da versão do Java**

O enforcer exige JDK 21+. Verifique com `./mvnw -v` qual JDK o Maven está usando — não é
necessariamente o mesmo do `java -version`. Ajuste o `JAVA_HOME` se preciso.

---

## CI

`.github/workflows/ci.yml` roda em todo push e pull request. Os dois primeiros jobs são paralelos:

- **backend** — `mvn clean verify` com JDK 21 (Temurin), incluindo os testes com Testcontainers.
- **frontend** — `npm ci`, `npm run lint`, `npm test` e `npm run build`.
- **imagens** — depois dos dois anteriores, somente em `main`, publica backend e frontend no GHCR
  com tag por SHA curto e `latest`.

---

## Documentação

`CLAUDE.md` tem as regras não negociáveis do projeto. A arquitetura completa está em `/docs`
(01 a 08) — esses arquivos ficam fora do versionamento por conterem material interno, mas precisam
estar presentes na pasta durante o desenvolvimento.
