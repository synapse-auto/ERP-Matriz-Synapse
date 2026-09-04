# Prompt E125 — A aba "Todos" só existe para gestão

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`feat/aba-todos-so-para-gestao`) e PR. **Sem merge, sem deploy.**
> Backend e frontend. **Sem migration. Nenhuma política RLS muda.**
> `./mvnw -pl crm-app -am verify` na raiz de `backend/` — comando unico; todas as ITs moram no `crm-app`;
> suíte do frontend em `frontend/`.

---

## O pedido do cliente

As abas da tela de Atendimentos, por papel:

| Papel | Abas |
| --- | --- |
| **Atendente** | Pendentes, Ativos, Potenciais — **e só** |
| **Gestor, subgestor, administrador** | as mesmas três **mais** Todos |

Palavras do cliente: *"Tirar aba de Todos dos atendentes (mesmo que só esteja visualizando os leads
deles) e deixar a aba Todos apenas nos gestores."*

O parêntese é o ponto: hoje o atendente **já** vê só a própria carteira em Todos — o recorte do
PR #37 (E106) está correto e funcionando. O cliente sabe disso e mesmo assim quer a aba fora. **Não
tente "consertar" o recorte; a etapa é remover a aba.**

As definições das outras três abas **não mudam nesta etapa** — nem no SQL, nem no texto:

- **Potenciais**: leads conversando com a IA, visão global, sem filtro de usuário. Já está assim.
- **Pendentes**: "meus" para atendente, "de todos" para gestão. Já está assim.
- **Ativos**: sempre "meus", em qualquer papel. Já está assim.

---

## Bloco 0 — A E108 NÃO é pré-requisito. Não pare por causa dela

A **E108** (`prompt-E108-aba-ativos-somente-respondidos.md`) mexe em `WHERE_ATIVOS`, na mesma classe
que esta etapa. Ela **não vai entrar antes** — é decisão do dono do projeto, já tomada.

Faça a branch a partir da `origin/main` **atual**, seja qual for, e implemente esta etapa
normalmente. As duas etapas tocam constantes diferentes do mesmo arquivo: a E108 edita
`WHERE_ATIVOS`; esta apaga `WHERE_TODOS_PROPRIOS` e os ternários do caso `TODOS`. Não há conflito.

**Não implemente a E108 aqui** e **não toque em `WHERE_ATIVOS`.**

Consequência conhecida e aceita de rodar sem ela: Ativos e Pendentes continuam se sobrepondo para o
atendente — um lead cuja última mensagem é do cliente aparece nas duas abas e é contado duas vezes
nos badges. Isso **já é assim hoje**; esta etapa não piora nem conserta. Registre no relatório que
rodou sem a E108.

---

## Bloco 1 — Backend: o servidor recusa, não só esconde

Esconder a aba no frontend não é a etapa. `GET /api/v1/atendimentos?visao=TODOS` continua sendo uma
chamada que qualquer atendente autenticado pode fazer no navegador. A decisão é do servidor.

Em `ListarAtendimentosVisiveisUseCase` (`executar` **e** `executarPaginado`) e em
`ListarInboxUnificadaUseCase`: quando `visao == TODOS` e `!atual.enxergaTodosOsLeads()`, lance
`org.springframework.security.access.AccessDeniedException` — o mesmo tipo que
`AlterarSenhaUseCase` e `AtualizarMeuUsuarioUseCase` já usam, que o Spring Security mapeia para 403.

Ponha essa decisão em **um lugar só**. `enxergaTodosOsLeads()` já é a única fonte de "vê a base
inteira" (`PapelUsuario`, com o comentário explicando por que a pergunta vive lá); a nova regra
"quem pode pedir a visão TODOS" tem que ficar igualmente centralizada, não copiada em três use cases.
Escolha onde — o `VisaoAtendimento` é um candidato natural — e diga no relatório qual foi.

### O código morto tem que sair junto

Com o atendente recusado na porta, `restritoAoProprioAtendente` deixa de ter qualquer efeito no caso
`TODOS`. Apague, em `PainelDeAtendimentosRepositorioJdbc`:

- `WHERE_TODOS_PROPRIOS`
- `SQL_TODOS_PROPRIOS` e `SQL_CONTAR_TODOS_PROPRIOS`
- os três ternários `restritoAoProprioAtendente ? … : …` do caso `TODOS`, em `listar`,
  `listarPaginado` e `contar` — passa a ser `SQL_TODOS` / `SQL_CONTAR_TODOS` / `""` direto
- o bloco `if (visao == VisaoAtendimento.TODOS && restritoAoProprioAtendente)` que empilha os dois
  parâmetros extras em `listarPaginado`

Sim, isso desfaz o que o PR #37 construiu. É proposital, e é o motivo de a etapa existir: deixar
`WHERE_TODOS_PROPRIOS` no arquivo depois de ninguém mais poder alcançá-lo é mentir para quem ler o
código no mês que vem.

