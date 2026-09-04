# Prompt E138 — Agenda: conector do filtro sai em inglês e o servidor recusa

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`fix/conector-do-filtro-da-agenda`) e PR. **Sem merge, sem deploy.**
> Frontend + uma anotação no backend. **Sem migration, sem mudança de comportamento no domínio.**
> Suíte do `frontend/` e `cd backend && ./mvnw -pl crm-app -am verify`.

**Quebrado em produção.** Buscar qualquer texto na Agenda de contatos devolve
"Não foi possível carregar a agenda". `POST /api/v1/leads/filtrar` e `.../filtrar/contagem` retornam
**400** em toda tentativa.

---

## A causa, já isolada — não reinvestigue

Resposta literal do servidor, capturada em produção:

```json
{ "title": "Filtro invalido", "status": 400,
  "detail": "conector nao permitido: OR. Permitidos: [E, OU]",
  "instance": "/api/v1/leads/filtrar/contagem" }
```

`CriterioComposto.Conector` (`crm-core/domain/filtro/CriterioComposto.java:51`) tem exatamente dois
valores: **`E`** e **`OU`**. O frontend manda `AND` e `OR`.

Nunca funcionou. Um filtro sozinho vira nó `SIMPLES`, que não tem conector, e passa. A busca por
texto monta um `COMPOSTO` com `OR` sobre `nome`/`telefone`/`cpf`, e dois filtros simultâneos montam
um `COMPOSTO` com `AND` — os dois casos morrem em 400. Não é regressão de deploy: é o contrato
divergente desde a construção da tela.

## O engano está reforçado em três lugares — corrija os três

1. **`frontend/src/lib/agenda/use-agenda.ts`** monta `conector: "OR"` (linhas 38 e 58) e
   `conector: "AND"` (linha 92).
2. **`frontend/src/lib/agenda/types.ts:82`** tipa `conector: "AND" | "OR"`.
3. **`frontend/src/lib/agenda/use-agenda.test.ts`** assere `"OR"` e `"AND"` (linhas 28, 65, 75) —
   **os testes estão verdes validando o contrato errado**. Corrigir só o código de produção faz
   esses testes quebrarem, e é esse o sinal de que a correção está certa.

E a documentação publicada concorda com o erro, não com a implementação:

4. **`FiltroDeLeadsController.java:237`** — `@Schema(description = "AND ou OR; usado em nó COMPOSTO.",
   allowableValues = {"AND", "OR"})`.
5. **`FiltroDeLeadsController.java:136`** — o `@ExampleObject` do OpenAPI usa `"conector":"AND"`.

## A direção da correção — decidida, não a inverta

**O frontend passa a mandar `E` e `OU`.** Não afrouxe o backend para aceitar `AND`/`OR`.

O vocabulário do filtro é português em todo o resto do contrato: os operadores que o próprio
frontend já envia são `CONTEM`, `EM`, `VAZIO`, `PREENCHIDO`, e os apelidos de campo são `nome`,
`telefone`, `empresa`. Só o conector ficou em inglês. Aceitar os dois no servidor
institucionalizaria a divergência e deixaria duas grafias válidas para a mesma coisa para sempre.

Não há dado a migrar: `filtro_modular` (V6) existe no banco mas **nenhuma classe de produção a
referencia** — não há árvore salva com `AND`/`OR` em lugar nenhum.

## O que fazer

- `types.ts`: `conector: "E" | "OU"`.
- `use-agenda.ts`: `OR` → `OU`, `AND` → `E`. O compilador aponta os três pontos.
- `use-agenda.test.ts`: asserções passam a esperar `E`/`OU`.
- `FiltroDeLeadsController`: `allowableValues = {"E", "OU"}`, descrição correspondente, e o
  `@ExampleObject` com `"conector":"E"`. **A anotação passa a descrever o que o código faz.**

Nenhuma outra mudança. Não mexa em `CriterioComposto`, `InterpretadorDeCriterio`,
`CampoFiltravel`, `Operador`, RLS ou visibilidade.

## Testes obrigatórios

1. `use-agenda.test.ts`: busca por texto produz `{tipo:"COMPOSTO", conector:"OU", …}` com os três
   nós `nome`/`telefone`/`cpf`.
2. Dois grupos de filtro ativos produzem raiz `{tipo:"COMPOSTO", conector:"E", …}`.
3. **Teste de contrato ponta a ponta** — o que faltava e é o que impede a recaída: uma IT em
   `crm-app` que envia ao endpoint real o **mesmo corpo que o frontend monta** para uma busca por
   texto e espera **200**, não 400. Monte o corpo a partir do JSON literal, não de um helper de
   teste que já saiba a grafia certa; um helper esconderia exatamente o erro que estamos
   corrigindo.
4. `FiltroModularIT` continua verde sem alteração.

## Definição de pronto

- Buscar texto na Agenda devolve resultado; dois filtros juntos também.
- Nenhuma ocorrência de `"AND"` ou `"OR"` como conector sobrou no frontend.
- O OpenAPI descreve `E`/`OU`.
- Existe teste que reprova se o frontend voltar a mandar o conector em inglês.
- Suíte do frontend, typecheck, lint e build verdes; `./mvnw -pl crm-app -am verify` verde.
- Relatório final com os sete itens do `AGENTS.md`.
