# 71. Disbrel — requisitos e plano de implementação em etapas

Documento de planejamento. **Nada aqui foi implementado.** Ele cruza os requisitos do cliente Disbrel com o que a base
(Base PAI) já tem e propõe etapas pequenas, ordenadas por risco e dependência.

- **Fonte:** *Requisitos do Projeto — Disbrel*, versão 1.2, 28/09/2026 (PDF de 4 páginas, "uso interno").
- **Fora do escopo por decisão do responsável:** a **seção 2 (Canal)** inteira — `DIS-NEW-01`, `DIS-NEW-02` e
  `DIS-NEW-03` (coexistência com o WhatsApp Business app, webhooks `smb_*`, alerta de desconexão). O responsável
  informou que essa parte está errada. Nenhuma etapa abaixo a toca; quando ela for reescrita, entra como etapa nova.
- **Escopo: somente o CRM.** Fora deste plano, por decisão do responsável: `DIS-ALT-06` (saudação e base de
  conhecimento da IA), a persona da IA, a classificação de mensagens por IA, o modo "botões" e os critérios de
  classificação do funil. Onde um requisito mistura as duas partes, o plano cobre só o que o CRM guarda, valida ou
  expõe pela própria API.
- **Como o documento do cliente lê a legenda:** itens `MATRIZ` são configuração genérica da base e **vão para todos os
  filhos**; itens `DISBREL` são parâmetros só deste cliente. O PDF só lista **diferenças** sobre a base; requisito que
  não aparece lá é igual à base.
- **Limite desta análise:** os documentos-base citados no PDF (*Requisitos da Automação*, *Requisitos do CRM*,
  *Requisitos técnicos Matriz*) **não estão no repositório**. Os IDs de requisito que o PDF menciona só foram
  resolvidos onde o `docs/05` ou o código os cita. O restante está marcado como **não verificado**.

---

## 1. Princípios que decidem o desenho

Vêm do `AGENTS.md` e valem para toda etapa:

1. **Um repositório, uma imagem, vários filhos.** Os três stacks (`erp-matriz-hml`, `fmnaprod-uzapi`, `disbrel-dbl`) leem
   o mesmo template (`docker/dokploy-stack.yml`) e a mesma imagem (`SYNAPSE_IMAGE_TAG`). Nada de `if (cliente ==
   "disbrel")` e nada de fork. Diferença entre clientes = configuração, dado ou **capacidade ligável**
   (`synapse.extensoes.X.habilitado`).
2. **Item `MATRIZ` entra desligado ou com padrão igual ao comportamento de hoje.** Estrutural e Fêmina não podem mudar
   de comportamento porque a Disbrel chegou. Cada etapa tem, por isso, um teste que prova que o padrão antigo continua
   igual.
3. **Variável nova no `dokploy-stack.yml` só com default vazio** (`${VAR:-}`). `:?obrigatoria` derruba o deploy dos
   outros stacks. Toda variável nova vai para "ação necessária no Dokploy", `.env.example` e README.
4. **Lead é incidente comercial se vazar.** Qualquer etapa que toque distribuição, vínculo ou visibilidade (RN-CRM-01,
   RN-CRM-02, RN-CRM-06) nasce com teste negativo e passa pela `VisibilidadeLeadSpecification` e pelas políticas RLS.
5. **Aba Atendimentos 08:00–18:30 é a regra de precedência.** Nada síncrono e bloqueante entra no caminho de
   mensagem. Distribuição por setor é consulta rápida no banco, nunca chamada externa.
6. **Migrations:** `git fetch` e próximo número livre a partir da `origin/main` (hoje a última é a `V101`); nunca editar
   migration aplicada.
7. **Strings de UI vão para `textos.json`, cores para tokens de `tema.json`.** Já é regra, e a Disbrel a exercita de
   verdade: a mesma imagem serve dois temas, pela marca gravada no banco (`marca_da_instancia`), sem variável nova.

---

## 2. Requisito × o que a base já tem

Legenda de situação: ✅ já atendido · 🟡 parcial · 🔴 lacuna · ⚪ só a configuração é do CRM · 🚫 fora do escopo.

### 2.1 Parâmetros (seção 1 do PDF)

