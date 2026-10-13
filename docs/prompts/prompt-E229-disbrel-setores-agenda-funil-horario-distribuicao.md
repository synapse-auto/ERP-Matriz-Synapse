Prompt E229 — Disbrel: setores, Agenda, funil, horário por dia e distribuição (somente CRM)
Número E229 provisório: confirmar o próximo livre em `docs/prompts/`. Executor: Claude Code (Sonnet 5.5, esforço alto). Produção. A Disbrel (`disbrel-dbl`) já está no ar, junto de Estrutural (`erp-matriz-hml`) e Fêmina (`fmnaprod-uzapi`), com a mesma imagem. Não faça deploy, não rode nada contra produção, não crie secret. Este prompt assume que o E228 (D1, tema/marca) está entregue ou em andamento: não o repita. Fora do escopo, sem exceção: n8n, IA, prompts, base de conhecimento, classificação por IA, modo "botões", a seção 2 (Canal) do PDF da Disbrel, e a D3d (atribuir a atendente offline fora do expediente), que depende de decisão comercial do responsável.
0. Protocolo anti-alucinação (vale para tudo abaixo)

1. O `docs/71` é uma lista de hipóteses, não de fatos. Ele já errou uma vez: afirmava que faltava override de marca por instância, e esse override existia (`PUT /api/v1/config/marca/*`). Antes de implementar cada item, reverifique no código a premissa dele que você vai usar, citando `arquivo:linha`.
2. Todo nome que você usar (classe, tabela, coluna, chave de configuração, rota, capability, migration) tem que ter sido encontrado por `git grep`/leitura nesta sessão. Nunca invente nome por analogia. Se não achar, escreva "não encontrado" e pare naquele ponto, não preencha.
3. Rotule cada afirmação do relatório como `verificado (arquivo:linha)`, `inferido` ou `não verificado`. Teste que você não rodou não é "passou".
4. Não escreva comando de build/teste de memória. Descubra no `AGENTS.md`/README/CI como rodar backend e frontend. Se o ambiente não permitir rodar (sem Docker/Postgres), diga qual teste ficou sem rodar e por quê, e não declare verde. CI verde só com o número da run.
5. Migrations: `git fetch` e use o próximo número livre da `origin/main` no momento de abrir cada PR (outro PR pode ter tomado o número); nunca edite migration aplicada. Pode haver buraco de numeração (ex.: V100): não "preencha".
6. Se um gate abaixo falhar, pare esse PR, relate, e siga só para PRs que não dependam dele. Não contorne.

1. Invariantes (valem para todos os PRs)

* Um repositório, uma imagem. Nada de `if (cliente == ...)` e nada de fork. Diferença entre clientes = configuração, dado ou capacidade ligável (confirme como as capabilities `synapse.extensoes.X.habilitado` são declaradas e lidas antes de criar uma). Tudo novo nasce desligado, e com a chave desligada o comportamento e os testes de hoje ficam idênticos para Estrutural e Fêmina.
* Variável nova em `dokploy-stack.yml` só com `${VAR:-}` (default vazio); `:?` é proibido (derruba os outros stacks). Variável nova entra em "ação necessária no Dokploy", `.env.example` e README. Preferir chave em `configuracao_automacao`/capability a variável de ambiente.
* Lead é dado comercial sensível. Qualquer mudança perto de visibilidade, vínculo, distribuição ou Agenda passa pela `VisibilidadeLeadSpecification` e pelas políticas RLS, e nasce com teste negativo (o teste tenta violar a regra de propósito). Não altere política RLS sem teste de equivalência por papel (ATENDENTE, OPERADOR, SUBGESTOR, GESTOR, ADMINISTRADOR).
* Desempenho (a VPS está saturada: CPU compartilhada com steal ~30%, painel limitado por CPU). Nenhuma consulta nova no caminho quente do painel/chat sem justificativa. Qualquer SQL novo ou alterado que rode por requisição ou por mensagem recebida precisa de EXPLAIN real com RLS, no padrão dos scripts `docker/operacoes/e224-*`/`e225-*` (como `synapse_app`, `BEGIN/ROLLBACK`, 3 repetições, menor tempo), entregue como arquivo com o comando exato para eu rodar na VPS. Sem número de EXPLAIN real, não declare ganho nem "custo desprezível". Proibido `count(*)` em tabela grande em caminho periódico, novo `@Scheduled` pesado e mudança de pool/`statement_timeout`/heartbeat/STOMP/backoff.
* Nada síncrono e bloqueante entra no caminho de mensagem. Distribuição é consulta rápida ao banco, nunca chamada externa.
* Contratos `/internal/v1` ficam retrocompatíveis (campo novo opcional; ausente = comportamento de hoje) e o OpenAPI/inventário do contrato é atualizado.
* Frontend: textos em `textos.json`, cores em tokens de `tema.json`; nada de hex ou nome de marca solto. Tipos, lint e testes do frontend rodam e são relatados.
* Segredos: nenhum token, DSN, senha ou URL real em repositório, log ou relatório.

