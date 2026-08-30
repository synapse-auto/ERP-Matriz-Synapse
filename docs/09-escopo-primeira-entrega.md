# 09. Escopo da Primeira Entrega (25/08)

Registro formal do recorte de escopo. Este documento tem precedência sobre a lista de features dos documentos de requisitos para **a primeira entrega** — os requisitos permanecem válidos como destino, não como escopo imediato.

---

## 1. Fora da primeira entrega

| # | Função cortada | Requisitos afetados | Fase 2 |
|---|---|---|---|
| 1 | **Aba de Relatórios** | RF-CRM-79 | Sim |
| 2 | **Aba de Banco de Arquivos** | RF-CRM-13, 55, 56 | Sim |
| 3 | **Aba de Campanhas** | RF-CRM-39 a 44 | Sim |
| 4 | **Sub-abas da Dashboard** (Operacional, Comercial, IA & Automação) | RF-CRM-32 (parcial) | Sim |
| 5 | **Configurações de aparência e notificação:** cor de destaque, resumo diário por e-mail, fonte, densidade | RF-CRM-80 (parcial) | Sim |
| 6 | **Aba de Horários** (janela de atendimento humano por dia da semana, cobertura da IA) | RF-CRM-54 | Sim |

### 1.1 Horários — por que sai e o que isso muda na rotina

A E15b (verificação de código) encontrou que `horario_trabalho` e `rotina_disponibilidade` existem **só como migration** (`V2__equipe.sql`) — nenhum domain, application, repository ou controller as usa em lugar nenhum do backend. `docs/05` marcava `RF-CRM-54` como concluído; não estava. A aba sai do menu por feature flag (`horarios = false`, mesmo padrão das demais), não por remoção de código, e o Placeholder não fica exposto — item de menu visível é promessa, e este item nunca teve entrega por trás.

> A disponibilidade do atendente é **manual** na primeira entrega. Ninguém entra em expediente automaticamente; cada um marca a própria presença. As tabelas `horario_trabalho` e `rotina_disponibilidade` permanecem no schema — a regra deste documento de não cortar schema continua valendo.

**Isto precisa ser dito à subgestora na homologação.** Não é detalhe técnico: muda a rotina de quem usa — hoje, cobertura fora do horário combinado depende de alguém lembrar de marcar presença como ausente/offline, não de uma janela configurada previamente.

### 1.2 `chat_interno` e `dashboard` — flags e escopo efetivo

A E15b (`docs/05` §Resumo da revisão, itens 1 e 2) encontrou as duas flags com `habilitado = TRUE` no seed sem nenhum código por trás. A situação foi corrigida:

- **`chat_interno`**: a E44 entregou conversas diretas de texto, leitura individual, RLS, API e entrega pela fila pessoal. Grupos, mídia, edição, reações, menções, busca e links para atendimento continuam fora desta etapa.
- **`dashboard` (situação encontrada na E15b):** tinha o mesmo problema. A afirmação anterior deste documento (§2) de que "a aba existe, sem as três sub-abas" **estava errada** naquele momento — não havia controller nem rota de frontend. A E20 corrigiu a lacuna após o cliente recolocar a Visão Geral no escopo.

A E16 corrigiu defaults de deploy. A E20 voltou `dashboard` para `true`; a E44 habilitou `chat_interno` no seed porque agora há implementação real. Instâncias existentes devem conferir a flag no banco antes de expor o menu. `fidelizacao` não foi tocada: tem domínio, repositório, entity e um caso de uso de listagem real, exposto no `AutomationConfigInternalController` — falta CRUD humano, não o módulo em si.

## 2. O que permanece, com ajuste

### Dashboard — Visão Geral recolocada no escopo (decisão do cliente, E20)

O cliente recolocou somente a aba **Visão Geral** na primeira entrega. A E20 entregou uma consulta consolidada por período e a tela com atendimentos, tempo médio, CSAT, funil, mensagens por hora e vendas calculadas pela transição para etapa `GANHO`. O acesso é exclusivo de `GESTOR`, `SUBGESTOR` e `ADMINISTRADOR`, porque o ranking expõe resultados comerciais de colegas. A flag `dashboard` volta a `true` onde a tela existe.

A grade de KPIs da Visão Geral **não mostra mais o card "Vendas fechadas"** — o lugar ficou com **Avaliação** (`avg(avaliacao.nota)` na escala 1–5). Vendas continuam no payload e na taxa de conversão. O ranking da Visão Geral passou a ser por avaliação. A coleta é `POST /api/v1/atendimentos/{id}/avaliacao` (após finalizar) e `POST /internal/v1/atendimentos/{id}/avaliacao` (Automação). Sem linha em `avaliacao`, o card continua mostrando vazio de verdade.

