# Contrato Uzapi/Autotic — E152 (substitui a investigação errada do E148c)

Fornecedor real contratado para o número da clínica: **Uzapi/Autotic** (`uzapi.com.br`), confirmado
pelo Marcondes em 06/09/2026. Não confundir com a **UazAPI** (`uazapi.dev`/uazapiGO), documentada em
`docs/37-contrato-uazapi.md` — são dois produtos de fornecedores diferentes, com nomes quase
idênticos. Toda esta etapa usa a chave de provedor `uzapi-autotic` (nunca `uazapi`) para nunca mais
confundir os dois.

Levantamento feito em 09/09/2026 a partir de duas fontes:

1. **Swagger oficial**, baixado diretamente de `https://api.uzapi.com.br/docs/swagger.json`
   (o link publicado, `https://api.uzapi.com.br/swagger`, é só a casca do Swagger UI — o JSON real
   está em `/docs/swagger.json`) e a página de exemplo `https://uzapi.com.br/docs/api/teste-de-endpoints/`.
2. **`Clinica-CRM-FMNA`** (`backend/.../integration/whatsapp/uazap/`), produto irmão do Marcondes,
   single-tenant, já integrando este mesmo fornecedor em produção real. Usado só como referência de
   formato — nenhum código de lá foi copiado.

## 1. Autenticação e identificação da instância

`Authorization: Bearer <token>` — **não** é o header `token` da UazAPI real. Toda rota de negócio
carrega `{username}` e `{version}` como segmentos de URL, além do `{phone_number_id}` (o número
principal da instância):

```
{baseUrl}/{username}/{version}/{phone_number_id}/...
```

`{username}` e `{version}` não existiam em `CanalProperties` antes desta etapa; agora são
`usuarioApi`/`versaoApi`, com default vazio (`WHATSAPP_USUARIO_API`/`WHATSAPP_VERSAO_API`).

## 2. Saúde da instância

`GET /{username}/{version}/{phone_number_id}/instance` — confirmado literalmente no Swagger:
`operationId: KubernetesController_getDeployment`, tag `Instancias`, summary "Consultar uma
instancia". 201 com corpo (o schema de resposta não é documentado no Swagger, mas 4xx/5xx e erro de
conexão bastam para `AutenticacaoDoCanal.recusada(...)`).

## 3. Envio de texto

`POST /{username}/{version}/{phone_number_id}/messages`, corpo:

```json
{"to": "5561999999999", "type": "text", "text": {"body": "Olá, tudo bem?"}}
```

`to` é só dígitos, sem `+` nem máscara — confirmado no exemplo do Swagger
(`"example": 5543996254177`). `context.message_id` opcional referencia a mensagem anterior.

## 4. Envio de mídia — upload em duas etapas

Confirmado nos doze variantes do `oneOf` de `POST .../messages` (Text, Image, Audio, Video,
Document, Reaction, Location, Contacts, Poll, Sticker, Revoke, Interactive):

1. `POST /{username}/{version}/{phone_number_id}/media`, multipart, campos `file` (binário) e
   `messaging_product: whatsapp` — confirmado no Swagger (`MediaController_uploadMedia`, tag
   `Midias`). Resposta `{"id": "<mediaId>"}` (confirmado nas duas fontes; o Swagger não documenta o
   schema da resposta 201, só a descrição vazia).
2. `POST .../messages` com `{"to", "type": "<image|audio|video|document>", "<type>": {"id":
   "<mediaId>", "caption": "<opcional>"}}`.

Antes de persistir uma gravação do composer, ela é convertida localmente para AAC/ADTS mono a
48 kHz. Como proteção para registros antigos, áudio ISO-BMFF fragmentado (`moof`, formato gerado
pelo `MediaRecorder` do navegador) também é convertido para AAC/ADTS no worker de entrega. A
conversão ocorre fora dos disjuntores do provedor: erro de conversão recusa somente aquela
mensagem, sem degradar os demais envios. Áudios anexados como arquivos seguem para o upload sem
transformação.

O objeto de mídia também aceita `link` (URL pública) no lugar de `id` — confirmado no schema
(`image`/`video`/`document` são `CaptionedLinkMessage`/`CaptionedFileMessage`, `audio` é
`LinkMessage`, todos com as duas propriedades `link` e `id`). **Não implementado nesta etapa**:
`ConteudoDeEnvio.MensagemMidia.referenciaStorage()` é documentado no próprio domínio como chave
opaca do bucket ("nunca bytes nem URL") — não existe hoje um chamador deste adaptador que entregue
uma URL pública em vez de uma referência de storage. Adicionar detecção de URL agora seria um ramo
sem chamador real para testar; fica registrado aqui para quando essa premissa do domínio mudar.

### Caption por tipo — confirmado campo a campo no Swagger

| Tipo | Schema no Swagger | Tem `caption`? |
|---|---|---|
| `image` | `CaptionedLinkMessage` | Sim |
| `video` | `CaptionedLinkMessage` | Sim |
| `document` | `CaptionedFileMessage` (+ `filename`) | Sim |
| `audio` | `LinkMessage` | **Não** |

