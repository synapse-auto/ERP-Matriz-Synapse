# Prompt E131 — Modal de templates em três colunas, e janela fechada com "Nova mensagem"

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`feat/modal-de-templates`) e PR. **Sem merge, sem deploy.**
> Frontend + `textos.json`. **Sem migration, sem endpoint, sem regra de negócio.**
> `./mvnw -pl crm-app -am verify` (o `textos.json` é recurso do backend) e a suíte do `frontend/`.

---

## O pedido

Duas mudanças de tela, que juntas resolvem a mesma queixa: **escolher um template hoje é apertado e
confuso.**

**1. O seletor de template vira um modal de três colunas:**

| Coluna | Conteúdo |
| --- | --- |
| **TEMPLATES** | busca por nome + lista rolável de cartões. Cada cartão: nome, `idioma · CATEGORIA`, e o selo de status |
| **CONFIGURAÇÃO DE ENVIO** | os campos de variável **do template selecionado**, um por linha, rotulados `Mensagem — variável {n}` |
| **PRÉVIA** | o corpo do template com as variáveis já substituídas, num cartão que imita o balão do WhatsApp |

O botão de ação é **um só**, no rodapé do modal. Cancelar ao lado.

**2. Com a janela de 24h fechada, o composer vira um aviso curto com um botão.** Nada de despejar a
lista inteira de templates dentro da conversa como hoje. Ícone de relógio, o texto explicando que a
sessão de 24 horas acabou e que só templates são aceitos, e um botão **"Nova mensagem"** que abre o
modal do item 1.

---

## Bloco 1 — O que existe hoje, e por que está apertado

`lista-templates-whatsapp.tsx` monta **um cartão por template**, e cada cartão carrega **tudo**:
nome, prévia, todos os campos de variável e o próprio botão de enviar (`CartaoDeTemplate`, a partir
da linha 137). Numa coluna estreita isso vira uma pilha em que o atendente rola muito e não vê o
resultado enquanto digita.

Ela é usada em **três lugares**, e confirme os três antes de mexer:

1. `composer.tsx:353` — painel inline da janela fechada, `max-w-[780px]` dentro da conversa. **É o
   que a mudança 2 substitui.**
2. `composer.tsx:721` — `Dialog` com `sm:max-w-lg`, aberto pelo botão de template do composer.
3. `dialogo-novo-contato.tsx:195` — dentro do diálogo de novo contato, entregue pela E127.

A boa notícia: os três passam a abrir **o mesmo modal**. Extraia-o num componente próprio e use nos
três. Não faça três variações da mesma tela.

O componente já tem `modoSelecao`, `templateSelecionado` e `rotuloAcao` (props da E127). O layout
novo é **sempre** seleção-primeiro, então essa distinção fica mais simples, não mais complexa —
aproveite em vez de acrescentar bandeira nova.

---

## Bloco 2 — O modal

- Largura: o suficiente para as três colunas respirarem. Abaixo desse ponto, empilhe as colunas na
  ordem TEMPLATES → CONFIGURAÇÃO → PRÉVIA. **Não** deixe uma grade de três colunas espremida no
  celular.
- Seleção: clicar num cartão seleciona. As colunas 2 e 3 refletem **o selecionado**. Sem seleção,
  as duas mostram um estado vazio explicando que é preciso escolher um template.
- Template **sem variável**: a coluna 2 diz que não há nada a preencher; a prévia aparece igual e o
  botão de ação fica habilitado.
- Só templates `APROVADO` entram na lista — o filtro já existe, **não afrouxe**.
- A busca, o agrupamento por categoria (`ORDEM_DAS_CATEGORIAS`), `filtrarTemplates` e
  `agruparPorCategoria` continuam como estão. É lógica testada; não reescreva para acomodar layout.
- O link "Criar template" para `/templates-whatsapp` continua acessível no modal.
- Validação: variável obrigatória vazia continua bloqueando a ação, com a mesma mensagem e o mesmo
  `aria-invalid`/`aria-describedby`. A E127 depende disso.
- Base UI: `data-active:`, **nunca** `data-[state=active]:`.

### A prévia

Hoje `interpolarCorpoDoTemplate` deixa o marcador cru (`{{1}}`) quando o valor está vazio. Numa
coluna dedicada a mostrar como a mensagem vai chegar, `{{1}}` não comunica nada.

Troque, **só na prévia**, por um marcador legível no padrão `[variável 1]`, vindo do catálogo de
textos. `interpolarCorpoDoTemplate` é usada em outro lugar — **não mude a função**; derive a prévia
por cima dela ou acrescente uma função nova ao lado, e diga no relatório qual caminho escolheu.

O corpo continua sendo exibido **literalmente**, com os asteriscos do WhatsApp visíveis. **Não**
renderize `*negrito*` como negrito: o que o atendente precisa ver é o texto exato que vai sair.

### O que a referência mostra e nós não temos

