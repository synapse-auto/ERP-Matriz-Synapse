# E14a — Runbook do deploy de homologação

> **Este arquivo não é um prompt para colar inteiro no agente.** É um runbook de duas colunas.
> **[VOCÊ]** = executa no Dokploy, no registrador de DNS ou no painel da Meta.
> **[AGENTE]** = trecho que você cola no Codex; ele produz arquivo, script ou diagnóstico.
>
> Referências: `README.md` §Deploy, `docs/10-infraestrutura-deploy.md`, `docker/dokploy-stack.yml`.
> Alvo: **11/08**. É o primeiro contato do cliente com o produto.

---

## Fase 0 — Decisões que só você toma

Nada abaixo avança sem estes valores. Anote-os antes de abrir o Dokploy.

| Item | Valor | Como decidir |
|---|---|---|
| VPS | região **São Paulo** | latência: ~15ms vs ~200ms da Europa. Ver `docs/10` §1.1 |
| RAM | **mínimo 8 GB, recomendado 12–16 GB** | a stack soma 5,25 GiB em regime e 7,25 GiB durante um rolling update, e ainda faltam SO + Docker + Dokploy + Traefik |
| Disco | **80 GB SSD** para homologação | Postgres + mídia do MinIO + imagens Docker. A mídia é a que cresce: anexo de WhatsApp fica no disco do VPS, não em serviço externo |
| `SYNAPSE_DOMINIO` | ex. `hml.crm.estruturalvidros.com.br` | precisa de domínio real — Let's Encrypt não emite para IP |
| `MIDIA_DOMINIO` | ex. `hml-midia.estruturalvidros.com.br` | host separado, obrigatório |
| `AUTOMACAO_DOMINIO` | ex. `hml-automacao.estruturalvidros.com.br` | editor e webhooks do n8n |
| `TRAEFIK_ROUTER_PREFIX` | `estrutural-hml` | curto, sem espaço, único no servidor |
| `N8N_IMAGE_TAG` | versão exata, ex. `1.109.2` | **nunca `latest`** — a stack recusa |

**Sobre o MinIO:** a stack já sobe MinIO no próprio VPS. Você **não precisa** contratar S3 externo agora. O que precisa de storage externo é o **backup** (Fase 9) — para isso use qualquer S3-compatível barato (Backblaze B2, Wasabi, Cloudflare R2). Backup no mesmo disco do banco não é backup.

---

## Fase 1 — VPS e Dokploy

**[VOCÊ]**

1. Provisione o VPS em São Paulo. Ubuntu 22.04 ou 24.04.
2. Instale o Dokploy:
   ```bash
   curl -sSL https://dokploy.com/install.sh | sh
   ```
3. Confirme que a rede externa existe (a instalação padrão cria):
   ```bash
   docker network ls | grep dokploy-network
   ```
   Se não aparecer, **pare** e me devolva a saída. A stack referencia essa rede como `external: true` e falha sem ela.
4. Confirme o Swarm ativo:
   ```bash
   docker info --format '{{.Swarm.LocalNodeState}}'   # esperado: active
   ```

**Devolva:** saída dos passos 3 e 4, e `free -h`.

---

## Fase 2 — Imagens no GHCR

**[VOCÊ]**

1. Confirme que o CI publicou as imagens. No GitHub → repositório → **Packages**, devem existir:
   - `erp-matriz-synapse-backend`
   - `erp-matriz-synapse-frontend`
2. Anote o **SHA curto** da última run verde da `main`. Esse é o `SYNAPSE_IMAGE_TAG`.
3. Crie um **Personal Access Token (classic)** com escopo `read:packages`.
4. No Dokploy → **Settings → Registry → Add Registry**:
   - Registry URL: `ghcr.io`
   - Username: seu usuário do GitHub
   - Password: o PAT

**Por que SHA e não `latest`:** rollback vira troca de uma variável, sem rebuild. Com `latest` você não sabe para onde está voltando.

---

## Fase 3 — DNS

**[VOCÊ]**

Três registros **A**, todos apontando para o IP do VPS:

| Nome | Tipo | Valor |
|---|---|---|
| `hml.crm...` | A | IP do VPS |
| `hml-midia...` | A | IP do VPS |
| `hml-automacao...` | A | IP do VPS |

Sem proxy (Cloudflare em modo "DNS only", nuvem cinza) no primeiro deploy — o desafio HTTP-01 do Let's Encrypt precisa chegar no Traefik.

Verifique a propagação antes de seguir:
```bash
dig +short hml.crm.SEUDOMINIO.com.br
```

---

## Fase 4 — Gerar os segredos

**[VOCÊ]** — rode no VPS (tem `openssl`), ou no PowerShell com o bloco alternativo abaixo.