2. Leitura obrigatória (antes de qualquer código)
`AGENTS.md`, `docs/13`, `docs/05`, `docs/18` (runbook), `docs/47` (papéis, §15 OPERADOR), `docs/62` (409 sem destino), `docs/71` (do PR #294, como hipóteses), `docker/provisionamento/README.md`, `PapelUsuario`, `VisibilidadeLeadSpecification`, `AtendenteDisponivelRepositorioJdbc` e seus ITs (`TransferenciaAutomacaoIT`, `CriterioDeRodizioAutomacaoIT`, `DisponibilidadeParaIaIT`, `RetornoDoLeadFinalizadoIT`, `IsolamentoDeAgendaIT`).
3. PRs, em ordem (um branch por PR)
PRs independentes partem da `origin/main` atualizada. PRs dependentes (D3 depende de D2) empilham sobre o branch do anterior e dizem isso na descrição. Abra cada PR como rascunho e não declare nenhum como pronto sem CI.
PR A — D2: setores e rótulos de papel (capacidade desligada por padrão)
Premissa a reverificar: não existe entidade de setor/departamento (já conferi que não há `CREATE TABLE` com esses nomes; o módulo `crm-equipe` é autenticação e chat interno; confirme e cite).

* Tabela própria `setor` (dado cadastrável, não enum; Vendas, Suporte e Diretoria são linhas) com o atributo booleano "recebe da triagem". Vínculo usuário × setor em tabela de vínculo separada, com `UNIQUE(usuario_id)` por ora (um usuário, um setor) de forma que a restrição possa ser relaxada depois sem reescrever o modelo. Índices para as consultas que você criar. Se o projeto exigir RLS em tabela nova, siga o padrão existente.
* Capability desligada por padrão. CRUD de setor e atribuição de usuário na gestão de equipe (backend + frontend), com os mesmos papéis que já gerenciam usuários (`docs/47`).
* Rótulos de papel por instância (Proprietário = GESTOR, Diretoria = SUBGESTOR, Interno = OPERADOR) via `textos.json`/identidade. O enum `PapelUsuario` não muda.
* Gate: setor não altera visibilidade de lead (teste negativo: atendente de um setor continua sem ver lead de colega por causa do setor); com a capability desligada, tela e comportamento são idênticos aos de hoje.

PR B — D5a: Agenda compartilhada incluindo OPERADOR
Premissa a reverificar: `app_e_agenda()` (V60) autoriza ATENDENTE, SUBGESTOR, GESTOR e ADMINISTRADOR e não OPERADOR (a V93 não a alterou). Confirme lendo as migrations e cite.

* Antes de mudar qualquer coisa, relate exatamente o que a ficha da Agenda (`GET /api/v1/leads/{id}/agenda`) devolve e quais campos pertencem a atendimento alheio (`resumo_ia`, notas, campos customizados, responsável).
* Mudança de RLS/função só se o relatório mostrar que a ficha não expõe `resumo_ia`/notas de lead alheio. Se expõe, pare esta parte e relate; não corrija escondendo campos sem me consultar.
* Pelo item `MATRIZ`, a mudança vale para todos os filhos. Então: ela é atrás de chave (desligada = função como hoje) ou você prova com teste de equivalência por papel que Estrutural e Fêmina não mudam de comportamento. Escolha uma e justifique.
* Testes: OPERADOR acha e abre a conversa pela Agenda; OPERADOR não vê a aba Atendimentos além do que já via; `IsolamentoDeAgendaIT` continua passando sem alteração.

PR C — D5b: funil de 5 etapas da Disbrel (script seguro, sem migration)
Premissas a reverificar: existem `docker/provisionamento/funil-padrao.sql` e `provisionar-instancia.sql` (que lê `SYNAPSE_ETAPAS_JSON`); etapa é dado do cliente, não enum.

* Cuidado crítico: o `funil-padrao.sql` usa `ON CONFLICT (ordem) DO UPDATE`, que renomeia a etapa que já ocupa a ordem. A Disbrel está no ar e pode ter leads em etapas. Não reutilize esse padrão cegamente.
* Entregar `docker/provisionamento/instancias/disbrel/funil-disbrel.sql` com: (1) bloco de diagnóstico somente leitura que lista etapas atuais com a contagem de leads por etapa; (2) aplicação só se for seguro (nenhuma renomeação/remoção de etapa com lead, ou com remapeamento explícito e atômico), transacional, idempotente, com `ON_ERROR_STOP`; (3) rollback documentado. Etapas: Novo Contato, Aguardando Medidas, Negociação, Fechado (`resultado = GANHO`), Pós-venda. Sem etapa "Orçamento". Respeite o índice de GANHO único.
* "Sem Retorno" (D-7): padrão = sem estado novo. Use o filtro `semRetornoDias` que já existe (confirme no código); documente como a Disbrel o usa. Não crie migration nem regra nova aqui.
* Não rode contra produção; entregue o comando exato para eu rodar primeiro o diagnóstico.

PR D — D4: janela crítica por dia da semana (padrão = comportamento atual)
Premissas a reverificar: `alerta.horario_cliente.inicio/fim` é uma janela única para todos os dias; `horario_trabalho` e `rotina_disponibilidade*` existem só como migration, sem código; a regra de precedência/alerta assume 08:00–18:30 todos os dias.

* Etapa 1 (entregue primeiro, sem alterar comportamento): auditoria. Liste todo `@Scheduled`/cron, finalização em massa, envio de campanhas e jobs de manutenção, com o horário em que rodam, e diga quais cairiam em sábado 08:00–12:00 (janela crítica da Disbrel). Cite `arquivo:linha`. Sem essa tabela, a Etapa 2 não é mesclada.
* Etapa 2: janela por dia da semana, com `alerta.horario_cliente.*` como padrão para quem não configurar (instância sem horário por dia se comporta exatamente como hoje). Estrutura de configuração por dia seguindo o padrão de chaves existente. Escopo mínimo: a janela do alerta ao cliente e a precedência. RF-CRM-75 (rotina de plantão de sábado) só entra se a leitura do código/`docs/05` mostrar escopo claro e pequeno; senão, relate a lacuna e deixe de fora. Não implemente `horario_trabalho` inteiro "porque existe a tabela".
* Testes: sábado 10h dentro da janela, sábado 13h e domingo fora, instância sem configuração igual a hoje; e o horário no fuso correto (confirme qual fuso o sistema usa e cite).

PR E — D3a/D3b/D3c: distribuição (depende do PR A; maior risco; tudo atrás de chave, tudo desligado)
Premissas a reverificar (já vi, confirme e cite): o rodízio é global e inclui `SUBGESTOR` (`u.papel IN ('ATENDENTE','SUBGESTOR')` em `AtendenteDisponivelRepositorioJdbc`, espelhando `PapelUsuario.recebeAtendimento()`); o contrato `transferir-proximo-humano` recebe só o `atendimentoId` e o servidor escolhe o destino; ao finalizar, o responsável do lead é limpo (`RetornoDoLeadFinalizadoIT`, `liberar-responsavel-de-leads-finalizados.sql`).

* D3a — rodízio por setor. O contrato aceita setor opcional (ausente = comportamento de hoje). O servidor valida que o setor existe e "recebe da triagem"; setor inexistente ou que não recebe da triagem → erro tratado explícito, nunca queda silenciosa para outro destino. Quem recebe continua sendo decisão do servidor (teste de que o chamador não consegue escolher usuário). Setor com "recebe da triagem = não" (Diretoria) nunca é destino da triagem. O filtro de setor é adicionado às duas consultas (menor carga e sequencial) e à consulta de contagem que as espelha, mantendo os filtros e a ordem atuais. EXPLAIN real antes/depois dessas consultas.
* D3b — atendente vinculado (toggle). Decisão provisória (confirmar com o responsável antes de ligar): vínculo explícito persistido (coluna nullable com FK para usuário, ou tabela própria; proponha e justifique após ler o modelo do lead), gravado quando um humano assume ou recebe o atendimento (ache os casos de uso reais por busca, não por suposição), nunca por si só dando acesso a outro atendente. Regra: toggle ligado + vinculado ativo, disponível e em setor que recebe da triagem → direto a ele; senão → rodízio do setor, e o novo atendente passa a ser o vinculado. Toggle desligado = nada muda e `RetornoDoLeadFinalizadoIT` passa sem alteração. Testes negativos de RN-CRM-01/02/06.
* D3c — chaves de configuração. Modo de distribuição e toggle da pergunta de preferência de atendente: o CRM só guarda, valida e expõe (pela API existente de configuração/`automation-config`). Compatível com o booleano `ia.distribuicao.sequencial` atual. Não implemente comportamento de IA/botões. Siga o padrão de nomes de chave existente.
* Gate geral do PR E: com tudo desligado, `TransferenciaAutomacaoIT`, `CriterioDeRodizioAutomacaoIT`, `DisponibilidadeParaIaIT` e `RetornoDoLeadFinalizadoIT` passam sem nenhuma alteração; com tudo ligado, os cenários novos e os negativos passam. Se algum IT existente precisar ser editado para passar, pare e relate.

4. Não-objetivos

* Nada de n8n/IA. Nada da seção 2 (Canal). Nada da D3d (atribuir offline fora do expediente) nem da pergunta "setor sem atendente de folga": são decisões comerciais do responsável.
* Não mudar tema/marca (E228), RLS além do PR B, `statement_timeout`, heartbeat, backoff, STOMP, pool, intervalos de agendadores, nem criar variável obrigatória.
* Não implementar `horario_trabalho` completo nem estado novo "Sem Retorno".
* Não fazer deploy, não aplicar script em produção, não apagar dado.

5. Relatório final exigido (por PR, e um consolidado)

* Diff real, lista de migrations (número usado e confirmação de que era o próximo livre), testes locais e CI separados, e se o job `imagens` passou.
* Tabela de afirmações rotuladas (`verificado`/`inferido`/`não verificado`) com `arquivo:linha`, incluindo as premissas do `docs/71` que você encontrou erradas ou incompletas.
* Para cada SQL novo/alterado em caminho quente: antes/depois, EXPLAIN real ou a declaração de que ficou sem prova, e o comando exato para eu rodar na VPS.
* Para cada capability/chave: nome, padrão (desligado), como ligar, como reverter.
* "Ação necessária no Dokploy" (se houver) e o que o responsável precisa decidir antes de ligar cada chave.
* Atualizar `docs/71` (marcar o que foi feito e corrigir as premissas), `docs/13` e `docs/05`.
* Lista final do que ficou sem prova e do que ficou de fora e por quê.

6. Decisões que continuam do responsável (para ligar, não para construir)
D-4 (definição final de "atendente vinculado" e o caso do vinculado em setor que não recebe da triagem), D-5/D-6 (fora do expediente), D-8 (campos da ficha da Agenda para OPERADOR), D-3 (um ou mais setores por usuário), quando aplicar cada script na Disbrel e quando promover cada chave para Estrutural/Fêmina.
