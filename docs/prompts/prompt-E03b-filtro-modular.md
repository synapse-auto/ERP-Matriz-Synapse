# Prompt E03b — Filtro Modular

> Pré-requisito: E03a commitada. **Comece com contexto limpo.**
> Não releia `docs/03` nem `docs/06` — tudo o que você precisa está aqui.

---

**Etapa E03b — Filtro modular (Composite + Interpreter).**

Mecanismo reusado em três telas (Atendimentos, Agenda de Contatos, Campanhas). Nasce genérico ou vira três implementações divergentes.

## 1. Composite

```
Criterio (sealed)
├── CriterioSimples    campo, operador, valor
└── CriterioComposto   AND|OR + List<Criterio>
```

Use tipo selado, como em `VisibilidadeLead`, pelo mesmo motivo: um operador novo deve **quebrar o build** até ter tradução SQL. É a lição do `switch` exaustivo da E02 aplicada aqui.

## 2. Interpreter

Percorre a árvore e emite condição SQL parametrizada.

**Trate a entrada como hostil.** O JSON de critérios vem do cliente. Um `campo` ou `operador` que chegue ao SQL é injeção.

- Valide contra **allowlist** de campos e operadores. Fora da lista → rejeita, não sanitiza.
- Valores sempre como parâmetro, jamais concatenados.
- Limite a profundidade de aninhamento (uma árvore de 10 mil nós é DoS barato).

## 3. Composição com visibilidade

O filtro do usuário compõe **por cima** da `VisibilidadeLeadSpecification`, com `AND`. Nunca substituindo.

Um filtro do cliente pode **reduzir** o que ele vê, nunca **ampliar**. Este é o ponto onde o isolamento de agenda poderia vazar depois de toda a blindagem da E02 — vale um teste explícito.

## 4. Contagem em tempo real

`POST /api/v1/leads/filtrar/contagem` retorna o total sob o filtro montado, antes de salvar.

A tela chama isso a cada mudança de critério. `COUNT` sobre a condição, sem carregar linhas. Combine *debounce* no contrato com o frontend.

## 5. Endpoints

| Método | Rota | Papel |
|---|---|---|
| POST | `/api/v1/leads/filtrar` | Atendente |
| POST | `/api/v1/leads/filtrar/contagem` | Atendente |

Persistência em `filtro_modular` (JSONB) fica para quando Campanhas existir — nesta etapa o filtro é transitório.

## 6. Testes

- Filtro aninhado: `(etapa = X OR tag = Y) AND semRetornoDias > 30` retorna o conjunto correto
- Contagem bate com o tamanho do resultado
- **Filtro do cliente não amplia visibilidade:** atendente monta filtro que "pegaria" leads de colega e continua sem ver nenhum
- Campo ou operador fora da allowlist → 400
- Tentativa de injeção via `campo` não executa SQL arbitrário
- Aninhamento além do limite → 400

## Definição de pronto

- [ ] Filtro aninhado com contagem
- [ ] Allowlist validada, com teste de injeção
- [ ] Composição com visibilidade provada por teste
- [ ] CI verde

Commit: `feat: filtro modular com composite e interpreter`.

Ao terminar, me diga quantos campos entraram na allowlist e se algum requisito de filtro não coube no modelo `campo/operador/valor` — é aí que o desenho costuma vazar.