```bash
echo "POSTGRES_PASSWORD=$(openssl rand -base64 32 | tr -d '\n=/+' | cut -c1-32)"
echo "N8N_DB_PASSWORD=$(openssl rand -base64 32 | tr -d '\n=/+' | cut -c1-32)"
echo "RABBITMQ_PASSWORD=$(openssl rand -base64 32 | tr -d '\n=/+' | cut -c1-32)"
echo "MINIO_ROOT_PASSWORD=$(openssl rand -base64 32 | tr -d '\n=/+' | cut -c1-32)"
echo "SYNAPSE_JWT_SEGREDO=$(openssl rand -base64 48 | tr -d '\n')"
echo "SYNAPSE_TOKEN_INTERNO=$(openssl rand -hex 32)"
echo "AUTOMACAO_TOKEN=$(openssl rand -hex 32)"
echo "N8N_ENCRYPTION_KEY=$(openssl rand -hex 32)"
echo "WHATSAPP_WEBHOOK_VERIFY_TOKEN=$(openssl rand -hex 24)"
```

PowerShell:
```powershell
function Segredo($n) { [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes($n)) -replace '[+/=]','' }
Segredo 36   # repita para cada segredo
```

**Guarde num gerenciador de senhas.** Não no `.env` local, não no chat, não no repositório.

**Três armadilhas:**

- `WHATSAPP_WEBHOOK_VERIFY_TOKEN` e `WHATSAPP_WEBHOOK_SECRET` são **coisas diferentes**. O verify token é inventado por você e usado só no `GET` de cadastro. O secret é o **App Secret da Meta** e é usado só no HMAC dos `POST`. Trocar um pelo outro já quebrou o webhook antes.
- `N8N_ENCRYPTION_KEY` não pode mudar depois. Muda = todas as credenciais salvas no n8n viram lixo.
- `N8N_DB_*` só é aplicado no **primeiro boot do volume** do Postgres. Alterar depois exige migração manual.

---

## Fase 5 — Criar a stack no Dokploy

**[VOCÊ]**

1. **Create Project** → `estrutural-vidros-hml`.
2. Dentro dele, **Create Service → Compose**.
3. Provider: **GitHub** → repositório → branch `main` → **Compose Path**: `docker/dokploy-stack.yml`.
4. **Compose Type: `Docker Stack` (Swarm)** — não `docker-compose`. Sem isso, `deploy.update_config` é ignorado e você perde o `start-first`.
5. Aba **Environment**: cole todas as obrigatórias. Lista completa no `README.md` §"Variáveis obrigatórias por instância".

Bloco base para colar e completar:

```env
SYNAPSE_IMAGE_TAG=<sha-curto>
N8N_IMAGE_TAG=1.109.2
TRAEFIK_ROUTER_PREFIX=estrutural-hml

SYNAPSE_DOMINIO=hml.crm.exemplo.com.br
MIDIA_DOMINIO=hml-midia.exemplo.com.br
AUTOMACAO_DOMINIO=hml-automacao.exemplo.com.br

SYNAPSE_TENANT_CODIGO=estrutural-vidros
SYNAPSE_TENANT_NOME=Estrutural Vidros
SYNAPSE_TIMEZONE=America/Sao_Paulo

POSTGRES_DB=synapse_crm
POSTGRES_USER=synapse
POSTGRES_PASSWORD=
N8N_DB_NAME=n8n
N8N_DB_USER=n8n
N8N_DB_PASSWORD=
N8N_ENCRYPTION_KEY=

RABBITMQ_USER=synapse
RABBITMQ_PASSWORD=
MINIO_ROOT_USER=synapse
MINIO_ROOT_PASSWORD=

SYNAPSE_JWT_SEGREDO=
SYNAPSE_TOKEN_INTERNO=
AUTOMACAO_TOKEN=

WHATSAPP_NUMERO=
WHATSAPP_TOKEN=
WHATSAPP_WEBHOOK_VERIFY_TOKEN=
WHATSAPP_WEBHOOK_SECRET=

FEATURE_CAMPANHAS=false
FEATURE_CHAT_INTERNO=true
FEATURE_FIDELIZACAO=true
```

6. **Não cadastre nada na aba Domains.** As rotas vêm das labels do YAML. Cadastrar de novo cria routers concorrentes.
7. Clique em **Preview Compose** e confira três coisas:
   - nenhuma porta de Postgres, Redis ou RabbitMQ publicada no host
   - healthcheck de backend e frontend terminando em `/health/liveness`
   - `order: start-first` presente em backend e frontend

**Devolva:** print ou texto do Preview Compose antes de dar Deploy.

---

## Fase 6 — Primeiro deploy e verificação

**[VOCÊ]** — clique em **Deploy** e acompanhe os logs.

