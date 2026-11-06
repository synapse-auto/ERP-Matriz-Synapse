# Etapa 2 — Dashboard, alternativa A

## 1. Estado

**Verificado:** continuação da branch `codex/frontend-alternativa-a` e do PR #350, ainda em rascunho. A Etapa 1 permanece no commit `8e7c102`; esta etapa tem commit próprio. Nenhuma implantação, ativação de flag, alteração de backend, migration ou workflow foi feita.

**Verificado:** o responsável reforçou que deseja aparência idêntica ao ZIP. A implementação desta etapa aproxima a geometria dos elementos com contrato disponível, mas **não está certificada como idêntica nem concluída em paridade integral**. Os limites técnicos e visuais abaixo fazem parte da entrega; não foram escondidos por dados fictícios.

## 2. Implementação e critérios

| Critério | Evidência |
|---|---|
| Legado como padrão | **Verificado:** chave independente `dashboard_layout_v2`, lida em `frontend/src/components/dashboard/pagina-dashboard.tsx:139`; sem ela, classe e composição legadas permanecem. A flag de Atendimentos não habilita Dashboard. |
| Uma consulta de dados | **Verificado:** `pagina-dashboard.tsx:164` continua usando `useVisaoGeralDashboard`; endpoint único em `frontend/src/lib/dashboard/api.ts:22`. Não há polling novo nem consulta por KPI. |
| Preservação de filtro e modo | **Verificado:** testes alternam a flag preservando meses, modo expandido e filtro enviado ao hook. Os estados continuam pertencendo à página, não à apresentação. |
| Filtros reais no celular | **Verificado:** botão com `aria-expanded` controla `dashboard-filtros`; em v2 abre ano, meses, modo e originação. O legado mantém o comportamento anterior. Não abre um aviso de “somente computador” em v2. |
| Período diário coerente | **Verificado:** um recorte com início/fim completos não dispara mais o alerta de meses vazios. O contrato permite datas em lugar de meses (`frontend/src/lib/dashboard/use-dashboard.ts:12`). |
| Cabeçalho e rolagem | **Verificado:** raiz v2 limita a altura; `dashboard-corpo` rola por dentro. O cabeçalho fica fora desse contêiner. A tabela e as barras de horário mantêm rolagem horizontal local. |
| Geometria da referência | **Verificado:** fonte Geist carregada pelo Next; título 30px; moldura 24px no tema de referência; cartões 18px; sidebar retraída com slot 88px. Teste Chromium em 1920×1080 confere esses valores. |
| Layout das métricas disponíveis | **Verificado:** grade de quatro colunas na largura nativa, com posições explícitas dos sete KPIs existentes; cartão de Atendimentos ocupa duas colunas. Não redistribui a duração para o espaço de uma métrica ausente. |
| Sidebar reversível | **Verificado:** `frontend/src/components/shell/shell-com-sidebar.tsx:31` limita a geometria nova ao Dashboard com a flag; pin e hover continuam usando o componente existente. Teste verifica retorno a Atendimentos com o slot original de 76px. |
| Marca da instância | **Verificado:** todas as cores de produção continuam por tokens. `tema-referencia.json` é um exemplo de configuração usado somente em teste/captura; não foi aplicado a cliente. |
| Dados e permissões | **Verificado:** sem alterações em `frontend/src/lib/dashboard/api.ts`, `types.ts`, `use-dashboard.ts` ou na proteção da rota `dashboard.ver`. |

**Verificado:** o fundo decorativo vem do JPG fornecido no ZIP, copiado sem edição para `frontend/public/images/frontend-v2-canvas.jpg`. SHA-256: `E09140147AF78D0DAD0824730ED7697587E9D141E337B20ABC7DB854738320B9`. A fonte é servida pelo build, sem CDN de fonte no runtime. Fonte, fundo e geometria novos só são aplicados no Dashboard v2.

## 3. Lacunas confirmadas do contrato

**Verificado — escopo da conclusão:** “sem dado no backend” nesta tabela significa que o contrato consumido pelo Dashboard não entrega a métrica, não que inexista informação bruta em todo o banco. O payload foi conferido em `backend/crm-relatorios/src/main/java/com/synapse/crm/relatorios/domain/dashboard/VisaoGeralDashboard.java:11` e em `frontend/src/lib/dashboard/types.ts:8`. Não foram criados agregados client-side para preencher lacunas.

| Elemento do ZIP | Evidência no ZIP | Evidência do contrato / veredito |
|---|---|---|
| 1ª resposta humana | `../mockup/fonte/DashboardV2.dc.html:536` | **Verificado:** não há KPI equivalente no DTO; sem dado no backend deste contrato. |
| NPS | `DashboardV2.dc.html:548` | **Verificado:** DTO expõe avaliação/satisfação, não NPS; sem dado no backend deste contrato. Não reinterpretar automaticamente CSAT como NPS. |
| Transferências IA → humano | `DashboardV2.dc.html:557` | **Verificado:** resolução por IA não fornece contagem de transferências; sem dado no backend deste contrato. |
| Leads parados por mais de dois meses | `DashboardV2.dc.html:560` | **Verificado:** sem agregado equivalente; sem dado no backend deste contrato. |
| Aguardando primeira resposta e esquecidos na faixa Agora | `DashboardV2.dc.html:716` | **Verificado:** exclusão explicitada pelo próprio DTO Java, linhas 70–74. Sem dado no backend deste contrato. |
| Conversão e primeira resposta por atendente | `DashboardV2.dc.html:240` | **Verificado:** `AtendenteDesempenho` não entrega essas colunas; justificativa no DTO Java, linhas 151–159. |
| Tempo parado por etapa | `DashboardV2.dc.html:192` | **Verificado:** `EtapaDoFunil` entrega quantidade/passagem, sem tempo parado; DTO Java linha 109. |

