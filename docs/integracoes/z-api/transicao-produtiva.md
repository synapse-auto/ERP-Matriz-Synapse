# Z-API — transicao no banco existente, sem encerrar atendimentos

26/10/2026. Prevalece sobre a proposta de HML vazia de #326 para a transicao produtiva
explicitamente autorizada. O numero ja foi conectado pelo operador; o agente nao conecta,
configura callbacks, envia mensagens, faz merge ou deploy. Testar somente com contato proprio.
Instrucoes Bash na VPS, nao PowerShell. Segredos nunca devem ser enviados ao chat.

## Diagnostico urgente de Client-Token e callback (26/10/2026)

A referência oficial de [POST /send-text](https://developer.z-api.io/message/send-text) e
[GET /status](https://developer.z-api.io/instance/status) mostra `Client-Token` nos exemplos
de ambas as operações. A página oficial de [Token de Segurança da conta](https://developer.z-api.io/security/client-token)
explica que, depois de ativado, todas as instâncias só aceitam chamadas com esse header.
Não documenta uma exceção de autenticação por endpoint. O adaptador do CRM aplica
`Z_API_ENVIAR_CLIENT_TOKEN` tanto a `GET /status` quanto a `POST /send-text`.

O operador confirmou posteriormente o mesmo HTTP 400 tanto no GET quanto no POST sem
header (`your client-token is not configured`). A opção false omite o header corretamente,
mas não superou essa exigência da conta. Não usar token fictício nem fallback após 401/403.
Essa recusa explícita de token no HTTP 400, assim como 401/403 e os erros de contrato 405/415,
agora encerra a tentativa como recusa permanente, não como aceite ou ambiguidade.
Timeout, 429, 5xx, 400 desconhecido e 2xx inválido continuam sem retry automático após POST.
Após atualizar o
backend, o log seguro `Z_API_FALHA` registra somente etapa, HTTP e código sanitizado, por
exemplo `GET_STATUS / 400 / CLIENT_TOKEN_NOT_CONFIGURED` ou
`POST_SEND_TEXT / 400 / FORNECEDOR_SEM_CODIGO`; nunca registra texto de erro, corpo, token,
telefone ou URL do fornecedor.

O 403 do callback agora é diagnosticado sem reduzir a proteção. `Z_API_WEBHOOK_REJEITADO`
registra apenas etapa, HTTP e motivo enumerado. `FILTER / HTTPS_NAO_RECONHECIDO` indica que
o servlet não reconheceu TLS (proxy/forwarded headers); `AUTH / SEGREDO_INVALIDO` indica que
TLS foi aceito, mas o segredo recebido não corresponde; `PAYLOAD / INSTANCIA_DIVERGENTE` ou
outros códigos `PAYLOAD` indicam que filtro e segredo passaram, mas a validação do evento
falhou. `CONTROLLER / AUTENTICACAO_AUSENTE` indica divergência de cadeia/configuração.
O filtro continua fail-closed, precede MVC, remove query e headers sensíveis antes dos
próximos filtros e só confia em `request.isSecure()` configurado pelo container. Não aceite
`X-Forwarded-Proto` cru no filtro nem desligue HTTPS/segredo para obter 200.

**VPS, somente leitura, depois do deploy do SHA revisado:**

```bash
docker service logs --since 15m fmnaprod-uzapi-36acu4_backend 2>&1 \
  | grep -E 'Z_API_FALHA|Z_API_WEBHOOK_REJEITADO' | tail -n 40
```

Interprete apenas as linhas enumeradas; não use `--raw`, não colete o ambiente completo e
não compartilhe URL de callback. Para o callback, um `403` acompanhado por
`FILTER/HTTPS_NAO_RECONHECIDO` pede revisar se o tráfego chega ao backend pela rota TLS
persistente do Traefik e se o container está reconhecendo o forwarded proto com
`SERVER_FORWARD_HEADERS_STRATEGY=NATIVE`. `AUTH/SEGREDO_INVALIDO` pede conferir o segredo
armazenado no Dokploy e o valor cadastrado no painel, sem imprimi-los. Ausência de uma linha
não prova que a requisição chegou a esse serviço.

O teste de integração existente exercita Tomcat embutido com forwarded proto HTTPS e prova
que o filtro aceita TLS reconhecido e recusa HTTP/forwarded ausente. Isso não confirma a
configuração real da rede Docker/Traefik da Fêmina; a linha enumerada do serviço após o novo
deploy é a evidência necessária para distinguir os dois 403. Nenhum comando abaixo altera
produção ou envia mensagem.

Auditoria completa, matriz por tipo/direção e limites da evidência:
[auditoria do fluxo de mensagens e mídias](./auditoria-fluxo-mensagens-midias-2026-10-26.md).

## A. Proximo passo, somente leitura

**VPS**, depois do deploy anunciado pelo operador, conferir imagem efetiva:

```bash
docker service inspect fmnaprod-uzapi-36acu4_backend \
  --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}'
docker service ps fmnaprod-uzapi-36acu4_backend \
  --format '{{.ID}} {{.Image}} {{.CurrentState}}'
```

Nao trocar ainda WHATSAPP_PROVEDOR. #325 entrega runtime protegido; #326 cadastro inativo.
A release precisa conter tambem esta transicao/V113, aprovada no CI/publicada. Guardar
SHA/digest anterior e confirmar backup recuperavel pelo procedimento operacional existente.
Nao recriar banco/volumes nem copiar pacientes para testes.

Se nao houver checkout na VPS, obter copia dedicada da release revisada, sem executar
scripts durante download. **VPS**, depois de receber SHA completo aprovado/publicado:

```bash
read -rp 'SHA completo da release aprovada (40 hex): ' RELEASE_SHA
[[ "$RELEASE_SHA" =~ ^[0-9a-f]{40}$ ]] || { printf 'SHA invalido\n'; exit 1; }
ROTEIRO_DIR="$(mktemp -d /root/synapse-z-api-transicao.XXXXXX)"
git clone --depth 1 --no-checkout https://github.com/synapse-auto/ERP-Matriz-Synapse.git "$ROTEIRO_DIR"
git -C "$ROTEIRO_DIR" fetch --depth 1 origin "$RELEASE_SHA"
git -C "$ROTEIRO_DIR" checkout --detach "$RELEASE_SHA"
test "$(git -C "$ROTEIRO_DIR" rev-parse HEAD)" = "$RELEASE_SHA"
cd "$ROTEIRO_DIR"
```

Nao usar tag latest mutavel ou baixar script por pipe direto no psql. Revisar os arquivos
antes das etapas com escrita. Clone dedicado nao modifica o checkout de deploy do Dokploy.

Baseline informado pelo operador, nao medicao independente do agente: schema V110, imagem
61a7c6f; 81 ciclos ancorados na Uzapi + 14 sem canal/credencial; 80 envios e 6.866 repasses
nao publicados, todos esgotados; nenhuma programada AGENDADA. Nao comprova ausencia de
envios externos/n8n em voo. Repetir inventario na janela de corte, sem exportar payload.

**VPS, checkout revisado da release**, preencher identificadores nao secretos:

```bash
set -euo pipefail
set +x
read -rp 'Container Postgres FMNA atual: ' PG_CONTAINER
read -rp 'Usuario de manutencao do Postgres: ' DB_USER
DB_NAME=fmna_db
docker exec -i "$PG_CONTAINER" psql -X -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" \
  < docs/diagnosticos/inventario-corte-z-api.sql
```

Confirmar banco/BYPASSRLS, migrations, credencial antiga, ciclos por ancora, filas por origem,
ambiguos/despachados e programadas. IDs privados. Nao deduzir zero por RLS ou exportar envs.

## B. Cadastro separado INATIVO

O script de cadastro ja funciona em banco com dados. Nao usar
ativar-canal-z-api-homologacao.sql nem retirar sua protecao HOMOLOGACAO_ISOLADA.

**VPS**, informar instanceId do painel e telefone produtivo internacional, privadamente:

```bash
read -rp 'instanceId da Z-API conectada: ' ZAPI_INSTANCIA
read -rp 'Telefone produtivo internacional, somente digitos: ' ZAPI_TELEFONE
read -rp 'Rotulo administrativo do novo canal (1–50 caracteres): ' ZAPI_CANAL_NOME
docker exec -i "$PG_CONTAINER" psql -X -U "$DB_USER" -d "$DB_NAME" \
  -v banco_esperado="$DB_NAME" -v canal_nome="$ZAPI_CANAL_NOME" \
  -v instancia_id="$ZAPI_INSTANCIA" -v telefone="$ZAPI_TELEFONE" \
  < docker/provisionamento/cadastrar-canal-z-api.sql
```

Essa chamada simula/ROLLBACK. Apos conferir, confirmar somente cadastro:

```bash
docker exec -i "$PG_CONTAINER" psql -X -U "$DB_USER" -d "$DB_NAME" \
  -v banco_esperado="$DB_NAME" -v canal_nome="$ZAPI_CANAL_NOME" \
  -v instancia_id="$ZAPI_INSTANCIA" -v telefone="$ZAPI_TELEFONE" -v confirmar=CADASTRAR \
  < docker/provisionamento/cadastrar-canal-z-api.sql
unset ZAPI_TELEFONE
read -rp 'UUID credencial_id retornado pela chamada CADASTRAR: ' ZAPI_CREDENCIAL
read -rp 'UUID da credencial Uzapi confirmado no inventario: ' UZAPI_CREDENCIAL
```

Usar UUID persistido, nunca da simulacao. Idempotente para mesmos parametros; conflito
aborta sem adotar identidade anterior. Banco guarda env://Z_API_TOKEN, nunca token.
Credencial/canal Uzapi e historico preservados. Nao reexecutar provisionador Meta.

## C. Pausa curta de SAIDA, nao da aba Atendimentos

**Dokploy, envs FMNA:** primeiro aplicar release desta transicao mantendo configuracao
Uzapi e Z_API_HABILITADO=false, com CANAL_TRANSICAO_SAIDAS_SUSPENSAS=true. Nao parar
backend/Postgres/Redis/MinIO. Confirmar todas as tasks ativas nessa release/pausa, nenhuma
task antiga rodando, health saudavel. Boot normal aplica V111/V112/V113 porque V73 ja
esta aplicada; runner V73 nao serve para esta transicao. Conferir historico Flyway.

Pausa recusa novo envio com HTTP 409/RFC7807 e rollback inteiro, inclusive eventual assuncao;
o composer recupera o texto recusado na conversa/sessao atual, sem reenviar automaticamente
nem sobrescrever texto novo digitado durante a espera. Falhas ambiguas nao entram nessa regra.
Publisher e job de programadas param. Leitura/login/
recebimento continuam. Pausa nao desfaz HTTP em voo. Coordenar janela/equipe e suspender
produtores externos no n8n pelo mecanismo deles; nao desligar workflows aleatoriamente.

**VPS**, simular PREPARAR e depois confirmar (duas chamadas, conferir a primeira):

```bash
docker exec -i "$PG_CONTAINER" psql -X -U "$DB_USER" -d "$DB_NAME" \
  -v banco_esperado="$DB_NAME" -v origem="$UZAPI_CREDENCIAL" -v destino="$ZAPI_CREDENCIAL" \
  -v operacao=PREPARAR < docker/provisionamento/transicionar-canal-z-api.sql
docker exec -i "$PG_CONTAINER" psql -X -U "$DB_USER" -d "$DB_NAME" \
  -v banco_esperado="$DB_NAME" -v origem="$UZAPI_CREDENCIAL" -v destino="$ZAPI_CREDENCIAL" \
  -v operacao=PREPARAR -v confirmar=APLICAR < docker/provisionamento/transicionar-canal-z-api.sql
```

PREPARADA bloqueia tambem pelo banco. Try-lock recusa se ha transacao de envio, nao espera
segurando chat. Nenhum lock e sustentado durante HTTP. Conferir chamadas/filas antigas e
confirmacao da automacao de que nenhum no Uzapi dispara em paralelo antes de avancar.

## D. Envs completas do canal, mantendo pausa

**Dokploy → Environment da FMNA:** preservar banco/storage/JWT/tokens internos/dominios/
flags e envs Uzapi anteriores. Nao substituir arquivo inteiro por este bloco. Preencher
placeholders privadamente; nao sao valores literais nem defaults:

```dotenv
WHATSAPP_PROVEDOR=z-api
WHATSAPP_NUMERO=<INSTANCE_ID_REAL_DO_PAINEL>
Z_API_HABILITADO=true
Z_API_INSTANCIA_ID=<MESMO_INSTANCE_ID>
Z_API_CREDENCIAL_ID=<UUID_PERSISTIDO_NO_PASSO_B>
Z_API_TOKEN=<TOKEN_REAL_DA_INSTANCIA_Z_API>
# Conta com Token de Segurança INATIVO: deixar vazio e omitir Client-Token explicitamente.
Z_API_CLIENT_TOKEN=
Z_API_ENVIAR_CLIENT_TOKEN=false
CANAL_TRANSICAO_SAIDAS_SUSPENSAS=true
CANAL_TRANSICAO_STATUS_UZAPI_LEGADO=true
Z_API_WEBHOOK_HABILITADO=true
Z_API_WEBHOOK_SOMENTE_COLETA=true
Z_API_WEBHOOK_AUTENTICACAO=SEGREDO_URL
Z_API_WEBHOOK_SEGREDO=<SEGREDO_NOVO_EXCLUSIVO_GERADO>
Z_API_WEBHOOK_HEADER_NOME=
Z_API_WEBHOOK_NAMESPACE_STATUS=NAO_CONFIRMADO
Z_API_MIDIA_HOSTS_PERMITIDOS=
Z_API_REPASSE_AUTOMACAO_HABILITADO=false
SERVER_FORWARD_HEADERS_STRATEGY=NATIVE
Z_API_LIMITE_RESPOSTA_BYTES=4096
Z_API_WEBHOOK_LIMITE_CORPO_BYTES=1048576
Z_API_MIDIA_ENTRADA_CONCORRENCIA=1
```

Ultimos tres sao defaults existentes. WHATSAPP_NUMERO e instanceId, nao telefone.
WHATSAPP_WEBHOOK_SECRET continua com segredo Uzapi original, usado so por status antigos
na mesma /webhook/canal; nao substituir pelo novo. Z_API_TOKEN de instancia continua obrigatorio.
Com Token de Segurança da conta ativo, usar Z_API_CLIENT_TOKEN real e
Z_API_ENVIAR_CLIENT_TOKEN=true. Se inativo, somente apos confirmar esse estado no painel,
usar client token vazio e false. Nao ha fallback/retry por 401/403. Esta chave nao altera
autenticacao do webhook (segredo URL exclusivo). Ver [contrato oficial](https://developer.z-api.io/security/client-token).
Novas envs CANAL_TRANSICAO_* com default false nao exigem mudanca nas outras instancias.

**VPS**, gerar somente segredo do callback, sem imprimir:

```bash
umask 077
SEGREDOS_DIR="$(mktemp -d /root/z-api-callback.XXXXXX)"
openssl rand -base64 48 | tr '+/' '-_' | tr -d '=\n' > "$SEGREDOS_DIR/callback.secret"
printf 'Arquivo privado criado em %s/callback.secret\n' "$SEGREDOS_DIR"
```

Transferir por gerenciador/painel autorizado sem compartilhar arquivo/print. Nao reutilizar
tokens. E autenticacao por posse de segredo, nao assinatura nativa/integridade criptografica
do corpo. Header nativo nao confirmado: nao inventar nem aguardar para usar a alternativa aprovada.

## E. Proxy persistente e os tres callbacks

**Atualizacao 26/10/2026:** seguir [webhooks-no-dokploy.md](webhooks-no-dokploy.md) para o
Command persistente e a remocao restrita de Origin. O override agora possui dez labels, nao
apenas as oito citadas no roteiro original. Nao copiar labels manualmente nem usar service
update como fonte; preservar Compose Path base e carregar o override em TODO deploy.

Traefik informado 3.6.7, Dokploy 0.29.13. Schema oficial 3.6 suporta observability por router;
ainda conferir entrypoint/resolver/service reais e configuracao aceita. Nao reiniciar proxy.

**VPS, somente leitura:**

```bash
docker exec dokploy-traefik traefik version
docker service inspect fmnaprod-uzapi-36acu4_backend --format '{{json .Spec.Labels}}'
docker service inspect fmnaprod-uzapi-36acu4_backend --format '{{json .Endpoint.Spec.Ports}}'
```

Backend sem porta 8080 publicada. **Dokploy:** preservar comando Stack atual e acrescentar
-c docker/dokploy-z-api-transicao.override.yml apos compose base. Se campo Command existir,
substitui comando inteiro e Dokploy ja acrescenta docker. Exemplo estrutural:

```text
stack deploy --with-registry-auth -c docker/dokploy-stack.yml -c docker/dokploy-z-api-transicao.override.yml NOME_REAL_DA_STACK_FMNA
```

Se o campo nao aparecer, conferir Source/Compose Type e a versao do Dokploy conforme o
procedimento atualizado; nao improvisar outra fonte de labels. Confirmar PREFIXO-backend, websecure,
letsencrypt e prioridade 150 maior que router geral 100. Regra cobre /webhook/z-api e
/webhook/canal, inclusive sufixos recusados, sem segredo em labels.

**VPS**, checkout/envs carregadas privadamente; validar sem despejar interpolacao:

```bash
docker compose -f docker/dokploy-stack.yml -f docker/dokploy-z-api-transicao.override.yml config --quiet
```

Aplicar manualmente config/release FMNA com pausa true; conferir health/router efetivo.
Labels no Docker nao provam router aceito. Tomcat accesslog OFF/pattern sem query; nao
ativar DEBUG HTTP/APM de corpo/query. Conferir todos os saltos WAF/LB/APM antes do segredo.
**VPS**, canario falso, sem efeitos:

```bash
read -rp 'Dominio CRM FMNA, sem protocolo: ' CRM_DOMINIO
CANARIO="ZAPI_CANARIO_$(openssl rand -hex 24)"
for ROTA in z-api canal; do
  curl -s --max-time 10 -o /dev/null -w 'HTTP %{http_code}\n' \
    -H 'Content-Type: application/json' --data '{}' \
    "https://$CRM_DOMINIO/webhook/$ROTA?secret=$CANARIO"
done
if docker logs --since 2m dokploy-traefik 2>&1 | grep -Fq "$CANARIO"; then
  printf 'STOP: canario apareceu no stdout do proxy\n'
else
  printf 'Ausente aqui; conferir tambem arquivos/APM/WAF/app antes do segredo real\n'
fi
```

Ambos devem retornar 403. Nao seguir redirect com segredo/exportar logs crus. Ausencia nesse stdout nao
prova outros destinos. Vazamento exige corrigir e rotacionar antes de expor.

**Painel Z-API da instancia ja conectada**, mesma URL nos tres campos:
`https://<DOMINIO_FMNA>/webhook/z-api?secret=<SEGREDO_EXCLUSIVO>`.
Preencher privadamente, nao colocar /receive, /delivery ou /status na URL do CRM.

| Campo | Endpoint de CONFIGURACAO documentado, nao URL do callback | Corpo |
|---|---|---|
| Ao receber / Receive | PUT /instances/{instanceId}/token/{token}/update-webhook-received | value: URL completa protegida |
| Ao enviar / Delivery | PUT /instances/{instanceId}/token/{token}/update-webhook-delivery | value: mesma URL |
| Status da mensagem | PUT /instances/{instanceId}/token/{token}/update-webhook-message-status | value: mesma URL |

Salvar/reabrir conferindo query preservada; prova final e callback autenticar, nao formulario.
Notificar enviadas por mim usa update-notify-sent-by-me/notifySentByMe:true. Nao habilitar
grupos/chamadas/outros eventos. Nao remover callback antigo Uzapi: opt-in aceita so status
da identidade original e de mensagem anterior ao corte, nao mensagem/eco/IA Uzapi.

## F. Continuidade: receber primeiro, liberar novos envios depois

Todas as tasks na nova release, seletor Z-API e pausa true, sem workers antigos. Repetir inventario.
Identidade/ciclo inesperados fazem script recusar; nao remover verificacoes para forcar.
**VPS**, simular ATIVAR e confirmar em segunda chamada:

```bash
docker exec -i "$PG_CONTAINER" psql -X -U "$DB_USER" -d "$DB_NAME" \
  -v banco_esperado="$DB_NAME" -v origem="$UZAPI_CREDENCIAL" -v destino="$ZAPI_CREDENCIAL" \
  -v operacao=ATIVAR < docker/provisionamento/transicionar-canal-z-api.sql
docker exec -i "$PG_CONTAINER" psql -X -U "$DB_USER" -d "$DB_NAME" \
  -v banco_esperado="$DB_NAME" -v origem="$UZAPI_CREDENCIAL" -v destino="$ZAPI_CREDENCIAL" \
  -v operacao=ATIVAR -v confirmar=APLICAR < docker/provisionamento/transicionar-canal-z-api.sql
```

Cria rota auditada por ciclo aberto, incluindo sem ancora; FKs historicas, responsaveis,
participantes, mensagens/anexos intactos. Desativa so canal antigo como principal, preserva
credencial antiga e ativa nova. Outboxes novas recebem target snapshot; worker nunca retargeta
despacho antigo. AGENDADA do corte fica retida, sem cancelamento silencioso.

**Coleta inicial antes de processar:** com SOMENTE_COLETA=true, do nosso contato enviar um
texto e uma imagem sintetica. Esperar fila autenticada pendente, nao mensagem na tela ainda.
Rodar coletor abaixo: conferir instanceId e host HTTPS publico real. Confirmar que o host
pertence ao fluxo da conta, configurar CSV exato em Z_API_MIDIA_HOSTS_PERMITIDOS, entao
definir Z_API_WEBHOOK_SOMENTE_COLETA=false mantendo SAIDAS_SUSPENSAS=true e aplicar.
O worker consome os eventos Z-API novos guardados, sem reprocessar Uzapi, baixar antes da
liberacao ou gastar tentativas na coleta. Nao estender essa janela alem dos prazos de
processamento/validade da URL; TTL do fornecedor nao e garantido. Se expirou, nao inventar
refresh. Nao habilitar processamento produtivo com allowlist vazia para descobrir a CDN:
isso poderia tornar arquivos de pacientes indisponiveis durante o teste.

1. Nosso contato envia texto ao numero produtivo: uma entrada processada, mesma conversa/
   ciclo, mesmo responsavel/participantes, tela sem F5. Se falhar, manter pausa:403→HTTPS/query/
   instancia/segredo;404→router/release;502→health; entrada sem mensagem→worker/tipo/phone-LID.
2. Dokploy: mudar so CANAL_TRANSICAO_SAIDAS_SUSPENSAS=false, aplicar/conferir tasks. Operacao
   normal de saida volta nesse ponto: coordenar equipe para testar primeiro nosso contato;
   nao existe modo canario por lead. Nao testar com pacientes.
3. Responder UMA vez pelo CRM no mesmo atendimento: uma outbox/mensagem, UUID de destino, IDs
   zaapId/messageId separados, autoria/permissoes/participacao. Aceite HTTP 200 conserva PENDENTE
   ate prova de status, mas fecha despacho: nao reenviar por falta de check.
4. Nosso receptor recebe/le: comparar ids[] literalmente com aliases pelo coletor. Default
   NAO_CONFIRMADO nao inventa entregue/lido. Namespace WHATSAPP ou PROCESSAMENTO so apos
   evidencia inequivoca; novo envio depois da config, nunca replay. Orfao nao altera outra mensagem.
5. Conferir que o host da CDN aprovado na coleta continua exato e permite baixar apenas
   HTTPS publico. Host divergente exige nova verificacao; nao liberar curinga ou IP privado.
6. Matriz nos dois sentidos: PNG/JPEG, Ogg, MP4, PDF, PNG/JPEG COMO DOCUMENTO. Validar tipo,
   MIME/nome/bytes, preview/reproducao/download autorizado e apos recarga. Negativo de acesso
   no harness, sem usar paciente. Texto/medias indisponiveis nao ganham download falso.
7. Confirmar filas Uzapi intactas, nenhuma duplicacao/eco/IA, acesso/responsaveis preservados
   e latencia normal, sem teste de carga. n8n so depois de aceite da automacao.

**VPS**, coleta sanitizada em janela curta, IDs privados:

```bash
read -rp 'Inicio UTC ISO8601: ' INICIO_UTC
read -rp 'Fim UTC exclusivo ISO8601: ' FIM_UTC
docker exec -i "$PG_CONTAINER" psql -X -U "$DB_USER" -d "$DB_NAME" \
  -v instancia="$ZAPI_INSTANCIA" -v inicio="$INICIO_UTC" -v fim="$FIM_UTC" \
  < docs/diagnosticos/coletar-z-api-homologacao.sql
```

IDs/campos/horarios/status/host sem caminho/conteudo/telefone/segredo. Ao atingir 100 linhas,
reduzir janela; nao representa total. Nao exportar payload.

## G. Legado, automacao e recuperacao

- 80 envios/6.866 repasses esgotados continuam retidos. Nao limpar esgotado/ambiguo, editar
  payload/publicar em lote. Conciliar individualmente no provedor original. Aceito/PENDENTE
  nao e pedido de reenvio; falta de callback nao prova falta de envio. Webhook Uzapi pendente
  nao e importado pelo worker Z-API nem repassado como evento novo.
- Programadas: inventario informou zero, mas ATIVAR captura AGENDADA do corte. Para liberar
  UMA apos revisar destino/autor/data/consentimento, mesma chamada SQL com
  -v operacao=LIBERAR_PROGRAMADA -v programada=UUID_REVISADO -v confirmar=APLICAR; simular
  primeiro. Auditoria individual, nao UPDATE em lote. Mantem conteudo/data/autor; job revalida
  regras de autorizacao. Nao cancela nem troca provedor silenciosamente.
- [Documento para automacao](./documento-para-automacao.md): equipe inventaria/suspende/adapta
  nos Uzapi diretos, timers, reservas e retornos. /internal/v1/atendimentos/{id}/responder
  usa rota nova; HTTP direto nao migra por env. Repasse OFF ate parser/token/dedup homologados.
- Citacao antiga: nao converter wamid Uzapi em Z-API. Referencia nao comprovada recebe 409 e
  preserva historico; usuario decide enviar sem referencia. Nao inventar alias.
- Falha: SUSPENDER com confirmar=APLICAR pelo mesmo script muda para RECUPERACAO e audita;
  API/worker/agenda bloqueiam, leitura continua. Definir tambem pausa env true. HTTP em voo
  nao e cancelado: registrar IDs/horarios e conciliar. ATIVAR nao reativa RECUPERACAO.
- Conservar schema/release/historico. Voltar Uzapi depende de numero conectado/confirmado,
  filas Z-API conciliadas e plano especifico inverso. Nao ha troca inversa automatica nem
  remocao de rotas. Nao restaurar binario anterior a V113 com despachos incertos.

## Decisoes, alcance e fontes

- V113 aditiva, uma transicao por silo nesta etapa. Proximo corte/inversao exige desenho
  explicito, nao sobrescrever trilha. Rota respeita RLS do atendimento, sem query de lead
  fora da Specification; nao muda propriedade/participacao. Try-lock/leituras locais curtas,
  HTTP no worker/outbox existente com CB/bulkhead.
- #326 nao entregava pausa/continuidade/status legado. Cenario novo autoriza complemento,
  nao retirar guardas HML. Comentario antigo PostgresIT sobre boot somente validar nao
  descreve estrategia atual: depois de V73, migrations posteriores sao aplicadas normalmente.
- Conferido [Traefik 3.6](https://doc.traefik.io/traefik/v3.6/reference/routing-configuration/http/routing/observability/),
  [status Z-API](https://developer.z-api.io/webhooks/webhooksConf/update-webhook-message-status),
  repositorio oficial previamente auditado em 0b8d420f1f592a7b5e55d28f3e9412ed785880d6.
  Nao presumido header de autenticacao de callback. URLs devem usar HTTPS.
- CI/testes/SHA registrados na PR/relatorio. Postgres/MinIO locais com HTTP simulado nao
  comprovam query/CDN/namespace/workflow da conta real. Nao declarar corte homologado sem F
  e aceite/inventario da automacao. Nenhuma operacao produtiva executada pelo agente.