`audio` é o único variante cujo schema **não tem** propriedade `caption`. O adaptador não envia esse
campo para áudio — não é suposição, é leitura direta do schema.

## 5. Templates — não implementado, confirmado que não há endpoint

O Swagger não tem nenhum path de gestão de template (`message_templates` ou equivalente) entre os 36
paths documentados. O valor `"template"` aparece **apenas** no `enum` solto do campo `type` de
`POST .../messages` — não há um variante de `oneOf` com schema de corpo correspondente (os doze
variantes reais são Text/Image/Audio/Video/Document/Reaction/Location/Contacts/Poll/Sticker/
Revoke/Interactive). Ou seja: o enum promete uma opção que o schema não sustenta. `MensagemTemplate`
é recusada com `Recusado.permanente`, sem chamada HTTP.

## 6. Resposta de sucesso e de erro

Confirmado nas duas fontes (o schema formal `SuccessResponse` do Swagger é incompleto — só declara
`statusCode`/`message`/`queueId`/`messageId` — mas o exemplo completo na página de testes mostra o
corpo real):

```json
{
  "status": "success",
  "message": "Mensagem colocada na fila de envios com sucesso!",
  "queueId": "BB3A206B22B97B70E3E2F74197747CB3",
  "messageId": "3EB033430E4C1324B0E016",
  "contacts": [{"input": "5521xxxxx", "wa_id": "5521xxxxx"}],
  "messages": [{"id": "wamid.PXpweDEvZWtnZTZ6Tk1rVFNFOGxZaVIzVnVyT1ZMclhUcHhVdUM9Nw=="}]
}
```

| Campo | O que é | Vira `idExterno`? |
|---|---|---|
| `queueId` | "Identificador da fila de envio" — interno | Não |
| `messageId` | "Identificador interno da mensagem" — interno, apesar do nome | Não |
| `messages[0].id` | "Identificador da mensagem gerado pelo WhatsApp" — o wamid real (prefixo `wamid.`) | **Sim** |

Falha: ausência de `messages[0].id`, `status` diferente de `"success"`, ou campo `error` presente —
mesmo com HTTP 2xx — é falha, não sucesso. O HTTP 200 do endpoint de mensagens é descrito no Swagger
como "Mensagem colocada na fila de envios com sucesso" — é confirmação de enfileiramento, não de
entrega; a leitura do corpo continua obrigatória.

## 7. Classificação de erro — sem retry documentado

Sem confirmação de rate limit ou retentativa para este fornecedor. A referência de produção real
(`Clinica-CRM-FMNA`/`UazapClient`) documenta explicitamente "Sem retry automático (não há chave de
idempotência de envio comprovada)". Critério adotado:

- timeout, erro de conexão, `5xx` → `temporario`.
- `4xx` (**incluindo 429** — diferente da Meta, que trata 429 como exceção; aqui não há base
  documentada para isso), ou 2xx com `status != "success"` / `error` presente / sem
  `messages[0].id` → `permanente`.

## 8. Recebimento — implementado na E155

O recebimento da Uzapi/Autotic agora usa o mesmo endpoint único do CRM, sem habilitar o provedor em
nenhum ambiente: `POST /webhook/canal?secret=<WHATSAPP_WEBHOOK_SECRET>`. O tradutor
`UzapiAutoticWebhookTradutor` absorve aliases do payload, filtra Status/Story, traduz texto, mídia,
interativas e localização, e mantém os identificadores no vocabulário do CRM. O segredo da query é
comparado em tempo constante; sem segredo configurado o webhook é recusado.

Mídia recebida chega como referência: `UzapiAutoticAdapter` resolve o `mediaId` em
`GET /{username}/{version}/{mediaId}` e baixa os bytes da URL retornada usando o disjuntor dedicado.
Localização não chama o downloader e é persistida em metadados estruturados.

### 8.0 Registro do callback

O Swagger oficial concentra o callback no campo `webhook` da atualização da instância; os 16 paths
`/webhook/message/*` e `/webhook/status/*` são eventos documentados pelo fornecedor, não sufixos que
o CRM precise expor. No momento do corte, Lucas deverá configurar (fora desta etapa):

```
PUT https://api.uzapi.com.br/{username}/{version}/{phone_number_id}/instance/update
Authorization: Bearer <token>
{
  "webhook": "https://<host-do-synapse>/webhook/canal?secret=<WHATSAPP_WEBHOOK_SECRET>",
  "webhookEvents": {"authentication": true, "connection": true, "group_messages": true,
    "message_status": true, "group_events": true, "history": false}
}
```

O endpoint do Synapse é único: `POST /webhook/canal?secret=<WHATSAPP_WEBHOOK_SECRET>`. Nenhum
registro foi executado contra uma conta real nesta etapa.

### 8.1 O que o Swagger oficial documenta — confirmado, primário

O Swagger tem 16 paths de webhook, agrupados por tipo de evento:

