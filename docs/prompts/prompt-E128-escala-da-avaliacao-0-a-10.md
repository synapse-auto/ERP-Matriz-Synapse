# Prompt E128 — Escala da avaliação de 1–5 para 0–10

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`feat/escala-avaliacao-0-a-10`) e PR. **Sem merge, sem deploy.**
> Backend e frontend. **Uma migration nova (V56), com conversão de dado.**
> `./mvnw -pl crm-app -am verify` na raiz de `backend/` e a suíte do `frontend/`.

---

## O pedido

O CRM guarda a nota do atendimento em **1–5**. O contrato EV-08, que o n8n já implementou, grava
**Ruim = 2, Bom = 7, Ótimo = 10**. Os dois lados são incompatíveis hoje: `nota = 7` e `nota = 10`
violam o `CHECK` da tabela e o domínio recusa antes disso.

O cliente decidiu: **a escala do CRM passa a ser 0–10.** É o CRM que muda, não o n8n.

Isso não é preferência de estilo — é o que destrava ligar a pesquisa de satisfação. Com a escala
atual, o cliente recebe os três botões, responde "Bom", e a gravação falha. Só "Ruim" entraria.

---

## Bloco 0 — Ordem e pré-requisito

Faça a branch **a partir de `main` com o PR #60 (E126) já mergeado** — a V55 tem que existir, porque
esta etapa cria a **V56**. Se a última migration for a V54, pare e avise.

---

## Bloco 1 — Onde a escala está cravada hoje

Levantei os pontos. Confirme cada um e diga no relatório se achou outro:

| Onde | O que tem |
| --- | --- |
| `V2__equipe.sql:23` | `nota SMALLINT NOT NULL CHECK (nota BETWEEN 1 AND 5)` |
| `Avaliacao.java` | `NOTA_MINIMA = 1`, `NOTA_MAXIMA = 5`, `exigirFaixa`, e o javadoc que diz "o prototipo 0–10 nao entra aqui" |
| `NotaDeAvaliacaoInvalidaException` | javadoc "escala 1–5" |
| `RegistrarAvaliacaoUseCase:20` | javadoc "nota 1–5" |
| `AtendimentoAcoesController:304,320` | duas descrições OpenAPI com "nota 1–5" |
| `AtendimentosAutomacaoInternalController:150` | descrição OpenAPI "escala 1–5" |
| `DashboardVisaoGeralRepositorioJdbc:77` | o literal **`5`** passado como `escalaMaxima` |
| `textos.json` → `atendimentos.avaliacao` | `"nota de 1 a 5"` e `"Avaliação registrada: {nota}/5"` |
| `dialogo-avaliacao.tsx:25` | `const NOTAS = [1, 2, 3, 4, 5]` |

**A escala tem que passar a viver num lugar só.** Hoje ela está repetida em pelo menos quatro.

Restrição que você vai encontrar: `crm-relatorios/pom.xml` depende **apenas** de
`crm-shared-kernel` — ele não alcança `Avaliacao` em `crm-atendimento`. Então a constante
compartilhada precisa ficar num módulo que os dois enxergam. Escolha, justifique no relatório, e
garanta que o `ArquiteturaTest` continua verde. Não crie dependência nova entre módulos de negócio
só para reaproveitar um número.

---

## Bloco 2 — Migration V56: só a estrutura. O dado antigo já foi tratado

`V56__escala_avaliacao_0_a_10.sql` faz **uma coisa só**: trocar o `CHECK`.

**Não escreva `UPDATE`, `DELETE` nem `CREATE TABLE` de arquivo nesta migration.** As 9 linhas que
existiam em produção já foram arquivadas e removidas manualmente, fora do Flyway, por decisão do dono
do projeto: eram notas que o próprio vendedor deu a si mesmo pelo diálogo manual que a E124 removeu —
nenhuma veio de cliente. A escala nova vale a partir de agora.

Se você acrescentar um `CREATE TABLE avaliacao_arquivo_pre_e128` aqui, ele vai falhar em produção
com *relation already exists*, o Flyway aborta e a aplicação **não sobe**. É a única forma de esta
etapa derrubar o CRM — não faça.

Qualquer banco novo (CI, Testcontainers, outro filho) nasce com `avaliacao` vazia, então não há dado
para converter em lugar nenhum.

### O `CHECK`

O `CHECK` da V2 é **inline e sem nome** — o Postgres gerou o nome sozinho. **Não chute
`avaliacao_nota_check`.** Descubra o nome real no catálogo (`pg_constraint` filtrando por
`conrelid = 'avaliacao'::regclass` e `contype = 'c'`) e faça o `DROP` por esse nome, ou escreva um
`DO $$ ... $$` que resolva o nome em tempo de execução. Diga no relatório qual caminho usou e qual
era o nome.

Depois, `ADD CONSTRAINT` com `CHECK (nota BETWEEN 0 AND 10)`, **nomeado desta vez** — constraint
anônima foi justamente o que tornou esta etapa mais chata do que precisava.

Sobre RLS: `avaliacao` **não tem** política nenhuma (confirmei nas migrations). Confirme você mesmo e
diga no relatório — se aparecer `FORCE ROW LEVEL SECURITY` nessa tabela, pare e avise em vez de
tentar contornar.

---

## Bloco 3 — O domínio e o resto do backend

- `Avaliacao`: `NOTA_MINIMA = 0`, `NOTA_MAXIMA = 10`. Reescreva o javadoc — a frase atual diz o
  contrário do que o código vai passar a fazer.
