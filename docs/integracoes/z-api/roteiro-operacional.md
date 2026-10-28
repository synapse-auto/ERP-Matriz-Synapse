# Z-API — roteiro executavel de cadastro, homologacao e preparacao do corte

> Cenario atualizado: o numero produtivo ja foi conectado pelo operador. Para continuidade
> no banco existente, usar [transicao-produtiva.md](./transicao-produtiva.md). As instrucoes
> de HML vazia abaixo permanecem somente para esse ambiente, nao para a FMNA.

25/10/2026. Substitui as instrucoes operacionais incompletas de B–D de
`homologacao-protegida.md`; conserva seu contrato e suas restricoes.
**O agente nao executou nenhum comando na VPS/Dokploy/Z-API.** Os comandos abaixo
sao para o operador; comandos Bash nao devem ser colados no PowerShell.

## 0. Ordem e limites

1. Conferencias somente leitura na Femina, versao/CI e proxy.
2. Cadastro separado **INATIVO**, com simulacao e confirmacao; nao troca o provedor.
3. Preparar **outra stack/banco/volumes/dominio/numero controlado** de homologacao.
4. Ativar somente esse canal no banco vazio HML; configurar envs/override **na HML**.
5. Verificar HTTPS/redacao com canario antes de cadastrar o segredo na Z-API.
6. Cadastrar callbacks da instancia de teste e homologar texto/status/midia/automacao.
7. Elaborar inventario/decisoes sobre ciclos/filas/agendas da Femina. **Nao executar
   corte automaticamente**: homologacao aprovada nao resolve ciclos antigos.

Nao conectar o numero real da Femina na instancia HML: isso pode desconectar o
provedor atual. Nao compartilhar Redis/MinIO/banco/filas/segredos entre as stacks.
Nao usar copia do banco de pacientes como base de homologacao. A HML deve ter esquema
migrado pelo runner existente e configuracao minima, sem dados operacionais.
O cadastro SQL nao instala schema nem cria usuarios, etapas ou permissoes.
Nao reexecutar o provisionador Meta depois do cadastro Z-API.

## 1. Pre-requisitos / somente leitura

**VPS, Bash, checkout revisado do repositorio (nao executar scripts baixados via pipe):**

```bash
pwd
git status --short --branch
git log -1 --format='%H %s'
docker version --format '{{.Server.Version}}'
docker service inspect fmnaprod-uzapi-36acu4_backend \
  --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}'
docker service ps fmnaprod-uzapi-36acu4_backend \
  --format '{{.ID}} {{.Image}} {{.CurrentState}}'
docker service inspect fmnaprod-uzapi-36acu4_backend \
  --format '{{json .Endpoint.Spec.Ports}}'
docker ps --filter status=running --format '{{.ID}} {{.Names}} {{.Image}}'
```

Esperado: imagem/digest conhecidos, backend saudavel e **nenhuma porta 8080 publicada**.
Nao usar inspect sem projecao, imprimir Env, `.env`, tokens, payload ou logs brutos.
Identificar pela lista o container **Postgres da instancia correta**, nao o Postgres do
Dokploy e nao usar resolvedor que pressupoe existir apenas um Postgres no host.

**VPS, mesma sessao, preencher apenas identificadores operacionais (sem senha):**

```bash
set -euo pipefail
set +x
read -rp 'Container Postgres da instancia a conferir: ' PG_CONTAINER
read -rp 'Banco CRM (nao banco n8n): ' DB_NAME
read -rp 'Usuario SQL de manutencao autorizado: ' DB_USER
docker inspect "$PG_CONTAINER" --format '{{.Name}} {{.Config.Image}}'
docker exec -i "$PG_CONTAINER" psql -X -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
  < docs/diagnosticos/inventario-corte-z-api.sql
```

O script e READ ONLY, timeout 5s, exige visibilidade BYPASSRLS e retorna banco/horario,
ultimas migrations, UUIDs/estados de canais/credenciais, ciclos abertos, filas por origem,
ambiguos e programadas. Sem telefone, tokens ou mensagens. IDs so por canal privado.
Se timeout, parar e restringir o diagnostico; nao aumentar carga em horario comercial.
As 100 programadas listadas nao sao o total; a contagem e independente.

**Maquina com gh autenticado (ou Actions do GitHub), somente leitura:**