```
/webhook/connection/connected        /webhook/connection/disconnected
/webhook/message/text                /webhook/message/image
/webhook/message/audio               /webhook/message/video
/webhook/message/document            /webhook/message/location
/webhook/message/sticker             /webhook/message/contacts
/webhook/message/reaction            /webhook/message/button-reply
/webhook/message/list-reply
/webhook/status/delivered            /webhook/status/read
/webhook/status/played
```

**Divergência importante com a hipótese inicial desta etapa:** o prompt original supunha que o
webhook só carrega eventos de status (`send`/`delivery`/`read`, sem texto). O Swagger contradiz isso
diretamente — `/webhook/message/text` tem um schema de corpo completo, com o texto real da mensagem
recebida:

```json
{
  "object": "whatsapp_business_account",
  "entry": [{"id": "", "changes": [{
    "value": {
      "messaging_product": "whatsapp",
      "metadata": {"display_phone_number": "554391665228", "phone_number_id": "896610136053100"},
      "contacts": [{"profile": {"name": "..."}, "wa_id": "554391241788"}],
      "messages": [{
        "from": "554391241788", "id": "A5203219F0E3658C70AD27075CE2A4E9",
        "isGroup": false, "text": {"body": "Olá tudo bem?"},
        "timestamp": "1768842483", "type": "text"
      }]
    },
    "field": "messages"
  }]}]
}
```

O envelope é **estruturalmente idêntico ao da Meta** (`entry[].changes[].value.messages[]`), inclusive
nos nomes de campo (`messaging_product`, `metadata.phone_number_id`, `contacts[].wa_id`). Os eventos
de status (`/webhook/status/delivered` etc.) usam o mesmo envelope, trocando `messages[]` por
`statuses[]` com `id`/`status`/`timestamp`/`recipient_id`/`conversation`/`pricing` — também idêntico
ao formato Meta. O tradutor dedicado reaproveita essa navegação estrutural sem compartilhar regras
específicas do fornecedor com `MetaCloudWebhookTradutor`.

O endpoint `getchat` mencionado na documentação narrativa (para buscar conteúdo completo por ID) não
foi localizado como path próprio nos 36 do Swagger — pode estar sob outro nome ou não documentado
publicamente; ele não é necessário para o fluxo implementado, que resolve mídias pelo endpoint
documentado de `mediaId`.

**Isto não é confirmação de como o número real desta clínica vai se comportar** — é o que o Swagger
documenta. Só um teste empírico contra a instância real confirma.

### 8.2 Decisões de compatibilidade trazidas da referência de produção

Meses de produção real, com correções de bugs reais (mídia inbound, Status/Story vazando pro chat).
Não copiado, só registrado como referência de formato:

- **Filtro de Status/Story**: `UazapInboundEventFilter` reconhece Status pela origem do chat,
  aceitando os nomes `chatid`, `remotejid`, `key.remotejid`, `from`, `sender`, `participant`, `jid`
  contendo `status@broadcast` — tolerância a nomes de campo que variam entre o envelope
  Meta-compatível e o evento nativo da UazAPI/Autotic.
- **Tolerância de nomes de mídia**: aceita `media_id`/`mediaId`/`id` e `mime_type`/`mimeType`/
  `mimetype` como aliases do mesmo dado, dependendo de qual variante do payload chega.
- **Sem retry automático** no envio (já usado no Bloco 7 acima).
- **Segurança do webhook**: sem assinatura nativa documentada. A referência mitiga com um parâmetro
  `?secret=` na URL, comparado em tempo constante (`MessageDigest.isEqual`) mais validação estrutural
  do payload e do identificador de instância esperado. Documentado lá mesmo como proteção **fraca**
  (query string vaza em log de proxy/histórico), mas é precedente real de meses em produção sem
  incidente conhecido. Esta etapa adota esse mecanismo como fallback porque o Swagger não oferece
  segredo/header próprio; a URL deve ser protegida por TLS e o segredo configurado fora do código.

O Swagger não documenta desafio `GET` nem um segredo/header de assinatura próprio. Por isso o
tradutor recusa a verificação GET (falha fechada) e usa o segredo de query já adotado pela referência
de produção, sem chamada à instância real nesta etapa.

## 9. Segredos

Três variáveis já existentes cobrem autenticação (`WHATSAPP_URL_BASE`, `WHATSAPP_NUMERO`,
`WHATSAPP_TOKEN`); duas novas cobrem o path (`WHATSAPP_USUARIO_API`, `WHATSAPP_VERSAO_API`). Nenhum
valor real entrou neste documento, no código ou nos testes — todos os exemplos acima são do Swagger
público ou de fixtures.

## Ponto de parada da E155

Envio e recebimento estão implementados (`UzapiAutoticAdapter` e
`UzapiAutoticWebhookTradutor`), mas o provedor continua desligado por configuração. Não ligar
`synapse.canal.whatsapp.provedor=uzapi-autotic` nem registrar webhook em ambiente real nesta etapa;
isso exige credenciais e uma decisão operacional fora do código.
