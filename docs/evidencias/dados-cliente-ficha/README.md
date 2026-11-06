# Evidencia visual — dados locais do cliente

CRM real, frontend Next.js e backend Spring com PostgreSQL/Redis descartaveis.
Somente dados sinteticos, Darwin desligada, nenhum paciente/provedor consultado.

- `dados-cliente-parcial-desktop.png`: ficha aberta antes de preencher CPF/nascimento (1600×1000).
- `dados-cliente-completo-desktop.png`: mesma ficha apos POSTs reais de preenchimento/data-nascimento, sem recarregar (1600×1000).
- `dados-cliente-completo-1280.png`: mesma ficha em 1280×800, sem overflow horizontal.

Fonte computada: `Inter, "Inter Fallback", Inter, sans-serif`, `document.fonts.status=loaded`.
Nome/telefone existentes foram reutilizados, sem duplicacao. Apos refresh e nova abertura,
CPF/nascimento permaneceram. A fixture opt-in desliga schedulers globais; o controle local
aciona exclusivamente o worker real da outbox, seguido de Redis/WebSocket reais.

Estas capturas comprovam apresentacao e fluxo sintetico local; nao comprovam integracao
do workflow produtivo da automacao nem habilitacao da capacidade na Femina.
