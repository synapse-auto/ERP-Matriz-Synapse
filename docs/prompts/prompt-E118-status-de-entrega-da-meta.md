# E118 — o CRM precisa saber se a mensagem chegou

## O problema

Produção, medido: **1000 mensagens `ENVIADO`, 0 `ENTREGUE`, 0 `LIDO`.** O CRM nunca soube se uma
mensagem chegou ao cliente.

A causa está no `MetaCloudWebhookTradutor`, que descarta o que não é mensagem nova:

```java
// Nem texto, nem midia suportada (status, reacao, sticker, etc.). Ignorar
```

`statuses[]` é o array em que a Meta informa `sent`, `delivered`, `read` e `failed`. Ele nunca foi
processado. E o `WebhookCanalController` sai cedo quando não há mensagens:

```java
List<String> idsExternos = tradutor.idsExternos(payloadCru);
if (idsExternos.isEmpty()) { return ResponseEntity.ok().build(); }
```

Um POST que só traz `statuses[]` cai exatamente aí.

**O custo disso já foi cobrado.** O bug do áudio (PR #48) viveu dias invisível porque a Meta devolve
`200` no envio e só depois descarta o arquivo, mandando `failed` com o código `131053`. A bolha dizia
"enviado", o cliente não recebia, e a operação só descobriu quando o cliente reclamou. Isso vale para
**qualquer** mídia que a Meta recuse depois do 200.

## A fundação já existe

- `mensagem_id_externo (wamid PK, mensagem_id, mensagem_enviada_em, atendimento_id)` — criada na V46,
  escrita pelo `PublicadorDaOutboxTransacoes` quando a Meta responde. **É o mapa de `wamid` para
  mensagem**, que é exatamente o que o `statuses[]` traz.
- `StatusEntrega` já documenta `PENDENTE → ENVIADO → ENTREGUE → LIDO` e `→ FALHOU`.
- A bolha no front já tem componente de status.

Ou seja: isto é ligação, não modelagem nova. Confirme cada afirmação acima antes de codificar.

## O que fazer

Processe `statuses[]` no caminho do webhook, **depois** do HMAC e do filtro de destino, e antes da
saída antecipada por lista de mensagens vazia.

Regras que não podem ser negociadas:

- **Status nunca anda para trás.** A Meta entrega fora de ordem com frequência: `read` pode chegar
  antes de `delivered`. Aplique só se o novo status for posterior ao atual. Um `read` seguido de um
  `delivered` atrasado tem de deixar a mensagem em `LIDO`.
- **Idempotente.** O mesmo status para o mesmo `wamid` chega mais de uma vez; a segunda vez não muda
  nada e não gera evento novo.
- **`wamid` desconhecido não é erro.** Pode ser mensagem anterior ao CRM, ou de outro sistema.
  Responda `200`, registre em log com contagem, siga em frente. Erro faz a Meta reentregar e, no
  limite, desativar o webhook.
- **`failed` guarda o motivo.** O código e o título do erro que a Meta manda precisam ficar
  acessíveis — é a diferença entre "falhou" e "falhou porque o arquivo não é suportado". Decida onde
  guardar e justifique; se precisar de coluna nova, migration na próxima livre (a V50 já foi aplicada
  em produção, **não encoste nela**).
- **Contexto de serviço obrigatório.** Não há usuário numa chamada de provedor; sem
  `ContextoDeServico` as políticas RLS negam a escrita e a atualização vira no-op silencioso — o
  mesmo padrão que a V50 documentou.
- **Tempo real.** Se a bolha atualiza por WebSocket hoje, a mudança de status tem de chegar pelo
  mesmo caminho. Não crie um segundo mecanismo.

## Testes obrigatórios

1. `sent` → `delivered` → `read` move a mensagem pelos três estados.
2. `read` chegando antes de `delivered`: fica `LIDO`, e o `delivered` atrasado não rebaixa.
3. O mesmo status duas vezes não muda nada nem duplica evento.
4. `failed` marca `FALHOU` e preserva o código do erro.
5. `wamid` desconhecido: responde 200, não estoura, não grava.
6. Payload só com `statuses[]` (sem `messages[]`) é processado — hoje ele sai antes.
7. Payload misto (mensagem nova + status) faz as duas coisas.
8. Sem contexto de serviço a escrita falharia: um teste que prove que o contexto está aplicado.

## Escopo

Não mexa no envio, no `EnviarMensagemUseCase`, no adaptador da Meta nem na busca por telefone —
há outras etapas nesses arquivos. Nenhuma política RLS muda.

## Entrega

Branch própria, Conventional Commit, push, PR contra `main`. Relatório nos sete itens do `AGENTS.md`.
No relatório, diga **quantas mensagens em produção passariam a ter status** se isto rodasse hoje —
ou explique por que não dá para estimar.
