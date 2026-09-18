# Runbook EV-05 — diagnóstico seguro

O EV-05 é executado pelo cron do n8n (cinco horas). O backend não agenda ciclos, não chama modelo
de IA e não concede acesso PostgreSQL ao workflow.

## Diagnóstico

1. Confirme que o n8n chama `http://synapse-backend-internal:8080/internal/v1` e envia
   `X-Synapse-Token` por credential, nunca no JSON do workflow.
2. Consulte `GET /automation-config/ev05` e confirme as duas configurações independentes.
3. Varra `GET /ev05/candidatos` usando `temMais`; registre apenas `leadId`, `atendimentoId`,
   `status HTTP` e `atualizadoEm`.
4. Para um candidato, consulte o resumo/preenchimento e o contexto limitado. O campo
   `contextoAte` deve acompanhar a escrita do resumo.
5. Em erro, guarde o status RFC 7807, ids técnicos e a `Idempotency-Key` truncada/mascarada. Nunca
   registre conteúdo, telefone, CPF, URL assinada, token, prompt ou resposta do modelo.

## Reprocessamento

Repetir uma escrita com a mesma chave devolve a resposta já concluída. Uma chave incompatível
responde `409`. Não faça SQL, não limpe marcos e não tente processar atendimento `FINALIZADO`.

## Resumo sob demanda

O workflow é acionado pelo webhook `AUTOMACAO_RESUMO_IA_URL` e deve responder `202` para uma nova
chave ou `200` para replay. Valide `evento`, os três UUIDs e `solicitadoEm` antes de criar a linha
de idempotência na Data Table do n8n. A operação deve ser exclusiva por `solicitacaoId`; se a mesma
chave vier com outro `leadId` ou `atendimentoId`, responda `409` sem executar IA.

Sequência operacional:

1. Grave/recupere a chave com estado `PENDENTE`; altere para `PROCESSANDO` antes da primeira chamada.
2. Consulte o contexto pelo `atendimentoId` e preserve `contextoAte` sem arredondar ou substituir.
3. Grave o texto pelo endpoint EV-05 com `Idempotency-Key: solicitacaoId`.
4. Ao concluir, chame `/resumo-status` com `CONCLUIDO`; em falha definitiva, chame `FALHOU` com
   `erroCodigo` allowlisted e mensagem sem token, telefone, URL, payload ou conteúdo.
5. Retry somente rede/HTTP 5xx, com limite e backoff do workflow. Não repita 400, 401, 403, 404,
   409 ou 422 automaticamente.

Se o atendimento deixar de estar `EM_ATENDIMENTO` antes da gravação, o CRM responderá `409`: marque
o item como obsoleto no Data Table e não tente outro atendimento do mesmo lead. O resumo antigo não
deve ser apagado. Para incidentes, registre somente IDs técnicos, status HTTP e o estado da chave;
não registre corpo de mensagens, histórico, token ou resposta bruta do provedor.