Release de aplicacao conferida nesta entrega: merge #325
`944536cacae740fc66cee6b5e65b20c606e1af83`, tag curta **944536c**,
[run main 40216865849](https://github.com/synapse-auto/ERP-Matriz-Synapse/actions/runs/40216865849)
concluida SUCCESS, Backend/Frontend/Stack e ambas as publicacoes GHCR SUCCESS.
Isso nao comprova o digest puxado na VPS nem o que esta implantado. Os novos scripts SQL
devem vir do SHA da PR deste roteiro; nao precisam de nova imagem para funcionar.

```bash
read -rp 'SHA completo da release aprovada: ' RELEASE_SHA
gh run list --repo synapse-auto/ERP-Matriz-Synapse --commit "$RELEASE_SHA" \
  --json databaseId,headSha,status,conclusion,event
read -rp 'Run de push na main dessa release: ' RELEASE_RUN
gh run view "$RELEASE_RUN" --repo synapse-auto/ERP-Matriz-Synapse \
  --json headSha,status,conclusion,jobs
```

Conferir SHA literal, Backend/Frontend/Stack e GHCR. CI de PR com imagem skipped
**nao comprova imagem publicada**. Antes de aplicar na HML, conferir tag/digest reais:

```bash
read -rp 'Tag da imagem publicada dessa release: ' IMAGE_TAG
docker buildx imagetools inspect "ghcr.io/synapse-auto/erp-matriz-synapse-backend:$IMAGE_TAG"
docker buildx imagetools inspect "ghcr.io/synapse-auto/erp-matriz-synapse-frontend:$IMAGE_TAG"
```

Nao fornecer SHA de scripts como se fosse tag de imagem: esta entrega nao altera
artefato executavel, mas depende da release de entrada protegida #325 ou posterior.
Registrar privadamente a imagem/configuracao anterior para rollback. Preservar todos
os valores Uzapi/Meta, inclusive WHATSAPP_TOKEN: nao substitui-los pelos tokens Z-API.

## 2. Cadastro suportado e obtencao do UUID

**VPS, banco selecionado explicitamente.** Pode preparar o cadastro inativo no banco
destino sem ativar Z-API, mas homologar primeiro em HML. Nao passar UUID ou tokens ao SQL.
O telefone abaixo e o numero internacional controlado, nao o ID da instancia.

```bash
read -rp 'Nome NOVO do canal, ate 50 caracteres: ' ZAPI_CANAL_NOME
read -rp 'instanceId fornecido pela Z-API: ' ZAPI_INSTANCIA
read -rp 'Telefone controlado, DDI+DDD+numero, so digitos: ' ZAPI_TELEFONE
docker exec -i "$PG_CONTAINER" psql -X -U "$DB_USER" -d "$DB_NAME" \
  -v banco_esperado="$DB_NAME" -v canal_nome="$ZAPI_CANAL_NOME" \
  -v instancia_id="$ZAPI_INSTANCIA" -v telefone="$ZAPI_TELEFONE" \
  < docker/provisionamento/cadastrar-canal-z-api.sql
```

Padrao: **ROLLBACK**. UUID exibido nessa simulacao nao e utilizavel. Apos revisar:

```bash
docker exec -i "$PG_CONTAINER" psql -X -U "$DB_USER" -d "$DB_NAME" \
  -v banco_esperado="$DB_NAME" -v canal_nome="$ZAPI_CANAL_NOME" \
  -v instancia_id="$ZAPI_INSTANCIA" -v telefone="$ZAPI_TELEFONE" \
  -v confirmar=CADASTRAR < docker/provisionamento/cadastrar-canal-z-api.sql
```

Anotar `credencial_id` **dessa execucao confirmada**. PostgreSQL gera `canal_id` e
`credencial_id` por `gen_random_uuid()`. Persistencia: canal WHATSAPP, ambos INATIVOS,
identificador externo instanceId e `token_ref=env://Z_API_TOKEN`. Nao existe token no SQL/banco.
Repetir exatamente o comando confirmado: mesmo UUID, `criado=f`, sem duplicar/auditar de novo.
Nome ocupado por outro canal, identidade ja usada, numero diferente ou credencial historica
causam erro e rollback, nunca adocao da identidade Uzapi ou atualizacao silenciosa.
Auditoria SISTEMA registra executor SQL/UUID/estado, sem telefone/token.

**VPS, leitura posterior (substituir pelo UUID realmente retornado):**

```bash
read -rp 'credencial_id confirmado: ' ZAPI_CREDENCIAL_ID
docker exec -i "$PG_CONTAINER" psql -X -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
  -v credencial="$ZAPI_CREDENCIAL_ID" <<'SQL'
BEGIN READ ONLY;
SET LOCAL statement_timeout='5s';
SELECT c.id AS canal_id, c.tipo, c.ativo AS canal_ativo, cc.id AS credencial_id,
       cc.ativo AS credencial_ativa, cc.token_ref, cc.vigente_desde, cc.vigente_ate
FROM canal c JOIN canal_credencial cc ON cc.canal_id=c.id WHERE cc.id=:'credencial'::uuid;
ROLLBACK;
SQL
```

**Somente HML isolada e vazia**, antes de conectar numero/criar lead/enviar qualquer coisa:

```bash
docker exec -i "$PG_CONTAINER" psql -X -U "$DB_USER" -d "$DB_NAME" \
  -v banco_esperado="$DB_NAME" -v credencial_id="$ZAPI_CREDENCIAL_ID" \
  -v confirmar=HOMOLOGACAO_ISOLADA \
  < docker/provisionamento/ativar-canal-z-api-homologacao.sql
```

O script exige visibilidade integral, recusa **qualquer** lead, atendimento, programada,
outbox ou entrada, trava brevemente as tabelas para evitar criacao concorrente e ativa o
canal/credencial Z-API. Outros canais HML ficam inativos; suas credenciais/UUIDs/campos ficam
intactos. Estado anterior auditado. Nao apague dados para contornar a protecao.
**Nao usar esse script na Femina real.** Ele nao migra operacao nem troca envs/provedor.

## 3. Segredos e bloco completo de envs do recurso

**VPS, diretorio privado fora do checkout**, nao usar terminal com gravacao/`set -x`:

```bash
umask 077
SEGREDOS_DIR="$(mktemp -d /root/synapse-zapi-hml.XXXXXXXX)"
openssl rand -base64 48 | tr '+/' '-_' | tr -d '=\n' > "$SEGREDOS_DIR/callback.secret"
openssl rand -base64 48 | tr '+/' '-_' | tr -d '=\n' > "$SEGREDOS_DIR/repasse.secret"
chmod 600 "$SEGREDOS_DIR/"*.secret
```

Sao dois segredos **gerados distintos**. Token de instancia e Client-Token sao **reais
fornecidos pela Z-API**, nao os gerados acima. Transferir pelo gerenciador/painel autorizado,
nao via chat/ticket/print/git. Nao colocar token da Z-API, Client-Token ou SYNAPSE_TOKEN_INTERNO
como segredo de callback. Se o suporte confirmar header nativo, preferi-lo e homologar antes:
nome exato, formato do valor, onde configurar, escopo, rotacao e entrega real; nao presumir.
A ativacao/rotacao de Client-Token afeta todas as instancias Z-API da conta; nao alterar
essa configuracao global sem inventario e autorizacao dos outros consumidores.

**Dokploy → stack HML → Environment. Mesclar, nao substituir todas as envs existentes.**
Bloco abaixo e um **template**, nao pode ser aplicado com placeholders. Banco, storage privado,
JWT, token interno, dominio, timezone, rede e tag da stack existente continuam obrigatorios.
Nao se cria env de runtime para `telefone`: ele foi usado apenas no cadastro SQL.

```dotenv
# REAIS / OPERADOR: dominio HML sem https:// e prefixo de router exclusivo dessa stack
SYNAPSE_DOMINIO=<DOMINIO_HML_REAL>
TRAEFIK_ROUTER_PREFIX=<PREFIXO_HML_REAL>
SYNAPSE_IMAGE_TAG=944536c
# HML apenas: muda o seletor. Producao permanece uzapi-autotic ate plano de corte aprovado.
WHATSAPP_PROVEDOR=z-api
WHATSAPP_NUMERO=<INSTANCE_ID_REAL>
Z_API_INSTANCIA_ID=<MESMO_INSTANCE_ID_REAL>
Z_API_CREDENCIAL_ID=<UUID_CONFIRMADO_NO_BANCO_HML>
Z_API_TOKEN=<TOKEN_REAL_DA_INSTANCIA_Z_API>
Z_API_CLIENT_TOKEN=<CLIENT_TOKEN_REAL_DA_CONTA_Z_API>
# Opt-ins HML apos cadastro/proxy. Defaults em outras instancias: false/false/DESATIVADO.
Z_API_HABILITADO=true
Z_API_WEBHOOK_HABILITADO=true
Z_API_WEBHOOK_AUTENTICACAO=SEGREDO_URL
# GERADO: conteudo de callback.secret, exclusivo do callback
Z_API_WEBHOOK_SEGREDO=<SEGREDO_GERADO_CALLBACK>
# DEFAULT: vazio enquanto nome nativo nao confirmado
Z_API_WEBHOOK_HEADER_NOME=
# DEFAULTS: nao aumentar concorrencia/limites sem medir
Z_API_LIMITE_RESPOSTA_BYTES=4096
Z_API_WEBHOOK_LIMITE_CORPO_BYTES=1048576
Z_API_WEBHOOK_NAMESPACE_STATUS=NAO_CONFIRMADO
Z_API_MIDIA_ENTRADA_CONCORRENCIA=1
WHATSAPP_TIMEOUT=10s
OUTBOX_CONCORRENCIA=4
# REAL: hosts CDN exatos comprovados; CSV sem protocolo/caminho/curinga.
# Vazio inicialmente recusa midia; nao autorizar hosts por tentativa nem usar *.z-api.io.
Z_API_MIDIA_HOSTS_PERMITIDOS=
# PROXY: default NONE; HML atras de Traefik confiavel precisa NATIVE para reconhecer HTTPS.
SERVER_FORWARD_HEADERS_STRATEGY=NATIVE
# DEFAULT OFF: ligar so depois do aceite da equipe de automacao
Z_API_REPASSE_AUTOMACAO_HABILITADO=false
AUTOMACAO_WEBHOOK_EVENTOS_URL=
AUTOMACAO_WEBHOOK_REPASSE_TOKEN=
```

WHATSAPP_NUMERO e instanceId, **nao o telefone e nao a credencial UUID**. Conferir igualdade
literal com Z_API_INSTANCIA_ID. Nao apagar envs antigas: preservar rollback seguro.
NATIVE exige backend sem porta publica e proxy confiavel que sobrescreva protocolo; nao
aceitar forwarded headers arbitrarios da internet. Se nao houver essa garantia, nao expor.
HML em proxy com IP fora do trust padrao exige revisao dessa configuracao, nao bypass HTTPS.

Depois do repasse HML homologado, preencher so no ambiente HML:

```dotenv
Z_API_REPASSE_AUTOMACAO_HABILITADO=true
AUTOMACAO_WEBHOOK_EVENTOS_URL=<URL_HTTPS_REAL_DO_WORKFLOW_HML_PREPARADO>
AUTOMACAO_WEBHOOK_REPASSE_TOKEN=<SEGREDO_GERADO_REPASSE>
```

O n8n valida **X-Synapse-Webhook-Token**, contrato do CRM, antes do workflow e deduplica
**X-Synapse-Evento-Id**. Nao e header de callback da Z-API. API interna conserva
X-Synapse-Token e Idempotency-Key existentes. Adaptar nos diretos Uzapi separadamente.

## 4. Dokploy / Compose Path e Traefik

**VPS, somente leitura:** escolher container Traefik da lista do passo 1, nao reinicia-lo.

```bash
read -rp 'Container Traefik: ' TRAEFIK_CONTAINER
docker inspect "$TRAEFIK_CONTAINER" --format '{{.Config.Image}}'
docker exec "$TRAEFIK_CONTAINER" traefik version
```

As opcoes por router `observability.accessLogs` e `observability.tracing` estao documentadas
na serie **3.3**. Este roteiro exige **Traefik >=3.3 da serie 3**, sem prometer compatibilidade
com 2.x/3.0–3.2/major futura. Conferir a documentacao **da versao instalada** e testar o router;
isso nao autoriza atualizar Traefik. Versao inferior/incompativel: **STOP na exposicao**,
pedir plano de infraestrutura/redacao separado; cadastro e testes locais continuam possiveis.
Nao confiar apenas em `docker compose config`: ele nao valida o schema dinamico do Traefik.

As labels dos dois webhooks fazem parte de `docker/dokploy-stack.yml`, usado como Compose Path
unico. O Dokploy deve continuar em modo Stack apontando para esse arquivo; nao configurar Command
customizado nem copiar labels para Domains/Raw Compose. Depois do merge, um deploy normal da HML
aplica a versao declarativa. Os overrides antigos estao vazios e permanecem somente para que
comandos antigos ainda funcionem. A regra tem prioridade antes do router geral, cobre os dois
paths e nao publica `/internal/v1`.

**VPS, checkout/config HML com envs carregadas de forma privada**, validar sem despejar envs:

```bash
docker compose -f docker/dokploy-stack.yml config --quiet
docker stack config -c docker/dokploy-stack.yml >/dev/null
```

So depois aplicar **deploy manual da HML** no Dokploy. Nao aplicar na stack Femina.
**VPS, verificar labels do servico HML sem consultar Env:**

```bash
read -rp 'Servico backend HML real: ' BACKEND_HML_SERVICE
docker service inspect "$BACKEND_HML_SERVICE" --format '{{json .Spec.Labels}}'
docker service inspect "$BACKEND_HML_SERVICE" --format '{{json .Endpoint.Spec.Ports}}'
```

Verificar router efetivamente aceito no dashboard/config dinamica do Traefik; labels
presentes no Docker nao provam router aceito. Sem alertas `field not found: observability`,
404 do frontend nem 502. Inspecionar logs **localmente**, sem exportar linhas cruas.
Conferir todos os outros saltos (CDN/WAF/LB/APM) antes do primeiro segredo real na URL.
Tomcat accesslog e OFF por default e seu pattern nao inclui query; nao habilitar DEBUG HTTP.

**VPS, HML, canario publico falso (nao o segredo real), sem efeitos:**

```bash
read -rp 'Dominio HTTPS HML, sem protocolo: ' HML_DOMINIO
CANARIO="ZAPI_CANARIO_$(openssl rand -hex 24)"
curl -s --max-time 10 -o /dev/null -w 'HTTP %{http_code}\n' \
  -H 'Content-Type: application/json' --data '{}' \
  "https://$HML_DOMINIO/webhook/z-api?secret=$CANARIO"
```

Esperado **403**, nunca frontend/redirect/200. Pesquisar esse marcador somente no
ambiente operador em access log, tracing/APM/WAF/app, retornando apenas encontrado/ausente.
Exemplo que nao imprime o log:

```bash
if docker logs --since 2m "$TRAEFIK_CONTAINER" 2>&1 | grep -Fq "$CANARIO"; then
  printf 'FALHA: canario encontrado; nao cadastrar segredo real\n'
else
  printf 'Canario ausente no stdout do Traefik; conferir tambem arquivo/APM/WAF/app\n'
fi
```

Ausencia nesse stdout nao comprova outros destinos. Se nao conseguir verificar a cadeia
inteira, **STOP**. Rotacionar segredo se uma URL real tiver sido registrada em qualquer log.

## 5. Painel Z-API — callbacks exatos, somente instancia controlada

Documentacao oficial: **Instancias → olho/Visualizar → tres pontos → Editar**.
O nome/layout pode variar por versao do painel: identificar os campos abaixo; nao
substituir cegamente todos os callbacks nem alterar instancia Femina.

Mesma URL nos tres campos, modo SEGREDO_URL:
`https://<DOMINIO_HML_REAL>/webhook/z-api?secret=<SEGREDO_GERADO_CALLBACK>`.
Os placeholders nao sao literais. A URL preenchida e uma credencial: copiar apenas no
painel autorizado; nao anexar print, colar em terminal gravado ou enviar no chat.
Nao adicionar `/receive`, `/delivery` ou `/status` ao endpoint CRM.

| Campo/evento no painel | Contrato para conferir se o rotulo variar | Corpo de configuracao via API | Evento entregue ao CRM |
|---|---|---|---|
| Ao receber / Receive | PUT `/instances/{instanceId}/token/{token}/update-webhook-received` | `value`: URL completa protegida | ReceivedCallback |
| Ao enviar / Delivery | PUT `/instances/{instanceId}/token/{token}/update-webhook-delivery` | `value`: mesma URL | DeliveryCallback |
| Status da mensagem / Status | PUT `/instances/{instanceId}/token/{token}/update-webhook-message-status` | `value`: mesma URL | MessageStatusCallback |

Esses PUTs sao configuracao de **saida do painel para API** e usam Client-Token.
Os callbacks entregues sao **POST JSON** e usam o segredo da URL; nao presumir que
tenham Client-Token. Nao usar os endpoints PUT como URL de callback do CRM.
Salvar e reabrir a edicao, conferindo privadamente que `?secret=` foi mantido inteiro.
A prova final e a entrega autenticar no CRM, nao apenas o formulario aceitar.

Para testar eco, habilitar **notificar enviadas por mim** somente na instancia HML.
Contrato: PUT `/update-notify-sent-by-me`, body `{"notifySentByMe":true}`; requer Ao receber
configurado. O endpoint combinado documentado e `/update-webhook-received-delivery`,
mas nao e necessario usa-lo nem substituir os tres campos. `fromMe` nao cria outra
mensagem nem dispara IA. Nao cadastrar conexao/desconexao/chat-presence/grupos/chamadas
nessa rota: nao fazem parte do escopo implementado.

Se o nativo for confirmado: `HEADER_NATIVO`, `Z_API_WEBHOOK_HEADER_NOME` confirmado,
segredo dedicado no mecanismo nativo e URL **sem query**. Rodar todos os negativos de novo.
Nao inventar nome/prefixo de header. Nao manter dois modos como fallback permissivo.

## 6. Homologacao em sequencia — somente numero controlado

Antes: criar usuarios HML com permissoes reais, identificar um remetente controlado externo,
registrar janela UTC e baseline do inventario. Usar a tela real CRM e o cliente WhatsApp de
teste; nada para pacientes. Cada item so libera o seguinte depois da evidencia esperada.

1. **HTTPS/protecao**: POST `{}` sem query e com segredo errado →403, nenhuma entrada/fila.
   URL correta com JSON truncado `{` →400; JSON sem instanceId →403, ambos sem gravacao. Via HTTP nao autentica (pode
   redirecionar no proxy; nao seguir redirect com segredo). Nao usar `curl -v` nem `-L`.
   Para enviar a URL correta sem segredo em argv/historico, **VPS HML**:

   ```bash
   WEBHOOK_SECRET="$(<"$SEGREDOS_DIR/callback.secret")"
   printf 'url = "https://%s/webhook/z-api?secret=%s"\n' "$HML_DOMINIO" "$WEBHOOK_SECRET" \
     | curl --config - -s --max-time 10 -o /dev/null -w 'HTTP %{http_code}\n' \
       -H 'Content-Type: application/json' --data '{'
   unset WEBHOOK_SECRET
   ```

   403 com segredo correto: conferir igualdade, flags, canal ativo, HTTPS/trust e query
   preservada; nao desabilitar filtro. 404: router/caminho/release; 502: backend/health.
   Nao mandar payload de mensagem falso para provar conectividade em producao.
2. **Receber texto real de teste**: enviar do numero controlado para instancia HML.
   Esperado uma entrada processada, um lead/ciclo na credencial Z-API, uma mensagem e
   atualizacao sem F5. Se nenhuma entrada, conferir os tres campos, entrega HTTPS e
   canario; se entrada pendente, worker/provedor/instancia; se descartada, tipo/phone/LID.
3. **Responder pelo CRM**: uma mensagem com acento/emoji/quebra de linha. Esperado um
   despacho e IDs preservados. HTTP200+IDs fecha outbox mesmo com PENDENTE: **nao clicar
   reenviar por falta de check**. DeliveryCallback prova processamento, nao leitura pelo cliente.
4. **Status e IDs**: no numero receptor, receber/ler a mensagem; coletar diagnostico abaixo.
   Comparar **literalmente** ids[] com os namespaces WHATSAPP/PROCESSAMENTO persistidos
   do aceite. Default NAO_CONFIRMADO nao altera checks por chute. Se inequivoco, configurar
   namespace comprovado na HML e fazer **novo envio controlado**, sem replay do anterior.
   IDs iguais nos dois namespaces nao distinguem qual e o contrato: obter outra evidencia
   ou confirmacao do fornecedor. Orfao/conflitante fica diagnosticavel, nao vincular por telefone.
5. **Eco/duplicacao/ordem**: enviadas por mim nao cria segunda mensagem/IA. Forcar repeticao/
   ordem invertida/callback precoce apenas no harness local ja testado; em homologacao observar
   ocorrencias reais, sem fabricar/reprocessar eventos de cliente nem induzir retry de POST aceito.
   Recarregar/reconectar mantem estado monotono. Ausencia de callback nao autoriza novo despacho.
6. **Descobrir CDN com seguranca**: receber uma imagem de teste com allowlist vazia;
   entrada e aceita, bytes recusados como indisponiveis. Coletor retorna **somente host**.
   Confirmar host HTTPS publico pertencente ao fluxo documentado/conta com fornecedor; nao
   liberar curinga, IP privado ou qualquer host que venha num evento. Preencher CSV exato em
   Z_API_MIDIA_HOSTS_PERMITIDOS na HML, aplicar configuracao HML e mandar **novo arquivo**.
   O primeiro arquivo recusado nao e automaticamente recuperado; nao replay.
7. **Matriz de midia**, em ambos os sentidos: imagem PNG/JPEG, audio gravado e anexado,
   video MP4, PDF e **PNG/JPEG enviados como documento**. Validar tipo DOCUMENTO preservado,
   nome/MIME, bytes/hash com original sintetico, preview/reproducao/download autenticado,
   recarga, permissao de outro atendente negativa. 410 aqui e **download da URL Z-API**,
   nao resolvedor mediaId Uzapi. Nao inventar refresh/fallback; 403 CDN/host: diagnosticar
   hostname, DNS publico, codec/MIME e permissao, sem compartilhar URL assinada.
8. **Falhas**: rede lenta/5xx/arquivo invalido em testes locais; na conta real nao provocar
   carga/indisponibilidade. Confirmar UI nao trava, resumo/dashboard/provedor isolados;
   erro transitorio segue politica existente, timeout de envio ambiguo nao redispara.
9. **Automacao HML**, so depois de adaptar workflow: ativar repasse com token distinto,
   parser Z-API e dedup antes de efeitos. Uma entrada →uma resposta pela API interna do
   CRM. Nao enviar direto na Z-API e chamar `/responder` de novo para registrar. Equipe
   revisa todos os nos que chamam Uzapi diretamente; mudar env do CRM nao os migra.
10. **Aceite**: medir latencia, filas, pool, memoria/disco com arquivos representativos sem
    teste de carga em producao. Registrar SHA/digests, IDs/horarios/estados, resultados por
    tipo; nenhuma credencial, URL temporaria, telefone, historico ou corpo na evidencia.

**VPS HML, diagnostico somente leitura por janela curta (IDs privados):**

```bash
read -rp 'Inicio UTC, ex. 2026-10-25T22:00:00Z: ' INICIO_UTC
read -rp 'Fim UTC exclusivo: ' FIM_UTC
docker exec -i "$PG_CONTAINER" psql -X -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
  -v instancia="$ZAPI_INSTANCIA" -v inicio="$INICIO_UTC" -v fim="$FIM_UTC" \
  < docs/diagnosticos/coletar-z-api-homologacao.sql
```

O coletor retorna tipos/nomes de campos, horarios, IDs sanitizados, correlacao e hosts
HTTPS, nunca path/query/telefone. Limite 100 nao prova ausencia de outras linhas; escolher
janela menor quando atingir o limite. Nao exportar webhook_entrada.payload ou ultimo_erro.

## 7. Plano SEPARADO para Femina / Uzapi — nao executar o corte ainda

Somente apos homologacao integral + release publicada + aceite da operacao/automacao:

| Inventario | Decisao/procedimento permitido | Condicao para corte |
|---|---|---|
| Atendimentos abertos com credencial Uzapi | Encerrar pelo fluxo normal autorizado apos conclusao real e abrir novos ciclos na credencial Z-API. Se precisam continuar abertos, solicitar desenho adicional de transicao de canal, nao reescrever FK | Zero ciclos antigos que ainda precisem enviar, ou solucao especifica implementada/homologada |
| Outbox envio Uzapi | Drenar no provedor de origem antes da troca. Aceitos/PENDENTE/ambiguos/esgotados precisam conciliacao individual | Nenhum despacho incerto/em voo, nao apenas count de pendentes zero |
| Webhook Uzapi e repasse n8n | Drenar e conciliar antes; seletor global nao processa callbacks tardios do provedor anterior | Mecanismo comprovado de drenagem/retencao; sem duplo processamento |
| Programadas CRM AGENDADA | Decisao por UUID: manter/cancelar explicitamente no fluxo autorizado/reagendar apos novo ciclo com consentimento | Nenhuma programada vai trocar de provedor/destino silenciosamente |
| Timers/filas/nos diretos n8n | Equipe de automacao inventaria, suspende com mecanismo proprio comprovado, adapta payload/autenticacao/reservas/registro | Nenhum no antigo dispara em paralelo; token/parser/dedup homologados |
| Citacoes a mensagens Uzapi | Preservar historico; nao enviar ID de outro provedor como referencia Z-API | Validar rejeicao/feedback ou nova mensagem sem referencia escolhida explicitamente |

Nao existe nesta entrega pausa nova de dispatcher por canal. **Nao usar intervalo alto,
zerar replica do backend ou desligar Atendimentos como substituto de pausa segura.** Se nao
ha mecanismo operacional comprovado para suspender produtores e conciliar chamadas em voo,
esse e um bloqueio concreto do corte, nao motivo para pular a etapa. A aba Atendimentos nao
pode cair em horario comercial. Cadastro inativo e homologacao podem avancar independentemente.

Programadas: a listagem limitada nao resolve tudo. Para pagina seguinte, repetir a projecao
do inventario com `WHERE status='AGENDADA' AND (data_envio,id) > (:ultima_data,:ultimo_id)`
usando parametros psql tipados, sem buscar conteudo; nao cancelar por SQL. Mais de um produtor
exige confirmacao de cada responsavel. Nao limpar despacho_ambiguo/esgotado ou editar payload.

No futuro corte aprovado, registrar novo canal/UUID **no banco Femina** pelo cadastro inativo;
UUID de HML nao e portavel. Ativacao produtiva/versionamento sera revisada contra o inventario
concreto; o script de ativacao HML **nao serve**. Nao reutilizar ID Uzapi nem copiar tokens para
o banco. Este roteiro nao entrega comando cego de corte que ignore esses bloqueios.

### Rollback a preparar antes de qualquer corte

Conservar configuracao/credencial/imagem anterior e a release com guardas de origem. Suspender
novos produtores com mecanismo comprovado, conciliar Z-API aceitos/ambiguos e registrar IDs.
So entao restaurar seletor/identidade anteriores explicitamente. **Nunca reenviar pela Uzapi
uma mensagem aceita pela Z-API.** Nao reativar esgotados/alterar snapshots; callbacks tardios e
programadas exigem a mesma conciliacao. Historico/midia/credenciais permanecem preservados.
Voltar binario antigo sem guardas exige filas reconciliadas, nao apenas rollback de env.

## Fontes verificadas e divergencias

- Repositorio oficial fornecido pelo usuario, snapshot
  [`0b8d420f1f592a7b5e55d28f3e9412ed785880d6`](https://github.com/Z-API/z-api-docs/tree/0b8d420f1f592a7b5e55d28f3e9412ed785880d6):
  `docs/quickstart/authentication.md`, `security/client-token.md`, `webhooks/introduction.md`,
  `on-message-received-notify-fromme.md`, `update-notify-sent-by-me.md`, `webhooksConf/*`.
  Leitura estatica, nenhum npm/script do repositorio executado.
- [Recebimento atual](https://developer.z-api.io/webhooks/on-message-received),
  [envio](https://developer.z-api.io/webhooks/on-message-send),
  [status](https://developer.z-api.io/webhooks/on-whatsapp-message-status-changes),
  [Client-Token](https://developer.z-api.io/security/client-token).
- O arquivo antigo `webhooksConf/update-webhook-receive.md` mistura `receive` e `received`;
  o contrato atual e o topo do proprio arquivo usam **update-webhook-received**. Nao reproduzir
  o typo nem confundir endpoint de configuracao com endpoint CRM. Alguns exemplos antigos
  duplicam instanceId; o CRM recusa chaves duplicadas, nao usar essas fixtures literalmente.
- Nenhum nome/configuracao de header de autenticacao de CALLBACK foi confirmado nesses arquivos.
  Nao concluir que Client-Token de SAIDA autentica entrada. Segredo URL proprio nao e assinatura
  nativa nem garante integridade criptografica do corpo; posse do segredo permite forjar eventos.
- [Traefik v3.3 observability por router](https://doc.traefik.io/traefik/v3.3/routing/routers/#observability)
  e [Dokploy Compose/Stack, Environment e Advanced Command](https://docs.dokploy.com/docs/core/docker-compose).
  Versao/UI/proxy instalados ainda precisam conferencia do operador. Nao foram acessados.

Resultados locais/CI desta entrega ficam no progresso e na PR. Testes locais com PostgreSQL e
transportes simulados nao substituem homologacao da conta, query preservada, CDN, codecs, IDs
e workflows publicados. Nenhuma operacao de producao foi realizada.
