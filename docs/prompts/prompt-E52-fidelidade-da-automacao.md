# Prompt E52 — a aba Automação como no modelo

> Leia `AGENTS.md`, `CLAUDE.md`, `docs/13-estado-do-projeto.md` e `docs/17-plano-de-fechamento.md`.
> **Pode commitar localmente a qualquer momento.** Não execute `git push` sem autorização explícita.

---

## Contexto

A tela `/automacao` funciona, mas não se parece com o modelo. Esta etapa é **fidelidade visual e de
interação**, com **um** acréscimo funcional que já tem backend pronto: o card de Atendentes
Disponíveis.

**Todo o backend de que esta etapa precisa já existe.** Você não cria migration, não cria endpoint,
não cria tabela. Se em algum momento parecer que precisa, você entendeu o escopo errado — pare e
relate.

### O que está fora, e está fora de propósito

O modelo mostra quatro coisas que **não** entram nesta etapa. Elas estão listadas aqui para você
**não** implementá-las, não como sugestão:

- **Rotinas pré-definidas** (nome, dias da semana, atendentes por rotina) — não existe tabela nem
  endpoint, e falta decidir quem aplica a rotina, já que o CRM não tem scheduler (RN-CRM-07).
- **Avaliação por atendimento** — o `{nota}` da mensagem aponta para uma tela de avaliação que não
  existe em lugar nenhum do produto.
- **Aniversário do cliente** — o lead não tem data de nascimento.
- **Datas festivas** — a tabela `mensagem_festiva` existe desde a V7, mas não tem coluna de nome e
  nunca teve tela; é etapa própria.

Não crie placeholder, não crie card desabilitado, não crie "em breve" para nenhuma das quatro.
Elas simplesmente não aparecem.

---

## Bloco 0 — O que o modelo é e o que ele não é

O modelo é referência de **layout, hierarquia, densidade, escala tipográfica e comportamento**.

**Não é referência de cor.** O modelo tem hex cravados (`#1F74E0`, `#0F2438`, `#8A9BAD`…). A E18
ligou a tela aos tokens do shadcn e o `tema.json` troca a cor por instância — este é um produto
multi-cliente e um hex cravado quebra o próximo filho. Use os tokens que a tela já usa
(`primary`, `muted-foreground`, `border`, `card`, `cor-sucesso`, `cor-ia`…). Se faltar um token para
algo do modelo, use o mais próximo que existe e diga no relatório qual foi e por quê — não invente
token novo nem escreva hex.

Ícones: o modelo usa Remix, o projeto usa `lucide-react`. Mapeie para o equivalente mais próximo.

Texto visível: o projeto não tem string solta em componente. Tudo passa por `useTextos()`, com o
catálogo validado por `frontend/src/lib/config/schema.ts` e por `schema.test.ts`. Toda string nova
desta etapa entra no schema **e** no catálogo, no mesmo commit.

---

## Bloco 1 — Cabeçalho, abas e moldura da página

- O subtítulo muda por aba, como no modelo: "Configurações gerais do assistente de IA." /
  "Mensagens automáticas enviadas quando o cliente demora para responder." / "Mensagens automáticas
  para reativar clientes sem contato recente."
- As abas são sublinhado fino, não pílula. **Atenção:** este projeto usa **Base UI**, não Radix — o
  seletor de aba ativa é `data-active:`, nunca `data-[state=active]:`. Isso já derrubou a E40 e virou
  CSS morto; não repita.
- O conteúdo tem largura máxima e respira: o modelo usa ~1320px de conteúdo com padding lateral
  generoso. Hoje a página é `p-6` e o conteúdo cola nas bordas.
- Cartões: fundo de card, borda de 1px, canto arredondado grande (o modelo usa 16px nos cards
  grandes e 14px nos cards de indicador), padding interno confortável. Hoje eles são pequenos demais.

## Bloco 2 — Os quatro indicadores do topo

Já existem (`CardsDeTelemetria`). O que muda é a apresentação:

- Ícone dentro de um quadrado arredondado com fundo tonal — não solto sobre `bg-muted` cinza.
- Rótulo pequeno em cinza acima, número grande e pesado abaixo.
- Os dois cards de estado (Conexão Automação, Status do CRM) mostram bolinha + palavra, como já
  fazem, na mesma escala dos outros.

Mantenha o comportamento atual: **carregando não desenha esqueleto e erro não desenha zero**. Zero
inventado numa tela de telemetria parece dado real e já causou confusão.

