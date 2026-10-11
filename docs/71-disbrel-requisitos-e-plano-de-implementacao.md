# 71. Disbrel — requisitos e plano de implementação em etapas

Documento de planejamento. **Nada aqui foi implementado.** Ele cruza os requisitos do cliente Disbrel com o que a base
(Base PAI) já tem e propõe etapas pequenas, ordenadas por risco e dependência.

- **Fonte:** *Requisitos do Projeto — Disbrel*, versão 1.2, 28/09/2026 (PDF de 4 páginas, "uso interno").
- **Fora do escopo por decisão do responsável:** a **seção 2 (Canal)** inteira — `DIS-NEW-01`, `DIS-NEW-02` e
  `DIS-NEW-03` (coexistência com o WhatsApp Business app, webhooks `smb_*`, alerta de desconexão). O responsável
  informou que essa parte está errada. Nenhuma etapa abaixo a toca; quando ela for reescrita, entra como etapa nova.
- **Como o documento do cliente lê a legenda:** itens `MATRIZ` são configuração genérica da base e **vão para todos os
  filhos**; itens `DISBREL` são parâmetros só deste cliente. O PDF só lista **diferenças** sobre a base; requisito que
  não aparece lá é igual à base.
- **Limite desta análise:** os documentos-base citados no PDF (*Requisitos da Automação*, *Requisitos do CRM*,
  *Requisitos técnicos Matriz*) **não estão no repositório**. Os IDs `RF-AUT-xx`, `RN-AUT-xx` e `RF-CRM-xx` que o PDF
  menciona só foram resolvidos onde o `docs/05` ou o código os cita. O restante está marcado como **não verificado**.

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
7. **Strings de UI vão para `textos.json`, cores para tokens de `tema.json`.** Já é regra; a Disbrel só a torna
   obrigatória de verdade, porque pela primeira vez a mesma imagem precisa servir dois temas.

---

## 2. Requisito × o que a base já tem

Legenda de situação: ✅ já atendido · 🟡 parcial · 🔴 lacuna · ⚪ fora do repositório (Automação/n8n) · 🚫 fora do escopo.

### 2.1 Parâmetros (seção 1 do PDF)

| Parâmetro | Situação | O que existe hoje (evidência) | Lacuna | Etapa |
|---|---|---|---|---|
| Tema branco + `#6b0504` | 🔴 | `tema.json`, `textos.json` e `logo.png` são lidos **do classpath** (`ConfiguracaoDeInstanciaResources`); o comentário diz "cada filho substitui os arquivos no próprio `src/main/resources`". Como a imagem é uma só para os três stacks, **não há como um stack ter tema diferente do outro** sem fork ou imagem própria. Não há cor hexadecimal solta em `.tsx`/`.ts` do frontend (o contrato de tokens está respeitado). `textos.json` traz `"marca": "Estrutural Vidros"` e uma frase com "(Estrutural)" | Override de marca **por instância**, sem rebuild; tokens do tema branco/vinho; textos de marca | D1 |
| Horário humano Seg–Sex 08–18, Sáb 08–12, Dom fechado | 🔴 | `horario_trabalho` e `rotina_disponibilidade*` existem **só como migration**, sem código (`docs/05`, RF-CRM-54 e RF-CRM-75 rebaixados). A única janela em uso é `alerta.horario_cliente.inicio/fim` (`08:00`–`18:30`, **uma janela por dia, igual para todos os dias**) | Horário semanal por dia da semana | D4 |
| Usuários 10–15; setores Vendas, Suporte, Diretoria | 🔴 | Não existe entidade de setor/equipe/departamento no modelo: a busca por "setor" só acha texto de UI | Setor como conceito | D2 |
| Distribuição: atendente vinculado; senão rodízio sequencial por setor | 🔴 | Ver `DIS-NEW-04/05` | — | D3 |
| Pergunta de preferência de atendente: desligada | ⚪ | Não existe no CRM; é fluxo da Automação (`RF-AUT-02/03`) | Chave de configuração + a Automação ler | D3 / D6 |
| Agenda de Contatos compartilhada | 🟡 | Ver `DIS-ALT-04` | — | D5 |
| Funil de 5 etapas sem Orçamento | 🟡 | Ver `DIS-ALT-05` | — | D5 |
| Campo "Origem do lead": **não implementar** | ✅ | Nenhum campo com esse nome existe no repositório | Só garantir que ninguém o crie | — |
| Persona da IA "Assistente virtual da Disbrel" | ⚪ | Prompts e workflows do n8n **não estão versionados** aqui (só `docs/n8n/resumo-ia-sob-demanda.json`) | Definir onde o prompt mora | D6 |

