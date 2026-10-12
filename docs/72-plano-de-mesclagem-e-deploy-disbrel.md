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
na ordem sugerida pelo `docs/18` (janela 00h–05h no fuso do responsável): **`erp-matriz-hml` → `disbrel-dbl` → `fmnaprod-uzapi`**.
**Não existe homologação separada:** o stack `erp-matriz-hml` é a **produção da Estrutural** (o nome é histórico). Portanto o primeiro stack da
sequência já é produção: o "teste" dele é a verificação da §4, feita fora do horário de atendimento e com o comando de reversão da §5
**já copiado** antes de começar. Só
avance para o próximo stack quando os checks da §4 do anterior estiverem verdes. O `docs/18` ("Rollout seguro no Swarm") já define
`start-first`, readiness do Traefik e rollback automático do Swarm.

## 3.1 Antes do deploy: a pré-checagem da V102 nos três bancos (somente leitura)

A V102 (#295) troca duas `CHECK` de `atendimento` com `ADD CONSTRAINT` **sem `NOT VALID`** (verificado em `V102__resultado_comercial_da_automacao.sql`):
o Postgres valida todas as linhas sob `ACCESS EXCLUSIVE`. Rode em **cada** banco, antes de mesclar a #295:

```
psql -X -d <banco> -v ON_ERROR_STOP=1 -f docker/operacoes/e231-v102-prechecagem.sql
```

Esperado: `LIBERAR`; tabela 3 vazia (nenhuma transação aberta há mais de 30 s). Detalhes e o que fazer se não for assim: §8.

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
6. No **primeiro stack da sequência** (fora do horário de atendimento), a verificação de mensagem do `docs/18` (um texto e uma mídia pequena
   recebidos/enviados) e ausência de 503 no Traefik.
7. Uma `transferir-proximo-humano` real nesse primeiro stack (corpo vazio) devolve o mesmo que antes — de preferência num lead de teste, não de cliente.

## 5. Como reverter cada coisa

| O quê | Como | Efeito |
|---|---|---|
| Aplicação (qualquer PR) | Restaurar o SHA anterior em `SYNAPSE_IMAGE_TAG` no Dokploy e reaplicar o stack | **Não** desfaz migration (`docs/18`, "Monitoramento e rollback"); as tabelas novas ficam inertes |
| `setores` | `UPDATE feature_flag SET habilitado = FALSE WHERE chave = 'setores'` | **Em até 2 s** (o rodízio lê a flag do cache de permissões, `synapse.permissoes.revalidacao`); as telas de gestão leem na hora; nada é apagado |
| Vinculado, por botões, perguntar preferência, sequencial | `PUT /api/v1/automacao/config/{chave}` com `false` (auditado) ou `distribuicao-disbrel.sql -v <chave>=false` | **Vinculado: em até 2 s** (valor guardado em memória por nó); `sequencial` é lido a cada chamada. Linhas de `lead_atendente_vinculado` ficam inertes |
| Janela por dia | `DELETE FROM configuracao_automacao WHERE chave IN ('alerta.horario_cliente.sab','alerta.horario_cliente.dom')` | O dia volta à janela padrão no próximo ciclo do monitor |
| Funil | `funil-disbrel-reverter.sql -v execucao=<id> -v confirmar=REVERTER` | Restaura etapas/ordens/leads movidos (não o que um atendente moveu depois) |
| Tema/marca | SQL de rollback do `docker/provisionamento/instancias/disbrel/README.md` (a API não volta a `NULL`) | Volta ao tema do classpath |

## 6. EXPLAIN do rodízio: quando é obrigatório

- **Antes de mesclar a #300: não é obrigatório**, porque (a) o SQL pesado do rodízio é byte-idêntico com tudo desligado e (b) depois do E231 **nenhuma
  consulta nova executa por chamada** com as chaves desligadas (§7). Só vira obrigatório antes de mesclar se alguma consulta nova voltar a rodar
  no caminho desligado e não puder ser guardada — não é o caso hoje.
- **Antes de ligar** `setores` ou o vínculo na Disbrel: **obrigatório.** Rodar na VPS `docker/operacoes/e229-explain-rodizio-antes.sql` e
  `...-depois.sql` (comando no cabeçalho de cada um) e anexar as saídas à #300. **Sem número, nenhuma afirmação de desempenho.** Ligar `setores` e o
  vínculo são decisões separadas do responsável.

## 7. O que a transferência da IA executa com tudo desligado (E231)

`POST /internal/v1/atendimentos/{id}/transferir-proximo-humano` ocorre uma vez por entrega de lead (não por mensagem recebida). Antes do E231 a #300 somava
**duas consultas por chamada** com as chaves desligadas:

| # | Consulta | Onde | O que fazia desligada |
|---|---|---|---|
| 1 | `SELECT chave FROM feature_flag WHERE habilitado = TRUE` | `FuncionalidadesHabilitadasJdbc.java:34`, chamada por `ListarAtendentesDisponiveisUseCase.escopo()` via `HabilitacaoDeSetoresPorFeatureFlag.habilitada()` | Ler **todas** as flags para saber que `setores` está desligada |
| 2 | `SELECT v.atendente_id FROM configuracao_automacao c JOIN atendimento a … JOIN lead_atendente_vinculado v …` | `VinculoDoLeadRepositorioJdbc` (SQL_VINCULADO_DO_ATENDIMENTO), chamada por `TransferirAtendimentoDaAutomacaoUseCase.vinculadoElegivel` | Ler a chave e, só se `true`, juntar o vínculo |

Correção (E231): **a guarda vem antes do banco.**
1. A flag `setores` passa a vir de `HabilitacaoDeSetores.habilitadaEmCache()` → `ResolvedorDePermissoesEfetivas.flagsHabilitadas()`, o instantâneo que o
   cache de permissões **já** mantém (atraso máximo `synapse.permissoes.revalidacao`, 2 s por padrão; ITs 50 ms). Zero consulta por chamada.
2. A chave `ia.distribuicao.atendente_vinculado` é lida **uma vez por intervalo por nó** (mesma propriedade, nenhuma variável nova) e guardada em memória
   (`VinculoDoLeadRepositorioJdbc.habilitado()`); desligada, nem a busca do vínculo nem a gravação executam SQL. **Ler a chave custa uma ida ao banco
   (por chave primária, tabela minúscula), no máximo uma a cada 2 s por nó**, e só quando alguém pergunta; não é uma por chamada. Falha de leitura fecha (vínculo ignorado) pelo mesmo intervalo.
3. O listener do vínculo recebia todo evento de atendimento com `@Transactional(REQUIRES_NEW)` no método (uma transação por evento, inclusive mensagem
   recebida); a transação agora existe só na gravação.

Provas: `VinculoDoLeadRepositorioJdbcTest` (100 chamadas com a chave desligada = 1 conexão no pool geral, 0 no do chat);
`AjustesDoVinculoE231IT` (evento irrelevante não abre transação; 5 transferências com a chave desligada não geram nenhum comando com
`lead_atendente_vinculado` no log de SQL do Postgres). **Não medi tempo**: o ganho é "deixou de executar", não um número de milissegundos.

**Efeito aceito:** ligar/desligar `setores` ou a chave do vínculo passa a valer em até 2 s (antes: na chamada seguinte).

## 8. V102 (#295): o que a migration faz e a compatibilidade entre a imagem anterior e o esquema V102–V104 (E231)

**O que a V102 faz** (verificado, `V102__resultado_comercial_da_automacao.sql`): em `atendimento`, `DROP CONSTRAINT` das duas `CHECK` da V97
(`ck_atendimento_origem_resultado_venda`, `ck_atendimento_estado_resultado_venda`) e `ADD CONSTRAINT` das duas novas, num único `ALTER TABLE`. As
novas aceitam `origem_resultado_venda = 'AUTOMACAO'` (com `venda_registrada_por_id` nulo); `MANUAL`/`FINALIZACAO` continuam exigindo quem registrou.
**Não usa `NOT VALID`/`VALIDATE`:** o `ADD CONSTRAINT` valida todas as linhas dentro do `ALTER`, sob `ACCESS EXCLUSIVE` em `atendimento`.

**Pode uma linha existente violar?** Não, por construção — e há prova. As `CHECK` novas são um **superconjunto** das da V97: enumerei as 96
combinações de (`resultado_venda`, `origem`, e a nulidade de `valor`, `por_id`, `registrada_em`) no Postgres 15 e **nenhuma** combinação válida na V97
é inválida na V102 (9 válidas na V97, 13 na V102; as 4 novas são as de `AUTOMACAO`). Isso vale **se** as `CHECK` da V97 estiverem validadas no banco; a
pré-checagem (§3.1) confere `convalidated`.

**Se, apesar disso, houvesse uma linha violadora** (ex.: `CHECK` removida ou criada `NOT VALID` à mão): o `ALTER` falha com `23514`, a migration falha
(**inferido**: o DDL do Postgres é transacional, então as duas `DROP` também voltam) e **o backend novo não conclui o boot**: com
`order: start-first` e `failure_action: rollback` (`docker/dokploy-stack.yml:308-312`) o Swarm volta à tarefa anterior, que seguia atendendo. Não trava o
serviço, mas queima uma janela de deploy.

**O risco real é a fila de locks, não a violação:** o `ALTER` pede `ACCESS EXCLUSIVE`; se houver uma transação aberta e longa em `atendimento`, ele espera, e
**tudo que chegar depois dele em `atendimento` espera atrás dele** (inclusive o tráfego da tarefa antiga). Medi 2 ms em 6 mil linhas sintéticas (E230); não
medi na VPS. Por isso a pré-checagem lista transações com mais de 30 s e o deploy é fora do horário de atendimento.

**Pré-checagem (somente leitura, nos três bancos, antes do deploy):** `docker/operacoes/e231-v102-prechecagem.sql` (comando na §3.1). Provada por
`PrechecagemV102ScriptIT` (executa no schema real e libera; numa cópia sem constraints distingue `BLOQUEAR` e `ATENCAO`; o script só tem `BEGIN READ ONLY`).

**A imagem anterior funciona contra o esquema V102–V104? (rollback por SHA)**

| Migration | Mudança | Imagem anterior |
|---|---|---|
| V102 | `CHECK` relaxadas (superconjunto) | Escritas da imagem anterior (`MANUAL`/`FINALIZACAO` com quem registrou, ou tudo nulo) passam nas `CHECK` novas (prova de enumeração acima). **Mas:** linhas com `origem_resultado_venda = 'AUTOMACAO'`, gravadas pela #295 em uso, **não são lidas** pela imagem anterior: `OrigemResultadoVenda.valueOf(...)` em `AtendimentoRepositorioJdbc.java:380-383` e `PainelDeAtendimentosRepositorioJdbc.java:577` (`main`, enum só com `MANUAL` e `FINALIZACAO`) lança `IllegalArgumentException` (**verificado** no código; o efeito na tela é **inferido**). Antes de reverter a imagem depois de a #295 ter rodado: `SELECT count(*) FROM atendimento WHERE origem_resultado_venda = 'AUTOMACAO'` (tabela 4 da pré-checagem); se for > 0, **não reverta a imagem às cegas** |
| V103 | 2 tabelas novas (`setor`, `usuario_setor`) + 1 linha em `feature_flag` (`setores`, `false`) | Aditiva. A imagem anterior não referencia as tabelas e lê `feature_flag` de forma genérica (`FuncionalidadesHabilitadasJdbc.java:34`); a chave extra é inerte (**inferido**) |
| V104 | 1 tabela nova (`lead_atendente_vinculado`) + 3 chaves `BOOLEAN` em `configuracao_automacao` | Aditiva. A imagem anterior lista as chaves de forma genérica (o tipo `BOOLEAN` já existe desde a V71, `ia.distribuicao.sequencial`); não lê a tabela nova (**inferido**) |

Teste "imagem anterior × esquema novo": §9.

## 9. Teste: o código atual de `main` contra o esquema V102–V104 (E231)

**Verificado (execução local, não a imagem Docker nem a VPS):** numa cópia de `origin/main` (`e6bc4808`, a "imagem anterior") acrescentei, sem alterar nenhum
arquivo versionado, as três migrations (`V102` da #295 em `ca7c6e28`; `V103` e `V104` da #300 em `95cf2938`). O Flyway aplicou `102`, `103` e `104` e o banco
ficou em `v104`. Com **o código de `main`** rodei 11 classes de IT e **162 testes passaram, 0 falhas**: `AtendimentoAcoesControllerIT` (37; resultado comercial e
finalização), `ComandosAutomacaoIT` (18), `CapacidadesDeAtendimentoIT` (15), `TransferenciaAutomacaoIT` (14), `CriterioDeRodizioAutomacaoIT` (10),
`DisponibilidadeParaIaIT` (10), `ContratosInternosAutomacaoIT` (7), `ContratoAutomacaoIT` (18 nas classes internas), `NovoContatoIT` (7),
`RetornoDoLeadFinalizadoIT` (6) e `AtendimentoIT` (20 nas classes internas).

**Não prova:** a leitura de linhas com `origem_resultado_venda = 'AUTOMACAO'` pelo código antigo (a falha é por leitura de código, §8); nada na VPS; a
imagem Docker construída; tráfego real.

