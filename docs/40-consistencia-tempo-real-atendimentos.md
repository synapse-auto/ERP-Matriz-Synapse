# 40. Consistência em tempo real dos atendimentos

Este documento descreve o sinal canônico de mudança de estado, a recuperação por snapshot e o
runbook de diagnóstico. O WebSocket não é fonte de verdade: ele informa que um atendimento mudou;
o snapshot REST, autorizado pela RLS, governa inbox, conversa selecionada, cabeçalho, participantes,
painel lateral e composer.

## Fluxo transacional

```text
REST ou comando interno
  -> caso de uso e lock do lead/atendimento
  -> mudança de negócio + incremento de atendimento.versao_evento
  -> evento de domínio na mesma transação
  -> outbox_evento tempo-real.atendimento.estado.v1
  -> COMMIT
  -> worker reserva a outbox em transação curta
  -> Redis synapse:atendimento:{atendimentoId}
  -> worker marca a outbox publicada em outra transação curta
  -> subscriber local revalida autorização
  -> /user/queue/notificacoes e/ou /user/queue/atendimento.{atendimentoId}
  -> reconciliador do navegador
  -> GET /api/v1/atendimentos/{atendimentoId}/estado
```

O incremento de `versao_evento` ocorre na mesma transação da ação. A função
`app_avancar_versao_evento_atendimento` é `SECURITY DEFINER`, só devolve a nova sequência e não
concede leitura nem modifica estado de negócio. Ela é necessária para operações autorizadas, como
pedido de entrada, nas quais o solicitante ainda não alcança a linha pela RLS.

Um `@TransactionalEventListener(BEFORE_COMMIT)` grava a outbox na própria transação; ele não publica
na rede. O worker só reserva a linha depois do commit e não mantém conexão SQL durante a chamada ao
Redis. Falha de Redis reage agenda a linha com backoff; esgotamento permanece auditável. Se o Redis
aceitar e o processo cair antes da confirmação SQL, o mesmo `eventoId` pode ser publicado novamente e
será deduplicado no navegador. Rollback não deixa linha nem frame.

Redis Pub/Sub permanece _at-most-once_ para cada tentativa já aceita pelo Redis. Uma sessão offline
recupera o estado pelo snapshot obrigatório na próxima conexão.

## Contrato `atendimento.estado.v1`

Envelope publicado no canal Redis do atendimento e entregue no STOMP:

```json
{
  "tipo": "ATENDIMENTO_ESTADO",
  "contrato": "atendimento.estado.v1",
  "eventoId": "uuid",
  "versaoContrato": 1,
  "dados": {
    "atendimentoId": "uuid",
    "leadId": "uuid",
    "eventoTipo": "ATENDIMENTO_TRANSFERIDO",
    "versao": 7,
    "ocorridoEm": "2026-09-12T12:00:00Z"
  }
}
```

Regras do contrato:

- `eventoId` identifica uma emissão e permite deduplicação entre filas e após reassinatura;
- `versao` é estritamente crescente por `atendimentoId`, nunca por `leadId`;
- `atendimentoId` é a âncora do ciclo. Um novo ciclo do mesmo lead tem outro identificador e outra
  sequência;
- `eventoTipo` informa a causa técnica: início, mensagem recebida/enviada, ação da automação,
  transferência, retorno à IA, finalização e mudanças de participação;
- o envelope não contém nome, telefone, conteúdo de mensagem, mídia, URL, token, audiência ou segredo.

O mesmo evento pode chegar pela fila pessoal e pela assinatura da conversa selecionada. Isso é
intencional: a fila pessoal atualiza a inbox mesmo quando a conversa não está aberta; a assinatura
selecionada reduz latência na conversa ativa. O navegador processa o mesmo `eventoId` uma vez.

## Audiência e RLS

O relay não embute destinatários. Na entrega, o subscriber calcula usuários ativos que refletem a
política RLS vigente: papéis amplos, responsável atual, participantes ativos e, quando o estado é
`EM_IA` ou `FINALIZADO`, atendentes autorizados pela visão correspondente.

Toda assinatura selecionada é revalidada em cada `ATENDIMENTO_ESTADO`, mesmo antes do TTL. Falha de
consulta também falha fechada: a assinatura é removida, `/user/queue/revogacoes` é enviado e o frame
de estado não chega à conversa. A fila pessoal só recebe usuários retornados pelo recorte autorizado.

## Snapshot canônico

`GET /api/v1/atendimentos/{atendimentoId}/estado` devolve, em uma única transação sob RLS:

