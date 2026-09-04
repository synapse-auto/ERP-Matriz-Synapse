# Prompt E134 — A resposta interativa do cliente é descartada em silêncio

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`fix/resposta-interativa-descartada`) e PR. **Sem merge, sem deploy.**
> **Somente backend.** Sem migration, sem alteração de contrato, sem frontend.
> `cd backend && ./mvnw -pl crm-app -am verify`.

**Bug em produção, com cliente reclamando.** Quando o cliente toca numa opção de lista ou num
botão no WhatsApp — escolher o atendente (EV-03) ou dar a nota do atendimento (EV-08) —, a
resposta **nunca vira mensagem no CRM**. Some. O histórico mostra a pergunta da IA e nada depois.

Um único defeito, dois sintomas visíveis: a escolha do atendente não aparece na conversa, e a
avaliação não aparece.

---

## A causa, já isolada — não reinvestigue

`MetaCloudWebhookTradutor.java:285`:

```java
private static String tituloDaResposta(JsonNode interativa) {
    String tipo = interativa.path("type").asText();
    if ("button".equals(tipo)) {
        return interativa.path("button_reply").path("title").asText(null);
    }
    if ("list".equals(tipo)) {
        return interativa.path("list_reply").path("title").asText(null);
    }
    return null;
}
```

e o consumidor, na linha 204:

```java
if ("interactive".equals(tipoMeta)) {
    String titulo = tituloDaResposta(no.path("interactive"));
    if (titulo == null || titulo.isBlank()) {
        continue;                       // descarta em silêncio
    }
    ...
}
```

`"button"` e `"list"` são os valores de `type` da mensagem que **sai**. Na resposta que **chega**,
a Meta manda `"button_reply"` e `"list_reply"`. A comparação nunca casa, `tituloDaResposta`
devolve `null`, e o `continue` apaga a mensagem antes de qualquer gravação.

**Confirmado com payload de produção** (`webhook_entrada`, 02/09, entre 16:15 e 16:20 — a linha
tem `processado_em` preenchido, ou seja, o webhook foi dado como processado e produziu zero
mensagens):

```json
"interactive":{"type":"list_reply","list_reply":{"id":"ev03_atendente_6701a2f8-...","title":"Mi…
"interactive":{"type":"list_reply","list_reply":{"id":"ev03_atendente_b52387ff-...","title":"An…
"interactive":{"type":"button_reply","button_reply":{"id":"ev08_avaliacao_bom","title":"Bom"}}
"interactive":{"type":"button_reply","button_reply":{"id":"ev08_avaliacao_otimo","title":"Otimo"}}
```

Não é falha de rede, de disjuntor nem de janela de 24h. A linha chega, é aceita, e a tradução
joga fora.

**Por que a Automação acerta e o CRM não:** o CRM repassa o **payload cru** para a Automação
(`AgendarRepasseWebhookAutomacaoUseCase` → `Outbox.enfileirarRepasseWebhook`). O n8n lê o JSON
original e enxerga a escolha; o CRM traduz o mesmo JSON para o próprio histórico e descarta. Por
isso a transferência sai correta e o registro não existe.

---

## O que esta etapa resolve, e o que ela não resolve

Em 3 dias chegaram **259 escolhas de atendente** (`ev03_atendente_<uuid>`) e **210** foram
honradas pela Automação. As outras ~49 são clientes que pediram troca **depois** de o atendimento
já ter dono humano — e aí `TransferirAtendimentoUseCase:99` recusa, corretamente:

```java
if (exigirOrigemIa && antes.status() != StatusAtendimento.EM_IA) {
    throw new TransferenciaDaAutomacaoInvalidaException(atendimentoId);
}
```

**Não mexa nessa regra.** A Automação não pode tirar um lead das mãos de um humano; afrouxar isso
é pior que o bug. Caso real de 02/09: o cliente escolheu Michael às 10:46:51 e foi atendido por
ele; às 12:34:41 escolheu Nayara e às 12:38:17 escolheu Andrilene, ambas recusadas — e **nenhuma
das três escolhas aparece na conversa**, porque o tradutor descartou as três.

O ganho desta etapa é exatamente esse: com a resposta gravada, o atendente responsável **vê** que o
cliente pediu outra pessoa e pode transferir na mão. Hoje o pedido desaparece sem deixar rastro.

Não é objetivo desta etapa fazer a transferência acontecer, nem impedir que o menu seja oferecido a
uma conversa que já tem dono — isso é fluxo da Automação, que já pode consultar
`GET /internal/v1/atendimentos/em-andamento` (devolve `EM_IA` e `EM_ATENDIMENTO` com responsável).

---

## Por que o teste está verde — corrija a fixture junto

`MetaCloudWebhookTradutorTest.java:222`:

