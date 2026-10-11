# 72. Disbrel — plano de mesclagem e deploy (PRs #294, #296–#300, mais a #295)

Documento operacional curto. **Nada aqui foi executado.** Quem mescla e quem faz deploy é o responsável; PR nenhuma foi marcada como
*ready for review*. Rótulos: **verificado** (arquivo:linha ou execução), **inferido**, **não verificado**.

## 1. Ordem de mesclagem

| # | PR | O que é | Migration | Muda a imagem? |
|---|---|---|---|---|
| 1 | #295 (`codex/auditoria-resultado-venda`) | Resultado comercial da automação e finalização ancorada | **V102** (`V102__resultado_comercial_da_automacao.sql`) | backend e frontend |
| 2 | #296 | Setores (desligado) | **V103** | backend (inclui `textos.json`, que é classpath) e frontend |
| 3 | #300 | Rodízio por setor, vinculado, chaves (retargetar a base de `claude/e229-a-setores` para `main` depois da #296) | **V104** | backend e frontend (a base #296 vem junto) |
| 4 | #299 | Janela de aviso ao cliente por dia | — | backend (`HorarioComercialConfiguravel`) |
| 5 | #294 | Plano, D1 (tema/marca) e documentação | — | frontend (tokens/CSS/sidebar; o tema padrão não muda — capturas comparadas pixel a pixel) |
| 6 | #297 | Scripts do funil da Disbrel | — | **não** (script, README e IT) |
| 7 | #298 | Caracterização da Agenda | — | **não** (só teste) |

- **A regra que manda é a numeração:** V102 → V103 → V104. A produção está em V101 (`origin/main`, última migration). Se uma migration de
  número maior for aplicada antes de uma menor ainda pendente, o Flyway a trata como **ignorada** e a validação do boot recusa o schema
  (**inferido** do comportamento do Flyway; `application.yml:41-43` só ignora `*:pending` e `*:future`) — o backend novo não sobe e o Swarm
  volta ao anterior. Não há perda de dados, mas é incidente evitável.
- Os demais itens (4–7) não têm migration e podem ser trocados de lugar. As PRs mexem em `docker/provisionamento/README.md` (cada uma
  acrescenta um bloco): a segunda a entrar provavelmente pede **resolução de conflito trivial** (manter os dois blocos).
- Migrations são aplicadas pelo próprio boot do backend (`FlywayValidacaoNoBootConfig.java:17-18`: depois da V73 o fluxo normal aplica as
  posteriores). O runner `--synapse.migrations.run-once` é só da V73 (`docs/18`, "Gate obrigatório").

## 2. Ação necessária no Dokploy

**Nenhuma.** Nenhuma PR toca `docker/dokploy-stack.yml`, `application.yml` ou `.env.example` (verificado por `git diff origin/main...<branch>`
em cada branch). Nenhuma variável nova. A #295 declara o mesmo no próprio corpo.

## 3. Ordem dos stacks

Um por vez, **fora do horário de atendimento** (a Atendimentos não pode cair entre 08:00 e 18:30 — `AGENTS.md`, regra de precedência),
na ordem sugerida pelo `docs/18` (janela 00h–05h no fuso do responsável): **`erp-matriz-hml` → `disbrel-dbl` → `fmnaprod-uzapi`**. Só
avance para o próximo stack quando os checks da §4 do anterior estiverem verdes. O `docs/18` ("Rollout seguro no Swarm") já define
`start-first`, readiness do Traefik e rollback automático do Swarm.

## 4. O que verificar depois de cada deploy

1. `docker service ps <stack>_backend` e `<stack>_frontend`: tarefa nova `Running`, sem `rollback` nos eventos.
2. Migration aplicada (consulta do `docs/18`):
   `select installed_rank, version, description, success from flyway_schema_history order by installed_rank desc limit 5` — esperar
   `102`, `103` e `104` com `success = true` (conforme as PRs já mescladas).
3. Saúde (o runbook usa `/health/*`, não `/actuator/*`): `GET /health/readiness` 200 por fora, `/health/liveness` 200 por dentro,
   `GET /health/critical` com tudo `UP`.
4. Tela de login abre (identidade Synapse — D-11) e o painel de Atendimentos carrega a lista.
5. Capacidades **desligadas** (estado esperado): `select chave, habilitado from feature_flag where chave = 'setores'` → `false`;
   `select chave, valor from configuracao_automacao where chave like 'ia.distribuicao.%'` → as três chaves novas em `false`;
   `GET /api/v1/setores` com token de gestor → 404; aba Equipe sem o painel "Setores".
6. Em `erp-matriz-hml`, a verificação de mensagem do `docs/18` (um texto e uma mídia pequena recebidos/enviados) e ausência de 503 no Traefik.
7. Uma `transferir-proximo-humano` real em hml (corpo vazio) devolve o mesmo que antes.

## 5. Como reverter cada coisa

| O quê | Como | Efeito |
|---|---|---|
| Aplicação (qualquer PR) | Restaurar o SHA anterior em `SYNAPSE_IMAGE_TAG` no Dokploy e reaplicar o stack | **Não** desfaz migration (`docs/18`, "Monitoramento e rollback"); as tabelas novas ficam inertes |
| `setores` | `UPDATE feature_flag SET habilitado = FALSE WHERE chave = 'setores'` | Imediato (a leitura é no banco a cada chamada); nada é apagado |
| Vinculado, por botões, perguntar preferência, sequencial | `PUT /api/v1/automacao/config/{chave}` com `false` (auditado) ou `distribuicao-disbrel.sql -v <chave>=false` | Imediato; linhas de `lead_atendente_vinculado` ficam inertes |
| Janela por dia | `DELETE FROM configuracao_automacao WHERE chave IN ('alerta.horario_cliente.sab','alerta.horario_cliente.dom')` | O dia volta à janela padrão no próximo ciclo do monitor |
| Funil | `funil-disbrel-reverter.sql -v execucao=<id> -v confirmar=REVERTER` | Restaura etapas/ordens/leads movidos (não o que um atendente moveu depois) |
| Tema/marca | SQL de rollback do `docker/provisionamento/instancias/disbrel/README.md` (a API não volta a `NULL`) | Volta ao tema do classpath |

## 6. Antes de ligar qualquer capacidade na Disbrel

Rodar na VPS `docker/operacoes/e229-explain-rodizio-antes.sql` e `...-depois.sql` (comando no cabeçalho de cada um) e anexar as saídas
à #300. **Sem número, nenhuma afirmação de desempenho.** Ligar `setores` e o vinculado são decisões separadas do responsável.
