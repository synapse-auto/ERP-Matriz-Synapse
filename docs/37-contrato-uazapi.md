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

**Não há handshake de desafio documentado para o callback.** A documentação descreve o cadastro por
`POST /webhook`, com `url`, `events`, filtros e opções de URL. Não há referência a um `GET` do provedor
com `hub.challenge`, como na Meta. Isso sugere que a rota GET do CRM não seria usada pela UazAPI, mas a
ausência precisa ser confirmada com a operação antes de codificar um tradutor.

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
vídeo, localização ou status de entrega. O schema `WebhookEvent` deixa `data` aberto (`additionalProperties`),
portanto não permite implementar leitura confiável por analogia com a Meta.

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
