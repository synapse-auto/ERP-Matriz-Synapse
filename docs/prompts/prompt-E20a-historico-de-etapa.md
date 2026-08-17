# Prompt E20a — Histórico de etapa e o conceito de "venda ganha"

> Leia `AGENTS.md`. Pré-requisito da E20 (Dashboard).
> Etapa curta de backend: sem tela. Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## Por que esta etapa existe

A E20 foi interrompida corretamente: nada registra a mudança de etapa de um lead. Calcular "vendas fechadas no período" pelo estado atual contaria para sempre um lead fechado em julho.

A investigação encontrou dois buracos, não um:

1. **Nenhum evento de transição de etapa.** `TimelineDeAtendimentoListener` registra mensagem, transferência e finalização — não `etapa_atendimento_id`.
2. **`etapa_atendimento` não sabe o que significa "ganho".** A tabela tem `nome`, `ordem` e `cor_visual`. Descobrir a venda pelo nome da etapa é hardcode disfarçado: o próximo filho chama de "Concluído" e toda métrica quebra em silêncio. Isso fere o modelo Base PAI.

## Bloco 1 — `etapa_atendimento` ganha resultado

Migration nova acrescentando à tabela um campo de **resultado**, com três valores: `EM_ANDAMENTO`, `GANHO`, `PERDIDO`. Default `EM_ANDAMENTO`.

É o que permite o sistema saber que "Fechado", "Concluído" ou "Vendido" significam a mesma coisa em filhos diferentes, sem uma linha de código conhecer nome de etapa.

- Enum no banco, no padrão dos outros tipos da `V1__extensoes_e_tipos.sql`
- Expor no CRUD de etapas para o gestor configurar
- **Regra:** no máximo uma etapa `GANHO`. Mais de uma torna "vendas fechadas" ambíguo. Garanta por constraint, não por validação de aplicação
- O script de provisionamento passa a marcar qual etapa é `GANHO` e qual é `PERDIDO`

## Bloco 2 — O evento de transição

Registre a mudança de etapa em `evento_timeline`, com um tipo novo `ETAPA_ALTERADA`, seguindo o padrão de domain event + `@TransactionalEventListener(AFTER_COMMIT)` que já existe.

Reaproveitar `evento_timeline` em vez de criar tabela dedicada: a `V20` já deu a ela `ator_id` e `dados` JSONB, e o atendente ver "Ana moveu para Negociação" na ficha do lead é valor imediato, independente da Dashboard.

No `dados`, no mínimo:

- etapa anterior e etapa nova
- **`responsavel_id`: o atendente responsável pelo lead no momento da transição**

Este último é o ponto que o relatório anterior levantou e o meu prompt não tinha: `ator_id` é **quem executou**, que pode ser um gestor arrastando o card. Quem leva a venda é o responsável comercial. Confundir os dois faz o ranking creditar comissão à pessoa errada — incidente comercial, não bug técnico.

Índice em `(tipo, criado_em)`, senão a Dashboard varre a tabela inteira a cada carga.

**Teste obrigatório:** gestor move o lead de outro atendente para a etapa `GANHO`; a venda tem que ser creditada ao **atendente**, não ao gestor.

## Bloco 3 — A regra de reabertura

Um lead pode voltar de `GANHO` para uma etapa anterior e fechar de novo. A regra, decidida:

> **"Vendas fechadas no período" = leads distintos que tiveram ao menos uma transição para a etapa `GANHO` dentro do período.**

Reabrir e fechar no mesmo período conta **uma** vez — é correção de operação, não venda nova. Fechar de novo num período seguinte conta **outra** vez, e isso é correto: cliente recorrente é venda nova para uma distribuidora de vidro, e "Cliente Recorrente" é literalmente uma das tags do negócio.

Registre essa definição no `docs/04-adrs-e-api.md` como ADR. É regra de negócio que alguém vai questionar em seis meses, e a resposta precisa estar escrita.

## Retroatividade

O histórico começa vazio. Nenhuma transição anterior a esta migration existe, e **não tente reconstruir a partir do `audit_log`** — ele é transversal, com política de retenção própria, e acoplar métrica de negócio a log de manutenção quebra quando alguém expurgar auditoria antiga.

Consequência aceita: a Dashboard mostra zero em períodos anteriores ao deploy. Em homologação não há dado real, e em produção o sistema começa junto com a operação. Deixe isso claro num comentário na migration.

## Definição de pronto

- [ ] Resultado da etapa no banco, com constraint de no máximo uma `GANHO`
- [ ] Configurável no CRUD de etapas; provisionamento marca `GANHO` e `PERDIDO`
- [ ] `ETAPA_ALTERADA` gravado em `evento_timeline` com etapa anterior, nova e **responsável comercial**
- [ ] Índice em `(tipo, criado_em)`
- [ ] **Teste: gestor move lead alheio para `GANHO` e a venda é creditada ao atendente**
- [ ] Teste: reabrir e fechar no mesmo período conta uma vez
- [ ] ADR da regra de reabertura em `docs/04`
- [ ] Comentário na migration sobre o histórico começar vazio
- [ ] CI verde com **número da run**

Commit: `feat: histórico de transição de etapa e resultado da etapa`.

Depois desta, a E20 roda sem o bloqueio. No relatório, diga se encontrou algum outro lugar do sistema que hoje deduz "venda" pelo nome da etapa — se existir, é o mesmo defeito em outro canto.