```java
private static String payload(String tipo, String titulo, String id) {
    return """
            ..."type":"interactive","interactive":{"type":"%s","%s":{"id":"%s","title":"%s"}}...
            """.formatted(tipo, tipo + "_reply", id, titulo);
}
```

Chamada com `"button"` e `"list"`, ela gera a chave certa (`button_reply`) e o `type` **errado**
(`button`). A fixture foi escrita com o mesmo mal-entendido do código de produção, então
`respostaDeBotaoUsaTituloNoHistorico` e `respostaDeListaUsaTituloNoHistorico` aprovam o defeito.

Corrigir só o código de produção e deixar a fixture como está faz os dois testes **quebrarem** — e
é exatamente esse o sinal de que a correção está certa. Ajuste a fixture para o formato real da
Meta e mantenha as duas asserções.

---

## Bloco 1 — Parar de comparar rótulo

Substitua a decisão por `type` pela presença do objeto. O nome da chave é estável na API da Meta;
o valor de `type` já mudou uma vez e é o que nos derrubou.

```java
private static final List<String> CHAVES_DE_RESPOSTA_INTERATIVA =
        List.of("list_reply", "button_reply", "nfm_reply");

private static String tituloDaResposta(JsonNode interativa) {
    for (String chave : CHAVES_DE_RESPOSTA_INTERATIVA) {
        String titulo = interativa.path(chave).path("title").asText(null);
        if (titulo != null && !titulo.isBlank()) {
            return titulo;
        }
    }
    return null;
}
```

Comente na classe **por que** não se compara mais o `type` — senão alguém "simplifica" de volta.

## Bloco 2 — O silêncio é metade do bug

O `continue` da linha 207 apagou a evidência por semanas. Antes de descartar, registre:

```java
log.warn("Resposta interativa sem titulo reconhecido; item descartado. type={} chaves={}",
        no.path("interactive").path("type").asText(""),
        campos(no.path("interactive")));
```

Mesma exigência para o `continue` de tipo desconhecido logo abaixo (linha ~228): hoje ele engole
qualquer `type` novo da Meta sem deixar rastro. Um `log.warn` com o `type` recebido, uma vez por
item. **Não** logue o payload inteiro — ele carrega telefone e conteúdo do cliente.

## Bloco 3 — Testes

Além de corrigir as duas fixtures existentes:

1. `respostaDeListaUsaTituloNoHistorico` — payload com `"type":"list_reply"`, objeto em
   `list_reply`, e o `title` vira o conteúdo da mensagem de texto do LEAD.
2. `respostaDeBotaoUsaTituloNoHistorico` — idem com `"type":"button_reply"`.
3. **Teste novo com o payload literal de produção**, copiado do trecho acima
   (`ev08_avaliacao_bom` / `ev03_atendente_<uuid>`), incluindo `id`, `timestamp`, `from` e
   `metadata.phone_number_id`. Esse é o teste que a fixture inventada não fazia.
4. `interactive` de tipo desconhecido continua sendo descartado, **sem derrubar** as outras
   mensagens do mesmo POST — e agora emite `log.warn`.
5. Uma IT em `crm-app` que entrega o payload real ao webhook e verifica que a mensagem aparece no
   histórico do atendimento, com `remetente_tipo = 'LEAD'`.

---

## Fora do escopo — não faça

**Não interprete o `id` da opção.** Os ids são estruturados (`ev03_atendente_<uuid>`,
`ev08_avaliacao_bom`) e é tentador fazer o CRM transferir ou registrar a avaliação a partir deles.
Hoje quem age sobre isso é a Automação, pelos endpoints `/internal/v1/...`. Mudar essa divisão é
decisão de produto e tem etapa própria. Esta etapa **só** grava a resposta do cliente no histórico,
como texto, que é o que já estava escrito no comentário da linha 209 e nunca funcionou.

**Não reprocesse as linhas antigas de `webhook_entrada`.** O backfill das respostas perdidas é
operação manual, com o correção já em produção, e depende de decisão sobre quais períodos vale
recuperar.

**Não mexa em `TIPO_META_PARA_CRM`** — vídeo e figurinha já foram resolvidos na E132.

---

## Definição de pronto

- Resposta de lista e de botão aparecem no histórico do atendimento, com o título que o cliente
  leu na tela.
- Os dois testes antigos passam com a fixture no formato real da Meta.
- Existe teste com payload literal de produção, incluindo um `ev08_avaliacao_*` e um
  `ev03_atendente_*`.
- Todo descarte de item no tradutor emite `log.warn` com o `type`, sem vazar payload.
- `./mvnw -pl crm-app -am verify` verde; `git diff --check` limpo.
- Relatório final com os sete itens do `AGENTS.md`.