| Parâmetro | Situação | O que existe hoje (evidência) | Lacuna | Etapa |
|---|---|---|---|---|
| Tema branco + `#6b0504` | 🟡 | **O mecanismo já existe** desde a E31b: `GET /api/v1/config/{tema,textos,logo}` (público) e `PUT /api/v1/config/marca/{tema,identidade,logo}` (`GESTOR`, `SUBGESTOR`, `ADMINISTRADOR`), gravados na linha única `marca_da_instancia`; vale na leitura seguinte, sem reiniciar; `NULL/NULL` usa o classpath (`MarcaDaInstanciaIT`). A versão anterior deste documento dizia que faltava override por instância: **estava errada**. Restam: o tema da Disbrel (dado), uma forma segura de aplicar e reverter, e o que o frontend deixava fixo (sidebar só funciona escura; falha de entrega sobre o balão). O `PUT` **não valida** o schema, e um tema incompleto derruba o layout raiz (login incluído) | Tema e marca como dados; aplicar e reverter com segurança; limpar o frontend; textos de marca fora do alcance da API de identidade (ver D1) | D1 |
| Horário humano Seg–Sex 08–18, Sáb 08–12, Dom fechado | 🔴 | `horario_trabalho` e `rotina_disponibilidade*` existem **só como migration**, sem código (`docs/05`, RF-CRM-54 e RF-CRM-75 rebaixados). A única janela em uso é `alerta.horario_cliente.inicio/fim` (`08:00`–`18:30`, **uma janela por dia, igual para todos os dias**) | Horário semanal por dia da semana | D4 |
| Usuários 10–15; setores Vendas, Suporte, Diretoria | 🔴 | Não existe entidade de setor/equipe/departamento no modelo: a busca por "setor" só acha texto de UI | Setor como conceito | D2 |
| Distribuição: atendente vinculado; senão rodízio sequencial por setor | 🔴 | Ver `DIS-NEW-04/05` | — | D3 |
| Pergunta de preferência de atendente: desligada | ⚪ | Não existe chave no CRM | Chave booleana em `configuracao_automacao`; o CRM só guarda e expõe | D3 |
| Agenda de Contatos compartilhada | 🟡 | Ver `DIS-ALT-04` | — | D5 |
| Funil de 5 etapas sem Orçamento | 🟡 | Ver `DIS-ALT-05` | — | D5 |
| Campo "Origem do lead": **não implementar** | ✅ | Nenhum campo com esse nome existe no repositório | Só garantir que ninguém o crie | — |

### 2.2 Requisitos numerados