As abas **Operacional**, **Comercial** e **IA & Automação** continuam na fase 2: aparecem desabilitadas, sem dados, apenas para manter honesta a casca aprovada no protótipo. A Visão Geral atende o recorte de `RF-CRM-31` e `RF-CRM-33`; o detalhamento de `RF-CRM-32` permanece fora.

### Anexos no chat — upload direto
O composer mantém o botão de anexo (`RF-CRM-09`, `RF-CRM-68`): o clipe abre um menu para cima com **Arquivos** (upload do computador, vários de uma vez) e **Templates** (templates WhatsApp aprovados, o mesmo fluxo da janela de 24h). Também dá para arrastar arquivos do explorador para a coluna do chat (histórico + composer). Cada arquivo segue o `POST` de mídia existente, em sequência. O que sai é o **repositório compartilhado** de arquivos frequentes (`RF-CRM-13/55/56`).

**Consequência técnica:** a infraestrutura de storage (S3/MinIO, upload, URL assinada) **permanece na primeira entrega**, porque o anexo do chat depende dela. O que não é construído é a aba de gestão desses arquivos.

---

## 3. Regras do corte

Três decisões que valem mais do que a lista acima, porque determinam o custo de trazer essas features de volta:

### 3.1 O schema permanece completo

**Não remova tabelas das migrations.** `campanha`, `campanha_mensagem`, `campanha_mensagem_metrica`, `arquivo_banco` e `filtro_modular` continuam sendo criadas na E01.

Motivo: uma tabela vazia custa zero em runtime. Uma migration futura para adicionar cinco tabelas relacionadas em produção custa uma janela de manutenção e risco. Como a E01 já está rodando com o schema completo, mexer nela agora seria trabalho para criar trabalho.

### 3.2 Tudo sai por feature flag, não por remoção de código

As abas cortadas **não são apagadas do menu por `if` no frontend** — elas simplesmente não são construídas, e as flags correspondentes ficam `false`:

```
campanhas          = false
relatorios         = false
dashboard_completo = false
banco_arquivos     = false
```

Quando a fase 2 chegar, ligar a flag e construir a tela é aditivo. Isso também mantém a coerência com a Base PAI: um filho que compre Campanhas liga a flag; um que não compre, não vê.

### 3.3 Os design tokens permanecem

O corte de "cor de destaque, fonte e densidade" é o corte do **controle na tela de configurações**, não da arquitetura de tema.

Os design tokens, o `tema.json` e o endpoint `GET /api/v1/config/tema` continuam sendo construídos na E10 — são fundação da Base PAI, e removê-los violaria a diretriz de "nada hardcoded". O que não existe na primeira entrega é o usuário final poder mudar isso pela interface; a customização continua sendo feita por arquivo de configuração da instância.

Confundir os dois seria o pior resultado possível deste corte: economizar meio dia de tela e perder a característica que justifica o projeto inteiro.

---

## 4. Impacto no plano de etapas

| Etapa | Impacto |
|---|---|
| E01 | **Nenhum.** Schema completo permanece. |
| E05 | Storage de mídia continua (anexo do chat depende dele). |
| E10 | Design tokens permanecem; some a tela de preferências de aparência. |
| E11 | Composer mantém anexo por upload; some o atalho para o Banco de Arquivos. |
| E13 | Reduzido: some Banco de Arquivos. Permanecem lembretes, mensagens programadas, mensagens rápidas, equipe. |
| **E15** (Campanhas) | **Removida da primeira entrega.** |
| **E16** (Relatórios) | **Removida da primeira entrega.** |
| **Nova: E15'** | Dashboard consolidada (visão única). ~1 dia. |

### Folga resultante

O plano de 14 etapas somava ~13 dias contra 29 corridos. Com os cortes:

- **−1 dia** em E13 (Banco de Arquivos)
- **−0,5 dia** em E10 (tela de preferências)
- **+1 dia** com a Dashboard consolidada (E15')
- **Campanhas e Relatórios já eram opcionais** — não entravam na conta dos 13 dias

Saldo: **~12,5 dias de trabalho**, com folga confortável. Isso não é convite para reabsorver escopo. A recomendação do `08-plano-execucao.md` §1 continua valendo: segure a folga como contingência genuína e decida em **18/08** se sobra espaço para algo mais.

O melhor uso desta folga não é uma feature adicional — é **hardening**. O cliente veio de um CRM que cai; a promessa que fechou o contrato é estabilidade, não quantidade de abas.

---

## 5. Como comunicar ao cliente

O corte é uma boa notícia se apresentado como sequenciamento, não como redução:

> "A primeira entrega foca no que vocês usam todos os dias: atendimento, leads, automação e a visão do gestor. Relatórios detalhados, campanhas e o banco de arquivos entram logo em seguida, já com o sistema em produção e com o feedback de vocês sobre o que realmente importa em cada um."

Isso é verdadeiro — construir Relatórios *depois* de ver o cliente usando o sistema produz relatórios melhores do que construí-los a partir de "mostrar milhares de informações sobre tudo".