### 2.2 Requisitos numerados

| ID | Pede | Situação | O que existe hoje (evidência) | Lacuna | Etapa |
|---|---|---|---|---|---|
| `DIS-ALT-01` (`DISBREL`, crítico) | Disponibilidade de 100% vale Seg–Sex 08:00–18:00 **e Sáb 08:00–12:00**; sábado como rotina de plantão (RF-CRM-75) | 🔴 | A regra de precedência e o alerta ao cliente assumem 08:00–18:30 todos os dias. `DataSourceConfig` e jobs agendados citam essa janela em comentário | Janela por dia; auditar jobs agendados contra o sábado; RF-CRM-75 implementado de verdade | D4 |
| `DIS-NEW-04` (`MATRIZ`, crítico) | Toggle **atendente vinculado**: contato com atendente anterior vai direto a ele; se indisponível/desativado, rodízio do setor e o novo atendente vira o vinculado | 🔴 | Ao **finalizar**, o responsável do lead é limpo; quando o cliente volta, o atendimento abre `EM_IA`, sem responsável (`RetornoDoLeadFinalizadoIT`, script `docker/provisionamento/liberar-responsavel-de-leads-finalizados.sql`). **Não existe vínculo persistido.** O último atendente é recuperável por `atendimento.atendente_id`, mas isso não é um vínculo | Conceito de vinculado, toggle, regra de fallback | D3 |
| `DIS-NEW-05` (`MATRIZ`, crítico) | Atendentes em setores; IA classifica a 1ª mensagem (Vendas/Suporte); rodízio sequencial **entre os disponíveis do setor**; sem certeza pergunta com botões; **Diretoria não recebe leads da triagem** | 🔴 | O rodízio é global: `AtendenteDisponivelRepositorioJdbc` filtra disponível-para-IA + `ONLINE` + papel `ATENDENTE`/`SUBGESTOR` (`PapelUsuario.recebeAtendimento()`), ordenado por menor carga ou sequencial (`ia.distribuicao.sequencial`, V71). O contrato `transferir-proximo-humano` **só recebe o `atendimentoId`**; o servidor decide o destino de propósito, para a Automação não forjar distribuição. **`SUBGESTOR` hoje recebe triagem**, o que contradiz "Diretoria não recebe" | Setor; filtro por setor no rodízio; como o setor chega ao CRM sem abrir mão de o servidor escolher a pessoa; Diretoria fora da triagem | D2, D3 |
| `DIS-NEW-06` (`MATRIZ`, média) | Modo de distribuição configurável: rodízio sequencial ou botões. Disbrel: sequencial | 🟡 | `ia.distribuicao.sequencial` (booleano) já escolhe entre "menor carga" e "sequencial". O modo "botões" é fluxo da Automação | Nomear o modo como configuração própria sem quebrar o booleano atual | D3 / D6 |
| `DIS-ALT-02` (`MATRIZ`, média) | Pergunta de preferência de atendente vira toggle | ⚪ | Vive na Automação. A Automação já lê configuração por `/internal/v1/automation-config/{chave}` | Chave nova de configuração; a Automação passar a lê-la | D3 / D6 |
| `DIS-ALT-03` (`MATRIZ`, alta) | Fora do expediente, atribuir ao vinculado ou a atendente do setor, para retomada no próximo período útil | 🔴 | O rodízio exige atendente `ONLINE` e marcado para a IA; fora do expediente não há ninguém online, então o resultado é **409 sem destino** (já investigado em `docs/62`) | Política de atribuição **sem exigir presença** fora do horário — é a mudança mais sensível de distribuição | D3 |
| Papéis (seção 4) | Gestor = Proprietário; Subgestor = Diretoria; Atendente = Vendas, Suporte; **Interno** = usuário interno com permissões detalhadas definidas pelo gestor, incluindo quem pode transferir atendimentos para ele | 🟡 | O papel **`OPERADOR`** (V92/V93, `docs/47` §15) é o "Interno": configurável, com `atendimentos.receber_de_operador` ("quem pode transferir para ele") | Só rótulos por instância (Proprietário/Diretoria/Interno) via `textos.json`; **o enum não muda** | D2 |
| `DIS-ALT-04` (`MATRIZ`, alta) | **Agenda de Contatos compartilhada**: todos os usuários veem todos os contatos; a aba Atendimentos mantém as visões por papel e a propriedade do lead | 🟡 | A V60 já deixa `ATENDENTE`, `SUBGESTOR`, `GESTOR` e `ADMINISTRADOR` **localizarem qualquer contato e abrir a conversa existente** pela Agenda, via `app_e_agenda()` e o marcador `app.contexto_agenda`; a ficha sai por `GET /api/v1/leads/{id}/agenda`. `RF-CRM-03` consta ✅ com `IsolamentoDeAgendaIT`. **`OPERADOR` não está na função** (a V93 não a alterou) | Incluir `OPERADOR`; confirmar o que a ficha da Agenda expõe (`resumo_ia`, notas, campos customizados) e provar com teste negativo que a aba Atendimentos não mudou | D5 |
| `DIS-ALT-05` (`DISBREL`, alta) | Funil sem Orçamento: Novo Contato → Aguardando Medidas → Negociação → Fechado → Pós-venda; estado fora do funil: **Sem Retorno** (7 dias sem resposta) | 🟡 | `etapa_atendimento` é **dado**, não enum (`/api/v1/etapas`, `/internal/v1/etapas`), com `resultado` (`GANHO`/`PERDIDO`). O seed de dev tem outras 7 etapas e só roda no perfil `dev`. Existe o filtro `semRetornoDias`, mas **não existe um estado "Sem Retorno"** | Provisionar as 5 etapas por script (não migration); decidir o que é "Sem Retorno"; os critérios de classificação são prompt da IA | D5 / D6 |
| `DIS-ALT-06` (`DISBREL`, alta) | Saudação por período do dia, base de conhecimento (endereço, horários, marcas, prazo etc.), IA não informa preço nem estoque | ⚪ | Fora do repositório | Cinco itens do PDF estão **"[a definir]"**: frete, desconto à vista, preço internet × loja, nota fiscal, assistência/garantia | D6 |
| `DIS-NEW-01/02/03` | Coexistência, webhooks `smb_*`, alerta de desconexão | 🚫 | Seção 2 ignorada por decisão do responsável | — | — |

