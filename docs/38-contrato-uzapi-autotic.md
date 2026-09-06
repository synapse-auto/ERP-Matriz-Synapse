# Contrato Uzapi/Autotic — E152

Este documento registra o contrato confirmado para **envio** pela Uzapi/Autotic. A fonte primária é
o Swagger publicado em [`api.uzapi.com.br/swagger`](https://api.uzapi.com.br/swagger) (o contrato
embutido no Swagger UI também está disponível em
[`api.uzapi.com.br/docs`](https://api.uzapi.com.br/docs)). A implementação real indicada como
segunda fonte é a integração `Clinica-CRM-FMNA`, nos caminhos
`backend/.../integration/whatsapp/uazap/UazapClient.java` e
`docs/integracoes/WHATSAPP_OUTBOUND.md`; ela é referência de operação, não substitui o Swagger
oficial. Essa árvore irmã não está presente nesta worktree, portanto os detalhes de recebimento
abaixo permanecem explicitamente não confirmados e não foram usados para inventar campos de domínio.

## 1. Escopo desta etapa

O adaptador implementado nesta etapa é somente de saída. O CRM envia texto e mídias por
`messages`, fazendo upload de bytes por `media` quando necessário. **Recebimento de webhook e
download de mídia recebida continuam indisponíveis:** não há `TradutorDeCanal`, controller de
webhook ou parsing de entrada da Uzapi/Autotic nesta etapa. `baixarMidiaRecebida` recusa
explicitamente para evitar tratar uma referência não investigada como se fosse contrato confirmado.

## 2. Autenticação e URL

Todas as operações usam o header:

```text
Authorization: Bearer <token>
```

Os segmentos da URL são, nesta ordem:

```text
/{username}/{version}/{phone_number_id}/...
```

O identificador de saúde/autenticação confirmado no Swagger é:

```text
GET /{username}/{version}/{phone_number_id}/instance
```

Qualquer resposta HTTP 2xx é considerada autenticação aceita; 4xx é recusa da credencial e falha
de rede/5xx é indisponibilidade. O detalhe devolvido ao CRM nunca inclui o token.

## 3. Mensagem de texto

```http
POST /{username}/{version}/{phone_number_id}/messages
Content-Type: application/json
Authorization: Bearer <token>
```

Corpo normalizado pelo adaptador:

```json
{
  "to": "5511999999999",
  "type": "text",
  "text": {"body": "Olá!"},
  "context": {"message_id": "wamid.da.resposta"}
}
```

`context` só é enviado quando há citação. O telefone é enviado somente com dígitos.

O contrato de aceite usado pelo adaptador exige resposta HTTP 2xx com `status` igual a `success`,
sem campo `error`, e `messages[0].id` não vazio. Corpo ausente, JSON inválido ou resposta sem esse
identificador é recusa permanente, mesmo com HTTP 2xx; isso evita confirmar na outbox um envio cujo
identificador não pôde ser casado.

## 4. Mídias

Primeiro os bytes são enviados como multipart:

```http
POST /{username}/{version}/{phone_number_id}/media
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

Os campos documentados são `file` (binário) e `messaging_product=whatsapp`. A resposta precisa
conter um `id` de mídia. Esse id é então referenciado em `messages`:

```json
{
  "to": "5511999999999",
  "type": "image",
  "image": {"id": "media-id", "caption": "Legenda opcional"}
}
```

Os tipos do CRM são traduzidos para `image`, `audio`, `video` e `document`. Legenda é enviada para
imagem, vídeo e documento quando existe. O Swagger oficial não documenta legenda para áudio nos
exemplos de envio; por isso o adaptador não inclui `caption` em áudio. Nome de arquivo é enviado em
documento quando está disponível nos metadados. O nome/extensão do multipart é derivado do MIME
normalizado pelo helper existente do CRM.

Falha no upload ou resposta sem id não chama o endpoint de mensagem e é recusa permanente quando a
resposta 2xx é inválida. Timeout, DNS, conexão recusada e 5xx são temporários; qualquer 4xx,
inclusive 429, é permanente conforme a política desta integração.

## 5. Templates e localização

O Swagger confirma o endpoint genérico de mensagens, mas não fornece uma operação de administração
ou envio de template que possa ser adotada com segurança para o CRM. Templates são recusados
permanentemente pelo adaptador e não geram chamada HTTP. Localização também não é mídia transferida;
o tipo `LOCALIZACAO` é rejeitado neste caminho.

## 6. Circuit breakers e configuração

Há três circuit breakers independentes: `canal-uzapi-autotic` para envio de mensagens,
`canal-uzapi-autotic-midia` para upload e `canal-uzapi-autotic-saude` para a sonda de
autenticação. A seleção do gateway continua sendo feita pelo seletor existente, pela chave
`uzapi-autotic`.

Os campos de configuração específicos são opcionais para a Meta e obrigatórios apenas quando este
adaptador está ativo:

```text
WHATSAPP_PROVEDOR=uzapi-autotic
WHATSAPP_URL_BASE=https://api.uzapi.com.br
WHATSAPP_NUMERO=<phone_number_id>
WHATSAPP_TOKEN=<token>
WHATSAPP_USUARIO_API=<username>
WHATSAPP_VERSAO_API=v1
```

Não há segredo novo no repositório. As duas variáveis novas são vazias por padrão e entram no stack
com default vazio para não quebrar deploys de instâncias que continuam na Meta.

## 7. Material de recebimento mantido como referência

O material operacional menciona aliases e tolerâncias de formatos de mensagens recebidas, além de
filtros como `status@broadcast`. Esses itens ficam registrados somente como referência para uma
futura investigação do webhook; não são contrato confirmado, não foram codificados e não devem ser
usados para habilitar recebimento nesta etapa.
