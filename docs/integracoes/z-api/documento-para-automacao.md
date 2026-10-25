# Z-API — documento para automação

## Complemento vigente — entrada protegida (25/10/2026)

1. CRM recebe `POST /webhook/z-api`, autentica antes do corpo, confirma `instanceId`, grava
   fila idempotente e responde sem baixar midia nem esperar o n8n. Segredo de callback nunca
   acompanha o repasse. A equipe nao precisa/nem deve fabricar assinatura Meta para Z-API.
2. Opt-in `Z_API_REPASSE_AUTOMACAO_HABILITADO=true` exige destino HTTPS configurado e
   `AUTOMACAO_WEBHOOK_REPASSE_TOKEN` exclusivo. Contrato **proprio CRM→n8n**:
   `X-Synapse-Webhook-Token`, `X-Synapse-Evento-Id` estavel e `X-Synapse-Tentativa`.
   Validar token no Webhook n8n antes de iniciar o fluxo; deduplicar pelo ID de evento antes
   de efeitos. Nao e o header nativo da Z-API (nome ainda pendente), nem token interno/API.
3. Recebidos suportados preservam envelope JSON Z-API; status, eco/fromMe, grupos, LID-only
   e tipos recusados nao disparam IA. Parser usa `text.message`, `image.imageUrl`,
   `audio.audioUrl`, `video.videoUrl`, `document.documentUrl`/`fileName`/`mimeType`.
   JPG/PNG em `document` continuam DOCUMENTO; nao procurar mediaId Uzapi. CRM persiste
   bytes no storage privado pelo worker; nao substituir pela URL temporaria no historico.
4. Preferir resposta via `/internal/v1/atendimentos/{id}/responder`, com autenticacao e
   idempotencia existentes, para usar outbox/correlacao do CRM. Nos diretos ao provedor,
   migrar explicitamente os nos: URL, `Client-Token` de SAIDA, payload, reservas e registro.
   Trocar configuracao do CRM nao muda esses nos e nao os audita automaticamente.
5. Guardar `zaapId` e `messageId` distintos. HTTP 200 com IDs fecha despacho, nao prova
   entrega. Nao reenviar mensagem PENDENTE aceita. `ids[]` so aplica apos confirmar namespace;
   default `NAO_CONFIRMADO` conserva diagnostico. Callback precoce fica duravel sem vinculo.
6. Outbox de repasse ganha origem aditiva. Origem divergente ou legado sem origem ao entrar
   na Z-API e bloqueado sem HTTP; nao editar payload nem reativar esgotados em massa.
   Drenar/conferir legado no provedor original e combinar filas/timers externos antes do corte.

Devolver inventario e evidencias sanitizadas dos fluxos reais abaixo. Nenhum workflow publicado
foi alterado nesta entrega. O restante deste documento conserva o historico da preparacao;
o [roteiro vigente](./homologacao-protegida.md) prevalece sobre bloqueios de codigo ja superados.

24/10/2026. Destinatária: equipe responsável pelo n8n (Dylan).
**Preparação, não liberação de troca.** Saída experimental de texto/mídia existe desligada; callbacks não autenticados
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

Variáveis opcionais implementadas na F2: Z_API_HABILITADO (manter false), Z_API_INSTANCIA_ID,
Z_API_TOKEN, Z_API_CLIENT_TOKEN, Z_API_CREDENCIAL_ID e Z_API_LIMITE_RESPOSTA_BYTES;
ver [fase-2-saida.md](./fase-2-saida.md). Mídia não adiciona variável. Não reutilizar
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

## Passagem incremental para a equipe

- Saída CRM experimental: texto/imagem/áudio/vídeo/documento pelo storage/worker.
  PNG/JPG como DOCUMENTO preservado. Áudio não recebe caption; não perder texto silenciosamente.
- 200 com zaapId/messageId fecha **despacho** da outbox e mantém PENDENTE na mensagem.
  Não adicionar retry/consulta periódica por esse estado; timeout após POST exige conciliação.
- Preparar, sem publicar, parser Z-API e inventário de chamadas/filas/reservas/registros.
  Não chamar /responder para apenas registrar um envio direto: isso criaria nova saída.
- Entrada/status/correlação operacional ainda não existem; não preencher wamid com zaapId
  nem registrar webhook até G1/G3 implementados/testados. Não mudar o contrato interno.
- Encaminhar ao fornecedor a [pergunta oficial](./autenticacao-callback-pendente.md) e
  devolver resposta sanitizada. Não enviar token, conversa ou URL privada.
- [Checklist e responsabilidades](./bloqueios-troca.md). Nenhum workflow real foi alterado;
  passagem não é homologação e não autoriza produção.
