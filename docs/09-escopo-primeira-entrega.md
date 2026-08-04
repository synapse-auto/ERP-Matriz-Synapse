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

## 2. O que permanece, com ajuste

### Dashboard — visão única consolidada
A aba **existe**, sem as três sub-abas. Uma tela só, com os indicadores principais: atendimentos do dia, leads por etapa, desempenho por atendente e filtro de período. Atende `RF-CRM-31` e `RF-CRM-33`; `RF-CRM-32` ("milhares de informações") fica explicitamente para a fase 2.

### Anexos no chat — upload direto
O composer mantém o botão de anexo com upload do computador (`RF-CRM-09`, `RF-CRM-68`). O que sai é o **repositório compartilhado** de arquivos frequentes (`RF-CRM-13/55/56`). O atendente continua enviando foto e orçamento — apenas sem a biblioteca reutilizável.

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