| ID | Pede | Situação | O que existe hoje (evidência) | Lacuna | Etapa |
|---|---|---|---|---|---|
| `DIS-ALT-01` (`DISBREL`, crítico) | Disponibilidade de 100% vale Seg–Sex 08:00–18:00 **e Sáb 08:00–12:00**; sábado como rotina de plantão (RF-CRM-75) | 🔴 | A regra de precedência e o alerta ao cliente assumem 08:00–18:30 todos os dias. `DataSourceConfig` e jobs agendados citam essa janela em comentário | Janela por dia; auditar jobs agendados contra o sábado; RF-CRM-75 implementado de verdade | D4 |
| `DIS-NEW-04` (`MATRIZ`, crítico) | Toggle **atendente vinculado**: contato com atendente anterior vai direto a ele; se indisponível/desativado, rodízio do setor e o novo atendente vira o vinculado | 🔴 | Ao **finalizar**, o responsável do lead é limpo; quando o cliente volta, o atendimento abre `EM_IA`, sem responsável (`RetornoDoLeadFinalizadoIT`, script `docker/provisionamento/liberar-responsavel-de-leads-finalizados.sql`). **Não existe vínculo persistido.** O último atendente é recuperável por `atendimento.atendente_id`, mas isso não é um vínculo | Conceito de vinculado, toggle, regra de fallback | D3 |
| `DIS-NEW-05` (`MATRIZ`, crítico) | Atendentes em setores; rodízio sequencial **entre os disponíveis do setor** (Vendas ou Suporte); **Diretoria não recebe leads da triagem** | 🔴 | O rodízio é global: `AtendenteDisponivelRepositorioJdbc` filtra disponível-para-IA + `ONLINE` + papel `ATENDENTE`/`SUBGESTOR` (`PapelUsuario.recebeAtendimento()`), ordenado por menor carga ou sequencial (`ia.distribuicao.sequencial`, V71). O contrato `transferir-proximo-humano` **só recebe o `atendimentoId`**; o servidor decide o destino de propósito, para o chamador não forjar distribuição. **`SUBGESTOR` hoje recebe triagem**, o que contradiz "Diretoria não recebe" | Setor; filtro por setor no rodízio; como o setor chega ao CRM sem abrir mão de o servidor escolher a pessoa; Diretoria fora da triagem | D2, D3 |
| `DIS-NEW-06` (`MATRIZ`, média) | Modo de distribuição configurável: rodízio sequencial ou botões. Disbrel: sequencial | 🟡 | `ia.distribuicao.sequencial` (booleano) já escolhe entre "menor carga" e "sequencial". O modo "botões" não tem representação no CRM | Nomear o modo como configuração própria sem quebrar o booleano atual; o CRM só guarda a escolha | D3 |
| `DIS-ALT-02` (`MATRIZ`, média) | Pergunta de preferência de atendente vira toggle | ⚪ | O CRM já serve configuração por `/internal/v1/automation-config/{chave}` | Chave nova de configuração (a mesma da linha de parâmetros) | D3 |
| `DIS-ALT-03` (`MATRIZ`, alta) | Fora do expediente, atribuir ao vinculado ou a atendente do setor, para retomada no próximo período útil | 🔴 | O rodízio exige atendente `ONLINE` e marcado para a IA; fora do expediente não há ninguém online, então o resultado é **409 sem destino** (já investigado em `docs/62`) | Política de atribuição **sem exigir presença** fora do horário — é a mudança mais sensível de distribuição | D3 |
| Papéis (seção 4) | Gestor = Proprietário; Subgestor = Diretoria; Atendente = Vendas, Suporte; **Interno** = usuário interno com permissões detalhadas definidas pelo gestor, incluindo quem pode transferir atendimentos para ele | 🟡 | O papel **`OPERADOR`** (V92/V93, `docs/47` §15) é o "Interno": configurável, com `atendimentos.receber_de_operador` ("quem pode transferir para ele") | Só rótulos por instância (Proprietário/Diretoria/Interno) via `textos.json`; **o enum não muda** | D2 |
| `DIS-ALT-04` (`MATRIZ`, alta) | **Agenda de Contatos compartilhada**: todos os usuários veem todos os contatos; a aba Atendimentos mantém as visões por papel e a propriedade do lead | 🟡 | A V60 já deixa `ATENDENTE`, `SUBGESTOR`, `GESTOR` e `ADMINISTRADOR` **localizarem qualquer contato e abrir a conversa existente** pela Agenda, via `app_e_agenda()` e o marcador `app.contexto_agenda`; a ficha sai por `GET /api/v1/leads/{id}/agenda`. `RF-CRM-03` consta ✅ com `IsolamentoDeAgendaIT`. **`OPERADOR` não está na função** (a V93 não a alterou) | Incluir `OPERADOR`; confirmar o que a ficha da Agenda expõe (`resumo_ia`, notas, campos customizados) e provar com teste negativo que a aba Atendimentos não mudou | D5 |
| `DIS-ALT-05` (`DISBREL`, alta) | Funil sem Orçamento: Novo Contato → Aguardando Medidas → Negociação → Fechado → Pós-venda; estado fora do funil: **Sem Retorno** (7 dias sem resposta) | 🟡 | `etapa_atendimento` é **dado**, não enum (`/api/v1/etapas`, `/internal/v1/etapas`), com `resultado` (`GANHO`/`PERDIDO`). O seed de dev tem outras 7 etapas e só roda no perfil `dev`. Existe o filtro `semRetornoDias`, mas **não existe um estado "Sem Retorno"** | Provisionar as 5 etapas por script (não migration); decidir o que é "Sem Retorno" | D5 |
| `DIS-NEW-01/02/03` | Coexistência, webhooks `smb_*`, alerta de desconexão | 🚫 | Seção 2 ignorada por decisão do responsável | — | — |

