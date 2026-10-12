# Z-API — autenticação dos callbacks: pendência oficial

## Decisões do responsável (12/10/2026)

- **G1:** obter contrato oficial de autenticação antes de implementar a entrada.
  Não autorizado substituir essa confirmação por segredo próprio na URL, Client-Token
  presumido no callback ou assinatura HMAC inventada. Entrada permanece não implementada.
- **G2:** suporte inicial a telefones. LID-only será descartado/quarentenado de maneira
  rastreável, com limitação explícita; não haverá conversão para dígitos, criação de telefone
  fictício nem ampliação do modelo de identidade nesta etapa. Decisão aprovada, código ainda
  não implementado. Fonte da limitação do fornecedor: [LID](https://developer.z-api.io/tips/lid).

## API Documentation Checker — resultado e limites observados

Usado por solicitação do responsável, operação `extract_api_contract`.
Foram fornecidos os textos oficiais das seis páginas abaixo, com suas URLs:

- [Referência da API](https://developer.z-api.io/api-reference/introduction)
- [Client-Token](https://developer.z-api.io/security/client-token)
- [Introdução aos webhooks](https://developer.z-api.io/webhooks/introduction)
- [Ao enviar](https://developer.z-api.io/webhooks/on-message-send)
- [Ao receber](https://developer.z-api.io/webhooks/on-message-received)
- [Atualizar todos webhooks](https://developer.z-api.io/webhooks/update-every-webhooks)

Resultado: `status: success`, `endpoints: []`, `errors: []`. Segunda chamada com o cURL
literal da página HTML oficial [send-text](https://developer.z-api.io/message/send-text),
contendo método POST, URL, Client-Token, Content-Type, phone e message, produziu novamente
`endpoints: []`. Não houve fonte estruturada alternativa vinculada localizada nessa página;
o conteúdo narrativo/HTML não foi convertido pelo plugin em contrato estruturado.

**Isso não é cobertura completa nem prova da ausência de uma capacidade do fornecedor.**
O resultado vazio é uma limitação observada da extração utilizada. Não confirma autenticação,
disponibilidade, compatibilidade, origem do callback ou assinatura. O Checker não consulta
conta real, não contata suporte e não obtém informação que não consta no material fornecido.

A inspeção manual das páginas verificadas continua sem localizar protocolo de autenticação
da requisição Z-API → CRM. A referência de API/Client-Token descreve CRM → Z-API; HTTPS/instanceId
não são prova de origem. Gate G1 continua aberto, sem alegar que toda documentação possível ou
uma funcionalidade privada do fornecedor foi excluída.

## O que solicitar ao suporte Z-API

Pedir documentação oficial, versionada ou confirmação escrita identificável do fornecedor,
para DeliveryCallback, ReceivedCallback e MessageStatusCallback:

1. Existe autenticação de saída desses callbacks? Quais headers/parâmetros efetivamente são
   enviados e como são configurados? Distinguir de Client-Token das chamadas à API.
2. Se há assinatura: algoritmo, formato do header, segredo e rotação, bytes/canonicalização
   usados no cálculo, timestamp/tolerância e proteção contra replay, com exemplo sanitizado.
3. Se o mecanismo é outro: procedimento de validação no receptor e suas garantias/limitações.
   Se não existe, confirmar explicitamente. Não inventar algoritmo ou configuração.
4. Documentar tratamento de falhas/reentrega e identificação da instância; separadamente,
   confirmar o namespace de `ids[]` dos status e relação com messageId/zaapId.

Não solicitar tokens reais, dados de clientes, payload de produção ou URL de callback com
segredo. Nenhuma mensagem ao suporte foi enviada automaticamente. Após receber a fonte,
reanalisar o contrato e os gates antes de retomar a implementação.

## Estado operacional

Somente pesquisa e documentação. Z-API sem adaptador/ativação; nenhum número conectado,
webhook registrado, chamada à API de negócio, VPS/Dokploy, n8n de produção, merge ou deploy.
