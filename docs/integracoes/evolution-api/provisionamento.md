# Evolution 2.3.7 / Baileys — nova instancia CRM de testes

Nao e migracao da Femina. Nao executar nos bancos/clientes existentes. Nenhuma
etapa abaixo foi executada pelo agente em ambiente externo. Nao habilitar outros
modulos, grupos externos, chamadas ou campanhas para testar o canal.

## Contrato fixado

Imagem informada: `evoapicloud/evolution-api:v2.3.7`. Fonte oficial da tag `2.3.7`:
commit `cd800f2976e1e5b682fbf86a01ee4d85ae61f370`, em
https://github.com/evolution-foundation/evolution-api/tree/2.3.7 .

- `src/api/dto/sendMessage.dto.ts` e `src/api/routes/sendMessage.router.ts`:
  POST `/message/sendText/{instanceName}` com `number`, `text`, `quoted`;
  POST `/message/sendMedia/{instanceName}` com `number`, `mediatype`, `mimetype`,
  `fileName`, `caption`, `media` (Base64) e `quoted`. O multipart nao desserializa
  `quoted` nessa release; o CRM usa JSON com codificacao do spool em fluxo.
- `whatsapp.baileys.service.ts`, `fetchMessages/getMessage`:
  POST `/chat/findMessages/{instanceName}` com `where.key.id/remoteJid`, `offset=1`,
  `page=1`; valida a chave/conversa retornada antes de enviar `quoted.key/message`.
  Nao confiar no fallback silencioso do fornecedor para referencia nao encontrada.
- POST `/chat/getBase64FromMediaMessage/{instanceName}` com
  `message.key` e `convertToMp4=false`. Base64 da resposta e decodificado em spool
  limitado; URL de CDN/mediaKey do callback nao e destino HTTP do CRM. Deteccao de
  bytes, limites, MIME, storage privado e download autorizado continuam locais.
- `src/api/integrations/event/webhook/webhook.controller.ts`: headers configuraveis;
  evento traz `event`, `instance`, `data`, `date_time`, `sender`, `server_url`,
  `destination`, `apikey`. O filtro remove `apikey/destination/server_url` ANTES da
  persistencia/observabilidade/repasse. Nao registrar payload bruto na Evolution.
- `messages.upsert`: `data.key.id/remoteJid/fromMe`, `messageTimestamp`, `message`.
  Texto conversation/extendedTextMessage; image/audio/video/documentMessage;
  buttonsResponseMessage selectedButtonId/selectedDisplayText; listResponseMessage
  title/singleSelectReply.selectedRowId. `contextInfo.stanzaId` e referencia, nunca ID
  proprio. Ecos sao ignorados. Grupos, broadcast e LID-only nao viram telefone.
  Referencia ainda nao encontrada fica em `referenciaExterna` com provedor/instancia/
  namespace/ID; a bolha informa origem indisponivel sem inventar navegacao ou perder
  a escolha recebida.
