# Prompt E56 — acabamento do chat e os ajustes pedidos

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/22-bugs-abertos-26-08.md` (bugs 6 e 8, e a seção "Ajustes finos").
> **Pode commitar localmente a qualquer momento.** Não execute `git push` sem autorização explícita.
> **Rode esta etapa depois da E54 e depois da E57.** Os Blocos 3 e 4 mexem em
> `painel-da-conversa.tsx` e `cabecalho-conversa.tsx`, e a E57 redefine nesses componentes o que "o
> atendimento" significa: eles passam a operar sobre **o atendimento aberto daquele lead**
> (`atendimentoAtivo`), não sobre o cartão que foi clicado. Construir estes dois blocos antes da E57
> é escrever contra uma semântica que vai mudar.
>
> Se a E57 ainda não tiver entrado, faça **apenas os Blocos 1, 2 e 5** — eles tocam
> `components/ui/textarea.tsx`, `atalho-tags.tsx` e a barra lateral, e não encostam em Atendimentos.
> Diga no relatório que 3 e 4 ficaram de fora e por quê.

---

## Bloco 1 — O campo de texto estoura o composer

Digitar uma palavra longa sem espaço ("kkkkk…") faz o campo crescer para fora da borda arredondada e
empurra a linha inteira do composer. São **três coisas somadas**:

1. **`field-sizing-content` na classe base do `Textarea`** (`components/ui/textarea.tsx`). Esse
   recurso dimensiona o campo pelo conteúdo — e faz isso **nos dois eixos**, não só na altura. Palavra
   sem espaço tem largura intrínseca ilimitada.
2. **`flex-1` sem `min-w-0`** no `<div className="relative flex-1">` que envolve o campo no composer.
   Item de flex nasce com `min-width: auto` e **se recusa a encolher abaixo da largura mínima do
   conteúdo**.
3. O composer sobrescreve **altura** (`min-h-11 max-h-32`) e nunca largura.

**Isto não é bug do composer, é da classe base.** Toda textarea do sistema tem o mesmo comportamento:
as mensagens de follow-up e fidelização na Automação, as notas, as mensagens rápidas. Corrija na base
e confira as outras telas.

- Restrinja o `field-sizing` à altura, ou troque por altura controlada e mantenha o crescimento por
  `rows` + `max-h`. O campo do composer **precisa continuar crescendo em altura** ao quebrar linha —
  isso é comportamento desejado, não regressão a evitar.
- Garanta `w-full` e `min-w-0` no campo e no wrapper, e quebra do texto digitado para palavra sem
  espaço.
- A bolha da mensagem **já está certa** (`max-w-[70%]` + `break-words`). Não mexa nela; só confirme
  que continua certa com uma palavra de 300 caracteres.

## Bloco 2 — "+ + Tag"

`atalho-tags.tsx`, modo `painel`, renderiza o ícone `<Plus/>` **e** o texto do catálogo, que já é
"+ Tag". Escolha um dos dois — o ícone, e o texto vira "Tag" no catálogo — e ajuste o catálogo junto,
com o `schema.test.ts` verde.

## Bloco 3 — Lembretes e Mensagens Programadas ganham ações no painel da conversa

Em `painel-da-conversa.tsx`, `SecaoDeLembretes` e `SecaoDeProgramadas` são hoje listas puramente de
leitura.

- Cada seção ganha **adicionar**; cada item ganha **editar** e **remover**.
- O lembrete e a mensagem programada pertencem ao **lead**, não ao atendimento — confirme isso antes
  de escrever e diga no relatório. Se estiver amarrado ao atendimento, com a conversa unificada da
  E57 o item some ao trocar de atendimento, que é o oposto do que se quer.
- **Não escreva endpoint novo.** O CRUD já existe e é o mesmo das telas de Lembretes e de Mensagens
  Programadas — reaproveite os hooks e os formulários que já estão lá, não duplique formulário.
- O lead já vem do contexto do painel: o formulário de dentro da conversa não pede o lead de novo.
- Remover pede confirmação e é otimista com reversão em erro, como o resto do projeto.
- As contagens das seções continuam corretas depois de cada operação.

## Bloco 4 — Finalizar todos os atendimentos

No menu de três pontinhos do cabeçalho da conversa (`cabecalho-conversa.tsx`).

Depois da E57 a lista é por cliente, então **defina e diga no relatório o que "todos" significa**:
todos os atendimentos abertos que quem clicou enxerga, e não os do lead da conversa aberta. Um botão
chamado "finalizar todos" que finaliza três de trinta é pior que não ter botão.

**Duas coisas que você precisa resolver antes de escrever a tela:**

1. **Não existe rota de finalização em lote.** Hoje é `POST /atendimentos/{id}/finalizar`, um por vez.
   Com muitos atendimentos abertos, disparar N chamadas do front é ruim — é rajada de requisição e
   falha parcial sem transação. **Crie a rota em lote no backend**, com a mesma autorização da
   individual, respeitando a visibilidade de quem chama: cada um finaliza o que **enxerga**, nunca
   mais que isso. Um gestor não vira atalho para encerrar a carteira alheia sem passar pela regra.
2. **`FINALIZADO` é estado terminal — dele não se sai.** A ação **exige confirmação explícita**,
   dizendo **quantos** atendimentos serão encerrados. Nada de confirmar com texto genérico.

A resposta diz quantos foram finalizados e quantos foram recusados, e a tela mostra isso. Falha
parcial não pode ser silenciosa.

Se você criar operação nova no OpenAPI, a contagem do `OpenApiIT` muda — **atualize no mesmo commit**,
não num commit de conserto depois.

## Bloco 5 — Ícone do Dashboard

Trocar o ícone do Dashboard na barra lateral pela seta de métrica (`TrendingUp` do `lucide-react` é o
equivalente). Uma linha.

---

## Verificação

- `npm run lint`, `npm run typecheck`, `npm test` no `frontend/`, verdes.
- `./mvnw clean verify` no reator inteiro, se o Bloco 4 tocar backend.
- Teste de que uma palavra de 300 caracteres sem espaço **não** altera a largura do composer, e de que
  quebrar linha **ainda** aumenta a altura.
- Verifique visualmente a mesma palavra nas textareas da Automação e das notas, já que a correção é na
  classe base.
- Teste de que o botão de tags mostra um único "+".
- Teste de criar, editar e remover lembrete e mensagem programada **de dentro do painel da conversa**,
  com a contagem da seção acompanhando.
- Teste de que finalizar em lote respeita a visibilidade de quem chamou e de que a confirmação informa
  a quantidade.
- Teste de que a contagem do `OpenApiIT` bate com a nova operação, se houver.

## Relatório

1. Como você resolveu o `field-sizing` mantendo o crescimento em altura.
2. Quais outras telas tinham o mesmo estouro de largura.
3. Se criou rota em lote e como ela respeita a visibilidade.
