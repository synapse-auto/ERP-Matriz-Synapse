# Prompt E32 — Um POST da Meta com várias mensagens grava só a primeira

> Leia `AGENTS.md`. Entrega em 25/08.
> Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## Contexto — perda silenciosa de mensagem de cliente

A Meta agrupa eventos: um único POST pode trazer várias `entry`, cada uma com várias
`changes`, cada uma com várias `messages`. O CRM processa **uma**.

`MetaCloudWebhookTradutor`:

```java
private Optional<JsonNode> primeiraMensagem(String payloadCru) {
    return valor(payloadCru)
            .map(valor -> valor.path("messages"))
            .flatMap(mensagens -> mensagens.isArray() && !mensagens.isEmpty()
                    ? Optional.of(mensagens.get(0))     // <- só a primeira
                    : Optional.empty());
}

private Optional<JsonNode> valor(String payloadCru) {
    ...
    JsonNode mudancas = entradas.get(0).path("changes");   // <- só a primeira entry
    ...
    return Optional.of(mudancas.get(0).path("value"));     // <- só a primeira change
}
```

O cliente manda três mensagens seguidas, a Meta agrupa, o CRM grava uma e responde `200`.
**Sem log, sem alerta, sem tentativa.** As outras duas somem — e o atendente responde a uma
conversa que não é a que o cliente teve.

Isto é pior que a aba cair: queda alguém percebe.

### A incoerência que a E27 deixou visível

`destinos()` **já percorre todas** as `entry` e `changes` para decidir o filtro por
`phone_number_id`:

```java
for (JsonNode entrada : entradas(payloadCru)) {
    for (JsonNode mudanca : mudancas) { quantidadeEventos++; ... }
```

O filtro é cuidadoso por evento; a tradução lê só o primeiro. As duas metades da mesma classe
discordam sobre o que é o payload.

### Por que não é `get(0)` → `for`

`webhook_entrada.id_externo` é **PRIMARY KEY** (`V16`): uma linha por POST, chaveada pelo id da
**primeira** mensagem. `ProcessadorDeWebhookEntradaOperacoes.processar` chama
`tradutor.traduzir(payloadCru)` e recebe um `Optional`, registra uma mensagem e marca
processado. O pipeline inteiro é single-message: controller, tabela e processador.

---

## Bloco 1 — O tradutor devolve todas as mensagens do canal

