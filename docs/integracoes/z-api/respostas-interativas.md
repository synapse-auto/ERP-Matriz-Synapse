# Z-API — respostas interativas e referências recebidas

## Contrato conferido em 26/10/2026

Fontes oficiais:

- [ReceivedCallback: exemplos de botão e lista](https://developer.z-api.io/webhooks/on-message-received-examples).
- [Resposta textual citada](https://developer.z-api.io/message/reply-message).
- [Imagem](https://developer.z-api.io/message/send-message-image), [vídeo](https://developer.z-api.io/message/send-message-video) e [documento](https://developer.z-api.io/message/send-message-document): campo de saída `messageId`.
- [Áudio](https://developer.z-api.io/message/send-message-audio): a fonte consultada não documenta citação na saída. Preservada a recusa explícita da #330; responder com TEXTO citando um áudio continua permitido.
- [Envio de botões](https://developer.z-api.io/message/send-button-list), [lista](https://developer.z-api.io/message/send-option-list) e [termos/instabilidade dos botões](https://developer.z-api.io/tips/button-status).

O repositório oficial `Z-API/z-api-docs` foi conferido no commit
`0b8d420f1f592a7b5e55d28f3e9412ed785880d6`, juntamente com o site atual.
Os exemplos abaixo são sintéticos, não são payloads de pacientes.

```json
{
  "type": "ReceivedCallback",
  "instanceId": "INSTANCIA-SINTETICA",
  "messageId": "ID-WHATSAPP-DO-CLIQUE-001",
  "referenceMessageId": "ID-WHATSAPP-DA-MENSAGEM-ORIGINAL",
  "phone": "5561999990000",
  "fromMe": false,
  "isGroup": false,
  "momment": 1791800000000,
  "buttonsResponseMessage": { "buttonId": "1", "message": "Ótimo" }
}
```

Uma escolha de lista substitui somente o objeto de resposta:

```json
{"listResponseMessage":{"selectedRowId":"2","message":"Descrição escolhida","title":"Título escolhido"}}
```

`messageId` identifica o clique e sua deduplicação; `referenceMessageId` identifica a
mensagem citada. `buttonId`/`selectedRowId` identificam opções, não mensagens WhatsApp.
Não converter nenhum desses campos em `zaapId`, UUID CRM ou chave interna de deduplicação.

## Persistência e interface

- Botão/lista válidos sem `text.message` viram mensagem `TEXTO`, com `conteudo` igual ao
  `message` escolhido. Não criam opções clicáveis nem executam novamente o botão.
- O JSONB já existente `midia_metadados` conserva `respostaInterativa` com `tipo`
  (`BOTAO`/`LISTA`), `identificador` e `titulo` opcional. Nenhuma migration é necessária.
- `referenciaExterna` conserva `provedor`, `instancia`, `namespace=WHATSAPP` e `valor`.
  A resolução consulta a identidade exata não conflitante, ligada ao mesmo lead. Não usa
  o índice legado/hash como fallback. Meta/Uzapi mantêm seu caminho anterior.
- Referência comprovada gera o snapshot já existente em `mensagem_referencia`. Histórico
  e evento de tempo real transportam o mesmo texto, metadados e citação. A navegação para
  origem fora da página usa a consulta autorizada já existente, não uma busca por IDs externos.
- Referência ausente, incompatível ou não localizada não descarta a escolha. Quando há
  referência externa não resolvida, a bolha informa pelo catálogo existente que a mensagem
  original está indisponível; não fabrica autor, UUID ou link navegável.
- Não há backfill de citações antigas nem reconciliação retroativa quando a mensagem original
  só chega depois. A referência externa fica preservada para diagnóstico; nenhum alias é inventado.
- Cancelar a citação mantém texto/anexos do composer; erro não dispara reenvio automático.

## Contrato para automação

O repasse NÃO troca o envelope por um payload Meta ou pela representação interna normalizada.
O n8n recebe os campos originais acima, inclusive `buttonsResponseMessage`,
`listResponseMessage`, `referenceMessageId` e o `messageId` próprio do clique.
Pode haver normalização JSON de espaços/ordem já existente, não remoção desses campos.

Conservar autenticação CRM→n8n por `X-Synapse-Webhook-Token` e deduplicar efeitos por
`X-Synapse-Evento-Id`, estável nas tentativas. `X-Synapse-Tentativa` aumenta no retry.
Um callback repetido agenda um só repasse lógico. Cliques com IDs de mensagem distintos
continuam distintos, mesmo com texto/opção iguais. Timeout não prova ausência de execução:
o workflow precisa deduplicar antes dos efeitos. Não foram alterados workflows publicados.

O parser da automação deve priorizar o objeto de resposta interativa presente, sem exigir
`text.message`, e manter o identificador da opção separado do rótulo. Campo obrigatório
ausente/tipo inválido é descartado com diagnóstico fixo sanitizado; não há texto inventado.
Referência inválida descarta somente o campo, não a mensagem válida. Não repassar segredo
do callback, telefone ou conteúdo a logs de diagnóstico.

## Operação e diagnóstico por etapa

Nenhuma variável nova. O opt-in já existente `Z_API_REPASSE_AUTOMACAO_HABILITADO` só deve
estar ON com destino HTTPS, token exclusivo e parser/deduplicação do workflow homologados.
No Dokploy, aplicar apenas a release revisada pelo procedimento normal; esta tarefa não
configura ou faz deploy. A proteção de entrada HTTPS/segredo/instância permanece obrigatória.

O suporte orientou habilitar **Configurações Beta → Mensagens de Botões** para envio de
botões. Essa instrução operacional foi fornecida pelo suporte; o nome exato da opção na
conta não foi verificado automaticamente. A documentação oficial consultada confirma
termos e instabilidade, mas seu artigo de termos é datado de 27/08/2024, não garantia de
entrega atual. O operador deve conferir a opção/termos na própria conta; nenhuma ativação
automática ou endpoint não documentado foi implementado.

| Etapa | Evidência a conferir (somente IDs/horários/estados por canal privado) |
| --- | --- |
| Botão não entregue | Resultado do envio, messageId/zaapId e callback de status; 200 não prova entrega |
| Clique sem callback | Entrega do callback no fornecedor; ausência de webhook no CRM não prova clique perdido |
| Callback recusado | HTTP e autenticação/proxy; não remover segredo nem CORS global |
| Recebido e descartado | webhook_entrada processado/descartes por motivo fixo, instanceId/tipo/IDs |
| Persistido sem aparecer | mensagem/correlação, GET autorizado e evento WebSocket para os mesmos recursos |
| Repasse ausente/recusado | outbox tipo automacao.webhook.repassar, publicado_em/tentativas/ultimo_erro e ID estável |
| Workflow | Recebimento e deduplicação do ID estável, parser interativo e resultado real; 2xx não prova conclusão |

Sem replay produtivo, envio real ou garantia de entrega do fornecedor nesta implementação.
Homologar com contato próprio: botão e lista, clique, histórico/reload, responder citando o
clique, conferir status e execução deduplicada do n8n. Não usar pacientes para teste.