## Bloco 3 — Atendentes Disponíveis (o único acréscimo funcional)

Card novo na coluna esquerda da aba Geral · IA. **Tudo que ele precisa já existe:**

- `GET /api/v1/usuarios` (`useEquipe`) devolve `nome`, `papel`, `ativo`, `statusPresenca`,
  `disponivelParaIa`, `fotoUrl`.
- `PATCH /api/v1/usuarios/{id}/disponibilidade-ia` (`useAtualizarDisponibilidadeParaIa`) liga e
  desliga.

O card:

- Título com ícone, e ao lado um selo com a contagem.
- Subtítulo: "Defina quais atendentes estão disponíveis agora para a IA direcionar clientes."
- Rótulo "DISPONIBILIDADE ATUAL" em maiúsculas pequenas, e grade de **duas colunas**.
- Cada item: avatar com iniciais, nome, cargo abaixo, e o switch à direita. O card inteiro muda de
  fundo quando o atendente está disponível, como no modelo.
- **Avatar:** use `frontend/src/components/ui/avatar-iniciais.tsx`, que já existe e já resolve a cor
  por hash do id. Não escreva um avatar novo, e não deixe todos cinza — isso foi corrigido na E41 e
  não pode regredir. Quando o usuário tem `fotoUrl`, mostre a foto.
- **Ponto de presença:** a bolinha no canto do avatar reflete `statusPresenca`, que é outra coisa que
  `disponivelParaIa`. Presença é "está com o CRM aberto"; disponibilidade é "entra no rodízio da IA".
  O switch mexe só na segunda.
- **Só atendentes ativos** aparecem. Gestor, subgestor e inativo não entram no rodízio e não têm o
  que fazer nessa lista.

**Duas decisões que você não toma sozinho — implemente como está aqui e registre no relatório:**

1. **O selo conta disponibilidade, e o texto tem que dizer isso.** O modelo escreve "4 de 6 online",
   mas o que o switch controla é disponibilidade para a IA, não presença. Escreva "N de M
   disponíveis", com o texto vindo do catálogo para o Marcondes trocar se discordar. Um contador que
   diz "online" e conta outra coisa é um bug de produto disfarçado de rótulo.
2. **`cargo` precisa sair na listagem.** A coluna `usuario.cargo` existe desde a V39 e
   `GET /api/v1/me` já a devolve, mas `GET /api/v1/usuarios` não. Acrescente `cargo` ao
   `UsuarioResposta` de `UsuarioController` — é campo em resposta existente, não operação nova, então
   a contagem do `OpenApiIT` não muda; confirme que não mudou. Quando `cargo` for nulo, a linha de
   baixo mostra o papel. Não invente cargo, não derive de papel silenciosamente.

## Bloco 4 — Recursos de IA

Já existe e funciona. Move para a coluna direita e vira lista, não grade de dois quadradinhos: cada
recurso é uma linha com título, uma frase de explicação abaixo e o switch à direita, separadas por
divisória. As duas frases estão no modelo — "Gera um resumo do atendimento ao finalizar." e
"Preenche dados do cliente (nome, empresa, endereço) a partir da conversa."

## Bloco 5 — Os parâmetros crus não somem, saem do caminho

Hoje a aba Geral lista todas as linhas de `configuracao_automacao` com um campo e um botão Salvar
por linha (`LinhaParametro`). O modelo não tem isso.

**Não apague.** Essa lista é o único lugar do produto onde se edita `automacao.habilitada` — o
desligamento de emergência da Automação — além dos limites de anexo e dos tempos que o n8n lê.
Apagar a lista é remover o freio de mão.

Coloque num bloco recolhido no fim da aba Geral, fechado por padrão, rotulado como parâmetros
avançados, com uma frase dizendo que são os valores lidos pela Automação. Aberto, ele continua
exatamente como é hoje.

## Bloco 6 — Follow-up e Fidelização: edição no card, não em formulário

Hoje é uma grade de cartões só de leitura com um botão "Editar" que abre um formulário separado. No
modelo, **o card é o formulário**. Uma coluna de cards à esquerda, prévia à direita.

Cada card, nas duas abas:

- Selo no topo com o gatilho por extenso — "2 horas sem resposta", "30 dias sem contato".
- À direita do selo: a palavra Ativo/Inativo, o switch, e o botão de excluir.
- Follow-up: rótulo "TEMPO SEM RESPOSTA PARA ENVIAR", campo numérico estreito e um **segmentado
  Horas/Dias** — dois botões colados, não um `Select`. A conversão para minutos continua sendo a que
  já existe (`labelTempo` e o cálculo `× 60` / `× 1440`); não mexa na regra, só na aparência.
