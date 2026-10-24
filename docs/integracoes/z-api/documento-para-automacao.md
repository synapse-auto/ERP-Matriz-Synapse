# Z-API — documento para automação

24/10/2026. Destinatária: equipe responsável pelo n8n (Dylan).
**Preparação, não liberação de troca.** Código Z-API ainda ausente; callbacks não autenticados
não serão habilitados. Este documento não autoriza editar workflow publicado ou enviar ao cliente.

## Inventário a devolver

Os workflows publicados não estão neste repositório. A documentação CRM identifica os caminhos,
mas não comprova nomes/IDs dos nós realmente implantados. Devolver exportação **sanitizada** e
tabela `workflow ID/version → função do nó → provedor atual → fila/agenda → destino após corte`.
Não incluir credentials, tokens, URLs privadas, payload de cliente ou texto das mensagens.

| Função a localizar em todos os sub-workflows | Ajuste a preparar | Evidência necessária antes do corte |
|---|---|---|
| Entrada crua repassada pelo CRM | Parser/roteamento explícitos para `ReceivedCallback` Z-API; não passar envelope novo no parser Uzapi/Meta | Testar texto, mídia, eco, outra instância, LID-only e campos ausentes |
| Resposta IA pelo CRM | Preservar `/internal/v1/atendimentos/{id}/responder`, `X-Synapse-Token` e `Idempotency-Key` estável; CRM usa outbox | Uma ocorrência → uma mensagem; retry do comando não duplica envio |
| HTTP direto na Uzapi | Identificar cada chamada; não basta trocar o host. Payload e autenticação têm contratos próprios | Inventário completo de operações e chamadores; nenhum caminho antigo fica enviando em paralelo |
| Follow-up/fidelização/festiva/aniversário/avaliação | Preservar reserva proativa **antes** da chamada e registro com a mesma chave depois | `podeEnviar=false` não envia; limites/origem continuam aplicados |
| Filas/timers externos, inclusive `follow_ups_temporary` se usado | Inventariar tarefas já pendentes e seu provedor de origem; suspender controladamente na janela autorizada | Nenhuma tarefa Uzapi reencaminhada automaticamente pela Z-API |
| Registro de mensagem já enviada | Preservar endpoint interno, autoria/origem e `chaveDeEnvio`; não gerar segunda saída pelo CRM | Registro idempotente, conflitos recusados e conteúdo/autor corretos |
| Callbacks/status/eco recebidos diretamente no n8n | Não processar em duplicidade com CRM; validar desenho autenticado oficial antes de escolher entrada | Instância/canal isolados e ID de namespace comprovado |

Referências canônicas existentes: [docs/16](../../16-acesso-da-automacao.md),
[docs/50](../../50-envio-unico-da-automacao.md),
[docs/51](../../51-origem-e-frequencia-das-mensagens-automaticas.md).
Conferir o OpenAPI do SHA final para campos/validações exatos; não expandir o contrato por suposição.

## Contratos preservados e distinção de caminhos

- Base interna: `http://synapse-backend-internal:8080/internal/v1`. Header `X-Synapse-Token`
  em Credential do n8n, nunca no JSON exportado. Não publicar namespace interno no proxy.
- Resposta pelo CRM: `POST /atendimentos/{id}/responder`; `Idempotency-Key` por ocorrência
  estável. Não enviar diretamente ao provedor e depois chamar `/responder` para registrar:
  isso provocaria outra saída.
- Envio direto: reserva de envio único em `POST /atendimentos/{id}/envios-automacao/reservas`,
  ou reserva proativa em `POST /leads/{leadId}/envios-proativos/reservas` conforme o fluxo
  existente. Só enviar após autorização positiva; registrar depois em
  `POST /atendimentos/{id}/mensagens-enviadas` com a mesma `chaveDeEnvio`.
- Preservar `origemTipo`, `origemRegraId`, `origemExecucaoId`, lead/atendimento e ocorrência.
  Não converter tudo para `RESPOSTA_IA` para escapar do teto de frequência.
- `wamid` é o nome legado do campo do CRM, não licença para enfiar qualquer ID Z-API ali.
  A resolução de namespaces/aliases ainda é gate do backend. Não usar `zaapId` e `messageId`
  como intercambiáveis nem prefixar o ID enviado de volta ao fornecedor em uma citação.
- `GET /envios-automacao/pendentes` e `GET /envios-proativos/pendentes` são **conciliação**,
  nunca listas de reenvio. Timeout após chamada pode significar envio efetivado.

## Formato Z-API a suportar, depois dos gates

Fontes: [entrada](https://developer.z-api.io/webhooks/on-message-received-examples),
[envio](https://developer.z-api.io/message/introduction),
[status](https://developer.z-api.io/webhooks/on-whatsapp-message-status-changes).

- Recebimento usa `instanceId`, `messageId`, `phone`, `fromMe`, `momment` e campos por tipo,
  por exemplo `text.message` ou `document.documentUrl`/`mimeType`/`fileName`.
  Esse objeto não equivale a `entry[].changes[].value.messages[]` da Uzapi.
- Aceite HTTP retorna `zaapId` (Z-API) e `messageId` (WhatsApp), com `id` como alias deste último.
- `DeliveryCallback` trata processamento, não recebimento pelo paciente.
  `MessageStatusCallback` traz `ids[]` e status; falta confirmação formal do namespace de IDs.
- `fromMe` não deve criar nova mensagem do CRM nem disparar a IA novamente.
- LID-only não gera telefone artificial: suporte inicial aprovado é a telefones, com
  descarte/quarentena técnica rastreável. Não inventar conversão removendo `@lid`.
- JPG/PNG dentro de `document` continuam DOCUMENTO, mesmo com MIME `image/*`.

## Credenciais e segurança

Saída Z-API exige instance ID, token da instância no path e header `Client-Token`.
Armazenar em Credentials/segredos backend; não logar URL completa de chamada ou erro cru.
Esses dados **não** comprovam autenticação dos callbacks. Obter confirmação oficial sobre
assinatura/header suportado, escopo e rotação antes de implementar a entrada.

Não há novos nomes de variáveis Z-API implementados nesta revisão. Não reutilizar
`WHATSAPP_WEBHOOK_SECRET`/HMAC Meta por analogia nem registrar webhook real agora.

## Homologação conjunta pendente

Após código/CI e autorização operacional, usar apenas número controlado e correlacionar:

`evento único → autorização/reserva → envio → IDs distintos → callback autenticado →
mensagem/status persistidos → WebSocket → tela após recarregar`.

Cobrir eco, callbacks repetidos/adiantados/invertidos, falha, timeout sem retry cego,
mensagem programada e encaminhamento. Apresentar somente IDs técnicos, horários, estados e
resultados sanitizados. Nenhum teste simulado local comprova o workflow publicado.

**Bloqueio operacional:** a troca não pode ocorrer enquanto o inventário e estes fluxos não
forem validados pela equipe de automação, mesmo que a PR do CRM esteja com CI aprovado.
