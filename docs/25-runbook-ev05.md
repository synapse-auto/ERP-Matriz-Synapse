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