---

## 3. Etapas propostas

Cada etapa é um PR (ou uma pequena sequência de PRs), com migration só quando necessário e **sem mudar o comportamento
dos outros filhos**. A ordem põe primeiro o que é visível e de baixo risco, depois o que mexe em distribuição.

### Etapa D0 — Decisões e insumos (sem código)

Nada começa a ser construído sem as respostas da seção 5 que bloqueiam a etapa seguinte. Entregáveis:
decisões D-2 a D-10 respondidas (a D-1 está fechada), ativos de marca recebidos (logo PNG, nome exibido e subtítulo) e lista
de usuários e setores.

### Etapa D1 — Tema, logo e nome da instância (usando o que já existe)

**Decisão D-1 fechada:** usar o mecanismo existente (`marca_da_instancia` e `PUT /api/v1/config/marca/*`). **Não** há
diretório de instância, variável nova, mudança no `dokploy-stack.yml` nem imagem por filho.

**Entregue (código e dados; nada aplicado em produção):**

- **Dois temas candidatos**, versionados em `docker/provisionamento/instancias/disbrel/`, com as mesmas chaves do `tema.json`
  padrão: **A** (conteúdo branco, sidebar vinho escuro derivada de `#6B0504`) e **B** (conteúdo branco, sidebar clara). Não há
  protótipo, então a escolha é do responsável, olhando as capturas em `docs/evidencias/disbrel-d1/`. Contraste calculado
  (30 pares, os que a interface usa): **A 30/30 e B 30/30**; `#6B0504` sobre branco, **12,74:1**.
- **Erro separado da ação principal.** O vinho e o `corErro` padrão são parentes, e o fundo suave rosado do primário era
  indistinguível do fundo suave do erro. Só nos temas da Disbrel: `corErro` `#BF360C`, `corErroSuave` `#FFEDE5`, perigo da sidebar
  ajustado, e `corPrimariaSuave` quase neutro (`#F6F0EF`). Sucesso e atenção intactos; o tema padrão intacto.
- **Frontend: o que estava fixo e travava o tema B.** A sidebar usava `text-white` e `bg-white/N`, o que só funciona com sidebar
  escura (no B, o nome da marca, o item ativo e o usuário ficavam brancos sobre branco), e a falha de entrega sobre o balão de
  saída reaproveitava a cor de perigo da sidebar (ilegível sobre um balão vinho). Dois **tokens opcionais** com padrão que reproduz
  o comportamento antigo: `sidebarTextoForte` e `mensagemFalhaTexto`. Nenhum dado do tema padrão mudou.
- **Script de aplicar e reverter** (`docker/provisionamento/aplicar-marca-da-instancia.mjs`, Node 18+, sem dependências):
  token só por variável de ambiente (não existe `--token`), validação local antes de qualquer `PUT` (mesmas chaves, cores
  legíveis e contraste), backup do estado atual, conferência por leitura depois de cada `PUT`, `--dry-run` sem rede, reversão
  pelo backup. A API **não** tem rota para voltar ao classpath (`NULL`): o SQL de rollback exato está no README da instância e
  foi validado em banco real.
- **Testes:** chaves de cada tema iguais às do padrão; os dois temas aceitos pelo schema do frontend; contraste; proteções
  que reprovam de propósito (tema incompleto, chave desconhecida, contraste baixo, sidebar clara com texto branco);
  18 testes do script contra uma instância de mentira (token nunca impresso nem gravado, backup antes de escrever,
  conferência por leitura, reversão); `MarcaDaInstanciaIT` sem alteração.

**Proposta, não feita (muda comportamento):** validar o schema no `PUT /marca/tema`. O `MarcaDaInstanciaIT` hoje envia um tema
**parcial** (`{corPrimaria, logoUrl}`) e espera 200, então a validação quebraria um teste e qualquer cliente que dependa disso;
precisa de decisão e de ajuste do teste. Enquanto isso, a proteção é a do script.

