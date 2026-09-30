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
chave ou `200` para replay. A autenticação chega no header configurado em
`AUTOMACAO_RESUMO_IA_AUTH_HEADER` (padrão `CRM-Synapse-RES`) e o segredo fica em
`AUTOMACAO_RESUMO_IA_TOKEN`. O corpo contém exatamente `atendimentoId` e `leadId`; a chave
`solicitacaoId` é o valor de `Idempotency-Key`. Valide os dois UUIDs e a chave antes de criar a linha
de idempotência na Data Table do n8n. A operação deve ser exclusiva por `solicitacaoId`; se a mesma
chave vier com outro `leadId` ou `atendimentoId`, responda `409` sem executar IA.

Sequência operacional:

1. Grave/recupere a chave com estado `PENDENTE`; altere para `PROCESSANDO` antes da primeira chamada.
2. Consulte o contexto pelo `atendimentoId` e preserve `contextoAte` sem arredondar ou substituir.
3. Grave o texto pelo endpoint EV-05 com `Idempotency-Key: solicitacaoId`. Essa escrita marca
   `CONCLUIDO` na **mesma transação**; 2xx confirma texto e estado juntos. Se `/resumo` falhar,
   `/resumo-status` não pode marcar `CONCLUIDO` isoladamente (responde 409).
   Uma chave diferente é recusada enquanto houver solicitação ativa para o atendimento,
   sem afetar a sobrescrita legada fora desse ciclo. O repositório recusa callbacks atrasados
   que tentem regredir um estado terminal.
4. Após a escrita confirmada, chame `/resumo-status` com `CONCLUIDO` apenas como confirmação
   idempotente do estado já concluído; confirme também o 2xx desse replay. Em falha definitiva,
   chame `FALHOU` com
   `erroCodigo` allowlisted e mensagem sem token, telefone, URL, payload ou conteúdo.
5. Retry somente rede/HTTP 5xx, com limite e backoff do workflow. Não repita 400, 401, 403, 404,
   409 ou 422 automaticamente.

Se o atendimento deixar de estar `EM_ATENDIMENTO` antes da gravação, o CRM responderá `409`: marque
o item como obsoleto no Data Table e não tente outro atendimento do mesmo lead. O resumo antigo não
deve ser apagado. Para incidentes, registre somente IDs técnicos, status HTTP e o estado da chave;
não registre corpo de mensagens, histórico, token ou resposta bruta do provedor.

### Quando a tela permanece em “Gerando resumo...”

O navegador consulta `GET /api/v1/atendimentos/{atendimentoId}/resumo-ia`. Enquanto o resultado
for `PENDENTE` ou `PROCESSANDO`, a ficha aberta revalida o estado na cadência técnica do cache
(30 segundos), além da invalidação por `RESUMO_IA_STATUS`. A revalidação para no estado terminal,
ao desmontar a ficha ou ao trocar de atendimento. A tela **não** inventa `FALHOU` por demora.
`CONCLUIDO` e `FALHOU` invalidam a ficha do lead; o resumo anterior continua visível após falha.

Para uma ocorrência, reúna de modo read-only a `solicitacaoId`, `atendimentoId`, `leadId` e horários.
Correlacione o POST público, a linha em `solicitacao_resumo_ia`, a linha correspondente em
`outbox_evento`, o status HTTP da entrega ao webhook, a execução **do workflow ativo**, os status
HTTP de `/resumo` e `/resumo-status`, o GET público e a presença (não o conteúdo) de
`lead.resumo_ia`/`resumo_ia_atualizado_em`. Confirme se o evento WebSocket chegou; se não, aguarde
uma revalidação da ficha aberta. Não registre corpo de mensagem, prompt, resumo, telefone, token,
cookie nem resposta bruta do provedor. Não atualize a tabela manualmente.

O arquivo `docs/n8n/resumo-ia-sob-demanda.json` é um template **inativo** (`active: false`), não
uma exportação do workflow publicado. Nele, os nós HTTP usam `neverError: true` e não verificam
explicitamente os status das respostas. Logo, `Succeeded` no n8n não prova que os callbacks
foram aceitos: consulte os status de cada nó na execução real e compare com exportação sanitizada
do workflow ativo. Não publique esse template como correção sem antes implementar/validar
classificação de 4xx versus rede/5xx, retry limitado e `FALHOU` no workflow efetivo.

Se o webhook respondeu `202`, mas **não houve callback**, o CRM conserva `PENDENTE` ou
`PROCESSANDO`: aceitação não é conclusão. Verifique primeiro a execução e a chave de idempotência
no n8n; não clique repetidamente nem altere `solicitacao_resumo_ia` por SQL. Após confirmar que
nenhuma escrita de resumo ocorreu e que a execução não pode mais prosseguir, o operador da
Automação pode comunicar `FALHOU` pelo callback autenticado, com a mesma `solicitacaoId` e o
`atendimentoId` original, usando apenas código/mensagem sanitizados. O CRM rejeita ciclo obsoleto.
Só depois do estado terminal, o usuário autorizado pode iniciar uma **nova** solicitação. Se o
callback `FALHOU` também falhar, preserve a evidência e escale; não fabrique estado terminal na UI.
