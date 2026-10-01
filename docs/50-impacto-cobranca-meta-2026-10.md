# Impacto da cobrança Meta em 01/10/2026 — diagnóstico e observação

## Fontes e limites de confirmação

- O comunicado de vigência usado nesta análise é a [documentação de mensagens não-template da Meta](https://developers.facebook.com/documentation/business-messaging/whatsapp/pricing/non-template-messages) indicada pela operação. O endereço respondeu com limite de acesso (HTTP 429) durante esta auditoria; por isso **a data, a abrangência por categoria e eventuais franquias gratuitas ainda exigem conferência no WhatsApp Manager da conta** antes de se afirmar um valor devido.
- A [página pública de preços da WhatsApp Business Platform](https://whatsappbusiness.com/products/platform-pricing/) descreve preço por mensagem entregue e exceção de janela gratuita de entrada, mas no momento da auditoria ainda descreve respostas de serviço como gratuitas. Há divergência temporal entre essa página e o comunicado de 01/10. Não usar uma delas isoladamente como fonte de conciliação.
- A [coleção oficial da Meta no Postman para notificações de status](https://www.postman.com/meta/whatsapp-business-platform/request/rgtfq23/message-status-update-notifications) confirma que `sent`, `delivered` e `read` podem chegar fora de ordem. Seu exemplo público não traz `pricing`.
- O contrato de preços associado a `statuses[].pricing` deve ser conferido com amostra **assinada** da conta operada: `billable`, `pricing_model`, `type` e `category` são campos conhecidos do contrato, mas o CRM não deve inferir cobrança quando o bloco estiver ausente nem interpretar valor monetário a partir deles. `pricing_analytics` da conta é a fonte para confronto agregado, níveis e cobranças; o webhook não contém tarifa em reais.
- Nenhum acesso autenticado ao WhatsApp Manager, à forma de pagamento ou ao Pricing Analytics da conta foi disponibilizado nesta auditoria. Não foi verificado saldo, crédito, instrumento de pagamento nem se a entrega seria interrompida. Essa checagem permanece com a operação, somente leitura.

## Matriz de fluxos

| Tipo de envio | Origem e observabilidade no CRM | Janela | Regra informada até 30/09 → a partir de 01/10 | Evidência disponível | Consequência |
| --- | --- | --- | --- | --- | --- |
| Texto/mídia manual | `POST /api/v1/atendimentos/mensagens`, `/mensagens/template`, `/{id}/mensagens/midia` e encaminhamento → `EnviarMensagemUseCase` → outbox → `MetaCloudApiAdapter` | Livre somente se `ultimaInteracaoDoLead > agora - janelaTextoLivre`; template fora | Serviço livre gratuito → potencialmente cobrável por entrega; template conforme categoria Meta | `wamid` do envio e `statuses[].pricing` quando recebido | Não mudar autorização nem bloqueio. Classificar só entrega observada. |
| Resposta da IA pelo CRM | `POST /internal/v1/atendimentos/{id}/responder` → outbox CRM | Mesma janela do adaptador | Serviço livre gratuito → potencialmente cobrável por entrega | Mesmo webhook Meta | Separar da linha seguinte para não contar duas vezes. |
| Envio direto pela Automação | n8n/outro sistema envia fora do CRM e registra via `POST /internal/v1/atendimentos/{id}/mensagens-enviadas` | Política externa não verificável pelo CRM | Categoria/custo determinados pela Meta, não pelo registro no histórico | `wamid` informado externamente; webhook pode chegar ou não ao canal do CRM | Não atribuir custo ao CRM sem status Meta associado; validar com n8n e Analytics. |
| Mensagem programada | `ProcessarMensagemProgramadaUseCase` → `EnviarMensagemUseCase.executarComoServico` → outbox | Mesma janela/template do CRM | Conforme tipo/categoria efetiva | Mesmo webhook Meta | Não criar cobrança síncrona no job. |
| Lembrete | `LembreteController` e `/internal/v1/atendimentos/{id}/lembretes` criam tarefa do atendente; não enviam mensagem Meta por si | Não se aplica | Não se aplica até haver envio explícito | Nenhum `wamid` pela criação do lembrete | Não contar lembrete como mensagem entregue. |
| Template de marketing/autenticação/utilidade | Rota de template do CRM, ou sistema externo não observado | Dentro/fora conforme aceitação Meta | Marketing/autenticação já cobrados; utilidade dentro da janela informada como gratuita → potencialmente cobrável em 01/10; fora já cobrável | Categoria efetiva em `statuses[].pricing`, não nome do template | Categoria pode divergir da intenção; não estimar por nome. |
| UZAPI/Autotic | Adaptador distinto selecionado por configuração | Contrato do provedor | Mudança da Meta Cloud não autoriza aplicar cobrança Meta a este adaptador | Não há `pricing` Meta confiável | Sem alteração. |

*"Potencialmente cobrável" não equivale a cobrança confirmada.* A confirmação por mensagem exige status de entrega com classificação informada pela Meta; a conciliação financeira exige Pricing Analytics/fatura. Envio aceito pela API ou status `sent` não é entrega cobrada.

## Estado técnico antes da mudança

- `MetaCloudApiAdapter` usa `janelaTextoLivre` para liberar mensagem livre; fora dela exige template. A verificação é de **permissão de envio**, não de preço. Não há razão demonstrada para mudar essa regra.
- `WebhookCanalController` autentica HMAC e filtra `phone_number_id`, aplica status local e responde 200. O `MetaCloudWebhookTradutor` só traduz estado e erro; `pricing` não atravessa a ACL. `webhook_entrada` recebe apenas POSTs com `messages[]`; POST só com `statuses[]` não fica retido ali. Essa é uma lacuna de observabilidade para cobrança e reprocessamento.
- `mensagem_id_externo` é o índice por `wamid` para mensagens conhecidas. O webhook pode chegar antes de o mapeamento ser gravado, repetido, ou com `pricing` ausente. Uma classificação durável não pode depender da ordem dos status nem sobrescrever status de entrega.
- Não foi encontrada decisão de produto para teto de gasto, substituição automática por template ou bloqueio de resposta. Nenhum desses mecanismos será introduzido sem aprovação explícita.

## Procedimento operacional pendente

1. Em acesso **somente leitura**, conferir no WhatsApp Manager a conta/WABA e número efetivamente usados por cada instância, billing ativo, método de pagamento e comunicados de vigência. Não copiar identificadores financeiros, tokens ou dados de cartão para tickets/logs.
2. Conferir no Pricing Analytics do mesmo WABA, data e fuso o volume e categorias de mensagens entregues; comparar com a classificação observada no CRM, identificando mensagens externas e sem `pricing`.
3. Antes de qualquer deploy, confirmar que a migration desta etapa (se houver) executa fora do fluxo de chat e que o backend usa a imagem da run verde. **Este documento e a PR não autorizam deploy.**
