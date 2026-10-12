# 71. Disbrel — requisitos e plano de implementação em etapas

Documento de planejamento. **O que foi implementado, e em qual PR, está na §7 (E228 e E229); o restante continua sendo plano.** Ele cruza os requisitos do cliente Disbrel com o que a base
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

**Auditoria do que está fixo no frontend (arquivo:linha, na cabeça do PR).** Corrigido no PR: `sidebar.tsx:251,297,322,325,339,352,398,499`
(`text-white` e `bg-white/N` viraram o token) e `status-entrega.tsx:35,48` (cor de falha de entrega). O resto, para decisão:

| Onde | O quê | Efeito na Disbrel | Mexi? |
|---|---|---|---|
| `globals.css:127-177` | 31 hexadecimais, todos como fallback de `var(--token, #hex)` (os da Estrutural) | Nenhum: o shell injeta o tema antes | Não (é fallback) |
| `.tsx`/`.ts` | Nenhuma cor hexadecimal ou `rgb()` fora dos tokens | — | — |
| `bolha-mensagem.tsx:346` | `bg-red-500/10 text-red-500` (paleta do Tailwind, não token) | Ícone de arquivo indisponível fica no vermelho `#EF4444` em qualquer tema | Não (mudaria o padrão) |
| `sidebar.tsx:297` | Painel de erro do menu usa `text-cor-erro-suave` (claro, pensado para sidebar escura) | **Tema B:** quase ilegível, mas só no estado raro de falha ao carregar o menu | Não (mudaria o padrão) |
| `sidebar.tsx:246` | `bg-white` no losango do logo padrão | Sobre o gradiente vinho, ok | Não |
| `ui/dialog.tsx:34` | `bg-black/10` no fundo dos modais | Neutro, ok | Não |
| `textos.json:3` | `app.nome` = "Synapse CRM" vira o `<title>` da aba; `PUT /marca/identidade` só troca `marca` e `subtitulo` | A aba da Disbrel continua "Synapse CRM" | Não (precisa de decisão) |
| `textos.json:4` | `app.marca` = "Estrutural Vidros" | Coberto pela identidade (`PUT /marca/identidade`) | Via script |
| `textos.json:1070`, `schema-campanhas.ts:19` | "...provedor oficial (Estrutural)." | Texto errado para a Disbrel; fora do alcance da API de identidade | Não (precisa de decisão) |
| `login/page.tsx:19`, `transicao-de-abertura.tsx:44`, `textos.json:116,144` | Marca e rodapé Synapse; a transição "abrindo painel" (pós-login) também usa a marca Synapse | Login fica Synapse (D-9); a transição é decisão | Não |
| `metadata.ts:12-16` | Favicon = `logoUrl` (`/api/v1/config/logo`), inclusive no login | Segue a logo aplicada | Não |

**Bugs encontrados no caminho (preexistentes, fora do escopo, nada foi alterado neles):**

- `lista-conversas.tsx:373-376` com `ui/tabs.tsx:63`: no celular (abaixo de `md`), a aba ativa da lista fica com texto branco sobre fundo claro (o
  `data-active:bg-background` do componente base vence o `data-active:bg-primary`). Acontece **em qualquer tema, inclusive o padrão**; no desktop está certo.
- O texto de falha de entrega sobre o balão azul do tema padrão tem contraste de **1,82:1** (`sidebarItemTextoPerigo` coral sobre `corPrimaria`).
- O botão "Remover" de uma tag (`/tags`) remove na hora, sem confirmação.
- `README.md` diz que `SPRING_PROFILES_ACTIVE=dev java -jar ...` sobe o banco populado, mas num banco vazio o boot pausa na V72 (`docs/41`) e a API responde 500 até
  rodar o one-shot `--synapse.migrations.run-once`.
- O Javadoc de `GET /config/logo` diz 404 quando a instância "não tem logo", mas o classpath tem `logo.png`, então o normal é 200 com a logo do classpath.

- **Risco:** baixo para quem não aplicar (padrão intacto, provado pixel a pixel nas capturas). **Pronto quando:** o responsável
  escolher A ou B, fornecer nome, subtítulo e o PNG, e a aplicação for feita fora do horário de atendimento, com a reversão
  testada antes **no primeiro stack da sequência** (não existe ambiente de homologação separado: `erp-matriz-hml` é a produção da
  Estrutural; ver `docs/72` §3).

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
- Aplicar a marca (D1) fora do horário de atendimento, com a reversão testada antes no primeiro stack da sequência (`docs/72` §3: não há homologação separada).
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
  qualquer stack de produção; e a avaliação de olho humano do tema (as capturas existem, a escolha A ou B é do responsável).
  Os 30 pares de contraste cobrem o que a interface usa nas telas capturadas, não toda combinação possível de componentes.