- Os três javadocs e as três descrições OpenAPI da tabela do Bloco 1: **atualize o texto**. Descrição
  de endpoint que promete "1–5" enquanto o servidor aceita 0–10 é contrato mentindo para quem
  integra, e o Dylan lê exatamente isso.
- `DashboardVisaoGeralRepositorioJdbc:77`: o literal `5` sai e passa a vir da constante única.
- **Não** mexa em `RegistrarAvaliacaoUseCase.executarPelaAutomacao`, no 409 de duplicata, no 422, nem
  no índice `uq_avaliacao_atendimento`. A regra "uma nota por atendimento" não muda.

---

## Bloco 4 — Frontend

**`dialogo-avaliacao.tsx` está órfão.** A E124 tirou o botão "Avaliar" da tela e **nada mais importa
esse componente** — confirme com uma busca antes de decidir.

Se estiver órfão mesmo: **apague o componente, o teste dele e os hooks que só ele usava**
(`useAvaliacaoDoAtendimento`, `useRegistrarAvaliacao`, se não sobrar outro consumidor). Um seletor de
cinco estrelas morto, cravando `[1,2,3,4,5]` depois desta etapa, é a próxima pessoa lendo e
concluindo que a escala é 1–5.

**Não apague os endpoints HTTP** `GET`/`POST /api/v1/atendimentos/{id}/avaliacao`. Eles são o
caminho humano e removê-los é decisão de produto, não desta etapa.

`textos.json`: `atendimentos.avaliacao.descricao` e `jaRegistrada` citam a escala antiga. Se o
diálogo for apagado, essas chaves ficam sem uso — **deixe as chaves no arquivo**, apenas corrija os
números. Remover chave de catálogo é etapa própria.

Dashboard: o card mostra `{media}/{escalaMaxima}`; com o backend corrigido ele passa a mostrar `/10`
sozinho. Ajuste os fixtures dos testes (`pagina-dashboard.test.tsx` tem `escalaMaxima: 5`).

---

## Bloco 5 — O toggle da avaliação não parece um toggle

Assunto separado, na mesma etapa porque é a mesma tela que o cliente vai usar para ligar a pesquisa.

A chave `avaliacao_atendimento.habilitada` (V55, tipo `BOOLEAN`) **é renderizada**, mas não parece um
interruptor. Em `pagina-automacao.tsx`, `CampoValor` desenha todo parâmetro `BOOLEAN` como um
`<input type="checkbox">` cru, com um botão "Salvar" separado — enquanto a mesma tela, em
`LinhaRecurso`, usa o componente `Switch` do design system para exatamente a mesma ideia.

Troque: parâmetro `BOOLEAN` passa a usar `Switch`, com o mesmo comportamento de salvar dos outros
tipos (o botão continua existindo; não invente salvamento automático numa tela onde todos os outros
campos exigem confirmação — mudar isso só para o booleano cria duas regras na mesma lista).

`aria-label` obrigatório no `Switch`, com a descrição do parâmetro. Base UI: `data-active:`,
**nunca** `data-[state=active]:`.

Não mexa em mais nada da tela de Automação.

---

## Bloco 6 — Testes

- `Avaliacao`: `0` e `10` são aceitos; `-1` e `11` levantam `NotaDeAvaliacaoInvalidaException`. O
  teste que hoje prova que `6` é inválido **prova o contrário do novo contrato** — inverta e diga no
  relatório o que ele passou a afirmar.
- `POST /internal/v1/atendimentos/{id}/avaliacao` com `nota = 7` e `nota = 10` → **201**. Estes são
  os dois casos que hoje quebram o workflow do Dylan; sem eles a etapa não provou nada.
- `nota = 11` → 422, com o mesmo corpo de erro de antes.
- Migration: com a V56 aplicada, `INSERT` de `nota = 0`, `7` e `10` passa e `nota = 11` é recusado
  pelo banco. Escreva no padrão do `NonoDigitoMigrationIT` e do `LeituraPorUsuarioMigrationIT` que já
  existem. **Não** escreva teste de conversão de dado — não há conversão.
- Dashboard: `escalaMaxima` devolvido é `10`.
- `AvaliacaoAtendimentoIT` e `WebhookAvaliacaoIT` continuam verdes — o `WebhookAvaliacaoIT` usa nota
  na coleta interna e vai acusar se você esquecer algum ponto.
- Frontend: se apagou o diálogo, os testes dele vão junto; nenhum outro teste pode quebrar por isso.
  Se algum quebrar, o componente **não** estava órfão — pare e avise.
- Automação: o parâmetro `BOOLEAN` renderiza um `Switch`, alterna, e salva com o botão.

## Verificação

```
./mvnw -pl crm-app -am verify
```
e a suíte do frontend. Spotless, ArchUnit e a contagem de endpoints do OpenAPI verdes.

## Relatório

1. Onde ficou a constante única da escala, e por que nesse módulo.
2. O nome real do `CHECK` da V2, como você o descobriu, e o nome que deu ao novo.
3. Confirmação de que a V56 não contém `UPDATE`, `DELETE` nem `CREATE TABLE`.
4. A confirmação de que `avaliacao` não tem RLS.
5. Se o `dialogo-avaliacao.tsx` estava órfão e o que foi apagado com ele.
6. O que o teste invertido de faixa passou a afirmar.
7. A lista dos textos de contrato (javadoc + OpenAPI) que deixaram de dizer "1–5".
