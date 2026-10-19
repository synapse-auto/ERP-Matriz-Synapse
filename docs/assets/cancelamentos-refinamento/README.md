# Cancelamentos — refinamento visual e de linguagem

Aplicação Next/Spring reais em localhost, com adaptadores de produção e PostgreSQL/Redis
descartáveis. Os 32 registros são sintéticos e foram gravados pelo endpoint autenticado real;
nenhuma consulta ou alteração em produção. O preview e seus controles não são entregues ao produto.
O reinício local para carregar o catálogo novo recriou os mesmos dados sintéticos; o horário de
registro nas imagens é o relógio real de cada preparação, não valor copiado para imitar igualdade.

Fonte original **Inter**, document.fonts.ready concluído, font-family computado conferido,
DPR/zoom 1 e mesmo tema claro padrão. Não foi usada reprodução HTML nem alteração tipográfica.
A marca é a configuração do fixture, não uma condição por cliente no componente.

## Antes e depois

| Viewport | Antes | Depois |
|---|---|---|
| 1920 × 1080 | [Antes](antes-1920.png) | [Depois](depois-1920.png) |
| 1280 × 900 | [Antes](antes-1280.png) | [Depois](depois-1280.png) |
| 390 × 844 | [Antes](antes-390.png) | [Depois](depois-390.png) |

Teste adicional em [768 × 1024](depois-768.png). Comparação intermediária:
[primeira rodada em 1280](rodada-1-1280.png), [segunda rodada em 1280](rodada-2-1280.png)
e [tabela ainda espremida em 768](rodada-2-768.png). Após a inspeção, as prévias longas foram
limitadas e a faixa de tablet passou a usar cartões; não foi encerrado só com build aprovado.

## Cenários

- [Filtros complementares](mais-filtros-1280.png): recolhem sem perder o rascunho. Medição no
  navegador: zero consultas ao digitar/recolher; uma consulta ao aplicar o filtro.
- Busca real sem resultados: [1280](busca-vazia-1280.png), [390](busca-vazia-390.png).
- [Vazio no período](vazio-periodo-1280.png): base agendamento, sem registros nas datas consultadas;
  não significa ausência de cancelamentos em toda a instância.
- Segunda página real com sete registros: [paginação](paginacao-1280.png).
- Detalhes longos: [desktop](detalhe-longo-1280.png), [celular](detalhe-longo-390.png).
- [Campos ausentes](detalhe-ausencias-390.png): ausência explícita, sem data inventada.
- [Loading](carregando-1280.png), [erro](erro-1280.png),
  [página posterior vazia](pagina-posterior-vazia-1280.png).

Para loading, erro 503 e página posterior vazia, o teste injeta somente o estado de transporte
ou uma página vazia no navegador. São provas da interface/recuperação, não falhas reais do backend
nem registros fictícios em produção. Demais dados e vazios foram consultados no banco local real.
Após remover a falha, Tentar novamente recupera a lista sem F5 ou mutações automáticas.

Validados foco/Enter/Escape no detalhe, retorno ao botão, Tab/Shift+Tab na ajuda e descrição
acessível, ausência de scroll horizontal nas larguras capturadas e conteúdo completo do detalhe.
No modal, o corpo rola sem levar o botão Fechar para fora da viewport.
Não equivale a auditoria WCAG completa nem homologação com dados/n8n de produção. Resultados dos
testes e runs de CI constam no relatório da PR específica deste refinamento.
