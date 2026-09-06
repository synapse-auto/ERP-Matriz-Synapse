> **Aviso (09/09/2026): este documento descreve a UazAPI (`uazapi.dev`/uazapiGO), que não é o fornecedor contratado pela clínica.** O fornecedor real é Uzapi/Autotic (`uzapi.com.br`) — ver `docs/38-contrato-uzapi-autotic.md`. Este documento fica como registro histórico da investigação original (E148/E148b), útil como referência geral sobre gateways não-oficiais de WhatsApp, mas não deve ser usado para implementar nada contra o número real desta clínica.

# Contrato UazAPI — Bloco 0 da E148

Levantamento realizado em 05/09/2026 a partir da especificação OpenAPI oficial publicada em
[`https://docs.uazapi.com/openapi-bundled.json`](https://docs.uazapi.com/openapi-bundled.json), versão
2.1.1. Não houve acesso a uma instância UazAPI de homologação nem a credencial para executar chamadas
reais.

## 1. Autenticação do webhook de entrada

**Não confirmada e tratada como bloqueador de segurança.** A especificação oficial documenta:

- header `token` para endpoints regulares da API;
- header `admintoken` para endpoints administrativos;
- `POST /webhook` como operação de configuração do webhook, autenticada pelo token da instância.

Ela não declara header, assinatura, segredo na URL ou outro mecanismo para autenticar o POST que a
UazAPI fará no callback configurado. O schema `WebhookEvent` também não declara um campo de segredo.
Portanto não é seguro implementar `assinaturaValida` retornando `true` ou aceitar qualquer callback sem
uma decisão explícita de segurança e uma forma de proteger a rota (por exemplo, segredo de URL ou
header configurável comprovado).

## 2. Handshake de cadastro

**Não há handshake de desafio documentado para o callback.** A especificação YAML completa da UazAPI
confirma o cadastro por `GET`/`POST /webhook`; os dois endpoints usam o header `token` da instância e
não são callbacks enviados ao CRM.

`GET /webhook` devolve um array dos webhooks configurados (pode haver mais de um por instância). Cada
item contém `id`, `enabled`, `url`, `events`, `excludeMessages`, `addUrlEvents` e
`addUrlTypesMessages`.

`POST /webhook` aceita dois modos:

- **Simples**, sem `action` nem `id`: gerencia automaticamente um único webhook por instância, criando
  ou atualizando a configuração. `url` é obrigatório; um exemplo mínimo é
  `{"url":"https://...","events":["messages"],"excludeMessages":["wasSentByApi"]}`.
- **Avançado**, com `action` igual a `add`, `update` ou `delete`; `id` é obrigatório em `update` e
  `delete`, permitindo múltiplos webhooks com eventos diferentes.

Os eventos aceitos são `connection`, `history`, `messages`, `messages_update`,
`newsletter_messages`, `call`, `contacts`, `presence`, `groups`, `labels`, `chats`, `chat_labels`,
`blocks` e `sender`. `excludeMessages` é filtro de mensagens, não autenticação, e aceita
`wasSentByApi`, `wasNotSentByApi`, `fromMeYes`, `fromMeNo`, `isGroupYes` e `isGroupNo`. A documentação
recomenda `wasSentByApi` para evitar loop quando a própria automação envia mensagens.

`addUrlEvents` e `addUrlTypesMessages` são booleanos de roteamento: quando ativos, acrescentam o tipo
do evento e/ou o tipo da mensagem como segmentos da URL (por exemplo,
`.../webhook/messages/conversation`) em vez de enviar tudo para a URL fixa.

Nenhum campo de segredo, HMAC ou assinatura aparece no schema de configuração ou no evento recebido.
Também não há `hub.challenge` nem outro handshake de desafio documentado para o callback. Portanto,
continua confirmado que a UazAPI não documenta autenticação do POST de callback; não se deve aceitar
callbacks anônimos no CRM sem decisão explícita de segurança.

Existe ainda `GET /webhook/errors`, autenticado com `token`, que mantém em memória (somente os 20 erros
mais recentes, perdidos ao reiniciar a UazAPI) os erros de entrega do webhook, incluindo o `payload`
que a UazAPI tentou enviar. É um plano B para captura de evidência: só registra falhas de entrega, não
sucessos, e o exemplo do spec contém apenas `EventType` e `token`; o conteúdo completo de mensagem
nesse endpoint ainda não foi confirmado.

## 3. Payload de mensagem recebida

**Payload completo não confirmado.** O schema oficial `Message` lista campos como `id`, `messageid`,
`chatid`, `sender`, `senderName`, `fromMe`, `messageType`, `status`, `text`, `quoted`, `fileURL` e
`messageTimestamp`. O único exemplo de evento de mensagem encontrado na especificação é o exemplo de
SSE:

```json
{
  "type": "message",
  "data": {
    "id": "3EB0538DA65A59F6D8A251",
    "from": "5511999999999@s.whatsapp.net",
    "to": "5511888888888@s.whatsapp.net",
    "text": "Olá!",
    "timestamp": 1672531200000
  }
}
```

Não há, no documento oficial, fixtures completas de webhook para texto, imagem, áudio, documento,
vídeo, localização ou status de entrega. O schema `WebhookEvent` deixa `data` aberto
(`additionalProperties`), portanto não permite implementar leitura confiável por analogia com a Meta.

**Bloco 3 empírico ainda não executado:** não há credencial de instância de teste disponível neste
ambiente e, por segurança, nenhuma instância da clínica foi consultada ou alterada. Assim, não há
headers ou corpos reais para registrar, nem confirmação de quais campos chegam para cada tipo de
mensagem.

## 4. Mídia recebida

O documento oficial descreve `fileURL`/referência no objeto de mensagem e `POST /message/download`, que
recebe o `id` da mensagem e pode devolver `fileURL`, `mimetype` e/ou `base64Data`. Ele não confirma se o
callback envia bytes inline, URL ou somente um identificador para cada tipo de mídia. A decisão é
relevante porque `CanalGateway.baixarMidiaRecebida(String)` pressupõe uma referência estável; não será
inventado um id sem confirmação.

## 5. Envio

Confirmado na especificação oficial:

- `POST /send/text`, header `token`, corpo com `number` e `text` (e opções como `replyid`);
- `POST /send/media`, header `token`, corpo com `number`, `type`, `file`, legenda em `text`, `docName`
  e `mimetype`; `file` aceita URL ou base64;
- `POST /send/location`, header `token`, corpo com `number`, `latitude`, `longitude`, e campos opcionais
  `name` e `address`;
- `GET /instance/status`, header `token`, retorna o estado e a identidade da instância.

A especificação não foi usada para implementar chamadas, porque a autenticação do callback e o formato
de entrada permanecem sem confirmação.

## 6. Credencial

Há dois níveis documentados: token de instância (`token`) para operações regulares e token de
administrador (`admintoken`) para operações administrativas. O modelo necessário para a instância do
CRM ainda precisa de decisão operacional sobre qual token será fornecido e como o callback será
protegido. Nenhum segredo real foi armazenado neste repositório.

## 7. Templates

Não foi encontrado endpoint de template pré-aprovado no índice da API oficial nem campo de template nos
endpoints de envio documentados. Isso é consistente com a hipótese de provedor sem camada de templates,
mas deve ser confirmado antes de fixar o comportamento de `listarTemplates`/`criarTemplate`.

## Ponto de parada da E148

O Bloco 0 não fornece mecanismo verificável para autenticar o callback nem payload completo para
traduzir mensagens. A implementação de `UazApiWebhookTradutor`/`UazApiAdapter` foi deliberadamente
adiada; não foi alterada nenhuma interface (`TradutorDeCanal`, `CanalGateway`, `ConteudoDeEnvio`), nem o
`WebhookCanalController`, nem o adaptador da Meta. É necessária confirmação oficial ou um teste real de
homologação antes de prosseguir.
