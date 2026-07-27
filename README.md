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

Os defaults de `.env.example` já funcionam para desenvolvimento. Os campos de **segredo**
(`WHATSAPP_*`, `AUTOMACAO_*`) ficam vazios de propósito — as etapas que dependem deles ainda não
existem, e um default falso é pior que um valor ausente.

### 2. Infraestrutura

```bash
cd docker && docker compose up -d
```

Sobe três serviços, cada um com healthcheck e volume nomeado:

| Serviço | Porta padrão | Credenciais de dev |
|---|---|---|
| PostgreSQL 15 | 5432 | `synapse` / `synapse`, banco `synapse_crm` |
| Redis 7 | 6379 | sem senha |
| RabbitMQ 3 | 5672 (AMQP), 15672 (UI) | `synapse` / `synapse` |

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

`.github/workflows/ci.yml` roda em todo push e pull request, em dois jobs paralelos:

- **backend** — `mvn clean verify` com JDK 21 (Temurin), incluindo os testes com Testcontainers.
- **frontend** — `npm ci`, `npm run lint` e `npm run build`.

---

## Documentação

`CLAUDE.md` tem as regras não negociáveis do projeto. A arquitetura completa está em `/docs`
(01 a 08) — esses arquivos ficam fora do versionamento por conterem material interno, mas precisam
estar presentes na pasta durante o desenvolvimento.
