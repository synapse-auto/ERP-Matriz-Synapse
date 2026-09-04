# Prompt E129 — Criar grupo do chat interno pela tela de Atendimentos

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`feat/criar-grupo-na-inbox`) e PR.
> **Sem merge, sem deploy.** Frontend + um ajuste pontual no backend. Sem migration, sem endpoint
> novo, sem regra de negócio nova.
> `./mvnw -pl crm-app -am verify` na raiz de `backend/` e a suíte do `frontend/`.

---

## O problema

A E122 entregou os grupos do chat interno inteiros — backend, RLS, `DialogoCriarGrupo`,
`PainelParticipantesGrupo`, renomear, sair. Mas **não existe forma de criar um grupo pela
interface.** O botão "Novo grupo" e o painel de participantes só aparecem em
`PaginaChatInterno` (`/chat-interno`), e **nenhum item de menu aponta para essa rota** — o único
jeito de chegar lá hoje é digitar a URL.

Confirme os dois fatos acima antes de escrever código, e diga no relatório.

---

## Onde o botão vai — e onde ele NÃO vai

**A decisão de produto é que o chat interno vive dentro de Atendimentos**, junto das conversas de
cliente. Foi o que a E62/E63 estabeleceram com a inbox unificada, e continua valendo.

Portanto: **não crie item de menu para `/chat-interno`.** Não mexa em `itens-do-menu.ts`, não mexa em
`ICONES_MENU`, não mexa em `itemDeMenuVisivel`. Uma versão anterior deste prompt mandava fazer isso e
**estava errada** — abria uma segunda porta pro chat interno e desfazia a inbox unificada.

O cabeçalho de `lista-conversas.tsx` já tem quatro botões:

| Ícone | O que faz hoje |
| --- | --- |
| `MoreHorizontal` | menu "Finalizar todos os atendimentos visíveis" |
| `UserPlus` | novo contato de WhatsApp (`onNovoContato`) |
| **`UsersRound`** | **nova conversa interna** — abre `DialogoSelecionarPessoa`, já gated por `chatInternoHabilitado` |
| `SlidersHorizontal` | filtros |

O `UsersRound` **já é a porta do chat interno**. Ele só não oferece grupo. É lá que o grupo entra.

---

## O que fazer

Dentro do `DialogoSelecionarPessoa` (`components/chat-interno/dialogo-selecionar-pessoa.tsx`),
acrescente uma ação **"Novo grupo"** que leva ao `DialogoCriarGrupo` já existente.

- **Conversa direta continua a um clique.** A lista de pessoas segue sendo o conteúdo principal do
  diálogo; a ação de grupo é secundária, não um passo a mais no caminho comum.
- A troca entre "escolher pessoa" e "criar grupo" acontece **sem fechar e reabrir** para o usuário —
  seja substituindo o conteúdo do mesmo diálogo, seja abrindo o de grupo e fechando o de pessoa.
  Escolha e justifique no relatório; o critério é não piscar e não perder a busca já digitada quando
  o usuário voltar.
- Do grupo criado em diante, o comportamento é o que a E122 já entrega. **Não** reimplemente
  validação de nome, mínimo de participantes ou qualquer regra: `DialogoCriarGrupo` já faz, e o
  backend recusa o resto.
- `criarGrupoChat(nome, participantes)` já existe em `lib/chat-interno/api.ts`. A `lista-conversas`
  recebe a ação por prop, como já faz com `onCriarConversaInterna` — siga o mesmo desenho, com a
  mutation morando em `pagina-atendimentos-cliente.tsx` ao lado de `abrirConversaInterna`.
- Depois de criar, a conversa do grupo abre, do mesmo jeito que a conversa direta abre hoje ao
  escolher uma pessoa.
- Tudo isso continua atrás de `chatInternoHabilitado`. Sem a flag, nem o botão nem o grupo existem.
- Base UI: `data-active:`, **nunca** `data-[state=active]:`.

Os textos **já existem** em `textos.json`, em `chatInterno`: `novoGrupo`, `criarGrupo`,
`nomeDoGrupo`, `nomeDoGrupoPlaceholder`, `selecionarParticipantes`, `participantesMinimos`,
`erroCriarGrupo`. **Não edite `textos.json`.** Se precisar criar chave de texto, leu algo errado.

---

## As conversas internas passam a aparecer na aba **Ativos**

Sem isto, a etapa entrega um botão que cria um grupo que ninguém consegue abrir.

Hoje as conversas internas só entram na inbox quando `visao == TODOS`. São dois pontos, e os dois
precisam mudar:

- `ListarInboxUnificadaUseCase.executar` tem a condição
  `if (visao == VisaoAtendimento.TODOS && ... && chatHabilitado())` — passa a valer também para
  `ATIVOS`.