- A dependencia fixada e Baileys `7.0.0-rc.9`; seu
  [WAProto versionado](https://github.com/WhiskeySockets/Baileys/blob/v7.0.0-rc.9/WAProto/WAProto.proto)
  define tambem templateButtonReplyMessage selectedId/selectedDisplayText e
  interactiveResponseMessage body.text/nativeFlowResponseMessage. O CRM exibe
  body.text e preserva paramsJson no envelope para automacao, sem adivinhar suas
  chaves de negocio, converter enquete em botao ou fabricar um buttonId.
- `messages.update`: `data.keyId`, `remoteJid`, `fromMe`, `status`. SERVER_ACK=ENVIADO,
  DELIVERY_ACK=ENTREGUE, READ/PLAYED=LIDO, ERROR=FALHOU. PENDING/desconhecido nao
  confirmam entrega. O horario de ingresso duravel e usado se nao ha horario do fato.
  Aceite HTTP com key.id registra apenas WHATSAPP, sem inventar processamento ID.

## 1. Conferencias somente leitura (VPS / terminal)

Limite comprovado do fornecedor: `prepareMediaMessage` na tag usa
`sharp(imageBuffer).jpeg()` para imagem comum. CRM/storage/download preservam o
original, mas a imagem entregue pelo WhatsApp pode ter MIME/bytes JPEG diferentes.
Nao se troca IMAGEM por DOCUMENTO para contornar. Documentos (incluindo JPG/PNG
como documento) seguem binarios; confirmar bytes no contato controlado.
Nomes enviados seguem a politica existente do upload CRM: DOC/DOCX preservam
espacos/acentos seguros; os demais formatos substituem caracteres fora do conjunto
ASCII seguro por underscore antes do storage/outbox. A Evolution recebe esse nome
persistido; esta integracao nao muda a politica global dos outros provedores.
Os tetos locais existentes (5 MiB imagem, 16 MiB audio/video, 100 MiB documento)
nao sao uma garantia de entrega do fornecedor. A tag configura JSON ate 136 MiB;
proxies/WhatsApp podem impor outros limites. Nao foram aumentados limites globais.

Na nova stack, substituir apenas o nome do servico, sem exportar envs:

```sh
docker service inspect NOME_DA_NOVA_STACK_backend --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}'
docker service ps NOME_DA_NOVA_STACK_backend --filter desired-state=running --format '{{.ID}} {{.CurrentState}}'
```

No banco novo, conferir versao Flyway e contagens sem conteudo. Nao apagar dados
para passar o guard de homologacao. O runner de migrations vigente deve preparar
o schema antes do backend; nao ha migration nova nesta entrega.

## 2. Cadastrar e ativar somente no banco novo (psql)

Executar a partir de um checkout da PR, onde os dois scripts compartilhados estao
presentes na mesma pasta dos wrappers. `\ir` resolve relativo ao arquivo, nao ao CWD.

```sh
psql -X -d BANCO_NOVO -v banco_esperado=BANCO_NOVO -v canal_nome='Evolution testes' -v instancia_id=NOME_REAL_NO_MANAGER -v telefone=NUMERO_CONTROLADO_INTERNACIONAL -f docker/provisionamento/cadastrar-canal-evolution.sql
```

Acima simula/rollback; UUID exibido em simulacao NAO e utilizavel. Repetir com
`-v confirmar=CADASTRAR` para persistir INATIVO. Usar somente o UUID dessa execucao
em EVOLUTION_CREDENCIAL_ID. O banco gera o UUID; token_ref=`env://EVOLUTION_API_KEY`.
Repeticao identica reutiliza o cadastro; identidade de outro provedor e recusada.

Com backend ainda parado/desativado e banco integralmente vazio de operacao:

```sh
psql -X -d BANCO_NOVO -v banco_esperado=BANCO_NOVO -v credencial_id=UUID_RETORNADO -v confirmar=HOMOLOGACAO_ISOLADA -f docker/provisionamento/ativar-canal-evolution-homologacao.sql
```

Exige executor administrativo/BYPASSRLS, verifica banco, dados operacionais e
credencial; audita a ativacao. Nao recebe nem grava token literal.

## 3. Dokploy — somente nova stack

Compose Path: **`docker/dokploy-stack.yml`**, ja inclui a rota Evolution; nenhum
override nem `docker service update` manual e necessario. Manter a rede interna
do backend nao publicada e somente Traefik confiavel encaminhando headers.

```dotenv
# Escolha explicita; o default do repositorio continua false
WHATSAPP_PROVEDOR=evolution
WHATSAPP_NUMERO=NOME_REAL_NO_MANAGER
EVOLUTION_HABILITADO=true
EVOLUTION_URL_BASE=https://evolution-hml.187.77.47.30.sslip.io
EVOLUTION_INSTANCIA=NOME_REAL_NO_MANAGER
EVOLUTION_CREDENCIAL_ID=UUID_REAL_DO_CADASTRO
EVOLUTION_API_KEY=CHAVE_REAL_DA_INSTANCIA_CONFIGURADA_NO_DOKPLOY
EVOLUTION_WEBHOOK_SEGREDO=GERAR_ALEATORIO_EXCLUSIVO_NAO_REUTILIZAR_APIKEY
SERVER_FORWARD_HEADERS_STRATEGY=NATIVE
# Defaults seguros; nao aumentar limites globais
EVOLUTION_CONCORRENCIA=1
EVOLUTION_WEBHOOK_LIMITE_CORPO_BYTES=1048576
EVOLUTION_LIMITE_RESPOSTA_BYTES=1048576
EVOLUTION_PERMITIR_DESTINO_PRIVADO=false
EVOLUTION_REPASSE_AUTOMACAO_HABILITADO=false
```

Gerar segredo localmente com `openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n'`,
colar apenas nos campos secretos do Dokploy/painel. Nao compartilhar a saida.
API key e um segredo real; o segredo de webhook e outro, aleatorio, minimo 43 chars.
Os campos WHATSAPP_TOKEN/WEBHOOK_SECRET/VERIFY_TOKEN legados podem ficar vazios
somente nesta instancia Evolution; nao alterar os valores dos clientes Meta/Uzapi.
O destino privado e OFF. Para uma API interna explicitamente configurada, esse
opt-in vale SOMENTE para o origin EVOLUTION_URL_BASE, com TLS e sem redirects.

O router dedicado HTTPS preserva remoção de Origin somente em `/webhook/evolution`,
`/webhook/z-api` e `/webhook/canal`; accessLogs/tracing OFF. CORS de `/api/v1` nao muda.
Nenhuma alteracao de Compose Path e necessaria se ja usa o arquivo acima; caso
use uma copia inline, trocar para esse caminho versionado antes do deploy manual.

## 4. Evolution Manager / configuracao da instancia

Criar/selecionar a instancia **WHATSAPP-BAILEYS**, com nome igual ao cadastro.
Conectar pelo QR Code manualmente. Nao usar WABA/Cloud nem token Meta.
Na Evolution, manter persistencia de mensagens (`DATABASE_SAVE_DATA_NEW_MESSAGE`)
para consultas de citacao e download por chave; configurar retenção compativel com
o historico desejado. Retencao do CRM nao recupera binarios apagados no fornecedor.

Configuracao da tag: POST `/webhook/set/{instanceName}`, autenticado com header
`apikey` da instancia. Corpo a aplicar pelo operador/Manager, sem colar segredos
em historico de comandos ou aqui:

```json
{"webhook":{"enabled":true,"url":"https://DOMINIO_DO_NOVO_CRM/webhook/evolution","headers":{"X-Evolution-Webhook-Token":"SEGREDO_EXCLUSIVO_DO_DOKPLOY"},"byEvents":false,"base64":false,"events":["MESSAGES_UPSERT","MESSAGES_UPDATE"]}}
```

Somente inscricao local da instancia; desligar inscricao global equivalente para
evitar entregas duplicadas. Nao habilitar `LOG_LEVEL=WEBHOOKS`: o codigo da versao
logaria payload/apikey/destination. Secret header e autenticacao por posse sobre
TLS, **nao HMAC nem garantia criptografica de integridade do corpo**.

## 5. Repasse para automacao

OFF por padrao. O workflow novo precisa interpretar envelope Evolution sanitizado:
`event`, `instance`, `data.key`, `data.message`, `data.messageTimestamp`; campos
buttons/list permanecem estruturados. Nao transformar em payload Z-API/Meta.
Recebe `X-Synapse-Webhook-Token`, `X-Synapse-Evento-Id`, `X-Synapse-Tentativa`.
Deduplicar pelo Evento-Id antes dos efeitos. Mesmo clique/retry mantem identidade
logica; erro/timeout do n8n nao bloqueia chat e usa a outbox/backoff existentes.
Ecos/status/grupos/formatos descartados nao sao repassados.

Depois de adequar o consumidor, configurar AUTOMACAO_WEBHOOK_EVENTOS_URL HTTPS e
AUTOMACAO_WEBHOOK_REPASSE_TOKEN exclusivo; entao EVOLUTION_REPASSE_AUTOMACAO_HABILITADO=true.
Preferir responder por `/internal/v1/atendimentos/{id}/responder` com o contrato CRM
existente, nunca chamar um no Uzapi/Z-API herdado. Nenhum workflow foi alterado.

## 6. Homologacao manual, somente contato controlado

1. Webhook sem header/header errado: 403, nenhuma linha webhook/outbox. Sem HTTPS:
   403. Origin externo via router com segredo valido: nao receber Invalid CORS request.
2. Enviar texto do proprio contato: um historico, uma correlacao, atualizacao em
   tempo real, recarga preserva. Repetir entrega nao duplica mensagem/repasse.
3. Responder no CRM: confirmar autoria/responsavel/participantes e recebimento.
   Aceite PENDENTE nao permite novo despacho; conferir SERVER_ACK/DELIVERY_ACK/READ
   reais antes de afirmar envio/entrega/leitura.
4. Imagem, audio, video, PDF, DOC/DOCX com espacos/acentos; conferir bytes, nome,
   reproducao/download, recarga e negativa de acesso. JPG como documento permanece
   DOCUMENTO. Abrir conversa nao baixa novamente do fornecedor.
5. Citar recebida/enviada, inclusive midia; remover citacao so por acao do usuario.
   Referencia inexistente/outra conversa deve falhar sem enviar texto descitado.
6. Respostas de botao/lista sem conversation devem aparecer e repassar uma vez.
7. Encaminhar cliente→cliente e interno→cliente, mensagem programada e resposta via
   Automacao no contexto autorizado. Nao usar pacientes/clientes reais.
8. Conferir falha/timeout simulado sem redisparo automatico; nao reprocessar antigos.

Homologacao real, QR e configuracao externa permanecem sob responsabilidade do
operador. Nao foram feitos deploy, cadastro externo, envios, replay ou alteracoes
nas instancias existentes. Retorno: desligar EVOLUTION_HABILITADO e parar a nova
stack; nao apontar filas pendentes a outro provedor nem redespachar aceites ambiguos.