- `cartao`: lead, responsável e status usados pela conversa selecionada;
- `versao`: versão persistida do evento;
- `participantes`: participantes ativos;
- `usuarioAtualEhResponsavel` e `usuarioAtualParticipa`;
- `podeEnviar`: permissão canônica do composer para o atendimento visível e aberto.

Atendimento inexistente, finalizado fora do recorte ou invisível responde `404` sem revelar qual das
condições ocorreu. O frontend limpa a seleção. Um comando ancorado em atendimento conhecido, porém
finalizado, responde `409` Problem Details e não cria mensagem, mídia, outbox, transferência ou ciclo.

Antes de texto, template, áudio ou mídia, o composer revalida esse mesmo snapshot. Rascunho, citação e
anexos preparados só são limpos depois de uma validação e envio aceitos.

## Ordem, deduplicação e reconexão

O reconciliador mantém uma janela limitada dos últimos `eventoId` e a maior `versao` conhecida por
atendimento:

1. evento repetido é ignorado;
2. versão menor ou igual ao snapshot conhecido é ignorada;
3. evento de outro `atendimentoId`, ainda que tenha o mesmo `leadId`, não altera a conversa aberta;
4. eventos concorrentes do mesmo atendimento compartilham uma única busca de snapshot;
5. se uma versão maior chegar durante a busca, uma nova leitura é feita antes de liberar o composer;
6. evento de atendimento não selecionado invalida apenas os recortes de inbox afetados.

Cada conexão bem-sucedida recebe um número de ciclo local. Na primeira conexão e em toda reconexão, o
composer e os incrementais de mensagem permanecem bloqueados até o snapshot do `atendimentoId`
selecionado terminar para esse ciclo. Frames de mensagem recebidos nesse intervalo são recuperados
pelo histórico HTTP; não há polling periódico, reload nem timeout de estado.

## Observabilidade

Os logs do sinal canônico usam somente `atendimentoId`, `leadId`, `eventoId`, `versao` e `tipo`. Para
correlacionar um incidente, conserve esses cinco campos, horário, código HTTP e ciclo de conexão. Não
copie texto, nome, telefone, mídia, URL assinada, JWT, `X-Synapse-Token` ou payload real.

## Runbook

### `lead ... não encontrado`

1. Confirme se o request levou o `atendimentoId` selecionado e a mesma `Idempotency-Key` do clique.
2. Consulte o snapshot pelo `atendimentoId`. `404` indica ciclo fora da visibilidade; `409` no comando
   indica ciclo finalizado conhecido. Nenhum deles é falha do provedor.
3. Verifique se a mensagem ou chave já existe antes de qualquer reenvio; siga também o
   [runbook de envio](39-runbook-consistencia-envio-atendimento.md).
4. Se o request levou apenas o lead ou outro ciclo, registre defeito de âncora no cliente. Não abra
   ciclo nem transfira manualmente para “corrigir” a tela.

### Evento duplicado

1. Compare `eventoId`. O mesmo ID nas filas pessoal e selecionada é entrega redundante esperada e deve
   ser aplicado uma vez.
2. IDs diferentes com a mesma `versao` indicam emissão duplicada no backend; correlacione os logs do
   caso de uso e do relay.
3. Um único ID aplicado duas vezes indica perda da janela de deduplicação ou nova instância do store;
   confirme se houve reload/reconexão e se o snapshot já tinha versão igual ou maior.
4. Não reprocessar Redis nem criar evento compensatório sem decisão operacional.

### Evento tardio ou de ciclo anterior

1. Compare `atendimentoId` antes de comparar `leadId`. IDs diferentes são ciclos diferentes.
2. No mesmo atendimento, compare a `versao` do frame com a do snapshot; versão menor ou igual deve ser
   descartada.
3. Se uma versão maior não é alcançada pelo snapshot, investigue réplica/banco e a transação que
   incrementou a sequência. Não aplique o payload diretamente ao cache.

### Inbox e conversa divergentes

1. Capture visão atual, `atendimentoId` selecionado, ciclo de conexão e versão do snapshot.
2. Consulte o snapshot com o mesmo usuário `ATENDENTE`. `404` deve limpar a conversa; `200` deve
   atualizar cabeçalho, participantes, painel e composer juntos.
3. Confirme que o evento invalidou as queries de inbox, sem substituir a seleção por outro atendimento
   do mesmo lead.
4. Se o composer estiver ativo após finalização/revogação, bloqueie o incidente como falha de
   autorização; não peça que o atendente tente enviar para testar.
