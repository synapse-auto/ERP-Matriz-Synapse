# Prompt E54 — uma fonte de verdade por dado no chat

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/22-bugs-abertos-26-08.md` (bugs 1, 2 e 4).
> **Pode commitar localmente a qualquer momento.** Não execute `git push` sem autorização explícita.

---

## Contexto

A tela de Atendimentos é a única do CRM com **quatro fontes de verdade sobre o mesmo dado**: o cache
paginado por cursor, o WebSocket, a atualização otimista do envio e o backfill de reconexão. Cada uma
está correta sozinha. Os três bugs desta etapa são elas discordando entre si.

Esta etapa é **frontend apenas**. Nenhuma rota muda, nenhum contrato muda, nenhuma chave de cache do
TanStack Query muda de nome.

## Bloco 1 — A mensagem duplicada

A sequência real, com rede de verdade:

1. `useEnviarMensagem.onMutate` insere a mensagem com id `temp-…`
2. o evento do WebSocket chega **antes** da resposta HTTP (o relay publica no commit; a resposta ainda
   está voltando) e `mesclarMensagens` insere a mensagem com o **id real**
3. `onSuccess` faz `atual.map(m => m.id === idOtimista ? real : m)` — substitui em posição, **sem
   deduplicar**

Sobram **dois itens com o mesmo id**. Um deles nasceu do WebSocket e tem `remetenteId`, então
`nomeDaAutoria` resolve o nome; o outro nasceu do `onSuccess`, que monta o objeto à mão com
`remetenteId: null` e `remetenteNome: null`. É por isso que aparecem duas bolhas iguais, uma com o
nome do atendente e outra sem.

Duas correções, as duas necessárias:

- **A reconciliação passa a deduplicar.** Depois de trocar o id temporário pelo real, o resultado tem
  que passar pelo mesmo funil que o WebSocket usa (`mesclarMensagens`, que já deduplica por id e
  ordena por `enviadoEm`). Não escreva uma terceira forma de mesclar: se o `onSuccess` precisa de algo
  que `mesclarMensagens` não faz, ajuste `mesclarMensagens` e faça os dois caminhos usarem a mesma.
- **A reconciliação para de descartar autoria.** O objeto montado no `onSuccess` precisa preservar
  `remetenteId` e `remetenteNome` — hoje ele os zera, e é isso que faz a mesma mensagem renderizar de
  dois jeitos. Se o backend não devolve esses campos na resposta do envio, use os do usuário
  autenticado; diga no relatório qual dos dois você fez.

O mesmo defeito existe em `use-enviar-midia.ts`. Confirme e corrija junto.

## Bloco 2 — As bolhas sobrepostas

Duas causas independentes, as duas reais.

**A primeira é consequência do Bloco 1.** `ListaMensagens` usa `key={mensagem.id}` numa lista
virtualizada com itens em `position:absolute`. Com dois itens de mesmo id, o React reaproveita o nó
errado e os `translateY` colidem. Corrigir o Bloco 1 já elimina esta.

**A segunda é independente e continua depois.** `useVirtualizer` está **sem `getItemKey`**. Sem isso,
o cache de alturas medidas é indexado por **posição**, não por mensagem. Como `onCarregarMais` insere
páginas antigas **no topo**, todos os índices andam e cada altura guardada passa a valer para a
mensagem errada.

- Passe `getItemKey` derivado do id da mensagem.
- `estimateSize` está em 64 para uma bolha de uma linha (~44px). Aproxime da altura real; o objetivo é
  o primeiro quadro não nascer sobreposto.
- O item medido inclui condicionalmente o separador de data e a linha de início do atendimento. Isso é
  aceitável, mas confirme que `measureElement` remede quando esses elementos entram ou saem.
- A busca dentro da conversa filtra a lista (`filtradas`). Confirme que filtrar não deixa alturas
  velhas valendo.

## Bloco 3 — A conversa aberta não acompanha a transferência

`PaginaAtendimentosCliente` guarda a conversa aberta num `useState<CartaoAtendimento>` — uma **cópia**
do cartão que foi clicado. `useTransferirAtendimento.onSuccess` invalida `["atendimentos"]` e não toca
nesse estado. A lista à esquerda atualiza; a conversa aberta continua com o dono antigo — e ela
alimenta o cabeçalho, o painel da direita (`responsavelNome`), o composer e a autoria das mensagens
(`atendenteId`/`atendenteNome` vão para `nomeDaAutoria`).

- **Guarde o `atendimentoId`, não o cartão.** A conversa aberta passa a ser derivada da lista já
  carregada, pelo id. Quando a lista atualiza — por transferência, por finalização, por evento em
  tempo real — a conversa aberta atualiza junto, de graça.
- Trate o caso de o id não estar mais na lista atual (mudou de visão, foi finalizado): a tela precisa
  se comportar, não quebrar.
- A revogação que fecha a conversa do dono anterior é **intencional** e continua. Só garanta que o
  aviso diga o que aconteceu.

**E o botão "abrir" da notificação de transferência recebida está quebrado:** ele chama
`setLeadParaAbrir(notificacao.dados.leadId)`. Se for o mesmo lead de antes, o valor não muda, o efeito
não dispara e nada acontece. Use um gatilho que mude sempre — um objeto com contador, ou um efeito que
não dependa da igualdade do id.

## Bloco 4 — O que não pode regredir

- Ordem das mensagens, paginação por cursor e o backfill ao reconectar continuam iguais.
- O status `PENDENTE → ENVIADO → FALHOU` e o botão de reenviar continuam funcionando, inclusive quando
  a transição chega pelo WebSocket depois da reconciliação.
- Nada de `remetenteNome` inventado: se não houver nome, a bolha continua sem rótulo, como hoje.

---

## Verificação

- `npm run lint`, `npm run typecheck` e `npm test` no `frontend/`, verdes.
- **Teste da corrida, e ele é o teste desta etapa:** simule o evento do WebSocket chegando **antes** da
  resposta do `enviarMensagem` e prove que fica **uma** mensagem, com autoria preenchida. Depois
  inverta a ordem e prove o mesmo resultado. Se você não escrever este teste, o bug volta na próxima
  etapa.
- Teste de que `mensagens` não contém ids repetidos depois de otimista + WebSocket + backfill sobre a
  mesma mensagem.
- Teste de que carregar página anterior não altera a associação altura↔mensagem (via `getItemKey`).
- Teste de que, ao transferir, o cabeçalho e o painel passam a mostrar o novo responsável sem
  precisar clicar de novo na conversa.
- Teste de que o botão "abrir" da notificação funciona **duas vezes seguidas para o mesmo lead**.

## Relatório

1. Se o backend devolve `remetenteId`/`remetenteNome` na resposta do envio, ou se você usou o usuário
   autenticado.
2. Se `use-enviar-midia.ts` tinha o mesmo defeito.
3. Qual `estimateSize` você adotou e por quê.