---

## 3. Etapas propostas

Cada etapa é um PR (ou uma pequena sequência de PRs), com migration só quando necessário e **sem mudar o comportamento
dos outros filhos**. A ordem põe primeiro o que é visível e de baixo risco, depois o que mexe em distribuição.

### Etapa D0 — Decisões e insumos (sem código)

Nada começa a ser construído sem as respostas da seção 5 que bloqueiam a etapa seguinte. Entregáveis:
decisões D-1 a D-12 respondidas, ativos de marca recebidos (logo, favicon, nome exibido), lista de usuários e setores,
e o texto dos cinco itens "[a definir]" da base de conhecimento.

### Etapa D1 — Identidade visual por instância (tema branco + `#6b0504`)

**Objetivo:** a Disbrel ter o próprio tema, logo e nome **na mesma imagem** que os outros stacks usam.

- **Mecanismo recomendado:** variável **opcional** que aponta para um diretório de instância (ex.:
  `SYNAPSE_INSTANCIA_DIR`, default vazio) onde `tema.json`, `textos.json` e `logo.png` sobrepõem os do classpath. O
  classpath continua sendo o padrão, então Estrutural e Fêmina não mudam. O diretório entra no stack por volume ou
  config do Swarm. Alternativas na seção 5 (D-1).
- **Tokens:** montar o `tema.json` da Disbrel. O contraste de `#6b0504` sobre branco, pela fórmula WCAG, é de cerca de
  **12,7:1** (passa AAA), então serve como cor de ação e de texto. Faltam derivados (hover, suave, borda) e decidir a
  sidebar (hoje azul-escura): D-2.
- **Cuidado de produto:** `corErro` é um vermelho (`#C0392B`). Um primário vinho ao lado do erro vermelho pode
  confundir "ação" com "problema". A etapa inclui revisar estados de erro e perigo nas telas principais.
- **Textos de marca:** trocar `"marca": "Estrutural Vidros"` e a frase com "(Estrutural)" por valor vindo do override
  de instância.
