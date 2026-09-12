# 39. Runbook de consistência do envio de atendimento

Este roteiro diagnostica um envio de texto ou template sem reproduzir mensagens nem expor dados do
cliente. Ele não autoriza reprocessamento, alteração de banco ou chamada ao provedor; qualquer ação
desse tipo precisa de decisão operacional explícita.

## Estado esperado por tentativa

| Sinal observado | Estado persistido esperado | Ação segura |
| --- | --- | --- |
| HTTP `200`, mensagem `PENDENTE` | Uma mensagem e uma outbox; `lead.atendente_responsavel_id` e `atendimento.atendente_id` apontam para o remetente, ambos em estado humano | Aguardar o worker; não reenviar por causa de `PENDENTE` |
| HTTP `409` para clique ancorado | Nenhuma mensagem, outbox ou conversa nova | Atualizar a tela e confirmar o ciclo aberto antes de uma ação nova |
| HTTP `4xx` de validação, janela ou visibilidade | Nenhuma gravação parcial | Corrigir a condição de negócio; não atribuir a falha ao provedor |
| `5xx` ou perda de rede após o clique | Resultado inicialmente desconhecido | Consultar o histórico pela mesma `Idempotency-Key`; a UI faz três tentativas e mantém a bolha pendente enquanto isso |
| Chave encontrada no histórico | A mensagem já foi aceita, mesmo que a resposta HTTP tenha se perdido | Tratar como sucesso; repetir a mesma chave devolve a mesma mensagem sem nova outbox |
| Chave ausente depois da reconciliação | A UI exibe `FALHOU`, sem afirmar recusa do provedor | Reenviar somente com a mesma chave da tentativa; não criar uma chave nova para o mesmo clique |
| Outbox temporariamente recusada | Mensagem permanece `PENDENTE`; a mesma linha de outbox tem próxima tentativa | Acompanhar o worker; não disparar uma segunda mensagem manual |
| Outbox esgotada | Mensagem `FALHOU` e a linha continua auditável | Abrir incidente com os identificadores técnicos e decidir o tratamento antes de qualquer reprocessamento |

## Correlação sem conteúdo pessoal

Registre somente `Idempotency-Key`, `mensagemId`, `atendimentoId`, `outboxId`, data/hora e os estados
HTTP/outbox/mensagem. Esses UUIDs são identificadores técnicos da tentativa. Não copie texto da
mensagem, nome do lead, telefone, URL assinada, payload de webhook, `Authorization`, JWT, token de
provedor ou segredo para ticket, chat ou log manual.

Com acesso operacional autorizado ao banco, as consultas abaixo usam apenas esses identificadores:

```sql
SELECT m.id AS mensagem_id,
       m.atendimento_id,
       m.status_entrega,
       a.status AS status_atendimento,
       a.atendente_id AS responsavel_atendimento,
       l.atendente_responsavel_id AS responsavel_lead
  FROM mensagem m
  JOIN atendimento a ON a.id = m.atendimento_id
  JOIN lead l ON l.id = a.lead_id
 WHERE m.id = :mensagem_id;

SELECT chave_idempotencia,
       atendimento_id,
       mensagem_id,
       mensagem_enviada_em,
       transferiu_lead
  FROM mensagem_envio_idempotencia
 WHERE chave_idempotencia = :chave_idempotencia;

SELECT id AS outbox_id,
       tentativas,
       publicado_em,
       esgotado_em,
       proxima_tentativa_em
  FROM outbox_evento
 WHERE tipo = 'canal.mensagem.enviar'
   AND payload->>'mensagemId' = :mensagem_id;
```

Para um `200`, os dois identificadores de responsável devem ser iguais. Para um `409` ancorado, as
três consultas não devem revelar uma nova mensagem, chave concluída ou outbox referente à tentativa.
Se a consulta por chave encontrar a mesma mensagem, a resposta HTTP perdida é apenas um caso de
reconciliação, não um segundo envio.

## Sequência de triagem

1. Reúna o código HTTP e a `Idempotency-Key` do navegador, sem capturar o corpo da mensagem.
2. Consulte a chave antes de qualquer novo clique. Se já houver `mensagem_id`, pare o reenvio.
3. Compare os estados de lead e atendimento. Divergência entre responsáveis ou estado IA após um
   `200` é incidente de consistência e deve bloquear qualquer ajuste manual.
4. Consulte a outbox pelo `mensagemId`. Uma linha não publicada é trabalho assíncrono pendente; uma
   linha esgotada precisa de decisão operacional, nunca de um replay cego.
5. Se a tela ainda divergir, atualize o histórico do atendimento. Eventos WebSocket do ciclo antigo
   não devem alterar a conversa ativa; conserve os IDs técnicos para investigação do servidor.
