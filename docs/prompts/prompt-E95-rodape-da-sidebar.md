# Prompt E95 — Feedbacks e Novidades no rodapé da sidebar

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`feat/rodape-da-sidebar`) e PR. **Sem merge, sem deploy.**
> Só `frontend/`. Verificação proporcional: suíte do frontend, **sem Maven**.

---

## O pedido

**Feedbacks** e **Novidades & Em Breve** saem da lista principal do menu e passam para uma faixa
discreta no **rodapé da barra lateral**, logo acima da divisória do bloco do usuário — o espaço vazio
que hoje existe entre o fim do menu e o cartão do Marcondes. Ícones pequenos, ou ícone com rótulo
curto.

São itens secundários: não competem com Atendimentos, Dashboard e Agenda pela atenção. Hoje o
Feedbacks está no meio da lista MENU, do mesmo tamanho de tudo, e o Novidades ficou solto no fim do
`<nav>`.

## Bloco 1 — Onde cada um está hoje

Antes de mexer, entenda que os dois têm naturezas diferentes:

- **Feedbacks** é rota (`/feedbacks`), declarada em `frontend/src/lib/navegacao/itens-do-menu.ts`
  dentro de `ITENS_MENU`, e renderizada pelo `MenuGrupo`.
- **Novidades** não é rota: é um `<button>` que abre o `NovidadesDialog`, já solto num `<div>` no fim
  do `<nav>`.

O rodapé é o `<div>` com `border-t` que vem **depois** do `</nav>` e contém a presença e o cartão do
usuário. A faixa nova fica **entre os dois**: no fim do `<nav>` ou no topo do rodapé, mas visualmente
colada na divisória, não flutuando no meio do vazio.

## Bloco 2 — A armadilha: `ITENS_MENU` não é só o menu

`itens-do-menu.ts` é consumido também por `visibilidade-do-menu.ts`, que — diz o próprio comentário —
é *"reutilizada na escolha de área do feedback"*. Ou seja, **a lista de áreas que o usuário escolhe ao
abrir um feedback é derivada da mesma lista do menu.**

Antes de tirar `feedbacks` de `ITENS_MENU`:

- descubra **quem mais consome essa lista** e o que acontece com cada consumidor se a chave sair;
- decida com base nisso se a chave sai da lista ou se ela fica e apenas **deixa de ser renderizada
  pelo `MenuGrupo`**;
- **relate qual das duas você escolheu e por quê.**

Não é detalhe: tirar uma linha de um array e ver o menu certo na tela é fácil; o efeito colateral
aparece três telas adiante, no formulário de feedback com uma área a menos.

Confirmado: `feedbacks` hoje **não tem flag e não tem restrição de papel** — é visível para todo
mundo. Seja qual for o caminho escolhido, isso não pode mudar.

## Bloco 3 — Ícones da sidebar 15% menores

Ainda nesta etapa, porque é o mesmo arquivo:

- Os ícones do menu lateral usam `size-[21px]`, cravado **três vezes** num ternário. Devem ficar
  ~15% menores: **`size-4.5`** (18px), que existe nativamente no Tailwind v4 deste projeto.
  Aproveite para escrever o tamanho **uma vez**, não três.
- Há também um `size-[17px]` no losango da logo. Não mexa nele: é marca, não ícone de navegação.
- Os dois itens novos do rodapé são **secundários** — devem ficar visivelmente menores que os do
  menu, não do mesmo tamanho.
- **Não** mexa em ícone fora de `components/shell/`. A escala geral é outra etapa e vai encostar em
  outros arquivos; misturar as duas gera conflito.

## Bloco 4 — Os dois estados da sidebar

A barra retrai e expande por hover. Isso vale para a faixa nova também:

- **Expandida:** ícone pequeno com rótulo curto.
- **Retraída:** só o ícone, com `aria-label` e `title` preenchidos — copie a mecânica que o botão de
  Novidades já usa hoje (`estiloDoRotuloDaSidebar(retraida)`, `aria-hidden` no rótulo, `aria-label`
  condicional). Não invente uma segunda forma de fazer a mesma coisa.
- O **alvo de clique** não encolhe junto com o ícone. Item pequeno no rodapé é onde mais se erra o
  clique.
- Estando em `/feedbacks`, o item precisa mostrar que está ativo — hoje quem faz isso é o
  `MenuGrupo`, e ele não vai mais renderizar esse item.

Rótulos vêm do catálogo de textos, como todo o resto. Nada cravado no componente.

## O que não fazer

- Não remova a rota `/feedbacks` nem a página; muda só o ponto de entrada.
- Não mexa no `NovidadesDialog` em si, só em como ele é acionado.
- Não altere a ordem nem o conteúdo dos grupos MENU e GESTÃO além de tirar o Feedbacks.
- Nada de backend, migration ou contrato.

---

## Verificação

- `npm run lint`, `npm run typecheck`, `npm test`, `npm run build` no `frontend/`. **Sem Maven.**
- Teste de que o Feedbacks **não** aparece mais nos grupos MENU/GESTÃO e **aparece** no rodapé.
- Teste de que `/feedbacks` continua acessível e marcada como ativa quando é a rota atual.
- Teste do consumidor que você identificou no Bloco 2 — se a lista de áreas do feedback mudou,
  isso precisa estar coberto.
- **Capturas obrigatórias**, com a aplicação no ar: sidebar expandida e sidebar retraída, as duas
  mostrando o rodapé; e uma com `/feedbacks` aberta, mostrando o estado ativo.

## Relatório

1. Quem consome `ITENS_MENU` além do menu, e qual caminho você escolheu no Bloco 2.
2. Como ficou o tamanho: ícone de menu, ícone do rodapé, em px.
3. As capturas do Bloco 4.