`restritoAoProprioAtendente` **continua existindo** — `PENDENTES` ainda depende dele. Não remova o
parâmetro da interface.

Atualize o javadoc de `VisaoAtendimento.TODOS`: hoje ele diz *"Tudo que a RLS deixa este usuario
alcancar, sem filtro extra"*, o que passa a ser meia-verdade. E o `@Parameter` do
`InboxUnificadaController` continua correto ("conversas internas só aparecem em TODOS") — não mexa
nele, mas leia o Bloco 4 antes de concluir que está tudo bem.

---

## Bloco 2 — A contagem não pode devolver uma aba que não existe

`ContarAtendimentosPorVisaoUseCase` hoje itera `VisaoAtendimento.values()` e devolve as quatro
contagens sempre. Se ele continuar assim, o atendente recebe um número para uma aba que a tela não
desenha — e, pior, o `painel.contar(TODOS, …)` seria justamente a chamada que o Bloco 1 recusa.

O mapa devolvido tem que conter **exatamente as visões do papel de quem pediu**: três chaves para
atendente, quatro para gestão. O frontend não pode precisar saber quais ignorar.

---

## Bloco 3 — Frontend: a lista de abas passa a depender do papel

Em `frontend/src/components/atendimentos/lista-conversas.tsx`:

- `VISOES` é hoje uma constante de módulo com as quatro visões, e `VISOES[0]` (`"TODOS"`) é a **aba
  padrão**. As duas coisas mudam.
- O papel já está disponível no frontend: `pagina-agenda.tsx` faz
  `const papel = useAuthStore((estado) => estado.papel)` e depois
  `const papelAmplo = papel && papel !== "ATENDENTE"`. A `sidebar.tsx` usa exatamente a mesma origem.
  **Reaproveite o `useAuthStore`** — não invente uma segunda forma de descobrir o papel, e não deduza
  o papel a partir das chaves que a contagem devolveu.
- Atendente: `["PENDENTES", "ATIVOS", "POTENCIAIS"]`. Gestão: `["TODOS", "ATIVOS", "PENDENTES",
  "POTENCIAIS"]`, exatamente a ordem de hoje.
- **Aba padrão do atendente: `PENDENTES`.** É a aba que exige ação dele. Gestão continua abrindo em
  `TODOS`.
- A guarda de `visaoInicial` (`visaoEscolhida && VISOES.includes(visaoEscolhida)`) já existe e passa a
  proteger o caso novo: um atendente que chegue com `?visao=TODOS` na URL cai na aba padrão em vez de
  quebrar. **Confirme que ela continua valendo depois de `VISOES` virar valor calculado** — se virar
  um `useMemo`, a comparação tem que ser feita contra o array do papel, não contra o antigo.
- `pagina-agenda.tsx` linha 115 já manda `papelAmplo ? "TODOS" : "ATIVOS"` — **já está certo**, não
  mexa.

Não mexa em `textos.json`: o rótulo `atendimentos.visoes.todos` continua existindo, porque a gestão
continua vendo a aba.

---

## Bloco 4 — O chat interno perde o único caminho que tem: devolva um

Este bloco **não é opcional** e é a razão de a etapa mexer no frontend além das abas.

Verifique você mesmo, e confirme no relatório:

- `useAtendimentos` (`frontend/src/lib/atendimento/use-atendimentos.ts`) só chama
  `/api/v1/atendimentos/inbox` quando `visao === "TODOS"`; as outras três abas caem no endpoint
  legado, que devolve **só clientes**. O próprio `@Parameter` do `InboxUnificadaController` diz:
  *"conversas internas só aparecem em TODOS"*.
- A página `/chat-interno` **existe e está completa** (`PaginaChatInterno`, com os diálogos de grupo
  que a E122 acabou de entregar).
- **Nenhum item de `ITENS_MENU` ou `ITENS_GESTAO` aponta para `/chat-interno`.** A rota está órfã: a
  inbox unificada é hoje o único caminho até o chat interno em toda a interface.

Ou seja: tirar a aba Todos do atendente, sozinho, **apaga o chat interno da tela dele por inteiro** —
não é perda de comodidade, é perda de funcionalidade entregue.

**Se a E129 (`prompt-E129-chat-interno-no-menu.md`) já estiver mergeada, este bloco vira só
verificação:** confirme que o item existe na sidebar e siga. Se ainda não estiver, faça aqui:

- Em `frontend/src/lib/navegacao/itens-do-menu.ts`, acrescente a `ITENS_MENU`:
  `{ chave: "chatInterno", rota: "/chat-interno", flag: "chat_interno" }`.
  A flag é a mesma que `pagina-atendimentos-cliente.tsx` já consulta
  (`flags?.includes("chat_interno")`) — não crie flag nova.
