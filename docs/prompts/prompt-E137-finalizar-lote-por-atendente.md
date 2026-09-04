# Prompt E137 — Finalizar em lote escolhendo o atendente

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`feat/finalizar-lote-por-atendente`) e PR. **Sem merge, sem deploy.**
> Backend + frontend. **Sem migration.**
> `cd backend && ./mvnw -pl crm-app -am verify` e a suíte do `frontend/`.
> **Empilha na E136** (as duas mexem em `lista-conversas.tsx`). Se a E136 ainda não estiver na
> `main`, ramifique a partir dela e diga isso no relatório.

## O pedido

Pedido do cliente, marcado como urgente: *"preciso encerrar somente conversas de um vendedor
específico e não consigo"*.

O diálogo de confirmação do **Finalizar todos** ganha a escolha do atendente. Um por vez:

```
( ) Todos          ( ) Clayton          ( ) Michael  …

Atendimentos que serão finalizados: 24

[ Voltar ]                                   [ Finalizar 24 ]
```

A contagem muda conforme a seleção. E o rótulo do item de menu passa de
`"Finalizar todos os atendimentos visíveis"` para **`"Finalizar Todos"`**.

**Decisão já tomada — seleção única, não múltipla.** O pedido diz "qual atendente" no singular. Um
rádio resolve o caso real (esvaziar a fila de um vendedor) e evita a pergunta de o que fazer quando
alguém marca cinco caixas e três falham. Não implemente seleção múltipla.

---

## Bloco 1 — O servidor decide o que entra no lote, sempre

A regra de visibilidade não pode ser reescrita no cliente. O `atendenteId` que vier do frontend é
um **filtro adicional** sobre o que a RLS já permite — nunca uma forma de alcançar algo novo. Um
atendente que mandar o id de um colega tem que continuar finalizando zero atendimentos, e isso
precisa de teste.

`AtendimentoRepositorioJdbc.SQL_ABERTOS_VISIVEIS` hoje é:

```sql
SELECT ... FROM atendimento WHERE status = 'EM_ATENDIMENTO' ORDER BY iniciado_em, id
```

O `status = 'EM_ATENDIMENTO'` veio da PR #70 e **não pode voltar a ser `<> 'FINALIZADO'`**:
Potenciais (`EM_IA`) fora do lote é deliberado — o `UPDATE` que os tira da visibilidade do
atendente é recusado pela RLS e derrubava o lote inteiro. O comentário na classe explica; mantenha-o.

Acrescente o filtro opcional por dono, no padrão `(? IS NULL OR atendente_id = ?)` ou com consulta
separada — o que ficar mais legível, mas **uma única definição** de "o que é finalizável", usada
pela contagem e pela execução.

## Bloco 2 — A contagem passa a vir quebrada por atendente

`GET /api/v1/atendimentos/finalizar-lote` hoje devolve só `quantidade`. Passe a devolver também a
quebra, na mesma consulta e no mesmo recorte:

```json
{
  "quantidade": 24,
  "porAtendente": [
    { "atendenteId": "…", "nome": "Clayton", "quantidade": 9 },
    { "atendenteId": "…", "nome": "Michael", "quantidade": 15 }
  ]
}
```

Uma requisição alimenta o rádio **e** todos os números. Não faça o frontend chamar o endpoint uma
vez por atendente, e não monte a lista do seletor a partir de
`/api/v1/atendimentos/destinos-de-transferencia`: aquilo lista destinos de transferência, não donos
de atendimentos finalizáveis, e produziria opções que finalizam zero.

Consequência natural e desejada: para atendente, `porAtendente` vem com uma entrada só (ele mesmo),
e o seletor fica trivial. Para gestão, vem a equipe toda que tem fila aberta.

`POST /api/v1/atendimentos/finalizar-lote` passa a aceitar corpo **opcional** `{ "atendenteId": … }`.
Sem corpo, comportamento idêntico ao de hoje — a compatibilidade importa porque o botão atual já
está em produção.

Atualize as descrições OpenAPI dos dois endpoints. A do `GET` hoje diz "atendimentos EM_ATENDIMENTO
que o usuário alcança"; precisa dizer também que a quebra respeita o mesmo recorte.

## Bloco 3 — A tela

- Rótulo do item de menu: `textos.json` → `atendimentos.finalizar.todos` vira `"Finalizar Todos"`.
  O texto vem de `backend/crm-app/src/main/resources/textos.json` e é validado por
  `frontend/src/lib/config/schema.ts` — mexer só no teste não muda a tela.
- No diálogo, um grupo de rádio com `Todos` selecionado por padrão, seguido de cada entrada de
  `porAtendente` com a quantidade ao lado.
- `Atendimentos que serão finalizados: N` e o rótulo do botão acompanham a seleção — os textos
  `todosDescricao` e `todosConfirmar` já usam `{quantidade}`, reaproveite-os em vez de criar chave
  nova.
- Atendente com uma única entrada: mostre o rádio mesmo assim, sem caso especial. Um caminho só é
  menos código e menos teste que dois.
- Botão desabilitado quando a seleção resulta em zero.
- Use Base UI conforme o projeto — `data-active:`, **nunca** `data-[state=active]:`.

## Bloco 4 — Testes

Backend:

1. Gestor finaliza em lote com `atendenteId` de um atendente: só os daquele atendente ficam
   `FINALIZADO`; os do colega seguem `EM_ATENDIMENTO`.
2. Sem `atendenteId`, comportamento idêntico ao atual (teste de regressão sobre
   `finalizarEmLote_finalizaSomenteAtendimentosVisiveis`).
3. **Atendente enviando o id de um colega finaliza zero** e não recebe erro que revele a existência
   do colega.
4. `EM_IA` continua fora do lote com e sem filtro — `finalizarEmLote_potencialVisivel_permaneceEmIa`
   continua verde.
5. A soma de `porAtendente.quantidade` é igual a `quantidade`, para atendente e para gestor.
6. `porAtendente` de um atendente traz exatamente uma entrada, a dele.

Frontend:

7. Trocar o rádio muda o número na descrição e no botão.
8. Confirmar envia o `atendenteId` selecionado; com `Todos`, envia sem `atendenteId`.
9. Seleção com zero atendimentos desabilita o botão.
10. O item de menu passa a se chamar `Finalizar Todos`.

---

## Fora do escopo

- Seleção múltipla de atendentes.
- Finalizar Potenciais em massa — é outra regra de produto e outra etapa.
- Mudar quem pode finalizar o quê: o filtro é adicional, a autorização não muda.
- A visão `Finalizados` do menu — é a E136.
- RN-CRM-01/02/06, RLS, papéis, migration.

## Definição de pronto

- Gestor consegue esvaziar a fila de um vendedor específico sem tocar na dos outros.
- Atendente não consegue finalizar nada de colega, nem por id forjado, e há teste disso.
- Uma requisição alimenta seletor e contagens.
- `POST` sem corpo continua funcionando como hoje.
- `./mvnw -pl crm-app -am verify` verde; testes, typecheck, lint e build do frontend verdes.
- Relatório final com os sete itens do `AGENTS.md`.
