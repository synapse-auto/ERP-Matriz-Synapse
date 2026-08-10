# E17b — Blocos 3 a 6, um por sessão

> Continuação da E17. Blocos 1 (Sidebar) e 2 (Atendimentos) entregues em `76ccea5`; Bloco 0 (seed) em `a7f0d7f`.
>
> **Cada seção abaixo é um prompt independente.** Cole uma por sessão — não tente duas. O agente que fez só o Bloco 0 bateu 92% de contexto.
>
> **Mudança de método:** a tabela de correspondência elemento por elemento sai. Ela provou o método nos Blocos 1 e 2 e agora só dobra o custo. Implemente direto, comparando com o HTML, e **liste no fim apenas o que ficou de fora e por quê**.
>
> Regras em todos: leia `AGENTS.md`; cor sempre por token (crie no `TOKENS.md` se faltar); ícones em `lucide-react`; nenhum elemento sem fonte de dado real; Dashboard, Relatórios, Campanhas e Banco de Arquivos continuam fora, nem a casca. Ao encerrar: `cd backend && ./mvnw clean verify`, commite a reformatação do Spotless se houver, e informe o **número da run** do CI. Commite e faça push **a cada tela**, não só no fim.

---

## PROMPT A — Bloco 6: endpoints de agregação

Único bloco de backend. Barato em contexto, não lê HTML, e destrava os outros dois. **Rode este primeiro.**

Três endpoints:

| Endpoint | Alimenta |
|---|---|
| contagem por visão (Todos, Pendentes, demais abas) | badges das abas de Atendimentos |
| contagem de leads por tag, tag mais usada, % de leads tagueados | mini-dashboard de Tags |
| leitura de `status_automacao_telemetria` | quatro cards do topo da Automação |

**Toda contagem passa pela mesma visibilidade da listagem.** Um atendente não pode ver "47 leads com a tag Obra" quando enxerga 6 — contador é vazamento de informação sem devolver uma linha. Reaproveite `visivel(filtro)` de `LeadRepositorioJpa`; não escreva query nova por fora.

Teste negativo **antes** do endpoint: contagem pedida por atendente devolve o número restrito, gestor devolve o total. Se já vier certo de graça, diga isso — não force um achado.

Ao terminar, ligue os badges nas abas de Atendimentos, que é uma linha de consumo e fecha o Bloco 2 de verdade.

---

## PROMPT B — Bloco 4: as quatro tabelas

`design/componentes/Equipe.html`, `Lembretes.html`, `MensagensRapidas.html`, `MensagensProgramadas.html`.

Maior volume visível que resta. As quatro foram construídas como tabela HTML crua + Dialog; o protótipo usa cards com avatar, tinta por pessoa, pills de status coloridas e agrupamento visual.

**Trabalhe uma tela por vez, commitando cada uma.** Se o contexto acabar na terceira, as duas primeiras já estão entregues — não deixe nenhuma pela metade.

Ordem: Equipe, Lembretes, Mensagens Rápidas, Mensagens Programadas.

Extraia para `components/ui/` o que se repetir — card de pessoa, pill de status — em vez de escrever quatro vezes. Mas só extraia na **segunda** ocorrência, não antecipe.

**Bug pendente:** o rótulo "Mensagens Programadas" continua truncando com reticências na Sidebar, mesmo depois da correção da fonte. Resolva junto.

---

## PROMPT C — Bloco 3: Agenda

`design/componentes/Agenda.html`.

A estrutura existe desde a E16 — tabela, filtros vindos do backend, chips removíveis, contador via `/contagem`. Falta igualar a aparência: ordem das colunas, desenho da barra de filtros, formato dos chips, densidade e espaçamento.

Não reescreva a lógica de filtro. É trabalho de apresentação.

Continuam fora, e vão para a lista do cliente: toggle Lista↔Kanban e import/export CSV — sem endpoint dos dois lados.

---

## PROMPT D — Bloco 5: Tags e Automação

`design/componentes/Tags.html`, `Automacao.html`. Depende do Prompt A.

- **Tags:** grid de cards no lugar da tabela atual, mais o mini-dashboard do topo (tag mais usada, % de leads tagueados, contagem por tag).
- **Automação:** desenho do protótipo para a seção Geral, com os quatro cards de telemetria. As seções atrás da flag `automacao_regras` continuam ausentes, sem casca.

---

## Ao fim dos quatro

Uma lista consolidada dos elementos do protótipo que ficaram de fora, com o motivo de cada um. Ela vai para o cliente por escrito — é o que transforma "está diferente" em "está combinado".
