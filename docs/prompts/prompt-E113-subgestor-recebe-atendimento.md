# E113 — subgestor passa a receber atendimento

## A decisão

Subgestor não recebia atendimento. Por decisão da gestão da Estrutural, passa a receber. Michele é
o caso concreto: ela é subgestora e precisa entrar na fila da IA e na lista de transferência, como
qualquer atendente.

O que muda é **quem pode receber lead**. O que **não** muda é o que subgestor enxerga — ver o
Bloco 5.

---

## Bloco 1 — onde o papel trava hoje

Sete lugares. Levantados do repositório, não de memória; confira cada um antes de mexer, porque a
lista pode ter crescido desde que este prompt foi escrito.

**Backend**

1. `AtendenteDisponivelRepositorioJdbc` — `AND u.papel = 'ATENDENTE' AND u.status_presenca = 'ONLINE'`.
   É quem a automação pode receber como destino. Enquanto isto não abrir, a Michele nunca entra na
   fila da IA, mesmo com o toggle ligado.
2. `EquipeRepositorioJdbc.atualizarDisponibilidadeParaIa` —
   `WHERE id=? AND papel='ATENDENTE' AND ativo=TRUE`. Zero linhas afetadas devolve
   `Optional.empty()`, que a camada acima traduz em "não existe". **É por aqui que o toggle da
   Michele falharia hoje, e falharia calado.**
3. `EquipeRepositorioJdbc.criar` — só insere em `disponibilidade_atendente_ia` quando
   `p == PapelGerenciavel.ATENDENTE`. Subgestor nasce sem linha.
4. `EquipeRepositorioJdbc.atualizar` — mesma condição ao promover/rebaixar.
5. `AtendenteParaTransferenciaRepositorioJdbc` — `ELEGIVEL = "ativo = TRUE AND papel = 'ATENDENTE'"`.
   É a lista de destinos da transferência entre atendentes (E100) **e** a validação do destino.
6. `TransferenciaAutomacaoInternalController` — valida o destino e documenta no OpenAPI
   "papel diferente de ATENDENTE" no 422. É o endpoint que o n8n chama.
7. `AtendimentoAcoesController` — mesma descrição de 422 no OpenAPI.

**Frontend**

8. `frontend/src/components/equipe/pagina-equipe.tsx` — `usuario.papel === "ATENDENTE" ? (toggle)`.
   O toggle simplesmente não é renderizado para subgestor. A lista já inclui subgestor
   (`u.papel === "ATENDENTE" || u.papel === "SUBGESTOR"`), então só o controle falta.

**Dados**

9. `V34__backfill_disponibilidade_ia.sql` fez backfill só para atendentes. Os subgestores que já
   existem não têm linha em `disponibilidade_atendente_ia`.

---

## Bloco 2 — o que fazer

**Uma pergunta única, num lugar só.** Não espalhe `papel IN ('ATENDENTE','SUBGESTOR')` por sete
arquivos. `PapelUsuario` já é onde essa classe de pergunta mora — `enxergaTodosOsLeads()` está lá
pela mesma razão. Acrescente a irmã dela:

```java
/**
 * Quem pode receber lead: entrar na fila da IA e ser destino de transferencia.
 *
 * <p>Separada de enxergaTodosOsLeads() de proposito. Sao duas perguntas diferentes sobre o mesmo
 * papel, e desde que o subgestor passou a atender elas deixaram de ter a mesma resposta: ele
 * recebe lead E continua enxergando a base inteira.
 */
public boolean recebeAtendimento() {
    return this == ATENDENTE || this == SUBGESTOR;
}
```

Onde o predicado vive em SQL (itens 1, 2 e 5), a condição vira `papel IN ('ATENDENTE','SUBGESTOR')`
— com um comentário apontando para `PapelUsuario.recebeAtendimento()` como a definição, para a
próxima mudança de papel não achar só metade dos lugares.

**Migration** (a próxima livre; a V50 já está tomada pela E111/E112): backfill em
`disponibilidade_atendente_ia` para os subgestores ativos que não têm linha. Valor inicial: o mesmo
que a V34 usou para atendentes.