**Verificado:** a captura nativa mostra **onze KPIs**, não oito. A contagem inicial em uma atualização da sessão estava errada porque a imagem de 1440px cortava a exportação fixa de 1920px. O HTML declara onze em `DashboardV2.dc.html:133`. A grade reserva as posições das métricas existentes; os espaços sem dado ficam vazios.

## 4. Diferenças visuais restantes

**Verificado:** busca global e sino de notificações do topo não foram adicionados como controles sem comportamento. O menu respeita capacidades e flags reais; a quantidade de itens da fixture não é a do mockup. Os glifos continuam no conjunto Lucide já adotado pelo produto, enquanto a referência usa Remix Icon.

**Verificado:** o funil e a equipe mantêm as colunas reais. O donut mantém a semântica de satisfação da implementação existente. As quatro métricas ausentes, os dois contadores ausentes e os campos das tabelas impedem igualdade completa com o ZIP. Nenhum desses elementos foi preenchido com números do protótipo.

**Verificado:** as capturas padrão mostram os tokens atuais do repositório. A captura com `tema-referencia.json` permite comparar a paleta pretendida sem alterar a identidade dos clientes. O arquivo não é configuração ativa. Texto secundário usa o token de contraste legível da instância; não replica automaticamente os pares fracos do mockup que falharam em AA na análise anterior.

**Não verificado:** comparação automatizada pixel a pixel com dados e navegador idênticos; todos os menus, tooltips e estados possíveis; configurações efetivas das três instâncias. Não há aceite de paridade visual integral registrado.

## 5. Testes e capturas

**Verificado:** testes novos cobrem ausência da flag específica, preservação de filtro/modo, filtros móveis, recorte Hoje sem falso alerta, erro/carregamento e pin/retração da sidebar. A fixture comum foi extraída para reutilizar o mesmo mecanismo local nas duas telas. Não há fixture importada por código de produção.

**Verificado:** rodada de navegador com Atendimentos e Dashboard passou 15 cenários antes de acrescentar a captura nativa com tema de referência; os oito cenários atuais de Dashboard também passaram após ajustar a rolagem. A rodada final focada de Vitest aprovou 94 testes em 8 arquivos; typecheck e build passaram; lint terminou com 0 erros e 4 avisos existentes. O recorte móvel e o legado foram retestados após restringir a asserção de alerta ao Dashboard (3/3 aprovados). A rodada completa final de navegador aprovou 16/16 cenários. O ciclo Maven foi iniciado com JDK 21 e Docker disponível; seu resultado final consta no relatório da tarefa e no PR. Não se deve interpretar uma execução anterior como prova de modificação posterior.

**Verificado — reprodução**, a partir de `frontend/`:

```text
npm run typecheck
npm run lint
npm run build
npm test -- --maxWorkers=2 src/components/dashboard src/components/shell src/lib/dashboard src/lib/config/temas-disbrel.test.ts
npx playwright test --config=playwright.frontend-v2.config.ts
```

**Verificado:** os testes de navegador usam apenas loopback, payloads sintéticos e handshake simulado; não certificam autorização ou provedores reais no backend. Alterar modo não dispara outra leitura de dados; alterar período usa o endpoint existente com os parâmetros reais.

**Verificado — correção após CI:** a run `42276955163` revelou uma suíte que o recorte focado não exercitava: `src/app/(shell)/layout.test.tsx` importava o novo carregador Geist sem simulação do transformador do Next. O teste do layout recebeu os mesmos limites de fonte e flags usados nos testes do shell; seus dois cenários passaram. A correção afeta apenas preparação de teste. Os resultados da suíte completa e do novo CI são registrados no PR e no relatório final, com o SHA correspondente.

| Captura | Link |
|---|---|
| Referência completa do ZIP, 1920×1080 | [Mockup](mockup-dashboard-1920.png) |
| Implementação com paleta de referência | [1920×1080](dashboard-v2-tema-referencia-1920.png) |
| Tema padrão do repositório | [1920](dashboard-v2-1920.png), [1440](dashboard-v2-1440.png), [1024](dashboard-v2-1024.png) |
| Celular e filtros reais | [390×844](dashboard-v2-390.png), [filtros](dashboard-v2-filtros-mobile.png) |
| Vazio e tema escuro sintético | [Vazio](dashboard-v2-vazio.png), [escuro](dashboard-v2-escuro-1440.png) |

**Verificado:** [contraste calculado da configuração de referência](contraste.md): os seis pares medidos atendem AA para texto normal (mínimo 4,755:1). Essa medição não certifica a tela inteira.

## 6. Publicação e reversão

**Verificado:** nenhuma flag foi ativada, nenhuma variável foi adicionada ao Dokploy e nenhum acesso ao VPS foi feito. O PR segue em rascunho. O CI publica imagens apenas na principal; publicar esta branch não é implantação.

**Inferido — reversão:** manter/remover `dashboard_layout_v2` da lista de habilitadas e recarregar para reler a configuração. Atendimentos tem chave independente. O commit desta etapa pode ser revertido sem alteração de schema ou dados.

## 7. Próximo passo para igualdade integral

**Inferido — proposta:** aprovar contratos e definições das métricas ausentes, busca global e notificações em trabalho separado; depois conectar cada elemento e repetir a comparação em todas as resoluções e estados. O default permanece não inventar dados, não ativar clientes e não afirmar “idêntico” enquanto as diferenças acima existirem.

**Verificado — ferramentas:** Playwright e documentação local do Next foram consultados. A skill clean-code não consta do catálogo disponível nem foi encontrada na busca nas skills instaladas. Nenhuma biblioteca nova de gráficos ou de estado foi adicionada.