- Fidelização: rótulo "DIAS SEM ENTRAR EM CONTATO", campo numérico e o sufixo "dias sem contato".
- Rótulo "MENSAGEM", textarea de três linhas, e a dica do `{nome}` embaixo.
- Acima da lista: a contagem ("3 follow-ups") à esquerda e o botão de criar à direita.
- Card em foco tem realce na borda esquerda — é ele que alimenta a prévia.

**Como salvar, que é a parte que dá errado se você não pensar nela:**

- Campo de texto e campo numérico salvam **ao sair do campo**, e só se o valor mudou. Não salve a
  cada tecla: são `PUT`s em rajada e a última resposta fora de ordem sobrescreve o que a pessoa
  acabou de digitar.
- Switch e exclusão salvam na hora, pelas rotas que já existem (`PATCH .../ativo`, `DELETE`).
- Enquanto salva, o valor que a pessoa digitou continua na tela. Se o servidor recusar, volte ao
  valor anterior e mostre o erro perto do campo — não engula.
- Criar um follow-up ou uma mensagem de fidelização acrescenta um card novo já editável no topo da
  lista. `regra_follow_up.nome` é `NOT NULL` no banco e o payload atual não manda nome
  (`Omit<RegraFollowUp, "id" | "nome">`); confirme como o backend preenche hoje e **não quebre isso**.
  Se descobrir que quebra, relate antes de contornar.

## Bloco 7 — Prévia no WhatsApp

Coluna à direita nas abas Follow-up e Fidelização, fixa ao rolar, com o título "VISUALIZAÇÃO NO
WHATSAPP" acima.

- Cabeçalho verde com avatar, nome da instância e "online".
- Fundo da conversa, selo "HOJE", e um balão branco alinhado à esquerda com o texto da mensagem e um
  horário fixo.
- Barra de composer falsa embaixo, sem interação.
- Abaixo do quadro, a legenda do gatilho: "Enviado após 2 horas sem resposta".
- O `{nome}` é substituído por um nome de exemplo vindo do catálogo de textos.
- O nome no cabeçalho vem do tema da instância, não cravado como "Estrutural Vidros" — de novo, isto
  é multi-cliente.

O modelo desenha uma moldura de iPhone. **Não** replique o aparelho: faça um enquadramento simples,
sem depender de imagem externa e sem `<img>` de recurso que não existe no repositório.

## Bloco 8 — O que não pode regredir

- A aba **Automação continua invisível e inacessível para o papel ATENDENTE** (E48). Se sumir a
  proteção, a etapa está errada.
- O `#reset` e a ponte de `ContextoDeServico` não são tocados nesta etapa.
- Nenhum comportamento de rede muda: mesmas rotas, mesmas chaves de cache do TanStack Query.
- `frontend/src/components/automacao/pagina-automacao.test.tsx` vai quebrar. Atualize **no mesmo
  commit** — não deixe conserto de teste para um commit depois.

---

## Verificação

- `npm run lint`, `npm run typecheck` e `npm test` no `frontend/`, todos verdes.
- Backend: `./mvnw -pl crm-equipe -am verify` pelo campo `cargo`, e depois `./mvnw clean verify` no
  reator inteiro. Confirme que a contagem do `OpenApiIT` **não** mudou; se mudou, você criou operação
  nova, o que está fora do escopo.
- Teste de que o switch de disponibilidade chama `PATCH /api/v1/usuarios/{id}/disponibilidade-ia` e
  que gestor, subgestor e inativo **não** aparecem na lista de atendentes.
- Teste de que editar o texto de um follow-up dispara **um** `PUT` ao sair do campo, e nenhum
  enquanto se digita.
- Teste de que a aba Automação continua negada para `ATENDENTE`.
- Teste de que o bloco de parâmetros avançados nasce fechado e continua editando
  `automacao.habilitada` quando aberto.
- `schema.test.ts` verde com as strings novas no catálogo.

## Relatório

Diga, com evidência:

1. Quais tokens você usou no lugar de cada família de hex do modelo, e onde faltou token.
2. Como o backend preenche `regra_follow_up.nome` hoje.
3. Se a contagem do `OpenApiIT` mudou.
4. As duas decisões do Bloco 3, para o Marcondes confirmar.
