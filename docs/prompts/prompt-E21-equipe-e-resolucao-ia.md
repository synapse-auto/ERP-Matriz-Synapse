# Prompt E21 — Completar Equipe e o card de Resolução por IA

> Leia `AGENTS.md`. Contexto: `docs/14-pendencias-de-funcionalidade.md`, prioridade 1.
> Etapa curta. Tudo aqui virou viável depois da E20a; nenhum item exige migration ou módulo novo.
> Commite e faça push por bloco. Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## Bloco 1 — Desempenho por atendente na tela de Equipe

O protótipo (`design/componentes/Equipe.html`) tem, e o construído não:

- colunas **ATEND.** e **VENDAS** na tabela de usuários
- card **"Ranking · avaliação (0-10)"** com as cinco primeiras posições
- card **"Ranking · vendas fechadas"** com as cinco primeiras posições

Ficaram de fora na E17b por não existir fonte de "venda". A E20a criou `ETAPA_ALTERADA` com `responsavel_id`, e a E20 já agrega exatamente isso para o Dashboard.

**Reaproveite a agregação da E20, não escreva outra.** Duas consultas com a mesma pergunta divergem na primeira mudança de regra, e aqui a pergunta é comissão — divergir significa dois números diferentes para a mesma venda, em duas telas do mesmo sistema.

Se a agregação do Dashboard estiver acoplada ao recorte de período dele, extraia o caso de uso comum em vez de duplicar.

**Visibilidade:** a tela de Equipe já é restrita a gestão. Confirme que o número de vendas por atendente **não** aparece para um atendente comum, nem via API. Teste negativo obrigatório.

**Vendas sem responsável** seguem a mesma regra do Dashboard: entram no total, não viram linha no ranking.

## Bloco 2 — Card "Resolução por IA"

A E20 omitiu, corretamente: `status_automacao_telemetria` é snapshot cumulativo, sem quantidade nem histórico, e não sustenta um número por período.

**A fonte certa é outra, e já existe.** O card do protótipo diz, embaixo do número, "Sem transferência humana". Ou seja:

> resolução por IA no período = atendimentos **finalizados** no período que **nunca tiveram evento de transferência** ÷ atendimentos finalizados no período

`atendimento` tem `finalizado_em`, e `TimelineDeAtendimentoListener` registra transferência. Não é schema novo, é uma consulta.

**Antes de implementar, verifique se o evento de transferência é gravado em todos os caminhos** — transferência manual pelo atendente, transferência pela Automação, reatribuição por gestor. Se algum caminho não registrar, o número fica otimista em silêncio: um atendimento transferido por fora contaria como resolvido pela IA.

Se algum caminho estiver descoberto, **pare e me avise** — corrigir o registro é a etapa, não o card.

## Bloco 3 — Duas correções que a E20 deixou anotadas

- **`feature_flag.dashboard`** está `false` nas instâncias já provisionadas. Acrescente ao script de provisionamento e documente no `docker/provisionamento/README.md` que instâncias existentes precisam do `UPDATE` manual.
- **Sincronize `docs/14-pendencias-de-funcionalidade.md`**: remova o que esta etapa entregou.

## Definição de pronto

- [ ] Colunas ATEND. e VENDAS na tabela de Equipe
- [ ] Dois rankings, cinco posições cada
- [ ] **Uma única agregação de vendas** servindo Dashboard e Equipe — não duas
- [ ] Teste negativo: atendente não obtém venda de colega, nem pela API
- [ ] Card de Resolução por IA calculado por ausência de transferência
- [ ] Verificação escrita de que todos os caminhos de transferência registram evento
- [ ] Flag da Dashboard no provisionamento e documentada
- [ ] `docs/14` atualizado
- [ ] CI verde com **número da run**

Commit por bloco: `feat: desempenho por atendente na equipe`, `feat: card de resolução por IA`.

No relatório: diga se conseguiu reaproveitar a agregação da E20 ou se precisou extrair caso de uso comum — e, se extraiu, confirme que o Dashboard continua dando o mesmo número de antes.
