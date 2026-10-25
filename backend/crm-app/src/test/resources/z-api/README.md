# Fixtures Z-API sanitizadas

Derivadas dos campos/exemplos oficiais consultados em 24/10/2026, nao de capturas de conta
real. Telefones/IDs/nomes/texto/horarios foram substituidos por valores sinteticos.
Nao incluem segredo, URL privada, assinatura inventada nem prova de autenticacao.

- received.json: https://developer.z-api.io/webhooks/on-message-received-examples
- delivery.json: https://developer.z-api.io/webhooks/on-message-send-examples
- status.json: https://developer.z-api.io/webhooks/on-whatsapp-message-status-changes

O namespace WHATSAPP de ids[] e escolhido explicitamente SOMENTE no teste. Continua
pendente de confirmacao oficial para producao. Os testes alteram IDs/instancia/status para
exercitar isolamento e ordem invertida, preservando o formato documentado.
