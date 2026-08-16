# 18. Runbook das pendências operacionais

Tudo que depende do Marcondes, na ordem de execução. Nenhum destes é prompt de agente.

Ordem pensada para: disparar cedo o que depende de terceiros, depois destravar o ambiente, depois provar que funciona.

---

## Fase 0 — Disparar agora (5 minutos, respostas chegam depois)

Cinco mensagens. Nenhuma depende da outra; manda todas antes de começar o resto.

**0.1 — Subgestora: etapas do funil e tags**

> Para configurar o ambiente de homologação preciso de duas listas:
> 1. As etapas pelas quais um atendimento passa, na ordem, com os nomes que a equipe usa hoje.
> 2. As marcações/tags que vocês usam para classificar cliente ou obra.
>
> Pode ser em texto corrido. Se hoje não existe processo formal, me diga como funciona na prática que eu proponho um rascunho para você ajustar.

**0.2 — Quem administra o DNS: três subdomínios**

> Preciso de três subdomínios apontando para o IP `187.77.47.30`, tipo A:
> `hml.crm`, `hml-midia`, `hml-automacao`.
> Não mexe em nada do site atual — são nomes novos.

**0.3 — Dylan: as regras de automação**

> Seus workflows vão **ler** as regras de follow-up, fidelização e mensagem festiva **do CRM**, ou você vai codificá-las dentro do n8n por enquanto?

Decide se um módulo grande entra ou não nos dias restantes.

**0.4 — Lucas: escala do CSAT**

> A Automação vai pedir a avaliação de **1 a 5 estrelas** ou de **0 a 10**?

O banco guarda 0–5, o protótipo aprovado mostra "9,4/10". Mudar agora, com o sistema vazio, é trivial; depois, com avaliação real dentro, não é.

**0.5 — Grupo: qual é este ambiente**

Fechar que `erp-matriz-hml` é a homologação da Estrutural, e que os workflows do Dylan são os do cliente.

---

## Fase 1 — Segurança do painel (15 minutos)

Antes de qualquer coisa, porque o painel guarda o segredo do JWT, o token da Meta e a senha do banco.

**1.1 — Fechar a porta 3000**

```bash
ssh root@187.77.47.30
ufw allow 22/tcp
ufw deny 3000/tcp
ufw --force enable
ufw status
```

Acesso ao Dokploy passa a ser por túnel:

```bash
ssh -L 3000:localhost:3000 root@187.77.47.30
```

Depois abre `http://localhost:3000` no navegador.

**1.2 — Regenerar o Webhook URL do deploy**

Ele apareceu inteiro num print. Dokploy → o projeto → **Deployments** → o ícone de refresh ao lado do Webhook URL.

---

## Fase 2 — Destravar o ambiente (20 minutos)

**2.1 — Deploy da imagem mais recente**

1. GitHub → Actions: confirma que a última run da `main` está verde
2. GitHub → Packages: confirma que existem as imagens com o SHA dela
3. Dokploy → **Environment** → `SYNAPSE_IMAGE_TAG` = esse SHA
4. **Deploy**

> Se quiser matar essa etapa recorrente: em homologação, use `SYNAPSE_IMAGE_TAG=latest`. O auto-deploy do push passa a trazer o código novo sozinho. Em produção, mantenha o SHA fixo.

Confere no navegador (Ctrl+Shift+R): Tags e Automação abrem tela, Agenda tem os quatro dropdowns, Mensagens Programadas não abre o calendário do Chrome.

**2.2 — Resolver o container do Postgres**

Vale para todos os comandos seguintes. **`name=postgres` casa também com o Postgres do Dokploy** e quebra o `docker exec`.

```bash
PG=$(docker ps -q --filter name=_postgres.1)
echo $PG    # tem que sair UM id só
```

**2.3 — Verificar o schema**

```bash
docker exec $PG psql -U synapse -d synapse_crm -c \
  "select count(*) filter (where success) ok, count(*) filter (where not success) falhou from flyway_schema_history;"

docker exec $PG psql -U synapse -d synapse_crm -c "\dx"

docker exec $PG psql -U synapse -d synapse_crm -c \
  "select relname from pg_class where relname like 'mensagem_%' order by 1;"
```

Esperado: todas as migrations ok e zero falhas, `pg_trgm` presente, partições do mês corrente e do próximo existindo.

**2.4 — Seed de demonstração**

Nunca foi executado. Enche as telas para você e para o Lucas conseguirem avaliar.

```bash
cd /caminho/do/repo
docker exec -i $PG psql -U synapse -d synapse_crm < docker/provisionamento/seed-demonstracao.sql
```

Se der erro de coluna ou enum, me manda a mensagem — é ajuste de minutos.

**2.5 — Ligar a Dashboard**

```bash
docker exec $PG psql -U synapse -d synapse_crm -c "\d feature_flag"
```

Confere o nome real das colunas e então:

```bash
docker exec $PG psql -U synapse -d synapse_crm -c \
  "update feature_flag set habilitada = true where chave = 'dashboard';"
```

---

## Fase 3 — O portão: smoke RLS (10 minutos)

**Este é o único item que, se falhar, muda tudo o que vem depois.** Você está logado como ADMINISTRADOR, que vê tudo por definição — nada do que você viu na tela diz qualquer coisa sobre isolamento entre atendentes.

```bash
cd /caminho/do/repo
./docker/verificacao/executar-smoke-rls.sh
```

O script foi escrito para **falhar com erro explícito** se um atendente enxergar lead de colega.

- **Passou** → segue
- **Falhou** → **pare tudo** e me manda a saída. Vazamento de lead entre atendentes é incidente comercial, não bug técnico, e o conserto pode mexer em migration

---

## Fase 4 — WhatsApp (30 minutos)

**4.1 — Token permanente**

O token da tela de "Configuração da API" **expira em 24 horas**. Gere o permanente:

Meta Business Suite → **Configurações do negócio** → Usuários → **Usuários do sistema** → criar/selecionar → **Adicionar ativos** (vincula o app do WhatsApp) → **Gerar novo token** → permissões `whatsapp_business_messaging` e `whatsapp_business_management` → expiração **Nunca**.

**4.2 — Os outros três valores**

| Variável | Onde |
|---|---|
| `WHATSAPP_NUMERO` | "Identificação do número de telefone" na tela de Configuração da API — é o Phone Number ID, não o telefone |
| `WHATSAPP_WEBHOOK_SECRET` | Configurações do app → **Básico** → Chave secreta do app → Mostrar |
| `WHATSAPP_WEBHOOK_VERIFY_TOKEN` | você inventa: `openssl rand -hex 24` |

**Os dois últimos são coisas diferentes.** Se colocar o mesmo valor nos dois, o cadastro passa e toda mensagem recebida é rejeitada, sem erro visível.

**4.3 — Cadastrar e fazer deploy**

Salva as quatro no Environment do Dokploy e **faz Deploy** — variável nova só entra em container novo.

**4.4 — Testar o desafio antes de tocar na Meta**

```bash
curl -i "https://crm.187.77.47.30.sslip.io/webhook/canal?hub.mode=subscribe&hub.verify_token=SEU_VERIFY_TOKEN&hub.challenge=12345"
```

Esperado: **200** com o corpo `12345` cru, sem aspas e sem JSON. Se vier 403, me manda a resposta.

**4.5 — Cadastrar o webhook**

Meta → WhatsApp → Configuration → Webhook → Edit:
- Callback URL: `https://crm.187.77.47.30.sslip.io/webhook/canal`
- Verify token: o mesmo do passo 4.2

Depois de verificar, **assine o campo `messages`**. Sem isso o webhook fica verde e não chega nada.

**4.5b — Publicar o app e inscrevê-lo na conta**

Dois passos que não aparecem no fluxo guiado da Meta e que, juntos, custaram uma noite de diagnóstico em 15/08. Os dois falham em silêncio: webhook verificado, campo assinado, e nenhuma mensagem chegando.

**Publicar o app.** Enquanto estiver "Não publicado", a Meta entrega **apenas** webhooks de teste do painel — nem para administrador, desenvolvedor ou testador. Menu do app → **Publicar**. No caso da Estrutural foi instantâneo, sem verificação de negócio.

**Inscrever o app na conta (WABA).** Assinar o campo `messages` diz *o que* receber; inscrever o app diz *que aquele app* recebe daquela conta. São coisas diferentes, e uma conta pode ter vários apps inscritos.

> A inscrição é por **WABA, não por número**. A WABA da Estrutural contém o
> número oficial e o número de homologação. O CRM filtra cada evento pelo
> `phone_number_id` da credencial ativa antes de guardar o payload ou repassá-lo
> ao n8n; sem essa credencial, ele falha fechado.

Antes de inscrever em homologação, execute o provisionamento com:

```bash
export WHATSAPP_NUMERO=1307417749115229
./docker/provisionamento/executar-provisionamento.sh
curl -fsS https://crm.187.77.47.30.sslip.io/health/critical
```

O componente `canal` precisa estar `UP`. Se disser `canal ativo sem
phone_number_id`, não inscreva o app: corrija o provisionamento primeiro.

```powershell
curl.exe -s "https://graph.facebook.com/v26.0/{WABA_ID}/subscribed_apps" -H "Authorization: Bearer TOKEN"
```

O token precisa ser **do app** — o temporário da tela de Configuração da API serve para este diagnóstico.

Se a lista vier vazia, ou trouxer **outro app**, inscreva o nosso:

```powershell
curl.exe -X POST "https://graph.facebook.com/v26.0/{WABA_ID}/subscribed_apps" -H "Authorization: Bearer TOKEN"
```

