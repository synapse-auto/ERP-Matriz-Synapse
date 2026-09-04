# Prompt E103 — Estado da janela de 24h no chat e acabamento da aba de templates

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`feat/janela-24h-e-templates`) e PR.
> **Sem merge, sem deploy.** Só `frontend/`. Se você concluir que o backend precisa mudar,
> **pare e explique** antes de tocar nele — nesta etapa ele não deveria entrar.

---

## O pedido

1. No chat, deixar visível **se a janela do WhatsApp está aberta ou fechada**.
2. Melhorar o estado de **janela fechada** — ele já existe, mas está feio.
3. Acabamento visual da **aba de templates**.

As prints de referência são de **outro CRM** e servem só como ponto de partida. O Marcondes disse
que elas "não estão tão boas" e que é para melhorar. Siga a linguagem visual **deste** produto — os
tokens, cantos, cores e tipografia que as outras telas já usam. Não copie a paleta das prints.

## Bloco 1 — O que já existe (não reescreva)

Leia antes de planejar:

- `frontend/src/lib/atendimento/janela-24h.ts` → `janelaTextoLivreAberta(ultimaMensagemDoLeadEm)`.
  Já calcula se a janela está aberta.
- `composer.tsx:120` já chama isso, e `composer.tsx:332` já troca o composer inteiro pelo estado de
  janela fechada, com `janelaFechadaTitulo`, `janelaFechadaDescricao` e a `ListaTemplatesWhatsApp`.
- `lista-templates-whatsapp.tsx` é a lista de templates.

Ou seja: o estado de janela fechada **já existe** e o pedido nele é acabamento. O que não existe é
qualquer indicação enquanto a janela está **aberta**.

## Bloco 2 — Decisão tomada: nenhum horário de fechamento na tela

A primeira versão deste pedido incluía escrever quando a janela fecha ("Fecha amanhã às 22:12", como
na print). **Isso saiu de escopo por decisão do Marcondes, e não é para reintroduzir.**

O motivo está escrito no próprio `janela-24h.ts`: o cálculo é client-side com 24h fixas porque *"o
backend não expõe o valor configurado de `synapse.canal.whatsapp.janela-texto-livre`"*, e a
estimativa erra de propósito a favor da segurança — pode fechar antes, nunca fingir aberta. Para
decidir se o composer libera o teclado, essa imprecisão é aceitável. Para escrever um horário exato
na tela, não é: vira uma promessa em cima da qual o atendente planeja a volta do cliente.

Então, nesta etapa:

- **Nada de horário exato de fechamento.**
- **Nada de contagem regressiva** ("fecha em 5h", "faltam 40 min"). Contagem é a mesma promessa em
  outra roupa, com a mesma imprecisão por baixo.
- Se você achar que dá para trazer o valor real do backend e fazer certo, **não faça** — relate a
  ideia e deixe para uma etapa própria, com o Marcondes decidindo.

O que fica é **estado**, não tempo: aberta ou fechada.

## Bloco 3 — Janela aberta

Uma indicação discreta acima do composer dizendo que a janela está aberta — ou seja, que dá para
escrever texto livre normalmente.

- **Discreta de verdade.** É o estado normal de uma conversa ativa. Faixa colorida gritando isso o
  dia inteiro vira ruído que ninguém mais lê, e aí quando o estado importa ninguém repara.
- Some quando não há nada a dizer: lead que nunca escreveu não tem janela. `janelaTextoLivreAberta`
  já devolve `false` nesse caso — cuide para que ele **não** apareça como "janela fechada" com cara
  de erro. Nunca houve janela; é diferente de ter fechado.

## Bloco 4 — Janela fechada

O estado já existe. O que melhorar:

- Explicar em uma frase **o que aconteceu** e **o que dá para fazer**. A regra é da Meta, não do
  CRM — o atendente precisa entender que não é bug do sistema.
- O caminho para os templates tem que ser óbvio: é a única ação possível ali.
- Nada de bloco amarelo de aviso genérico. Use os tokens de superfície e de texto suave que as
  outras telas usam, e verifique nos **dois temas**.
- Todo texto novo entra nos textos da instância, como o resto da tela. Nada cravado no componente.

## Bloco 5 — Aba de templates

Olhando a primeira print e a lista de hoje, o que está concretamente ruim:

- **As variáveis são cegas.** "Mensagem — variável 1 … variável 13" não diz nada. Mostre o trecho do
  template em que cada variável cai, ou pelo menos numere junto da prévia de forma que dê para
  parear campo e posição. Treze campos anônimos garantem erro de preenchimento.
- **A prévia precisa acompanhar a digitação.** Preencheu a variável, a prévia mostra o valor no
  lugar do marcador. Prévia estática com `[variável 7]` não ajuda a conferir antes de enviar.
- **Enviar com variável vazia não pode passar em silêncio.** Ou desabilita, ou marca o campo. A Meta
  recusa e o atendente fica sem entender por quê.
- Busca, agrupamento e o rótulo de aprovação podem melhorar, mas **não invente estado que o backend
  não devolve** — se a lista não traz categoria, não desenhe filtro por categoria.

Trabalhe com o que `lista-templates-whatsapp.tsx` e a API já entregam. Se faltar dado para fazer o
certo, **pare e diga o que falta** em vez de preencher com invenção.

## Bloco 6 — Testes

- Janela aberta: a indicação aparece. Janela fechada: o estado novo aparece e o composer continua
  bloqueado para texto livre.
- Lead que nunca escreveu: **não** aparece "janela fechada" com cara de erro.
- Um teste que falhe se alguém voltar a renderizar horário de fechamento ou contagem regressiva —
  é a decisão do Bloco 2, e ela merece uma trava, não só um parágrafo.
- Templates: preencher variável reflete na prévia; enviar com variável vazia é impedido.
- Os dois temas: nenhuma cor definida só dentro de um bloco de tema.

## Verificação

```
npm run lint && npm run typecheck && npm run test && npm run build   # em frontend/
```

## Relatório

1. Como ficou a indicação de janela aberta, e por que você considera que ela é discreta o bastante.
2. O que mudou no estado de janela fechada.
3. O que você mudou na aba de templates e o que **não** deu para melhorar por falta de dado vindo do
   backend.
4. Descrição dos dois temas nos três estados: janela aberta, janela fechada, sem janela.