- **Teste:** carrega o tema da instância quando o diretório existe; cai no classpath quando não existe; falha alto
  se o diretório existe e o arquivo é inválido (a regra de hoje: não servir tema vazio em silêncio). Verificação visual
  por captura de tela das telas principais nos dois temas.
- **Variável nova:** sim → "ação necessária no Dokploy", `.env.example`, README.
- **Risco:** baixo. **Pronto quando:** um stack sobe com o tema Disbrel e outro, na mesma tag de imagem, continua com o
  tema atual.

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

- **D3a — Rodízio por setor.** O rodízio ganha filtro de setor; o contrato de transferência da Automação passa a aceitar
  um **setor opcional** (compatível com o contrato atual: ausente = comportamento de hoje). A Automação diz *qual setor*;
  **quem** recebe continua sendo decisão do servidor. Setor com `recebe da triagem = não` nunca é destino. Teste de
  contrato `/internal/v1` e teste de que a Automação não consegue escolher usuário.
- **D3b — Atendente vinculado (toggle).** Persistir o vínculo (decisão D-4), toggle em `configuracao_automacao`, regra:
  vinculado disponível → direto; indisponível ou desativado → rodízio do setor e o novo atendente passa a ser o
  vinculado. Teste negativo de RN-CRM-01/02/06: o vínculo **não** dá a outro atendente acesso ao lead, e não
  transfere por si só quando o toggle está desligado.
- **D3c — Modo de distribuição e pergunta de preferência.** Configuração explícita do modo (sequencial × botões),
  compatível com `ia.distribuicao.sequencial`, e a chave do toggle da pergunta de preferência. O CRM **só expõe a
  configuração**; o comportamento de botões e da pergunta é da Automação (D6).
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

### Etapa D6 — IA e Automação da Disbrel (fora do repositório)

**Objetivo:** `DIS-ALT-06`, o lado da Automação de `DIS-NEW-05/06` e `DIS-ALT-02`. O CRM entrega contrato e
configuração; **o prompt e o workflow moram no n8n, que não está versionado aqui**.

- Persona "Assistente virtual da Disbrel", sem nome próprio, tom consultivo; saudação conforme o período do dia
  ("Bom dia! Bem-vindo à Disbrel!").
- Base de conhecimento: endereço, horário, contatos, loja física, marcas, bancadas somente em aço inox, prazo de 10 a
  15 dias, compra a prazo com análise de crédito, formas de pagamento.
- **A IA não informa preço nem estoque**, e para os cinco itens "[a definir]" deve **encaminhar a um humano**, nunca
  responder por suposição.
- Classificação Vendas × Suporte, botões quando sem certeza, critérios de etapa do funil, estado Sem Retorno.
- **Decisão D-9:** onde os prompts passam a viver (versionados no repositório, como o template de resumo, ou só no n8n).

### Etapa D7 — Provisionamento, homologação e entrada no ar

- Provisionar a instância `disbrel-dbl`: usuários (10–15), setores, horário, funil, tema/logo/textos, configuração de
  distribuição e do toggle de preferência, número principal `(61) 99666-6025` pelo canal já existente na base.
- Checklist de homologação com o cliente, no horário em que o sábado ainda não vale; só depois liga a janela de 100%.
- Mudança de infraestrutura com impacto em outros stacks segue a regra do runbook: janela de madrugada, um stack por
  vez, e conferência do Auto Deploy de cada app antes de mesclar (ver `docs/18`, E221).

---

## 4. Ordem, dependências e as duas tarefas do ClickUp

```
D0 ──┬─► D1 (tema)               ──────────────────────────────┐
     ├─► D2 (setores) ──► D3a ──► D3b ──► D3c ──► D3d ──────────┤
     ├─► D4 (horário/sábado)      (independente)                ├─► D7 (homologação e go-live)
     ├─► D5 (funil + Agenda)      (independente; RLS com cuidado)│
     └─► D6 (IA/n8n)              (precisa do contrato da D3a/c)┘
```

Mapeamento sugerido com as tarefas que você mostrou (a data exata não aparece nas capturas; só que uma vence "amanhã" e
a outra está **atrasada há 2 dias**):