> **Correção pós-E14b:** `docker ps -qf name=postgres` **casa também com o Postgres do próprio Dokploy** e expande para dois IDs, quebrando o `docker exec`. Use sempre a task do Swarm, e confira que saiu um ID só.

Verificações, em ordem:

```bash
# 0. Resolver o container CERTO do Postgres
PG=$(docker ps -q --filter name=_postgres.1)
[ "$(echo "$PG" | wc -w)" -eq 1 ] || { echo "ERRO: $(echo "$PG" | wc -w) candidatos"; }

# 1. Todos os serviços com 1/1
docker service ls

# 2. Migrations — as 20 versões aplicadas, nenhuma "success = f"
docker exec $PG psql -U synapse -d synapse_crm -c \
  "select version, description, success from flyway_schema_history order by installed_rank desc limit 5;"

# 3. pg_trgm instalada
docker exec $PG psql -U synapse -d synapse_crm -c "\dx"

# 4. Partições de mensagem cobrindo o mês corrente e o próximo
docker exec $PG psql -U synapse -d synapse_crm -c \
  "select relname from pg_class where relname like 'mensagem_%' order by 1;"

# 5. HTTPS válido e app viva
curl -i https://SYNAPSE_DOMINIO/health/liveness
curl -i https://SYNAPSE_DOMINIO/health/readiness
```

**Se a `V1` falhar por `pg_trgm`:** o container do Postgres roda como superusuário, então não deve acontecer. Se acontecer, me devolva o erro — a saída é habilitar a extensão fora da migration.

### 6b. Teste de fumaça do RLS — o mais importante desta fase

Nenhum teste automatizado pega isso, porque no CI o usuário das migrations e o da aplicação são o mesmo. Aqui também são — o que significa que **o RLS pode não estar protegendo ninguém e tudo parecer certo.**

**[AGENTE]** — cole no Codex:

> Escreva `docker/verificacao/smoke-rls.sql`: um script que, conectado como o usuário da aplicação com a role `synapse_app` assumida via `SET LOCAL ROLE`, prova que um atendente **não** enxerga lead de outro atendente. Ele deve criar dois atendentes e dois leads de teste, consultar sob o contexto de cada um, e **falhar com erro explícito** (`RAISE EXCEPTION`) se a contagem visível for maior que a esperada. Ao final, limpar o que criou. Referência: `V12__rls_isolamento_lead.sql` e `V13__role_da_aplicacao.sql`. Commite e faça push.

**[VOCÊ]** — depois rode:
```bash
docker exec -i $(docker ps -qf name=postgres) \
  psql -U synapse -d synapse_crm < docker/verificacao/smoke-rls.sql
```
Erro = bom sinal de que o script funciona. **Sucesso silencioso quando deveria falhar = o `GRANT` foi para o usuário errado.** Me devolva a saída de qualquer jeito.

---

## Fase 7 — Provisionamento dos dados iniciais

O seed de desenvolvimento (`R__seed_dev.sql`) **não roda fora do perfil dev**, de propósito. Este ambiente sobe vazio.

**[AGENTE]** — cole no Codex:

> Crie `docker/provisionamento/` com um script SQL idempotente `provisionar-instancia.sql` que popula uma instância nova: usuário administrador (senha recebida por variável, hash BCrypt, nunca em texto no arquivo), etapas do funil, tags iniciais, e `configuracao_automacao` com faixas válidas. Tudo parametrizado por `\set` — nada da Estrutural Vidros hardcoded, porque este script vai ser reusado em cada filho. Crie também `docker/provisionamento/README.md` explicando como executar. Referência de estrutura: `R__seed_dev.sql`. Commite e faça push.

**[VOCÊ]** — as **etapas do funil e as tags** precisam vir da subgestora (ver seção "Sobre a subgestora" no fim deste arquivo). Se ela ainda não respondeu até o dia do deploy, suba com um conjunto provisório e marque para trocar — mas **peça hoje**, porque etapa de funil errada faz a tela inteira parecer errada para quem for homologar.

Entregue a senha do admin por canal separado do que você usou para mandar a URL.

---

## Fase 8 — Webhook da Meta

**[VOCÊ]**

1. Meta for Developers → seu app → **WhatsApp → Configuration → Webhook → Edit**.
2. Callback URL: `https://SYNAPSE_DOMINIO/webhook/canal`
3. Verify token: o `WHATSAPP_WEBHOOK_VERIFY_TOKEN` que você gerou na Fase 4.
4. Antes de clicar em Verify, teste você mesmo o desafio `GET`:
   ```bash
   curl -i "https://SYNAPSE_DOMINIO/webhook/canal?hub.mode=subscribe&hub.verify_token=SEU_VERIFY_TOKEN&hub.challenge=12345"
   ```
   Esperado: **200** com o corpo `12345`, sem aspas, sem JSON.
   **403 aqui é o bug que a E14 corrigiu** — o `GET` reusando o validador HMAC do `POST`. Se voltar 403, me devolva a resposta.