**Fica para o responsável decidir** (lista com arquivo e linha no relatório do PR): título da aba (`textos.app.nome` =
"Synapse CRM", fora do alcance de `PUT /marca/identidade`, que só troca `marca` e `subtitulo`), a frase com "(Estrutural)"
em `textos.json:1070` e `schema-campanhas.ts:19`, a marca Synapse na transição pós-login, e o texto de falha de entrega do
tema padrão (1,82:1 sobre o balão azul). O login continua com a identidade Synapse (D-9).

- **Risco:** baixo para quem não aplicar (padrão intacto, provado pixel a pixel nas capturas). **Pronto quando:** o responsável
  escolher A ou B, fornecer nome, subtítulo e o PNG, e a aplicação for feita fora do horário de atendimento, com a reversão
  testada antes na homologação.

### Etapa D2 — Setores e papéis da Disbrel

**Objetivo:** o CRM passar a conhecer setor, sem tocar ainda em distribuição.

- Entidade **`setor`** e vínculo usuário × setor (migration nova; capacidade `synapse.extensoes.setores.habilitado`,
  desligada por padrão). Setor é dado cadastrável, não enum: Vendas, Suporte e Diretoria são **linhas**, não código.
- Cadastro e atribuição de setor na gestão de equipe (frontend), por quem já gerencia usuários (`docs/47`).
- Atributo do setor "**recebe da triagem**": Diretoria = não. Isso resolve o `SUBGESTOR` ser elegível hoje sem mexer
  em `PapelUsuario`.
- Rótulos por instância: Proprietário (GESTOR), Diretoria (SUBGESTOR), Interno (OPERADOR) vêm do `textos.json`.
  O enum de papéis **não muda**.
- **Testes:** setor não altera visibilidade de lead (negativo: atendente de Vendas continua sem ver lead de colega por
  causa do setor); capacidade desligada = tela e comportamento idênticos aos de hoje.
- **Risco:** médio-baixo (modelo novo, sem regra nova). **Pronto quando:** dá para cadastrar os 3 setores e alocar os
  10–15 usuários, e nada mais mudou.

### Etapa D3 — Distribuição: setor, vinculado e fora do expediente

**Objetivo:** `DIS-NEW-04`, `DIS-NEW-05`, `DIS-NEW-06`, `DIS-ALT-02` e `DIS-ALT-03`. É a etapa de maior risco comercial e
deve ser fatiada em PRs menores, cada um atrás de chave própria:

- **D3a — Rodízio por setor.** O rodízio ganha filtro de setor; o contrato interno de transferência passa a aceitar
  um **setor opcional** (compatível com o contrato atual: ausente = comportamento de hoje). O chamador diz *qual setor*;
  **quem** recebe continua sendo decisão do servidor. Setor com `recebe da triagem = não` nunca é destino. Teste de
  contrato `/internal/v1` e teste de que o chamador não consegue escolher usuário.
- **D3b — Atendente vinculado (toggle).** Persistir o vínculo (decisão D-4), toggle em `configuracao_automacao`, regra:
  vinculado disponível → direto; indisponível ou desativado → rodízio do setor e o novo atendente passa a ser o
  vinculado. Teste negativo de RN-CRM-01/02/06: o vínculo **não** dá a outro atendente acesso ao lead, e não
  transfere por si só quando o toggle está desligado.
- **D3c — Modo de distribuição e pergunta de preferência.** Configuração explícita do modo (sequencial × botões),
  compatível com `ia.distribuicao.sequencial`, e a chave do toggle da pergunta de preferência. O CRM **só guarda e
  expõe a configuração**, com validação e auditoria.
- **D3d — Fora do expediente (`DIS-ALT-03`).** Atribuir ao vinculado ou a atendente do setor **sem exigir `ONLINE`**,
  para retomada no próximo período útil. Hoje a presença é pré-requisito do rodízio; mudar isso afeta quem é notificado
  e a carga do primeiro minuto da manhã. Só entra depois das decisões D-5 e D-6.
- **Risco:** alto. **Pronto quando:** com tudo desligado, os ITs atuais de transferência e rodízio
  (`TransferenciaAutomacaoIT`, `CriterioDeRodizioAutomacaoIT`, `DisponibilidadeParaIaIT`, `RetornoDoLeadFinalizadoIT`)
  passam sem alteração; com tudo ligado, os cenários novos passam, incluindo os negativos.