- `TradutorDeCanal.traduzir` passa a devolver **`List<MensagemRecebidaDoCanal>`**, na ordem em
  que a Meta mandou. Lista vazia onde hoje é `Optional.empty()` — o significado ("não é
  mensagem de cliente") não muda.
- Percorre **todas** as `entry` × `changes` × `messages`, não só as primeiras.
- Tipo não suportado (status, reação, sticker) continua sendo **ignorado individualmente**, sem
  derrubar as mensagens boas do mesmo payload. Hoje um `status` na posição 0 já descarta o POST
  inteiro — verifique se é isso mesmo que acontece e diga no relatório.

**`nomeDeExibicao` está errado para payload múltiplo.** Ele lê `contacts[0]`:

```java
.map(contatos -> contatos.get(0).path("profile").path("name").asText(null))
```

Com mensagens de remetentes diferentes no mesmo POST, todas herdam o nome do primeiro contato —
e o lead errado recebe o nome de outra pessoa. Resolva o contato **por mensagem**, casando
`messages[].from` com `contacts[].wa_id`. Sem correspondência, `null` (o comportamento atual
quando não há contato).

## Bloco 2 — Idempotência no nível da mensagem, não do payload

`webhook_entrada.id_externo` é chave do **POST**, não da mensagem. Enquanto o CRM processava uma
mensagem por POST as duas coisas coincidiam. Deixam de coincidir aqui.

O caso concreto: a Meta reentrega e reagrupa. O POST original trouxe `[A, B]` e a reentrega traz
`[B]`. O id da reentrega é o de `B`, que não colide com a linha chaveada por `A` — e `B` vira
mensagem duas vezes na conversa.

**Prior art nesta base:** a E30 resolveu exatamente esta forma de problema no sentido de saída,
com `mensagem_automacao_idempotencia` (`wamid` PRIMARY KEY, tabela estreita não particionada),
porque `mensagem` é particionada por `enviado_em` e não aceita UNIQUE global. A migration `V29`
explica o raciocínio. O caminho de entrada tem a mesma necessidade e hoje **não tem índice
nenhum** — `mensagem` não possui coluna `id_externo`.

Decida entre generalizar a tabela da E30 para os dois sentidos ou criar a irmã de entrada, e
**justifique no relatório**. O que não pode é o caminho de entrada seguir sem idempotência por
mensagem depois desta etapa.

`webhook_entrada` continua com **uma linha por POST**, chaveada como hoje.

> **Não exploda o payload em uma linha por mensagem.** A `V17` guarda o payload cru byte a byte
> justamente para permitir reconferir o HMAC e reprocessar depois de corrigir um bug de
> tradução. Reescrever ou fatiar o payload destrói essa propriedade — e ela é o que torna
> recuperável o que já foi perdido.

## Bloco 3 — O processador drena a lista inteira

`ProcessadorDeWebhookEntradaOperacoes.processar`:

- Percorre a lista. Cada mensagem resolve o próprio lead por telefone.
- **Falha no meio não pode perder o que já passou nem duplicar o que já entrou.** Decida entre
  transação por mensagem ou por payload; com a idempotência do Bloco 2, reprocessar o payload
  inteiro é seguro. Descreva a escolha.
- `marcarProcessado` só depois de todas.
- Payload sem nenhuma mensagem de cliente: `marcarProcessado`, como hoje.

**Não altere o comportamento de payload MISTO.** Continua descartado com log `ERROR`. Não é
otimização pendente: é sinal de configuração errada na Meta, e processar parcialmente esconderia
o sinal. A E27 decidiu isso de propósito.

## Bloco 4 — Medir o que já foi perdido

O payload cru das mensagens perdidas **está em `webhook_entrada`**. Antes de qualquer coisa,
meça:

- Quantas linhas de `webhook_entrada` contêm mais de uma mensagem
- Quantas mensagens ao todo ficaram para trás
- Entre que datas

Documente em `docs/18` o procedimento de reprocessamento — **sem executar**. Reprocessar traz
para a conversa mensagem de dias atrás, e isso é decisão do arquiteto, não da etapa. Parte do
período está contaminada pelo incidente de 16/08 (conversa de cliente de terceiro), então o
reprocesso cego é errado.

---

## Testes — a proteção nasce com um teste que a viola

- POST com **três mensagens** em uma `change`: as três viram mensagem, na ordem, cada uma no
  lead certo.
- POST com **duas `entry`**, uma mensagem cada, ambas do canal cadastrado: as duas entram.
- POST com **duas mensagens de remetentes diferentes**: cada lead recebe o **próprio** nome de
  exibição. Este é o teste que pega o `contacts[0]`.
- POST com `[status, mensagem]` nessa ordem: a mensagem entra. Hoje não entra.
- Reentrega do **mesmo** payload: nada duplica.
- Reentrega **reagrupada** (`[A,B]` e depois `[B]`): `B` não duplica. Este é o teste do Bloco 2 —
  sem ele o bloco não está feito.
- Payload misto por `phone_number_id`: continua descartado, com `ERROR`. Regressão da E27.
- Teste de **ponto de entrada**, chamando o controller como o runtime chama.

## Definição de pronto

- [ ] `traduzir` devolve `List`, percorrendo todas as entry × changes × messages
- [ ] Nome de exibição resolvido por mensagem, via `contacts[].wa_id`
- [ ] Tipo não suportado ignorado individualmente, sem derrubar o payload
- [ ] Idempotência por id de mensagem no caminho de entrada, com a decisão justificada
- [ ] `webhook_entrada` continua com uma linha por POST e o payload cru intacto
- [ ] Processador drena a lista, com política de falha parcial descrita
- [ ] MISTO inalterado
- [ ] Os oito testes acima
- [ ] Medição do passivo + procedimento de reprocessamento em `docs/18`, **não executado**
- [ ] CI verde com **número da run**

## No relatório

1. **Quantas mensagens já foram perdidas**, e entre que datas. É o número que decide se vale
   reprocessar.
2. Se um evento de `status` na primeira posição já descartava o POST inteiro — e desde quando.
3. A decisão do Bloco 2, com o porquê.
4. Variável nova no Dokploy: a expectativa é **nenhuma**. Se precisou de uma, item próprio.
5. O SHA final, porque `SYNAPSE_IMAGE_TAG` é fixado por commit (`docs/18`).

---

## Fora desta etapa

Executar o reprocessamento. Trazer conversa antiga para a tela é decisão do arquiteto, e parte
do período está contaminada pelo incidente de 16/08.
