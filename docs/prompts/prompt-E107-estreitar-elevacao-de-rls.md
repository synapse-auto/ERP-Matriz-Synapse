# Prompt E107 — Estreitar a elevação de RLS da transferência

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`fix/estreitar-elevacao-de-rls`) e PR.
> **Sem merge, sem deploy.** `./mvnw -pl crm-atendimento -am verify`.
> **Sem migration. Nenhuma política RLS pode ser alterada.**

---

## O que já está estabelecido — não reinvestigue

O PR #36 introduziu `elevarRlsParaEscritaDeNovoDono()` na transferência entre atendentes. A
justificativa foi contestada e depois **confirmada experimentalmente**. O diagnóstico é este, e é
para tratá-lo como fato:

Com `FORCE ROW LEVEL SECURITY` e uma política de leitura por dono, um `UPDATE` que **tira a linha da
própria visibilidade de quem executa** é recusado com
`new row violates row-level security policy` — mesmo com `WITH CHECK (TRUE)`. Quem recusa não é o
`WITH CHECK`: é a política de **leitura** aplicada à linha nova, porque o `UPDATE` tem `WHERE` e
portanto lê a relação.

Reproduzido em Postgres 16 com a mesma forma de política do projeto:

- `ALL … WITH CHECK (TRUE)` → falha.
- `SELECT` e `UPDATE` como políticas separadas, leitura restrita → falha igual.
- Leitura permissiva (`USING (true)`) + escrita restrita → passa.
- Leitura restrita, mas linha nova ainda visível (`status = 'EM_IA'`) → passa.

É por isso que devolver para a IA sempre funcionou e gestor sempre funcionou. **Transferir para um
colega é, por definição, tornar a linha invisível para quem transferiu.** A elevação é legítima.

**Não tente remover a elevação.** Não tente resolver "não relendo" — não é releitura. Não altere
política. Esta etapa é sobre **escopo**.

## Bloco 1 — O que apertar

Hoje o `SET LOCAL app.papel = SERVICO` vale até o commit da transação. O próprio relatório do PR #36
mapeou o que roda depois dele:

1. `UPDATE lead SET atendente_responsavel_id = …` — **precisa** da elevação, pelo mesmo motivo.
2. `SELECT nome FROM lead …` (`nomeParaTempoReal`) — **não precisa**, e hoje só funciona porque está
   dentro da janela elevada.
3. `publishEvent` — sem SQL; os listeners são `AFTER_COMMIT` e abrem transação própria.

Então:

- **Leia o nome antes** de trocar o dono, enquanto o contexto ainda é o do usuário. Uma leitura a
  menos dentro da janela elevada.
- **Eleve só em volta dos dois `UPDATE`** e **restaure o `app.papel` original imediatamente depois**,
  na mesma transação. Verificado: `set_config('app.papel', <original>, true)` restaura, e a
  visibilidade volta a valer na consulta seguinte.
- **Restaure também no caminho de erro.** Se o segundo `UPDATE` estourar, a transação vai reverter de
  qualquer forma, mas não deixe uma saída em que a janela elevada continue aberta enquanto ainda
  roda alguma coisa.
- O nome do método deve dizer o que ele é: uma janela curta, em volta de statements nomeados, com o
  motivo escrito. Nada de um utilitário genérico de "elevar RLS" — isso vira convite para o próximo
  uso, e o próximo uso vai ser em algum lugar que realmente vaza.

## Bloco 2 — O teste que importa mais que o conserto

Um teste que **falhe** se a janela elevada vazar para além dos dois `UPDATE`. Concretamente: depois
da transferência, dentro da mesma transação, uma leitura feita com o contexto do atendente de origem
**não** pode enxergar o atendimento que ele acabou de transferir.

Foi assim que confirmei o comportamento, e é assim que se prova que a janela fechou:

```
eleva → UPDATE → restaura → SELECT com o contexto do atendente de origem → 0 linhas
```

Sem esse teste, a etapa não está pronta. Ele é o que impede a janela de crescer de novo em algum
refactor futuro.

## Bloco 3 — Documentar, porque isto vai voltar

Este não é um caso isolado: **qualquer** operação que mova uma linha para fora da visibilidade de
quem a executa vai bater na mesma parede. Transferir lead, mudar dono, arquivar — tudo que a RLS
recorta por dono.

Registre isso onde a próxima pessoa vai ler antes de bater a cabeça: um parágrafo curto no
`AGENTS.md` ou no doc de RLS, dizendo o comportamento, por que `WITH CHECK (TRUE)` não resolve, e
qual é o padrão aceito (janela elevada mínima, restaurada, com teste). Sem isso, daqui a três meses
alguém escreve um segundo `elevarRls…` do zero.

## Bloco 4 — Decisão pendente do Marcondes (não implemente)

O PR #36 recusa com **403** transferir um lead **Potencial** (`EM_IA`) direto para um colega, com
base no `AGENTS.md`: atendente não distribui Potencial, só devolve para a IA ou assume para si. O
raciocínio está registrado e é coerente — inclusive a escolha de 403 em vez de 404, já que o recurso
é visível ao atendente.

**Não mexa nisso.** Fica como está até o Marcondes decidir se quer permitir a distribuição direta.

## Verificação

```
./mvnw -pl crm-atendimento -am verify      # na raiz de backend/
```

## Relatório

1. Onde a janela elevada começa e termina agora, com o trecho.
2. Confirmação de que `nomeParaTempoReal` saiu de dentro da janela.
3. O teste do Bloco 2, e a saída dele.
4. Onde você documentou o comportamento (Bloco 3).
5. Confirmação de que nenhuma política e nenhuma migration foram tocadas.