### Etapa D4 — Horário semanal e janela crítica com sábado

**Objetivo:** `DIS-ALT-01` e `RF-CRM-75`.

- Modelo e leitura do **horário semanal** (a tabela `horario_trabalho` já existe e nunca teve código) e da rotina de
  plantão de sábado (`rotina_disponibilidade*`).
- Janela do alerta ao cliente passa de "um par início/fim para todos os dias" para **horário por dia**, mantendo
  `alerta.horario_cliente.*` como padrão para quem não configurar.
- **Auditoria obrigatória antes de ligar:** todo `@Scheduled`/cron, a finalização em massa, o envio de campanhas e os
  jobs de manutenção contra Sáb 08:00–12:00, porque hoje "fora do horário protegido" assume 08:00–18:30 todos os dias.
  Se algum job pesado cair no sábado de manhã, ele muda de horário ou fica atrás de trava.
- **Teste:** alerta crítico no sábado às 10h carrega o destino cliente; no sábado às 13h e no domingo não carrega;
  instância sem horário configurado se comporta como hoje.
- **Risco:** médio. Independente das etapas D2/D3, pode andar em paralelo.

### Etapa D5 — Funil e Agenda compartilhada

**Objetivo:** `DIS-ALT-05` e `DIS-ALT-04`.

- **Funil:** as 5 etapas entram por **script de provisionamento** da instância (`docker/provisionamento/`), usando o CRUD
  de etapas, e não por migration, porque etapa é dado do cliente. "Fechado" recebe `resultado = GANHO`. Instância nova,
  sem leads, então não há remapeamento de dados.
- **"Sem Retorno":** depende da decisão D-7 (estado novo ou filtro/etiqueta que já existe). Se virar estado, é migration
  e regra nova.
- **Agenda:** incluir `OPERADOR` em `app_e_agenda()` **se** a decisão D-8 confirmar; revisar o que a ficha da Agenda
  devolve; teste negativo de que Atendimentos continua por papel e de que a Agenda não devolve `resumo_ia`/notas de
  lead alheio. Como `DIS-ALT-04` é `MATRIZ`, ela é revisada para **todos** os filhos antes de qualquer mudança.
- **Risco:** médio (RLS), por isso a mudança de política exige teste que a viole de propósito.

### Etapa D6 — Homologação com o cliente (a entrada no ar já foi feita)

A Disbrel (`disbrel-dbl`) **já está no ar**. O que resta desta etapa é só a homologação do que cada etapa acima entregar:

- Checklist de homologação com o cliente a cada etapa liberada. A janela de 100% com sábado (D4) só liga depois de o sábado
  ter sido homologado.
- Aplicar a marca (D1) fora do horário de atendimento, com a reversão testada antes na homologação.
- Mudança de infraestrutura com impacto em outros stacks segue a regra do runbook: janela de madrugada, um stack por vez, e
  conferência do Auto Deploy de cada app antes de mesclar (ver `docs/18`, E221).

---

## 4. Ordem, dependências e as duas tarefas do ClickUp

```
D0 ──┬─► D1 (tema)               ──────────────────────────────┐
     ├─► D2 (setores) ──► D3a ──► D3b ──► D3c ──► D3d ──────────┤
     ├─► D4 (horário/sábado)      (independente)                ├─► D6 (homologação com o cliente; o go-live já ocorreu)
     ├─► D5 (funil + Agenda)      (independente; RLS com cuidado)│
```

Mapeamento sugerido com as tarefas que você mostrou (a data exata não aparece nas capturas; só que uma vence "amanhã" e
a outra está **atrasada há 2 dias**):

| Tarefa no ClickUp | Status mostrado | Etapas |
|---|---|---|
| **Sistemas novos, Departamento, Front end de toggles etc** ("tudo seguindo como base os requisitos") | Em progresso, atrasada | **D2** (departamento = setor), **D3** (toggles de vinculado, modo, preferência) |
| **Versão CRM Disbrel Completo, Tema novo** | Pendente, vence "amanhã" | **D1** (tema), **D4** e **D5** (horário, funil, Agenda), **D6** (só a homologação) |