5. Subscribe no campo **`messages`**.
6. Mande uma mensagem real do seu celular para o número e confirme que ela aparece na tela de Atendimentos.
7. Responda pela tela e confirme que chega no celular.

**Ponta a ponta nos dois sentidos.** O bug do `@Scheduled` da E07 quebrou as duas direções e o build continuou verde — só uma mensagem real prova.

---

## Fase 9 — Backup, antes de existir dado real

**[AGENTE]** — cole no Codex:

> Crie `docker/backup/` com um script de `pg_dump` para storage S3-compatível: dump comprimido de hora em hora, retenção de 7 dias, credenciais exclusivamente por variável de ambiente. Inclua `docker/backup/RESTAURAR.md` com o procedimento de restauração passo a passo — com 20 migrations e a role `synapse_app`, `pg_restore` puro não basta. Documente também como agendar no Dokploy (Schedules). Commite e faça push.

**[VOCÊ]**

1. Contrate o bucket (B2/Wasabi/R2) e cadastre as credenciais no Dokploy.
2. Agende o backup.
3. **Restaure agora, com o banco ainda vazio de dado real:** restaure num banco novo e suba a aplicação apontando para ele. Backup nunca restaurado é esperança, não backup.

> **Dívida registrada:** `pg_dump` horário significa perder até uma hora num incidente. Aceitável em homologação. **PITR entra antes do go-live de produção** (`docs/10` §1.1b).

---

## Fase 10 — Deploy sem downtime

**[VOCÊ]** — o `start-first` está no YAML, mas você precisa ver funcionando antes de acreditar.

1. Deixe rodando num terminal:
   ```bash
   while true; do curl -s -o /dev/null -w "%{http_code} " https://SYNAPSE_DOMINIO/health/liveness; sleep 1; done
   ```
2. Troque `SYNAPSE_IMAGE_TAG` para outro SHA e clique em Redeploy.
3. **Nenhum código diferente de 200** durante a troca. Se aparecer 502 ou 503, o Compose Type não está em Docker Stack — volte à Fase 5 passo 4.

**Devolva:** a sequência de códigos e quanto tempo levou.

---

## Checklist de pronto

- [ ] `dokploy-network` existe, Swarm ativo
- [ ] GHCR cadastrado no Registry, imagens puxando por SHA
- [ ] Três DNS propagados, certificado válido nos três domínios
- [ ] Todas as obrigatórias cadastradas; Preview Compose sem porta de banco publicada
- [ ] 20 migrations aplicadas, `pg_trgm` presente, partições cobertas
- [ ] **Smoke test do RLS passando no ambiente real**
- [ ] Admin criado, etapas e tags carregadas
- [ ] Webhook verificado (200 no `GET`) e **mensagem real trocada nos dois sentidos**
- [ ] Backup agendado **e restauração testada**
- [ ] Rolling update sem um único não-200
- [ ] Flags: `FEATURE_CAMPANHAS=false` (`docs/09`)
- [ ] `smoke-rls.sql`, `provisionamento/` e `backup/` commitados e no `origin`

**Ao terminar, anote:** quanto de RAM sobrou, quanto tempo levou o deploy, e o que você teve que fazer manualmente que não estava aqui. Isso vira o roteiro do segundo filho — e o segundo filho é o teste real do modelo Silo.

---

## Sobre a subgestora e as etapas do funil

**Subgestora** é a pessoa da Estrutural Vidros que acompanha a operação comercial no dia a dia — quem conhece o processo real dos atendentes. O documento interno da Synapse prevê entregar o ambiente a ela **10 a 15 dias antes da implantação**, para pegar desalinhamento cedo em vez de na virada. Com meta em 25/08, isso é ~11/08.

**Etapas do funil** são as colunas por onde um lead caminha até virar venda. O sistema não inventa: elas vêm da operação real. Algo como *Novo → Em atendimento → Orçamento enviado → Negociação → Fechado / Perdido* — mas os nomes e a quantidade têm que ser os que a equipe dela já usa, senão o atendente abre a tela e não reconhece o próprio trabalho.

**Tags** são as marcações livres para segmentar lead — tipo de obra, origem, prioridade.

**O que pedir a ela, em uma mensagem:**

> Para configurar o ambiente de homologação preciso de duas listas:
> 1. As etapas pelas quais um atendimento passa, na ordem, com os nomes que a equipe usa hoje.
> 2. As marcações/tags que vocês usam para classificar cliente ou obra.
>
> Pode ser em texto corrido mesmo. Se hoje não existe um processo formal, me diga como funciona na prática que eu proponho um rascunho para você ajustar.

É o único item da E14a bloqueado em terceiro. Tudo o mais depende só de você.
