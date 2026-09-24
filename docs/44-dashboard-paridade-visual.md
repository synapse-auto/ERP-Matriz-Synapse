# Dashboard — paridade visual com o modelo aprovado (24/09/2026)

Referência visual fornecida: `CRM Estrutural Vidros - Sistema Completo.html`, nos modos
Compacta e Expandida. Os valores e séries desse HTML são ilustrativos e não entram no CRM.

Capturas da aplicação local com banco de desenvolvimento sem atendimentos/leads, após duas
rodadas de comparação visual:

- [Compacta · 1920px](assets/dashboard-paridade/final-compacta-1920.png)
- [Expandida · 1920px](assets/dashboard-paridade/final-expandida-1920.png)
- [Compacta · 1280px](assets/dashboard-paridade/final-compacta-1280.png)
- [Expandida · 1280px](assets/dashboard-paridade/final-expandida-1280.png)

## Antes → depois

| Área | Antes | Depois |
| --- | --- | --- |
| Sidebar | Retraída por padrão na tela do Dashboard | Aberta por padrão somente nesta rota, mantendo o botão de fixação e o comportamento das outras telas |
| Cabeçalho e abas | Dentro do canvas, sem faixa branca própria | Faixa branca única com título, descrição e abas sublinhadas |
| Filtros | Rótulos empilhados e faixa alta | Ano, meses, modo e originação na mesma faixa; quebram linha quando necessário |
| Modos | Um único formato de card | Controle acessível Compacta/Expandida; grade de 4/3 colunas conforme a largura |
| Cards | Mesma altura e densidade nos dois modos | Compacta curta; Expandida com hierarquia e área reservada à série |
| Faixa AGORA e painéis inferiores | Dados reais já existentes | Preservados, sem polling nem chamadas por card |

## Série mensal em desenvolvimento na PR #207

`GET /api/v1/dashboard/visao-geral` passa a fornecer `seriesMensais`, com um ponto por mês
selecionado. Os sete indicadores já existentes usam agregados reais; `null` representa média
sem amostra ou mês futuro, enquanto `0` representa contagem observada. `parcial` identifica o
mês corrente ou um recorte de datas que não cobre o mês inteiro no fuso configurado. O mesmo payload alimenta as barras de Compacta e Expandida,
sem requisições por card e sem copiar os valores ilustrativos do HTML.

| Série | Fonte e fórmula | Unidade e recorte |
| --- | --- | --- |
| Atendimentos | `count(atendimento.id)` por `iniciado_em` | atendimentos/mês selecionado |
| Novos leads | `count(lead.id)` por `criado_em` | leads/mês selecionado |
| Duração média | média de `finalizado_em - iniciado_em` entre atendimentos finalizados, agrupados pelo mês de início | segundos; indisponível sem finalizados |
| Vendas fechadas | primeira transição `ETAPA_ALTERADA` para `GANHO` de cada lead no recorte, com a mesma coorte opcional do KPI | vendas/mês do evento |
| Conversão | vendas mensais / leads criados no mês; com coorte de originação, vendas mensais / leads da coorte | percentual; indisponível sem denominador |
| Avaliação média | média de `avaliacao.nota`, excluído `ADMINISTRADOR` | pontos de 0 a 10; indisponível sem avaliações |
| Resolução por IA | finalizados sem evento de transferência / finalizados no mês | percentual; indisponível sem finalizados |

Os limites mensais são convertidos pelo fuso configurado na instância. `ano`, `meses`,
`inicio`/`fim` e `origemInicio`/`origemFim` continuam com a semântica anterior. O recorte de
originação só se aplica a vendas/conversão/funil; não muda as demais séries. A autorização do
endpoint permanece de gestão; o cache mantém a chave por usuário, papel, filtros e fuso e foi
versionado para evitar desserializar o contrato anterior.

O teste de custo local com Postgres/Redis Testcontainers registra **20 consultas SQL** na
leitura fria e **4** na leitura de cache — a mesma contagem anterior às séries. Quatro
agregações reaproveitam a leitura dos totais via `ROLLUP`; o ranking de vendas e a série
mensal vêm da mesma leitura da timeline. Isso não prova custo constante de CPU: o banco de
teste é pequeno. A PR permanece em draft até obter plano/latência com dados representativos.

## Indicadores ainda não entregues

As definições abaixo foram confirmadas pelo produto, mas ainda não há agregação segura nem
UI concluída para elas. Não exibir zero como se fosse uma medição.

| Indicador | Regra aprovada | Fonte candidata / risco pendente |
| --- | --- | --- |
| 1ª resposta humana | Média entre exigência de ação humana (transferência IA→humano ou entrada direta em Pendentes) e primeira mensagem manual. Casos sem resposta ficam fora da média; reatribuição humana não reinicia relógio. | `evento_timeline`, `atendimento`, `mensagem`; precisa vincular com segurança o primeiro marco e a resposta em cada ciclo. |
| Leads parados +2 meses | Abertos cuja última mensagem do cliente, resposta humana ou mudança manual de etapa ocorreu há mais de dois meses corridos; ignorar automação/status técnico, excluir finalizados e perdidos. | `mensagem`, `evento_timeline`, `atendimento` e `etapa_atendimento`; varredura histórica pode ser cara e exige plano em volume real. |
| Transferências IA→humano | Contar eventos efetivos distintos por ID; um atendimento pode contar novamente após voltar à IA. Nunca contar humano→humano. | Eventos `ATENDIMENTO_TRANSFERIDO` e eventual transferência por envio, com classificação da origem. |
| NPS | Indisponível até existir pergunta explícita de recomendação; então `% notas 9–10 − % notas 0–6`. | `avaliacao.nota` atual não prova essa pergunta. A avaliação média 0–10 permanece separada. |

As abas Operacional, Comercial e IA & Automação permanecem desabilitadas e marcadas “Em
breve”, conforme o escopo vigente.

## Operação

O controle de modo altera apenas o layout no navegador; ano, meses e originação continuam
usando a mesma consulta TanStack Query e o mesmo cache do servidor. O catálogo-base
`textos.json` contém os rótulos dos modos, e `dashboard.modos` tem defaults no schema do
frontend para configurações antigas já salvas. Não há variável de deploy nova.