> Em 15/08 a conta estava inscrita no app `f-bot` (fbot.chat) e não no `Estrutural-Synapse`. A Meta entregava normalmente — para o outro lugar.

**Atenção no go-live:** antes de liberar o número oficial, reexecute o
provisionamento com o Phone Number ID oficial. O código não muda; muda a
credencial ativa. Se a conta já estiver inscrita em outro app em uso, confirme
com quem opera a estratégia de transição. O filtro impede este CRM de guardar
eventos dos demais números da WABA, mas não controla o que outro app responde.

**4.6 — Liberar seu número**

O `+55 61 3199 1947` é **número de teste** da Meta: só envia para destinatários cadastrados. Adiciona o seu celular no campo "Até" da tela de Configuração da API.

**4.7 — O teste que importa**

1. Manda mensagem do seu celular para o número → tem que aparecer em Atendimentos
2. Responde pela tela → tem que chegar no celular

**Os dois sentidos.** O bug do `@Scheduled` da E07 quebrou as duas direções com o build verde; só mensagem real prova.

---

## Fase 5 — Backup (40 minutos)

**5.1 — Bucket**

Contrata um S3-compatível barato: Backblaze B2, Wasabi ou Cloudflare R2. Cria o bucket e gera as chaves.

**5.2 — Credenciais no host, não no painel**

```bash
sudo install -m 600 -o root -g root /dev/null /etc/synapse/backup.env
sudo tee /etc/synapse/backup.env >/dev/null <<'EOF'
S3_ENDPOINT=...
S3_ACCESS_KEY=...
S3_SECRET_KEY=...
S3_BUCKET=...
EOF
```

`chmod 600` e dono root: o Server Job roda como root e lê; mais ninguém lê. Melhor que o campo de ambiente do painel, que aparece na tela e vaza em print.

**5.3 — Agendar**

Dokploy → **Schedules** → Server Job, de hora em hora:

```bash
set -a; . /etc/synapse/backup.env; set +a; /caminho/do/script-backup.sh
```

Detalhes em `docker/backup/README.md`.

**5.4 — Restaurar de verdade**

**Backup nunca restaurado é esperança, não backup.** Com o banco ainda sem dado real, siga `docker/backup/RESTAURAR.md`: restaura num banco novo e sobe a aplicação apontando para ele. Com 22 migrations e a role `synapse_app`, `pg_restore` puro não basta.

---

## Fase 6 — Watchdog (30 minutos)

Passo a passo completo em `docs/15-operacao-watchdog-externo.md`.

**6.1 — VPS mínimo em outro provedor.** Não pode ser o mesmo host: monitor que morre junto com o monitorado não é monitor.

**6.2 — Uptime Kuma** nesse VPS, monitor HTTP em:
```
https://crm.187.77.47.30.sslip.io/health/critical
```
Intervalo 30 s, alerta após **duas** falhas consecutivas.

**6.3 — Canal de alerta.** Define o destino (grupo de WhatsApp da Synapse, Discord, o que for) e põe a URL em `ALERTA_WEBHOOK` no Dokploy.

**6.4 — Teste destrutivo.** Derruba o backend de propósito e confirma que o alerta chega:

```bash
docker service scale <stack>_backend=0
# espera o alerta
docker service scale <stack>_backend=1
```

**Alerta que nunca disparou não é alerta.** Este projeto já encontrou treze vezes o padrão da proteção que existia e não protegia.

---

## Fase 7 — Subdomínios (quando o DNS responder)

**7.1** — Confere a propagação: `dig +short hml.crm.SEUDOMINIO.com.br`

**7.2** — Dokploy → Environment: troca `SYNAPSE_DOMINIO`, `MIDIA_DOMINIO` e `AUTOMACAO_DOMINIO` → Deploy. O Traefik pede certificado novo sozinho.

**7.3** — **Reaponta o webhook na Meta.** Ela não segue redirecionamento; se esquecer, mensagem para de chegar em silêncio.

**7.4** — Avisa o Dylan: o `N8N_HOST` e o `WEBHOOK_URL` mudaram, e qualquer webhook que ele tenha cadastrado em serviço externo precisa ser refeito.

---

## Ordem resumida

| Fase | Tempo | Bloqueia |
|---|---|---|
| 0 — cinco mensagens | 5 min | tudo que depende de terceiros |
| 1 — segurança do painel | 15 min | nada, mas é risco aberto agora |
| 2 — deploy, seed, flag | 20 min | a Fase 3 e qualquer avaliação de tela |
| 3 — **smoke RLS** | 10 min | **se falhar, para tudo** |
| 4 — WhatsApp | 30 min | o teste que prova o produto |
| 5 — backup | 40 min | go-live |
| 6 — watchdog | 30 min | go-live |
| 7 — subdomínios | 20 min | produção |

Fases 0 a 4 somam menos de duas horas e cobrem tudo que impede alguém de testar o produto.
