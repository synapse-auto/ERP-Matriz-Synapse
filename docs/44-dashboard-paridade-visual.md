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

## Lacunas deliberadas do contrato

`GET /api/v1/dashboard/visao-geral` não retorna série mensal de nenhum KPI. Por isso a
área correspondente no modo Expandida informa “Série mensal indisponível”, em vez de
simular barras. Não se deriva uma série de um total agregado nem se fazem doze requisições
por mês: o endpoint já custava 20 consultas SQL na leitura fria conforme
`docs/43-dashboard-performance.md`. Acrescentar agregações exige desenho e medição em base
representativa, com `pg_stat_statements`, antes de colocar mais carga no PostgreSQL.

O modelo também mostra primeira resposta humana, NPS, transferências IA→humano e leads
parados por mais de dois meses. Esses indicadores não têm todos critério de negócio e fonte
no contrato atual. Eles não aparecem com números fabricados. As abas Operacional, Comercial
e IA & Automação permanecem desabilitadas e marcadas “Em breve”, conforme o escopo vigente.

## Operação

O controle de modo altera apenas o layout no navegador; ano, meses e originação continuam
usando a mesma consulta TanStack Query e o mesmo cache do servidor. O catálogo-base
`textos.json` ganhou os rótulos dos modos; não houve mudança de endpoint, consulta,
autenticação ou configuração de deploy. `dashboard.modos` tem defaults no schema do
frontend para configurações antigas já salvas.
