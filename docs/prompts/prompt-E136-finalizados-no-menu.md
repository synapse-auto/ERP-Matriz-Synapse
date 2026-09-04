# Prompt E136 — Finalizados no menu de ações, com hierarquia

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`feat/finalizados-no-menu`) e PR. **Sem merge, sem deploy.**
> Backend + frontend. **Não deve precisar de migration** — se você achar que precisa, **pare e
> explique** antes de escrever uma.
> `cd backend && ./mvnw -pl crm-app -am verify` e a suíte do `frontend/`.

## O pedido

No menu de três pontinhos da tela de Atendimentos, um item **Finalizados**. Ao clicar, a lista
passa a mostrar os atendimentos finalizados. Clicar em qualquer aba (`Ativos`, `Pendentes`,
`Potenciais`, `Todos`) devolve a lista ao normal.

Com hierarquia: **atendente vê só os finalizados dele; gestão vê os de todo mundo.**

`Finalizados` **não é uma aba**. É um estado alternativo da lista, acionado pelo menu.

---

## Bloco 0 — Antes de planejar: o cartão da lista é o LEAD, não o atendimento

Isto muda o desenho inteiro. Desde a E57, `PainelDeAtendimentosRepositorioJdbc` faz
`ROW_NUMBER() OVER (PARTITION BY a.lead_id ...)` e `agrupar()` filtra `linha_do_lead = 1`: **um
cartão por lead**, mostrando o atendimento mais recente daquele lead. O cartão já carrega
`atendimento_ativo_id` — o atendimento aberto do lead, ou `NULL` quando não há nenhum.

Portanto **"cartão finalizado" não é `status = 'FINALIZADO'`**. É **o lead não ter nenhum
atendimento em aberto**. Um lead cujo último atendimento foi finalizado mas que já tem outro
aberto **não** é um cartão finalizado — ele está ativo.

Escrever `WHERE a.status = 'FINALIZADO'` produz cartões duplicados e leads que aparecem em dois
lugares ao mesmo tempo. Não faça.

## Bloco 1 — A visão no servidor

Acrescente `FINALIZADOS` a `VisaoAtendimento`. Ela é **"meu" para atendente e "de todos" para
gestão**, exatamente como `PENDENTES` já é — o parâmetro `restritoAoProprioAtendente` que
`ListarAtendimentosVisiveisUseCase` já calcula a partir do papel resolve isso. **Não** invente um
segundo caminho de decisão de visibilidade.

Diferença importante em relação a `TODOS`: `FINALIZADOS` **não** é capacidade de gestão. Todo papel
pode pedi-la; o que muda é o recorte. Ajuste `podeSerSolicitadaPor` para continuar barrando só
`TODOS`.

O filtro, no padrão dos `WHERE_*` já existentes no repositório:

- o lead **não** tem nenhum atendimento em aberto (`EM_ATENDIMENTO` ou `EM_IA`);
- para atendente, o atendimento mais recente do lead é dele (`atendente_id = ?`);
- para gestão, sem filtro de dono.

Monte-o ao lado dos outros `WHERE_*`, com a contrapartida em `SQL_CONTAR_*`, e mantenha a regra
que já está comentada ali: **a contagem lê exatamente o mesmo `WHERE` da listagem**, nunca uma
segunda decisão escrita à parte.

### Cuidado com os `switch` exaustivos

Acrescentar um valor ao enum quebra a compilação em `listar`, `listarPaginado`, `contar` e em
`ContarAtendimentosPorVisaoUseCase`. **Isso é bom** — é o compilador cobrando. Trate cada um de
propósito; não use `default ->` para calar o compilador em nenhum deles.

`disponiveisPara` alimenta as abas da tela. **`FINALIZADOS` não pode entrar nessa lista** — senão
vira uma quinta aba, que não é o pedido. Se a semântica de `disponiveisPara` ficar ambígua com a
entrada do novo valor, separe em dois métodos com nomes honestos (o que a tela mostra como aba × o
que o usuário pode solicitar) em vez de filtrar o valor no frontend.

## Bloco 2 — A tela

- Item **Finalizados** no `DropdownMenu` de `lista-conversas.tsx`, junto do item de finalização
  em lote que já existe ali.
- Ao acionar, a lista passa a consultar `FINALIZADOS`. Nenhuma aba fica marcada como ativa.
- Clicar em qualquer aba sai do estado e volta à visão escolhida.
- Paginação, busca e filtros continuam funcionando; use a infraestrutura de cursor que já existe,
  sem recorte de tempo — a lista de finalizados é longa por natureza e é para isso que a
  paginação serve.

**Atenção ao controle de visão introduzido na PR #71.** `ListaConversas` hoje aceita `visaoAtual`
vindo do pai e cai em `visaoEscolhida` quando ele não vem; a página troca para `ATIVOS` após o
primeiro envio. `FINALIZADOS` precisa conviver com isso sem que um envio bem-sucedido jogue o
usuário para fora da lista de finalizados de surpresa. Descreva no relatório como você resolveu.

Não altere o comportamento do cartão finalizado que já existe (composer substituído pela faixa de
atendimento encerrado e ação de abrir novo atendimento).

## Bloco 3 — Testes

Backend:

1. Atendente pede `FINALIZADOS` e recebe **somente** leads cujo último atendimento é dele e que
   não têm atendimento aberto.
2. Gestor pede `FINALIZADOS` e recebe os de outros atendentes também.
3. Lead com atendimento finalizado **e** outro aberto **não** aparece em `FINALIZADOS`, e continua
   aparecendo em `ATIVOS`.
4. A contagem de `FINALIZADOS` bate exatamente com o tamanho da listagem, para os dois papéis.
5. Paginação por cursor devolve o mesmo conjunto que a listagem sem paginação.
6. `TODOS` continua barrada para atendente (`AccessDeniedException`), sem regressão.

Frontend:

7. O menu mostra **Finalizados** para atendente e para gestor.
8. Acionar o item troca a lista e desmarca as abas; clicar numa aba volta ao normal.
9. As abas continuam sendo `Ativos`, `Pendentes`, `Potenciais` para atendente — **`Finalizados`
   não vira aba**.

---

## Fora do escopo

- Reativar atendimento, divisória "Finalizados" no meio da lista, ou qualquer parte da E99 além do
  Bloco 0 que este prompt reaproveita.
- Alterar RN-CRM-01/02/06, RLS, papéis ou a regra de quem enxerga o quê além do recorte descrito.
- Migration, endpoint novo fora da visão, mudança no cartão.
- O diálogo de finalização em lote — é a E137.

## Definição de pronto

- Item **Finalizados** no menu de três pontinhos, funcionando para os dois recortes.
- Atendente não vê finalizado de colega em lugar nenhum.
- Nenhum `default ->` novo em `switch` sobre `VisaoAtendimento`.
- `./mvnw -pl crm-app -am verify` verde; testes, typecheck, lint e build do frontend verdes.
- Relatório final com os sete itens do `AGENTS.md`.