**Frontend**: renderize o toggle para `ATENDENTE` e `SUBGESTOR`. Nada mais muda na tela — a pill de
papel, a cor e o formulário ficam como estão.

**OpenAPI**: as descrições de 422 nos itens 6 e 7 dizem "papel diferente de ATENDENTE". Corrija.
Se a contagem de endpoints do teste de OpenAPI mudar, atualize-a.

---

## Bloco 3 — o contrato com o n8n

O item 6 é o endpoint que o Dylan chama para transferir por decisão da automação. A mudança é uma
**ampliação**: um UUID que antes dava 422 agora é aceito. Nenhuma chamada que funcionava para de
funcionar, então não há quebra de contrato e não é preciso avisá-lo antes de subir.

Mas o documento de contrato precisa acompanhar: procure em `docs/` onde o destino é descrito como
"usuário ativo com papel ATENDENTE" e ajuste para "papel ATENDENTE ou SUBGESTOR". Se o texto
estiver num documento que já foi enviado ao integrador, diga isso no relatório para o Marcondes
decidir se reenvia.

---

## Bloco 4 — o que verificar antes de dar por pronto

`status_presenca = 'ONLINE'` continua sendo requisito para a IA distribuir. Uma subgestora com o
toggle ligado e presença OFFLINE não recebe nada, e isso **não é bug** — é a mesma regra do
atendente. Diga isso no relatório, com essas palavras, porque é o primeiro "não funcionou" que vai
voltar da operação.

Confira também se `disponibilidade_atendente_ia` tem alguma constraint ou trigger que assuma papel
ATENDENTE. O nome da coluna é `atendente_id` e vai ficar impróprio — **não renomeie**: uma migration
de renomeação nesta semana, com a V50 já armada na main, não paga o risco. Registre como dívida.

---

## Bloco 5 — a decisão que NÃO é sua

`PapelUsuario.enxergaTodosOsLeads()` devolve `this != ATENDENTE`, então **subgestor enxerga a base
inteira**. E `ListarAtendimentosVisiveisUseCase` deriva
`restritoAoProprioAtendente = !atual.enxergaTodosOsLeads()`.

Consequência: mesmo recebendo atendimento, a Michele continua vendo **todos os atendimentos de
todos** na aba Todos e todos os leads na Agenda. É exatamente o recorte que a E106 acabou de
apertar para os atendentes.

**Não mexa nisso nesta etapa.** Deixar subgestor enxergando tudo é o comportamento atual e é
defensável — ela é gestão, não deixou de ser por passar a atender. Mas é decisão do Lucas, não do
código, e mexer em `enxergaTodosOsLeads()` alcança RLS, o recorte do painel e a Agenda de uma vez.

Registre no relatório, com o comportamento resultante descrito em uma frase que o Lucas consiga
ler: *"a Michele vai receber leads como atendente, mas vai continuar enxergando os atendimentos de
todo mundo — se ela deve passar a ver só a carteira dela, é outra etapa."*

---

## Bloco 6 — testes

1. `PapelUsuario`: `recebeAtendimento()` para os quatro papéis. GESTOR e ADMINISTRADOR continuam
   fora — quem gerencia não entra em fila de distribuição.
2. IT: subgestor ativo e ONLINE com o toggle ligado **entra** no resultado do repositório de
   disponíveis para a IA; com o toggle desligado, não entra.
3. IT: `PATCH` da disponibilidade num subgestor devolve sucesso e persiste (hoje devolveria vazio).
4. IT: subgestor ativo aparece na lista de destinos de transferência e é aceito como destino;
   gestor continua sendo recusado com 422.
5. IT da migration: subgestor pré-existente ganha linha em `disponibilidade_atendente_ia`; rodar de
   novo não duplica.
6. Front: o toggle aparece para SUBGESTOR e dispara a mesma mutação; continua ausente para papéis
   que não recebem atendimento.
7. Teste que trava o Bloco 5: subgestor continua com `restritoAoProprioAtendente = false`. Se
   alguém mudar isso sem decisão, o build reprova.

## Bloco 7 — entrega

PR próprio, CI verde. **Não** encoste em nada da E111/E112 — a V50 está na main e armada; qualquer
migration nova entra depois dela, sem tocá-la.