- `useAtendimentos` (`frontend/src/lib/atendimento/use-atendimentos.ts`) só usa
  `/api/v1/atendimentos/inbox` quando `visao === "TODOS"`; as outras abas caem no endpoint legado,
  que devolve **só clientes**. `ATIVOS` passa a usar a inbox também.

Decisão do dono do projeto: **Ativos** é onde o chat interno mora. Não invente aba nova, não espalhe
para `PENDENTES` nem para `POTENCIAIS` — essas duas continuam sendo exclusivamente sobre cliente
(«esperando resposta» e «com a IA»), e uma conversa de equipe não é nenhuma das duas.

Isto também é o que mantém o chat interno vivo para o atendente depois da **E125**, que remove a aba
Todos do papel de atendente. Sem esta mudança, a E125 apagaria o chat interno da tela dele.

Ajuste junto o `@Parameter` do `InboxUnificadaController`, que hoje afirma *"conversas internas só
aparecem em TODOS"* — vira contrato mentindo no instante em que este bloco entrar.

Cuidado com a paginação: o cursor da inbox é opaco e codifica `grupo|data|id`. A fonte interna já é
lida só quando `apos == null || apos.grupo() == 0`. **Não mude o formato do cursor** — um cursor
emitido antes do deploy tem que continuar sendo aceito, e o `decodificar` já trata o caso antigo
reiniciando em vez de derrubar a aba.

---

## O que NÃO fazer

- Nenhum item de menu, nenhuma rota nova, nenhuma mudança em `/chat-interno`. A página continua
  existindo e funcionando como está — ela não é o alvo desta etapa.
- Fora do que o bloco de Ativos pede, não mexa na inbox: `PENDENTES` e `POTENCIAIS` continuam no
  endpoint legado e sem chat interno, e nenhuma aba nova é criada.
- Não mexa em `PaginaChatInterno`, `DialogoCriarGrupo`, `PainelParticipantesGrupo` ou
  `componentes-chat-interno.tsx` além do necessário para reusar o diálogo de grupo.
- No backend, **só** a condição do `ListarInboxUnificadaUseCase` e o texto do `@Parameter`. Nenhum
  endpoint novo, nenhuma migration, nenhuma política RLS, nenhuma mudança em
  `ListarAtendimentosVisiveisUseCase` ou no `PainelDeAtendimentosRepositorioJdbc`.

---

## Testes

- Com `chatInternoHabilitado`: o botão `UsersRound` abre o diálogo de pessoas, e dali a ação "Novo
  grupo" leva ao diálogo de grupo.
- Criar um grupo chama `criarGrupoChat` com nome e participantes, e abre a conversa criada.
- Escolher uma pessoa continua abrindo conversa direta em um clique — o teste que já existe para isso
  **não pode mudar de asserção**. Se mudar, você acrescentou passo no caminho comum.
- Sem a flag: o botão `UsersRound` não é renderizado, e não há caminho para grupo.
- Os testes de `dialogo-criar-grupo.test.tsx` e `dialogo-selecionar-pessoa.test.tsx` continuam
  verdes; se algum precisar de ajuste, diga no relatório o que passou a afirmar.
- `itens-do-menu.ts` e `sidebar.tsx` **não aparecem no diff**. É a prova de que a inbox unificada não
  foi desfeita.
- Backend: `GET /api/v1/atendimentos/inbox?visao=ATIVOS` devolve conversas internas junto dos
  clientes; com `visao=PENDENTES` e `visao=POTENCIAIS` **não** devolve nenhuma. Com a flag
  `chat_interno` desligada, `ATIVOS` volta a devolver só clientes.
- A paginação de `ATIVOS` com cursor continua correta e não repete nem pula item.
- O `InboxUnificadaIT` existente continua verde; se precisar de ajuste, diga no relatório o que
  passou a afirmar.

## Verificação

```
./mvnw -pl crm-app -am verify
```
e a suíte do frontend. Spotless, ArchUnit e a contagem de endpoints do OpenAPI verdes (nenhum
endpoint novo — a contagem não deve mudar).

## Relatório

1. Os dois fatos do diagnóstico, confirmados no código com arquivo e linha.
2. Como a troca pessoa → grupo acontece, e por que desse jeito.
3. Confirmação de que `itens-do-menu.ts`, `sidebar.tsx` e `textos.json` não foram tocados.
4. Confirmação de que o caminho de conversa direta continua com um clique.
5. As duas linhas que passaram a incluir `ATIVOS`, e a confirmação de que `PENDENTES` e
   `POTENCIAIS` continuam sem chat interno.
6. Confirmação de que o formato do cursor da inbox não mudou.