Se o prazo apertar, a ordem de menor risco para o cliente enxergar progresso é **D1 → D2 → D5 (funil) → D4 → D3**.
D3 é a que não deve ser apressada.

---

## 5. Decisões que dependem de você

| # | Decisão | Minha recomendação | Bloqueia |
|---|---|---|---|
| ~~D-1~~ | ~~Como a Disbrel ganha tema próprio na mesma imagem~~ | **Decidida:** usar a marca no banco (`marca_da_instancia`, `PUT /api/v1/config/marca/*`), que já existe. Sem diretório de instância, variável nova ou imagem por filho | D1 |
| D-2 | **Tema A (sidebar vinho) ou B (sidebar branca)?** Olhar as capturas em `docs/evidencias/disbrel-d1/`. Não há protótipo | A decisão é do responsável. Os dois passam em 30/30 pares de contraste | D1 (aplicar) |
| D-3 | Setor: tabela própria, ou reaproveitar "Equipe"? Um usuário pode estar em **mais de um** setor? | Tabela própria `setor`; um usuário em um setor só, exceto se o cliente disser o contrário | D2 |
| D-4 | **O que é "atendente vinculado"?** O último que finalizou, o último que respondeu, ou quem o lead tem como responsável? Quando passa a valer, e quando é desfeito? | Coluna explícita no lead, gravada quando o atendimento é assumido ou finalizado e limpa quando o usuário é desativado | D3b |
| D-5 | Fora do expediente, **atribuir a atendente offline** muda o que a comissão significa: o lead "pertence" a alguém que ainda não viu. Confirmar com o cliente | Atribuir só ao vinculado ou, sem vinculado, ao próximo do setor, com notificação no início do expediente | D3d |
| D-6 | Fora do expediente, setor sem nenhum atendente ativo (ex.: Suporte inteiro de folga) | Cair no rodízio de outro setor, ou ficar na fila sem dono? | D3d |
| D-7 | "Sem Retorno" é um **estado novo** (migration e regra) ou basta o filtro `semRetornoDias` que já existe? | Filtro/etiqueta primeiro; estado só se o cliente precisar dele em relatório | D5 |
| D-8 | Na Agenda compartilhada, o **Interno** (`OPERADOR`) também vê todos os contatos? Quais campos da ficha ficam visíveis a quem não é o dono? | Sim ao Operador; ficha sem `resumo_ia` e notas de lead alheio | D5 |
| D-9 | Ativos de marca: **logo em PNG** (a API só aceita PNG), nome exibido e subtítulo, e o texto do login (D-11 na primeira versão deste documento). O login usa a identidade **Synapse** (`identidade-synapse.css`); fica assim ou vira da Disbrel? | Login continua Synapse, a menos que o contrato diga o contrário | D1 |
| D-10 | O PDF diz "Usuários 10–15" e "Diretoria **não recebe** leads da triagem, só por transferência manual": Diretoria também **atende** (recebe por transferência) ou só supervisiona? | Recebe por transferência manual; não entra no rodízio | D2/D3 |

---

## 6. O que esta análise **não** verificou

- Os três documentos-base do PDF; por isso `RF-AUT-01/02/03/09/10/12` e `RN-AUT-03/04/10` só foram lidos como o PDF os
  descreve, não como a base os define.
- O estado real do stack `disbrel-dbl` no Dokploy (variáveis, volumes, Auto Deploy).
- Cobertura de testes das áreas que as etapas tocam além dos ITs citados; cada etapa deve começar relendo os testes
  vigentes e provando que eles continuam passando com a capacidade desligada.
- O cálculo de contraste do `#6b0504` foi feito pela fórmula WCAG, sem ferramenta visual.
- **D1, o que ficou sem prova:** o upload da logo contra um storage real (o MinIO não subiu no ambiente de desenvolvimento, e o
  `PUT /marca/logo` do backend devolveu 500; o caminho da logo só foi provado contra uma instância de mentira); a aplicação em
  produção e na homologação; e a avaliação de olho humano do tema (as capturas existem, a escolha A ou B é do responsável).
  Os 30 pares de contraste cobrem o que a interface usa nas telas capturadas, não toda combinação possível de componentes.