- Em `ICONES_MENU` (`sidebar.tsx`), mapeie `chatInterno`. `MessageSquareText` já está importado e é
  usado por `mensagensRapidas`; escolha um ícone do `lucide-react` que ainda não esteja em uso no
  menu e diga qual no relatório.
- O rótulo **já existe** em `textos.json` (`menu.itens.chatInterno`: "Chat interno"). Não crie chave
  nova, não edite o arquivo.
- Posição: logo depois de `lembretes`, antes de `feedbacks`.
- `itemDeMenuVisivel` **não ganha regra de papel** para essa chave — o chat interno é de todo mundo.

A inbox unificada continua existindo como está para a gestão. Não a mexa.

---

## Bloco 4b — A perda que esta etapa aceita de propósito

**Os finalizados saem da tela de Atendimentos do atendente.** A divisória da E99
(`indicePrimeiroFinalizado`) só é calculada quando `visao === "TODOS"`, e só `SQL_TODOS` não filtra
status — Ativos e Pendentes exigem `EM_ATENDIMENTO`. Sem a aba, o atendente não tem mais onde ver, na
tela de Atendimentos, uma conversa que ele já finalizou, nem como reativá-la por ali.

Isto está registrado e **aceito nesta etapa**: não compense, não crie aba nova, não afrouxe o
`WHERE_ATIVOS`. Se o cliente decidir que o atendente precisa dos finalizados de volta, vira etapa
própria.

Junto com a aba some também a **paginação infinita**, que só roda em `TODOS`. As outras três abas
sempre foram não paginadas — não é regressão nova, mas registre no relatório quantos cartões a aba
Potenciais devolve no cenário de teste, porque ela é global e não tem limite.

---

## Bloco 5 — Testes

Backend, em `PainelDeAtendimentosControllerIT` e `RecorteDaAbaTodosIT`:

- `todos_gestorVeTudoAtendenteNao` e `atendenteVeEmTodosSomentePropriosEParticipadosEMantemPotencial
  NaAbaCorreta` **provam o comportamento que está sendo removido.** Não os apague em silêncio:
  inverta-os para a regra nova (atendente pedindo `TODOS` recebe **403**) e diga no relatório o que
  cada um passou a afirmar. `gestorMantemVisaoGeralEmTodos` continua verde sem alteração.
- `Contagem.todos_atendenteRestritoGestorTotal` idem: vira "a contagem do atendente **não tem** a
  chave TODOS; a do gestor tem".
- `contadorBateComAListaEmTodasAsAbasParaAtendenteEGestao` e `Contagem.contagem_bateComOTamanhoDaListagem`
  passam a iterar **as visões do papel**, não `values()`. Continuam sendo o teste que garante que
  badge e lista nunca divergem.
- Novo: `GET /api/v1/atendimentos/inbox?visao=TODOS` como atendente → **403**.
- `agendaMantemOConjuntoAnteriorParaAtendenteEGestao` continua verde **sem edição**. Se ele quebrar,
  você mexeu em coisa que não devia.
- Ativos, Pendentes e Potenciais: os testes existentes ficam verdes sem edição, para os dois papéis.

Frontend:

- Atendente: a `TabsList` tem três gatilhos, nenhum deles "Todos", e a aba inicial é Pendentes.
- Gestor: quatro gatilhos, aba inicial Todos, ordem inalterada.
- Atendente com `?visao=TODOS` na URL: cai em Pendentes, sem erro de renderização.
- O item "Chat interno" aparece na sidebar quando a flag `chat_interno` vem habilitada, para
  atendente **e** para gestor, e some quando a flag não vem. O teste de `itemDeMenuVisivel` e o da
  sidebar são os lugares naturais.
- Base UI: os gatilhos usam `data-active:` — **nunca** `data-[state=active]:`.

## Verificação

```
./mvnw -pl crm-app -am verify
```
e a suíte do frontend. Spotless e ArchUnit verdes.

## Relatório

1. Onde ficou centralizada a regra "quem pode pedir a visão TODOS", e por que ali.
2. A lista do código morto que saiu do `PainelDeAtendimentosRepositorioJdbc`, nome por nome.
3. Como a contagem passou a devolver só as visões do papel.
4. De onde o frontend tirou o papel, e a confirmação de que é a mesma origem da `pagina-agenda`.
5. O que cada teste invertido passou a afirmar.
6. A confirmação das três verificações do Bloco 4 (inbox só em TODOS, página `/chat-interno`
   completa, rota órfã no menu) e qual ícone você escolheu.
7. Quantos cartões a aba Potenciais devolveu no cenário de teste.
8. Confirmação de que Ativos, Pendentes, Potenciais, a visão de gestão e a Agenda ficaram intocadas.
