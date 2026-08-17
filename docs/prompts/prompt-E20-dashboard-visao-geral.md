# Prompt E20 — Dashboard, aba Visão Geral

> Leia `AGENTS.md`. Referência visual: `design/componentes/Dashboard.html`.
> Maior etapa restante. Blocos em ordem; commite e faça push a cada um.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## Mudança de escopo, registrada

`docs/09-escopo-primeira-entrega.md` cortou a Dashboard inteira. **A aba "Visão Geral" voltou ao escopo** por decisão do cliente. As outras três abas do protótipo — Operacional, Comercial, IA & Automação — continuam fora.

Atualize o `docs/09` com essa reversão e o motivo, no mesmo padrão das outras notas. É a documentação acompanhando a realidade, não o contrário.

**Acesso: `GESTOR` e `SUBGESTOR` apenas.** Mesmo `hasAnyRole` de `ListarConfiguracoesAutomacaoAdminUseCase`. Atendente não vê o item no menu e recebe 403 na rota. O card "Top atendentes · vendas fechadas" expõe comissão de colega — é regra comercial, não preferência.

## Bloco 0 — Duas pendências da E19

**Catálogo de cidades.** Hoje o dropdown lista só as cidades presentes na página carregada — filtro que mente, porque uma cidade que só aparece na página 3 não é selecionável. Crie o endpoint de valores distintos de `localizacao`, **restrito pela mesma visibilidade da listagem** (`visivel(filtro)`). Vale o mesmo raciocínio para o catálogo de tags, se o mesmo problema existir lá.

**`datetime-local` entra na proibição.** Mesmo widget nativo, mesmo problema. Converta as ocorrências e estenda a regra de lint que você criou.

## Bloco 1 — Os números

Sete métricas, todas agregação sobre tabela existente. **Nenhuma tabela nova, nenhuma migration.**

| Métrica | Fonte |
|---|---|
| Atendimentos no período (+ acumulado) | `atendimento.iniciado_em` |
| Tempo médio de atendimento | `iniciado_em` → `finalizado_em` |
| Avaliação média (CSAT) | tabela de avaliação |
| Resolução por IA | `status_automacao_telemetria` — leitura já existe desde a E16 |
| Funil de conversão | `lead` agrupado por etapa, com quantidade e % de passagem |
| Horário de pico | `mensagem` agrupada por hora de `enviado_em` |
| **Vendas fechadas, taxa de conversão, top atendentes** | **timeline, não etapa atual — ver abaixo** |

### O ponto onde o número fica errado sem ninguém notar

"Vendas fechadas em agosto" **não é** "leads que estão na etapa Fechado hoje". Um lead fechado em julho continua nessa etapa e contaria em agosto, para sempre.

O correto é ler a **transição** de etapa registrada na timeline (`V20__ator_estruturado_timeline.sql`) e contar as que caíram dentro do período. Mesma coisa para taxa de conversão e para o ranking por atendente.

**Confirme primeiro que a timeline registra mudança de etapa com data e responsável.** Se não registrar, **pare e me avise** — aí é decisão de arquitetura, não de tela, e implementar por cima da etapa atual seria entregar número errado com cara de número certo. Esta é a única tela do sistema onde o erro vira decisão de gestão.

### Comparativos

O protótipo mostra variação contra o período anterior (`+8%`, `+1,4pp`, `-12%`). Calcule no backend, não no cliente — e note a diferença entre `%` (variação relativa) e `pp` (diferença em pontos percentuais). Trocar um pelo outro é erro que passa despercebido e muda o significado.

### Contrato

A tela carrega tudo de uma vez. **Não faça sete chamadas.** Um `GET` por período devolvendo o payload completo, ou no máximo dois se a separação for justificável — diga qual escolheu e por quê.

Tudo em `crm-relatorios`, que hoje só tem auditoria. Siga a estrutura que já está lá.

## Nota — decisões que vieram da E20a

**O bloqueio da timeline foi resolvido.** A E20a entregou `resultado` na etapa (`EM_ANDAMENTO`/`GANHO`/`PERDIDO`), o evento `ETAPA_ALTERADA` com responsável comercial, e o índice `(tipo, criado_em)`. Leia daí, nunca da etapa atual do lead, e nunca do `audit_log`.

**Vendas sem responsável:** entram no **total**, não no ranking. Se o card disser 49 e a soma do ranking der 44, alguém soma, acha a diferença e perde a confiança no painel inteiro — com razão. Mostre uma nota discreta no rodapé do card do ranking, apenas quando o número for maior que zero: *"3 vendas sem responsável atribuído"*. Explica a diferença sem inventar uma linha, e ainda é alerta operacional.

**Sincronize a documentação junto.** `docs/02`, `docs/03` e `docs/11` ainda descrevem o schema sem `resultado`. É a quarta vez que um documento deste projeto descreve uma realidade que não existe mais — atualize os três nesta etapa, não depois.

## Bloco 2 — A tela

`design/componentes/Dashboard.html`, aba Visão Geral apenas.

- Filtro de período: ano, meses, e o seletor "Período de originação"
- Seis cards de KPI com ícone, valor, variação e linha de apoio
- "Top atendentes · vendas fechadas" com posição, avatar e número
- "Funil de conversão" com barra por etapa, quantidade e % de passagem
- "Horário de pico · mensagens por hora"

**Gráfico em CSS puro, sem biblioteca nova.** As barras do funil e do horário de pico são `div` com largura ou altura proporcional. Não vale trazer recharts para duas visualizações.

As abas Operacional, Comercial e IA & Automação **aparecem desabilitadas**, como no protótipo, com indicação de que entram depois. Aqui a casca é honesta: elas existem no modelo aprovado e o cliente sabe que virão. Nenhum número falso dentro delas.

Nada de dado mockado. Se alguma métrica não tiver fonte, o card não existe — e vai para a lista do relatório.

## Definição de pronto

- [ ] Catálogo de cidades restrito por visibilidade; `datetime-local` convertido e no lint
- [ ] Sete métricas, todas sobre tabela existente, sem migration
- [ ] **Vendas fechadas, conversão e ranking calculados pela timeline**, com teste que prova que um lead fechado no período anterior não conta no atual
- [ ] Comparativos no backend, com `%` e `pp` distintos
- [ ] Uma chamada (ou duas justificadas), não sete
- [ ] Rota e item de menu restritos a `GESTOR`/`SUBGESTOR`, com **teste negativo de atendente**
- [ ] Tela conforme o protótipo, gráficos em CSS
- [ ] Três abas restantes desabilitadas, sem número dentro
- [ ] `docs/09` atualizado com a reversão de escopo
- [ ] CI verde com **número da run**

Commit: `feat: dashboard visão geral`.

No relatório: diga se a timeline sustentou as métricas de período ou se você teve que aproximar. Se aproximou, quero saber exatamente onde — número aproximado num painel de gestão é pior que número ausente.
