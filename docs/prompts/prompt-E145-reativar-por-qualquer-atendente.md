# Prompt E145 — Reativar atendimento: qualquer atendente pode assumir

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`feat/reativar-por-qualquer-atendente`) e PR. **Sem merge, sem deploy.**
> Backend. **Uma migration** que altera policy de RLS (use o próximo número livre na `main`).
> `cd backend && ./mvnw -pl crm-app -am verify`.
> **Empilha na E142** (`fix/retorno-do-lead-finalizado`), se ela ainda não estiver na `main`.

Pedido do cliente, **urgente**: **qualquer atendente pode reativar um atendimento finalizado e
assumi-lo** — não só quem finalizou, não só a gestão.

**Aconteceu em produção:** um cliente com atendimento finalizado por um atendente ficou parado
porque nenhum outro atendente conseguia assumi-lo. Ele não aparece para ninguém além do dono
anterior e da gestão, então a tela onde o botão viveria nunca é aberta.

---

## Bloco 0 — O botão não é o problema. Leia antes de planejar.

`IniciarNovoContatoUseCase.abrirParaLeadExistente` já é `@PreAuthorize("isAuthenticated()")` e
**não tem verificação de dono**. Qualquer usuário autenticado pode chamar. Não mexa nele.

O que bloqueia é a RLS, e em dois lugares. `rls_lead` e `rls_atendimento` (V36) dão ao atendente:

```
atendente_id = app_usuario_id()  OR  status = 'EM_IA'  OR  participante_ativo
```

Depois de finalizar, o atendimento fica `FINALIZADO` com o `atendente_id` anterior e o lead fica
`status_basico = 'FINALIZADO'` com o dono anterior. Para os outros atendentes o lead **não existe**:
não aparece na lista, na busca nem na agenda. O botão nunca chega a ser renderizado. E, se alguém
chamasse a API com o id na mão, `transferirPara` faz `SELECT ... FOR UPDATE` sob RLS, não acha
linha, e o caso de uso levanta `ContatoIndisponivelParaInicioException`.

Portanto: **mexer no frontend ou no caso de uso não resolve nada.** A correção é na policy.

## A decisão, já tomada — e a consequência, aceita

Atendimento encerrado não tem dono em curso: volta para o balcão, como já acontece com `EM_IA`.
Acrescente `FINALIZADO` ao mesmo escape que `'IA'`/`'EM_IA'` já usa, nas duas policies.

**Consequência aceita, e ela é grande — registre no comentário da migration:** todo atendente passa
a enxergar os leads finalizados de todos os colegas — na agenda, na busca, na visão Finalizados —
**incluindo o histórico completo das conversas anteriores**. Isso é intencional: é o que permite
assumir um cliente que voltou sabendo o que já foi tratado. Quem ler a policy depois precisa saber
que foi decisão de produto, não descuido.

O que **não** muda: atendimento `EM_ATENDIMENTO` de um colega continua invisível. Ninguém enxerga
conversa em andamento de outra pessoa. A RN-CRM-01 continua valendo para o que está aberto.

## Bloco 1 — A migration

Redefina `rls_lead` e `rls_atendimento` no padrão da V36: `DROP POLICY IF EXISTS` seguido de
`CREATE POLICY`, com o `WITH CHECK (TRUE)` preservado exatamente como está.

Acrescente ao ramo do atendente:

- `rls_atendimento`: `OR status = 'FINALIZADO'`
- `rls_lead`: `OR status_basico = 'FINALIZADO'`

**Estrutural apenas** — nenhum `UPDATE`, nenhum `DELETE`. Mantenha `app_enxerga_todos_os_leads()`,
o ramo de participante e tudo mais intocado; a mudança é uma alternativa a mais, não uma reescrita.

Copie os comentários existentes e acrescente o desta etapa. A policy é o documento mais lido do
projeto quando alguém pergunta "quem vê o quê".

## Bloco 2 — O que isso libera sozinho

Com a policy ajustada, três coisas passam a funcionar **sem mais nenhuma linha de código**:

1. O lead finalizado do colega aparece na busca, na agenda e em Finalizados.
2. O botão "Reativar atendimento" é renderizado e o `abrirParaLeadExistente` conclui — o
   `transferirPara` acha a linha e o atendente assume.
3. O histórico de atendimentos anteriores de outros atendentes passa a aparecer na conversa: o
   `JOIN atendimento a` de `HistoricoDeMensagensRepositorioJdbc` deixa de filtrar os finalizados.

O item 3 conserta, de quebra, uma incoerência que existe hoje: o painel do lead mostra
`num_atendimentos`/`num_mensagens` — contadores desnormalizados que **não** passam por RLS — e
podia anunciar "3 atendimentos, 47 mensagens" numa tela que exibia 9. Verifique que passou a bater.

**Não escreva código para nenhum dos três.** Se algum não funcionar só com a policy, **pare e
explique** antes de compensar no Java.

## O que você vai encontrar e não deve consertar aqui

Ao ler o caminho de transferência você vai notar que `AtendimentoParaAlteracao.carregar` **não
rejeita atendimento finalizado**. Quem barra é acidente: `AtendimentoRepositorioJdbc.salvar` faz
`UPDATE ... WHERE id = ? AND status <> 'FINALIZADO'`, altera zero linhas, cai no `INSERT` com o
mesmo id, toma `DuplicateKeyException` e converte para
`AtendimentoJaFinalizadoException(id, "atualizacao concorrente")` — mensagem que mente, porque não
houve concorrência nenhuma.

Isso é real, está anotado, e **não é desta etapa**. Não conserte, não refatore o `salvar`, não
acrescente checagem de status ao `carregar`. Etapa urgente não recebe escopo extra.

## Testes obrigatórios

Todos sob RLS, com usuários diferentes — asserção sobre coluna não prova visibilidade.

1. Atendente B enxerga o lead finalizado cujo último atendimento foi do atendente A.
2. Atendente B **reativa** esse atendimento e passa a ser o responsável; o atendimento anterior
   continua `FINALIZADO` e intocado.
3. Atendente B **não** enxerga atendimento `EM_ATENDIMENTO` do atendente A — a regra do que está
   aberto não afrouxou. **Este é o teste que impede a etapa de virar "todo mundo vê tudo".**
4. O histórico do lead reativado traz as mensagens do atendimento anterior do colega.
5. `RlsIsolamentoIT` continua verde; ajuste apenas o que a nova regra tornou incorreto, e diga no
   relatório exatamente o que mudou e por quê.
6. `RecorteDaAbaTodosIT` e a visão `TODOS` continuam restritas à gestão (E125) — sem regressão.
7. **Gestor também reativa**, sem regressão: ele já enxergava o lead finalizado antes desta etapa,
   e precisa continuar reativando. Este teste separa "a policy destravou o atendente" de "a policy
   quebrou quem já funcionava".

## Fora do escopo

- Mexer no botão, no `IniciarNovoContatoUseCase` ou em qualquer caso de uso.
- Mexer na visão `TODOS`, nas abas ou em papéis.
- Limpar `atendente_responsavel_id` de leads finalizados.
- Backfill ou correção retroativa de dados.

## Definição de pronto

- Qualquer atendente enxerga, reativa e assume um atendimento finalizado de colega.
- Atendimento em andamento de colega continua invisível, com teste provando.
- Migration estrutural, sem `UPDATE`, com o comentário explicando a decisão e sua consequência.
- Nenhuma linha de Java para compensar a policy.
- `./mvnw -pl crm-app -am verify` verde; `git diff --check` limpo.
- Relatório final com os sete itens do `AGENTS.md`.