---

## 7. Andamento: o que o E228 e o E229 entregaram (11/10/2026)

Tudo abaixo está em **PRs em rascunho, não mergeadas**, e **nada foi aplicado em produção**. Cada capacidade nasce
desligada; com ela desligada o comportamento é o de antes. Rótulos: **verificado** (arquivo:linha ou número de execução da
CI), **inferido**, **não verificado**.

| Etapa | PR | Situação | Chave para ligar | CI (Backend / Frontend / Stack; `imagens` = *skipped* em PR) |
|---|---|---|---|---|
| D1 tema, logo, nome | #294 (esta) | feito; escolha A/B é do responsável | script `aplicar-marca-da-instancia.mjs` | execuções 38110578857 e 38110576555 (verde) |
| D2 setores | [#296](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/296) | feito, **V103** | feature flag `setores` (`habilitar-setores.sql`) | 38113270285 e 38113272478 (verde) |
| D3a rodízio por setor, D3b vinculado, D3c chaves | [#300](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/300) (empilhada na #296) | feito, **V104** | `setores` + `ia.distribuicao.atendente_vinculado` | aguardando |
| D3d fora do expediente | — | **não feito** (fora do escopo; D-5/D-6 abertas) | — | — |
| D4 janela por dia | [#299](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/299) | feito, sem migration | chaves `alerta.horario_cliente.<dia>` (`horario-alerta-por-dia.sql`) | 38112599979 e 38112587520 (verde) |
| D5a Agenda para o OPERADOR | [#298](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/298) | **parado pelo gate**: só relatório e teste de caracterização | — | 38112276215 e 38112263413 (verde) |
| D5b funil de 5 etapas | [#297](https://github.com/synapse-auto/ERP-Matriz-Synapse/pull/297) | feito (scripts), sem migration | `funil-disbrel.sql` (simula por padrão) | 38112150346 e 38112164607 (verde) |

**Ordem de merge das migrations (Flyway sem *out of order*):** `#295 (V102)` → `#296 (V103)` → `#300 (V104)`. Se uma entrar antes
da anterior, a produção (em V101) recusa a migration de número menor que chegar depois. A V100 nunca foi preenchida.

### Premissas deste documento que o código desmentiu ou completou

1. **Rótulos de papel por instância (Proprietário/Diretoria/Interno) não saem de `textos.json`.** O arquivo é de classpath, lido uma
   vez no boot (`ConfiguracaoDeInstanciaResources`), único para os três stacks; só tema, logo e nome têm override por instância
   (`marca_da_instancia`). **Não implementado**: exige mecanismo novo (decisão abaixo).
2. **"Mesmos papéis que gerenciam usuários" não inclui o SUBGESTOR por padrão.** `equipe.editar` nasce negada ao SUBGESTOR
   (`Capacidade.java:157-160`); ele só edita (e só atribui setor) por delegação. Setor é criado por GESTOR/ADMINISTRADOR.
3. **D5a: a ficha da Agenda expõe `notas` sem filtro e `resumoIa` a quem tem `resumo_ia.ver` (padrão: todos).** Por isso a regra do
   E229 mandou parar a mudança de `app_e_agenda()`. Confirmado que `app_e_agenda()` (`V60:10-13`) exclui OPERADOR.
4. **D4: a "janela crítica" só decide se o alerta crítico também vai ao grupo do cliente** (único consumidor:
   `MonitorarSaudeCriticaUseCase.java:35`). Nenhum job agendado muda. A API de configuração só **edita** chaves existentes
   (`PUT /api/v1/automacao/config/{chave}`), então as chaves por dia nascem por script, não por migration.
5. **D5b: vendas são contadas por evento da timeline**, não pela etapa do lead; mover lead por SQL para uma etapa GANHO não gera
   venda. Por isso o remapeamento exige o mesmo resultado dos dois lados. `lead` tem RLS FORCE: os scripts recusam papel que
   não enxerga todos os leads. "Sem Retorno" não é etapa: é o filtro `semRetornoDias` (`CampoFiltravel.java:64`).
6. **D3: `transferir-proximo-humano` não tinha corpo**; agora aceita `{setorId}` opcional (aditivo). `SUBGESTOR` continua
   recebendo triagem por papel; quem não recebe é decidido por **setor** (`recebe da triagem = não`), não por papel.
7. **`OpenApiIT` exige `description` em toda operação**; a #296 só passou depois de acrescentá-las.

### Decisões que continuam com o responsável

D-4 (definição final de "vinculado"; a #300 usa: gravado quando um humano recebe/assume ou o envio manual troca o dono; não ao
finalizar), D-5/D-6 (fora do expediente), D-8 (campos da ficha da Agenda para OPERADOR: aceitar equivalência com ATENDENTE, ficha reduzida
ou manter fora), D-3 (um ou mais setores por usuário: hoje um, índice relaxável), rótulos de papel por instância, **quando aplicar
cada script na Disbrel** e **quando promover cada chave para Estrutural/Fêmina**. Antes de ligar `setores`/vinculado: rodar na VPS
`docker/operacoes/e229-explain-rodizio-{antes,depois}.sql` e colar as saídas (sem isso **não há prova de desempenho**).

### O que ficou sem prova

EXPLAIN real do rodízio (scripts prontos e testados contra o schema, mas só a VPS mede); execução dos scripts SQL com o `psql`
da VPS (versão não verificada; exigem 13+) e contra dados reais da Disbrel; suíte inteira do backend localmente (o ambiente de
desenvolvimento não baixa a imagem do MinIO usada por outros ITs; a CI cobre); aplicação em qualquer instância; a tela de setores
foi testada por vitest, **sem captura visual**.

---

## 8. Decisões registradas em 11/10/2026 (E230)

Decisões **provisórias e reversíveis** do responsável do projeto. As marcadas **(cliente)** dependem da confirmação da Disbrel e
**não destravam ligar nada em produção**. Esta seção vale mais que o texto antigo das §§ 2–6 sempre que houver conflito.

| # | Decisão | Valor decidido | Onde está / efeito |
|---|---|---|---|
| D-1 | Mecanismo de tema/marca | Usar o que já existe (`PUT /api/v1/config/marca/{tema,logo,identidade}`), sem variável nova | Feito na D1 (#294) |
| D-2 | Sidebar do tema Disbrel | **Tema A provisório** (conteúdo branco, sidebar vinho); a escolha final é do responsável olhando as capturas **(cliente)** | A e B ficam no repositório até a escolha |
| D-3 | Setores por usuário | Um setor por usuário; vínculo em tabela separada com `UNIQUE(usuario_id)` removível | #296: `V103` (`ux_usuario_setor_usuario`, `DROP INDEX` relaxa) |
| D-4 | "Atendente vinculado" | O **último humano que assumiu ou recebeu** o atendimento do lead (não necessariamente quem finalizou). Vale só se o usuário está ativo, disponível e em setor que recebe da triagem; senão cai no rodízio do setor e o novo atendente vira o vinculado | #300 — divergências da E230 resolvidas pelo E231 (§10) |
| D-5 / D-6 | Fora do expediente (atribuir a atendente offline; setor sem ninguém) | **Não decidido** — depende do dono da Disbrel **(cliente)**. Nenhum código | Opções abaixo |
| D-7 | "Sem Retorno" | Sem estado novo: filtro `semRetornoDias` existente | #297 não cria estado (`CampoFiltravel.java:64`) |
| D-8 | Agenda para o OPERADOR | **Manter o OPERADOR fora por enquanto**. Se o cliente confirmar que o "Interno" precisa da Agenda: **ficha reduzida** | #298 fica só de caracterização; opção detalhada abaixo **(cliente)** |
| D-11 | Login | Continua com a identidade Synapse | Nada |
| D-12 | Diretoria (SUBGESTOR na Disbrel) | Recebe por transferência manual; **não entra no rodízio da triagem** | Setor com "recebe da triagem = não"; nenhum enum muda; provado em `DiretoriaPorTransferenciaManualIT` (#300) |
| — | Rótulos de papel (Proprietário/Diretoria/Interno) | Aceitar os nomes atuais agora; estender a marca depois, em PR próprio | Pendência de **baixa prioridade**; nenhum código |
| — | Itens "[a definir]", IA, n8n, Canal | Fora do escopo deste repositório/plano | Nada |

### Continua em aberto
- **D-5/D-6 (cliente).** Opção **A**: fora do expediente, atribuir **só ao vinculado** (sem vinculado, o lead espera). Opção **B**:
  **no início do expediente**, distribuir entre os que estiverem online. Hoje o rodízio exige `ONLINE`; o resultado fora do expediente é 409
  sem destino (`docs/62`). Qualquer das duas muda quem é notificado e a carga do primeiro minuto da manhã. Nada foi implementado (D3d).
- **Escolha final entre os temas A e B** e os textos da identidade (nome exibido, subtítulo, PNG da logo).
- **D-8: o que o cliente precisa confirmar** — se o papel "Interno" (OPERADOR) realmente precisa abrir contatos da Agenda e o que pode ver.
- **Rótulos de papel por instância**, quando alguém quiser (PR próprio).

### D-8, "ficha reduzida": o que precisaria mudar (não implementado)
Mostrar, a quem abre um lead alheio pela Agenda, só **nome, telefone, empresa e responsável atual** (sem `notas`, `resumoIa`, e-mail, CPF,
localização nem campos customizados). Superfície de mudança, verificada na leitura do código:
1. `FichaDoLead.de` (`LeadController.java:253-260`) e `LeadController.porIdNaAgenda` (`:117-123`): devolver a forma reduzida quando
   `ContextoDeAgenda.ativo()` e o lead não é do usuário — isso **muda o contrato também para o ATENDENTE**, que hoje recebe a ficha completa.
2. `app_e_agenda()` (`V60:10-13`) passaria a incluir `OPERADOR`, atrás de chave, e as políticas que a chamam (`V60`, `V75`, `V78`, `V87`,
   `V93`) precisariam de teste de **equivalência por papel** (ATENDENTE/OPERADOR/SUBGESTOR/GESTOR/ADMINISTRADOR) — sem isso, nenhuma mudança de RLS.
3. Abrir a conversa pela Agenda (`AtendimentoAcoesController.java:187-191`) registra o solicitante como participante e dá leitura do
   histórico: a ficha reduzida **não** limita isso; é decisão separada.
4. Frontend das telas da Agenda e `IsolamentoDeAgendaIT`/`AgendaDoOperadorCaracterizacaoIT` (o segundo falha de propósito quando a função muda).

## 9. Auditoria do E230 (PRs contra as decisões)

Resultado completo, com `arquivo:linha`, no relatório da E230. Resumo do que mudou no plano:
- **Conferido:** D-3 (`V103:30-41`), D-7 (#297 sem migration nem estado), D-12 (agora com teste), D-8 (#298 só teste).
- **D-4, divergências a decidir (nada foi alterado em silêncio):** (a) usuário **sem setor** continua elegível a vinculado com `setores`
  ligado (a decisão diz "em setor que recebe da triagem"); (b) o vínculo só é gravado em transferência com destino humano e em envio manual
  que trocou o dono — **abrir contato pela Agenda e responder sem troca de dono não grava**; (c) com a chave desligada nada é gravado, então
  ligar depois só vincula dali em diante. Correções propostas na PR #300.
- **Plano de mesclagem e deploy:** [`72-plano-de-mesclagem-e-deploy-disbrel.md`](./72-plano-de-mesclagem-e-deploy-disbrel.md).

## 10. Decisões registradas em 12/10/2026 (E231): o atendente vinculado

O responsável decidiu as três divergências da D-4 que a E230 deixou à vista. Implementação na #300; nada disto vale com as chaves desligadas.

| # | Decisão | Efeito |
|---|---|---|
| (a) | **Aplicar.** Com `setores` ligado, o vinculado só recebe direto se pertencer a um **setor ativo que recebe da triagem**. Usuário **sem setor** não é elegível ao vínculo direto: o lead cai no rodízio do setor e quem recebe vira o vinculado. | Só o vínculo direto mudou (`disponivelPorId`). O **rodízio** continua tratando usuário sem setor como elegível (fora do escopo desta decisão). Com `setores` desligado nada muda. |
| (b) | **Aplicar.** O vínculo também é gravado (1) quando o **dono** do atendimento envia mensagem sem ser participante e (2) quando um atendente **abre contato pela Agenda e fica como responsável** (`IniciarNovoContatoUseCase.executar` sem mensagem e `abrirParaLeadExistente`; com mensagem, quem grava é o envio). **Abrir a ficha sem assumir não grava**; colaborador (convidado ou entrada pela Agenda em lead alheio) não vira vinculado. | Origens gravadas: `TRANSFERENCIA`, `ENVIO_MANUAL`, `CONTATO_ABERTO`. A coluna `origem` não tem `CHECK`, então não houve mudança de migration (o `COMMENT` da V104 cita só duas origens: defasagem de documentação, sem efeito). |
| (c) | **Não fazer.** Sem backfill: o vínculo vale **a partir da V104**. | Um backfill opt-in a partir de `atendimento.atendente_id` (o último dono de cada lead) é **possível** e só deve ser considerado **se o cliente pedir**; não existe script nem está planejado. |

**Efeito colateral medido no E231 (corrigido junto):** o listener do vínculo recebia todo `EventoDeAtendimento` (inclusive mensagem recebida) com
`@Transactional(REQUIRES_NEW)` no método, ou seja, abria uma transação para cada evento só para devolver sem fazer nada. A anotação saiu do
listener e ficou só na gravação. As duas leituras novas do caminho de transferência (flag `setores` e chave do vínculo) agora vêm de memória
(ver `docs/72` §7).

