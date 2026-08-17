# Prompt E19 — Controles nativos e a barra de filtros da Agenda

> Leia `AGENTS.md`. Continuação da E18, que ligou o tema à base do shadcn.
> Levantado olhando as telas renderizadas em homologação, não relatório.
> Commite e faça push por bloco. Ao encerrar: `cd backend && ./mvnw clean verify` se tocar em backend, e informe o **número da run** do CI.

---

## Bloco 1 — Nenhum controle nativo do navegador

É o que mais destoa hoje. `<select>` e `<input type="date">` crus renderizam com o visual do sistema operacional — o calendário do Chrome, a lista azul do Windows — e nenhum token de tema alcança isso.

Ocorrências vistas em homologação:

| Tela | Controle |
|---|---|
| Mensagens Programadas | `De` e `Até` como `<input type="date">`, com o calendário do navegador |
| Mensagens Programadas | `Status` como `<select>` nativo |
| Agenda | `Campo` e `Operador` do construtor de filtro como `<select>` nativo |

**Varra o frontend inteiro atrás de `<select>` e `<input type="date">`** — não corrija só estes três. Substitua por:

- `Select` do shadcn para escolha
- `Popover` + `Calendar` do shadcn para data

Se o `Calendar` não estiver instalado, instale-o pelo shadcn (é o único caso em que dependência nova está autorizada nesta etapa). Confira antes se já existe.

Formato de data em pt-BR e locale correto no calendário — dia da semana começando em domingo, meses em português, como no protótipo.

## Bloco 2 — A barra de filtros da Agenda

`design/componentes/Agenda.html`. Este bloco é sobre **quem usa**, não sobre aparência.

Hoje a tela expõe o motor de filtro: o usuário escolhe um campo numa lista de 15, depois um operador, depois um valor. Isso é a ferramenta de quem construiu, não a de quem atende. Alguém que quer ver os leads de Taguatinga não deveria precisar saber que existe um campo "Localização" e um operador "CONTÉM".

O protótipo tem, na mesma linha:

- **caixa de busca livre**, placeholder "Buscar por nome, telefone, CNPJ/CPF ou tag..."
- **quatro dropdowns prontos**: Etapa, Atendente, Cidade, Tag — cada um listando os valores que existem, com seleção múltipla
- **contador** "Exibindo X de Y leads" à direita

**A busca livre não precisa de lógica nova.** `CriterioComposto.ou(...)` já existe no domínio — o javadoc da classe usa como exemplo `(etapa = X OU tag = Y) E semRetornoDias > 30`. A caixa única é compor quatro critérios `CONTEM` com `OU` e mandar para o `POST /api/v1/leads/filtrar` que já está lá. Nenhum endpoint novo.

Os quatro dropdowns viram, cada um, um critério `IGUAL`/`EM` no mesmo composto, ligados por `E` entre si.

**O construtor campo→operador→valor não some** — vira "Filtros avançados", recolhido, para quem precisar de `Dias sem retorno` ou `Nº de mensagens`. Perder essa capacidade seria jogar fora a E03b.

Chips de filtro ativo removíveis continuam como estão.

## Bloco 3 — A superfície da página

No protótipo, o conteúdo fica dentro de um **painel branco com cantos arredondados**, flutuando sobre o fundo `#E6ECF4`. Hoje o conteúdo está direto sobre o fundo, sem essa superfície.

Ajuste no layout do `(shell)`, para valer em todas as telas de uma vez. Confira no `.html` a margem e o raio reais antes de escolher valores.

## Definição de pronto

- [ ] Nenhum `<select>` ou `<input type="date">` nativo em nenhuma tela — varredura completa, não só as três citadas
- [ ] Calendário em pt-BR
- [ ] Agenda com busca livre + quatro dropdowns + contador na mesma linha
- [ ] Busca livre usando `CriterioComposto.ou`, sem endpoint novo
- [ ] Construtor avançado preservado, recolhido
- [ ] Conteúdo dentro da superfície branca arredondada, em todas as telas
- [ ] Testes no padrão existente; CI verde com **número da run**
- [ ] Commit e push por bloco

Commit por bloco: `feat: controles do design system`, `feat: barra de filtros da agenda`, `feat: superfície da página`.

No relatório: **quantas ocorrências de controle nativo você encontrou na varredura.** Se for mais que as três que eu listei, é sinal de que o problema é sistêmico e vale uma regra de lint para não voltar.