| Tarefa no ClickUp | Status mostrado | Etapas |
|---|---|---|
| **Sistemas novos, Departamento, Front end de toggles etc** ("tudo seguindo como base os requisitos") | Em progresso, atrasada | **D2** (departamento = setor), **D3** (toggles de vinculado, modo, preferência) |
| **Versão CRM Disbrel Completo, Tema novo** | Pendente, vence "amanhã" | **D1** (tema), **D4** e **D5** (horário, funil, Agenda), **D7** |

Se o prazo apertar, a ordem de menor risco para o cliente enxergar progresso é **D1 → D2 → D5 (funil) → D4 → D3 → D6**.
D3 é a que não deve ser apressada.

---

## 5. Decisões que dependem de você

| # | Decisão | Minha recomendação | Bloqueia |
|---|---|---|---|
| D-1 | Como a Disbrel ganha tema próprio na mesma imagem | Diretório de instância opcional sobrepondo o classpath (variável com default vazio). Alternativas: imagem por filho (volta a ser fork de fato) ou tema no banco (editável na tela, mas essa tela foi cortada do escopo da primeira entrega) | D1 |
| D-2 | "Tema branco" inclui a **sidebar** branca, ou só o conteúdo, com a sidebar em `#6b0504`? Existe protótipo? | Pedir o protótipo antes de montar os tokens | D1 |
| D-3 | Setor: tabela própria, ou reaproveitar "Equipe"? Um usuário pode estar em **mais de um** setor? | Tabela própria `setor`; um usuário em um setor só, exceto se o cliente disser o contrário | D2 |
| D-4 | **O que é "atendente vinculado"?** O último que finalizou, o último que respondeu, ou quem o lead tem como responsável? Quando passa a valer, e quando é desfeito? | Coluna explícita no lead, gravada quando o atendimento é assumido ou finalizado e limpa quando o usuário é desativado | D3b |
| D-5 | Fora do expediente, **atribuir a atendente offline** muda o que a comissão significa: o lead "pertence" a alguém que ainda não viu. Confirmar com o cliente | Atribuir só ao vinculado ou, sem vinculado, ao próximo do setor, com notificação no início do expediente | D3d |
| D-6 | Fora do expediente, setor sem nenhum atendente ativo (ex.: Suporte inteiro de folga) | Cair no rodízio de outro setor, ou ficar na fila sem dono? | D3d |
| D-7 | "Sem Retorno" é um **estado novo** (migration e regra) ou basta o filtro `semRetornoDias` que já existe? | Filtro/etiqueta primeiro; estado só se o cliente precisar dele em relatório | D5 |
| D-8 | Na Agenda compartilhada, o **Interno** (`OPERADOR`) também vê todos os contatos? Quais campos da ficha ficam visíveis a quem não é o dono? | Sim ao Operador; ficha sem `resumo_ia` e notas de lead alheio | D5 |
| D-9 | Onde ficam os prompts da IA (repositório ou só n8n) | Versionar no repositório, como o template de resumo | D6 |
| D-10 | Os cinco itens "[a definir]" (frete, desconto à vista, preço internet × loja, nota fiscal, assistência/garantia) | Cliente responde antes da D6; até lá a IA encaminha ao humano | D6 |
| D-11 | Ativos de marca: logo (SVG ou PNG), favicon, nome exibido, texto do login. O login usa a identidade **Synapse** (`identidade-synapse.css`); fica assim ou vira da Disbrel? | Login continua Synapse, a menos que o contrato diga o contrário | D1 |
| D-12 | O PDF diz "Usuários 10–15" e "Diretoria **não recebe** leads da triagem, só por transferência manual": Diretoria também **atende** (recebe por transferência) ou só supervisiona? | Recebe por transferência manual; não entra no rodízio | D2/D3 |

---

## 6. O que esta análise **não** verificou

- Os três documentos-base do PDF; por isso `RF-AUT-01/02/03/09/10/12` e `RN-AUT-03/04/10` só foram lidos como o PDF os
  descreve, não como a base os define.
- O estado real do stack `disbrel-dbl` no Dokploy (variáveis, volumes, Auto Deploy).
- O n8n e a Automação: nada do que é prompt ou workflow foi lido, porque não está no repositório.
- Cobertura de testes das áreas que as etapas tocam além dos ITs citados; cada etapa deve começar relendo os testes
  vigentes e provando que eles continuam passando com a capacidade desligada.
- O cálculo de contraste do `#6b0504` foi feito pela fórmula WCAG, sem ferramenta visual.
