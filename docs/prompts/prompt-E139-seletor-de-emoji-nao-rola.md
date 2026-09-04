# Prompt E139 — Seletor de emoji não rola nem troca de categoria

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`fix/seletor-de-emoji-nao-rola`) e PR. **Sem merge, sem deploy.**
> **Somente frontend.** Um arquivo de produção. Sem backend, sem migration.
> Suíte do `frontend/`, typecheck, lint e build.

No composer e na barra de reação, o seletor de emoji abre, mostra a grade e **deixa clicar num
emoji — mas não rola e não troca de categoria**. As abas de categoria aparecem e não respondem.

---

## A causa, medida no DevTools — não reinvestigue

Com o seletor aberto em produção, o elemento interno da rolagem está assim:

```
div.scroll.flex-grow.padding-lr   →   352 × 6273
```

**6273px de altura.** Ele não é uma caixa rolável; virou a lista inteira renderizada, empurrando
nav e tudo mais para fora da área visível, que o `overflow-hidden` do `PopoverContent` recorta.

A árvore renderizada:

```html
<em-emoji-picker style="display: block; width: 100%">   <!-- override da E130 -->
  #shadow-root (open)
    <section id="root" class="flex flex-column" style="width: 100%">   <!-- sem altura -->
      <nav id="nav" class="padding">…</nav>
      <div class="padding-lr">…</div>
      <div class="scroll flex-grow padding-lr">…</div>   <!-- 352 × 6273 -->
```

O CSS do shadow root do `emoji-mart` (`node_modules/emoji-mart/dist/module.js`) traz:

```css
:host { width: min-content; height: 435px; min-height: 230px; /* sem display */ }
.scroll { overflow-x: hidden; overflow-y: auto; }
.flex-grow { flex: auto; }
```

Ou seja: `.scroll` tem `overflow-y: auto`, mas **nenhuma altura própria**. Ela só fica limitada se
o `#root` tiver altura resolvida — e `#root` tem `height: auto`. Com o host em `display: block`, os
`435px` do `:host` valem para o host, mas o `#root` continua sendo um bloco de altura automática
que transborda. Nada limita a rolagem.

**A E130 (`e034e2e`) consertou metade.** Ela trocou o `display` do host para `block` porque custom
element sem `display` é `inline`, e caixa inline ignora `width`/`height` — era o bug dos emojis
espremidos. Resolveu a largura e deixou a altura sem ancorar.

## A correção

Em `frontend/src/components/mensagens/seletor-emoji-completo.tsx`, o override inline do host passa
de `block` para **`flex`**:

```js
picker.style.display = "flex";
picker.style.width = "100%";
```

Com o host como contêiner flex, o `#root` vira o item único e, pelo `align-items: stretch` padrão,
**estica para os 435px do host**. A partir daí a coluna flex interna do `emoji-mart` funciona como
foi desenhada: `nav` e busca com altura natural, `.scroll` com `flex: auto` ocupando o resto e
rolando.

A largura não regride: o `#root` já carrega `width: 100%` inline, posto pelo próprio `emoji-mart`
por causa do `dynamicWidth: true` que este componente passa na configuração. Num contêiner flex em
linha, `width: 100%` do item resolve contra o contêiner e continua preenchendo.

**Atualize o comentário do arquivo.** O bloco atual explica só a largura e é o que faria a próxima
pessoa reintroduzir `block`. Ele precisa dizer as duas coisas: por que o host recebe `display`
(custom element é `inline` por padrão) e por que precisa ser `flex` e não `block` (o `#root` não
tem altura própria e só estica como item flex).

## Verificação obrigatória

Isto é layout em shadow DOM: teste unitário não prova. **Abra no navegador, autenticado, e
confirme nos dois pontos de uso** — o botão de emoji do composer (`painel-emoji-composer.tsx`) e a
reação de mensagem (`interacao-mensagem.tsx`):

1. A grade **rola** com roda do mouse e com arrastar da barra.
2. Clicar numa aba de categoria no topo **salta** para a seção correspondente.
3. A busca de emoji filtra e o resultado continua rolável.
4. A grade continua com a largura cheia do popover, sem voltar a espremer — confira em **1440,
   1024 e 390** de largura.
5. No DevTools, `div.scroll` fica com altura **limitada** (na casa das centenas de px), não 6273.

Anote no relatório a altura medida do `.scroll` depois da correção. Se ela continuar na casa dos
milhares, a correção não pegou — **pare e explique**, não tente empilhar outro override.

## Fora do escopo

- Trocar o `emoji-mart` por outra biblioteca, ou abandonar o web component.
- Mexer em `PopoverContent`, no tema, no `dynamicWidth` ou no `set: "native"`.
- Fixar altura em pixel no `PopoverContent` para "resolver" por fora — isso esconde o sintoma e
  deixa a caixa interna quebrada do mesmo jeito.
- Qualquer outro componente de mensagem.

## Definição de pronto

- O seletor rola e troca de categoria nos dois pontos de uso.
- A largura da grade continua correta nas três larguras de tela.
- O comentário do arquivo explica `display: flex` de forma que ninguém volte para `block`.
- Testes, typecheck, lint e build do frontend verdes; `git diff --check` limpo.
- Relatório final com os sete itens do `AGENTS.md`, incluindo a altura medida do `.scroll`.