O CRM de referência desenha, no rodapé da prévia, o **botão de resposta rápida** do template ("Sim,
podemos"). Nosso `TemplateWhatsApp` (`types.ts:33`) tem apenas `nome`, `idioma`, `categoria`,
`status`, `corpo` e `quantidadeDeParametros` — **a API não devolve botões**.

Portanto: **não invente esse botão na prévia.** Deixar a prévia mostrando um botão que o backend não
conhece é mentir para o atendente. Se achar que vale a pena, registre como sugestão no relatório —
seria etapa própria, com mudança de contrato.

---

## Bloco 3 — Janela fechada: aviso + "Nova mensagem"

Em `composer.tsx`, o ramo `if (!janelaAberta)` (linha 340) hoje devolve um cartão de 780px com a
lista de templates inteira dentro. Passa a devolver um **aviso compacto**:

- ícone de relógio;
- o texto de janela encerrada — os dois casos que já existem no catálogo continuam distintos:
  `janelaFechadaTitulo`/`janelaFechadaDescricao` quando a janela **existiu e expirou**, e
  `janelaInexistenteTitulo`/`janelaInexistenteDescricao` quando o cliente **nunca escreveu**. Não
  unifique: são situações diferentes e o atendente precisa saber qual é;
- um botão **"Nova mensagem"** que abre o modal do Bloco 2.

Nada mais. Sem lista, sem campos, sem prévia nessa faixa.

O envio em si **não muda**: continua sendo o mesmo `enviar.mutate` com
`template: { nome, idioma, parametros }` que já existe nos dois call sites do composer. Nenhuma
chamada de API nova, nenhum parâmetro novo.

---

## Bloco 4 — Textos

As chaves novas (títulos das três colunas, `Mensagem — variável {n}`, o marcador `[variável {n}]`,
o rótulo "Nova mensagem", os estados vazios) vão para `backend/crm-app/src/main/resources/textos.json`,
**dentro de `atendimentos.composer`**, junto das que já existem (`previaTemplate`,
`parametroTemplate`, `enviarTemplate`, `buscaTemplate`, `escolherTemplate`, `semTemplates`,
`semResultadosTemplate`, `criarTemplate`).

`frontend/src/lib/config/schema.ts` valida esse catálogo com zod: **toda chave nova entra lá também**,
no mesmo commit. Catálogo e schema fora de sincronia quebram a tela inteira em runtime, não só o
pedaço novo.

**Não remova nenhuma chave existente**, mesmo que o layout novo deixe de usá-la. Remoção de chave de
catálogo é etapa própria.

---

## Bloco 5 — O que não muda

- `useTemplatesWhatsApp` / `GET /api/v1/whatsapp/templates`, e a `queryKey` `["whatsapp-templates"]`.
- Qualquer arquivo `.java`. Se abrir um, parou de fazer esta etapa. (`textos.json` é recurso, não
  código.)
- A checagem de janela do backend, `janela-24h.ts`, e o `estadoDaJanelaTextoLivre` com os três
  estados. O layout muda; a decisão de quando a janela está aberta, não.
- O diálogo de novo contato continua **exigindo template** quando o canal exige (E127) e continua
  permitindo abrir a conversa sem mensagem nenhuma.

---

## Bloco 6 — Testes

- Selecionar um template preenche as colunas de configuração e prévia; trocar de template troca as
  duas.
- Sem seleção, o botão de ação está desabilitado e as colunas 2 e 3 mostram o estado vazio.
- Template com variável vazia: ação bloqueada, mensagem de obrigatório, `aria-invalid` no campo.
- Template **sem** variáveis: ação habilitada de imediato.
- Prévia com variável preenchida mostra o valor; com variável vazia mostra `[variável n]` e **não**
  `{{n}}`.
- Só `APROVADO` aparece na lista.
- Janela fechada: o composer mostra o aviso e o botão "Nova mensagem", e **não** renderiza a lista de
  templates direto na conversa. Clicar no botão abre o modal.
- Janela **inexistente** mostra o texto de "nunca escreveu", não o de "expirou".
- Os testes da E127 (`dialogo-novo-contato`) continuam verdes: escolher template dispara
  `onConfirmar` com `template` preenchido e sem `primeiraMensagem`.
- Nenhum teste do composer que valide o **envio** pode mudar de asserção. Se algum mudar, você mexeu
  em regra e não em layout — pare e avise.

## Verificação

```
./mvnw -pl crm-app -am verify
```
e a suíte do frontend. Spotless e a contagem de endpoints do OpenAPI verdes.

## Relatório

1. Os três call sites, confirmados, e como cada um passou a abrir o mesmo modal.
2. Como a prévia passou a mostrar `[variável n]` sem alterar `interpolarCorpoDoTemplate`.
3. As chaves novas de `textos.json` e a confirmação de que o `schema.ts` foi atualizado junto.
4. Confirmação de que a prévia **não** inventou o botão de resposta rápida, e por quê.
5. Confirmação de que nenhum `.java` foi tocado e que nenhuma chamada de API mudou.
6. Como o layout se comporta abaixo da largura das três colunas.
