# Prompt E130 — Status de entrega invisível no balão e seletor de emoji quebrado

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`fix/status-e-seletor-de-emoji`) e PR.
> **Sem merge, sem deploy.** Só `frontend/`. Sem backend, sem migration, sem contrato.
> Verificação proporcional: **suíte do frontend, sem Maven.**

Dois defeitos visuais independentes, na mesma tela e na mesma etapa porque são pequenos.

---

## Bloco 1 — "O template só fica com um traço"

### O que o cliente relatou

Que a mensagem de template fica com **um traço só** no WhatsApp — ou seja, parece que nunca foi
entregue. A suspeita do dono do projeto é outra, e está certa: **o ✓✓ existe, mas é invisível dentro
do balão.** Quem olha a tela conclui que parou no "enviado".

### A causa, no código

O balão de saída é `bg-primary text-primary-foreground` (`bolha-mensagem.tsx:117`) — fundo azul,
texto branco. A linha do rodapé já herda a cor certa: `text-primary-foreground/70` (linha 231).

Mas o `StatusEntregaIcone` (`status-entrega.tsx`) **sobrescreve** essa herança com cores pensadas
para fundo claro:

- o `<span>` externo aplica `text-muted-foreground` — cinza sobre azul, contraste péssimo;
- `LIDO` aplica `text-primary` — **exatamente a cor do balão**. Ícone azul sobre fundo azul: sumiu;
- `FALHOU` aplica `text-destructive` — vermelho sobre azul, também ruim.

Esse componente só é renderizado quando `doAtendente` é verdadeiro, isto é, **sempre dentro do balão
azul**. As três cores foram escolhidas como se o fundo fosse claro.

### O que fazer

O ícone passa a **herdar** a cor do rodapé do balão em vez de impor a sua. Tire o
`text-muted-foreground` do `<span>`.

`LIDO` continua precisando ser distinguível de `ENTREGUE` — são dois `CheckCheck` iguais, e a única
diferença hoje é a cor. Resolva isso com contraste **dentro da paleta do balão**: por exemplo,
`ENVIADO`/`ENTREGUE` seguindo a opacidade do rodapé e `LIDO` em `text-primary-foreground` cheio, ou
outro token que você justifique. **Não invente cor fora dos tokens** de `globals.css`; a E74 fixou a
identidade visual e cor solta no componente é dívida.

`FALHOU` é o único que legitimamente precisa gritar. Verifique como ele fica sobre `bg-primary` e, se
o vermelho não tiver contraste suficiente, use o token de destaque sobre superfície escura em vez de
`text-destructive` cru. Diga no relatório o que escolheu e por quê.

**Não** mexa nos ícones em si (`Clock`, `Check`, `CheckCheck`, `AlertTriangle`), nos textos de
`textos.json`, na máquina de estados de entrega, nem no botão "Reenviar". A E118 acabou de entregar a
monotonia do status no backend — nada disso muda.

---

## Bloco 2 — Seletor de emoji "meio bugado"

### O que aparece

O popover do emoji abre com o catálogo **escuro e estreito à esquerda**, e uma **faixa branca vazia à
direita**, ocupando quase metade da largura do popover. Duas causas somadas.

### Causa 1 — o tema do picker segue o sistema operacional, não o CRM

`seletor-emoji-completo.tsx` constrói o `Picker` com `theme: "auto"`. No emoji-mart, `auto` significa
`prefers-color-scheme`, ou seja, **o tema do sistema operacional do usuário**.

O CRM não usa `prefers-color-scheme`: `globals.css:5` declara
`@custom-variant dark (&:is(.dark *))` — o tema é **classe no elemento raiz**. Resultado: máquina com
Windows no escuro e CRM no claro entrega um picker preto colado numa interface branca.

Passe o tema a partir do estado real do CRM (presença da classe `dark` na raiz do documento), não de
`auto`. Se o CRM mudar de tema com o popover aberto, não precisa reagir — o picker é remontado a cada
abertura.

### Causa 2 — a largura é medida antes do popover existir

`dynamicWidth: true` faz o emoji-mart calcular quantos emojis cabem por linha a partir da largura do
host **no momento da construção**. O `Picker` é construído num `useEffect` que roda assim que
`{aberto && <SeletorEmojiCompleto/>}` monta dentro do `PopoverContent` — antes de o popover ter
largura final. Com largura pequena ou zero, a grade nasce estreita e nunca mais cresce; o resto do
popover (`w-[min(22rem,calc(100vw-2rem))]`, `p-0`, fundo claro) fica aparecendo do lado.

Resolva de um dos dois jeitos, e justifique no relatório:

- construir o picker **depois** de o popover ter largura (medir o host e só então instanciar); ou
- abrir mão do `dynamicWidth` e fixar `perLine` coerente com a largura do popover.

O critério de aceite não é qual caminho você escolheu: é **não sobrar faixa vazia**, com o popover em
352px e no tamanho de celular (`100vw-2rem`).

### O que não muda

- `set: "native"`, `previewPosition: "none"`, `skinTonePosition: "search"` e o `i18n` vindo do
  catálogo de textos. São decisões da E-do-emoji, não regressões.
- O carregamento dinâmico com `ssr: false` e o `{aberto && ...}` que só monta o picker com o popover
  aberto — isso existe para não pesar o caminho de envio. **Não** passe a montar sempre para
  "resolver" a largura.
- `inserirNoCursor` / `posicionarCursor` e o comportamento de inserção no textarea.
- O mesmo seletor é usado no chat interno e no seletor de reações. Verifique os dois e confirme no
  relatório que não regrediram.

---

## Testes

- Balão de saída com status `LIDO`: o ícone **não** tem a classe da cor do balão. É o teste que
  falharia hoje.
- `ENVIADO`, `ENTREGUE` e `LIDO` renderizam ícones distinguíveis entre si, com `title` correto.
- `FALHOU` continua mostrando o texto e o botão "Reenviar", e o `onReenviar` continua sendo chamado.
- Os testes de `status-entrega.test.tsx` que já existem continuam verdes; se algum afirmava a cor
  antiga, **inverta e diga no relatório** o que ele passou a afirmar.
- Seletor de emoji: o picker é construído com o tema derivado da raiz do documento — um caso com a
  classe `dark` presente e outro sem, afirmando o `theme` recebido pelo `Picker`.
- Escolher um emoji continua chamando `onEscolher` com o caractere nativo (`nativoSelecionado`
  intacto).

## Verificação

Suíte do `frontend/`. **Sem Maven** — nenhum arquivo de backend foi tocado.

## Relatório

1. As três cores do `StatusEntregaIcone` que assumiam fundo claro, e o que cada uma virou.
2. Como `LIDO` continua distinguível de `ENTREGUE` sem usar a cor do balão.
3. Como o tema do picker passou a ser derivado, e por que `auto` estava errado neste projeto.
4. Qual dos dois caminhos você usou para a largura, e a prova de que não sobra faixa vazia nos dois
   tamanhos.
5. Confirmação de que o chat interno e o seletor de reações continuam funcionando.
6. Confirmação de que nenhum arquivo de backend e nenhuma chave de `textos.json` foi tocada.
