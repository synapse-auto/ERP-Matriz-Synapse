> Registro da fase visual anterior, feita antes da localização do repositório. O responsável autorizou depois a implementação da alternativa A. O estado atual, as referências de código, as validações e as limitações estão em [entrega-alternativa-a.md](entrega-alternativa-a.md). As observações abaixo sobre ausência de repositório são históricas.

# Plano visual — refatoração do frontend Synapse

Data: 06/11/2026. Estado: estudo do protótipo; integração ainda não verificada.

**Verificado — ambiente:** a pasta de trabalho não contém repositório Git; `git status --short` e `git remote -v` retornaram “not a git repository”. O responsável orientou usar o ZIP fornecido como base. Não foi inicializado um repositório substituto.

**Não verificado:** numeração E234, próximo número livre, código atual, origem remota, branch, commit e publicação. Nenhum número de prompt foi reservado. A entrega local não equivale à entrega da branch solicitada originalmente.

**Verificado — fonte:** ZIP `Protótipo v2 (clique entre telas)-html.zip`, fornecido pelo responsável. Os nomes e valores de demonstração são dados fictícios, não requisitos de negócio. Os quatro HTML pequenos em `mockup/fonte/` são cópias de referência, com apenas espaços ao fim de linhas normalizados, sem runtime e sem assets; não constituem implementação de tela. Para executar o protótipo, usar o ZIP completo.

**Convenção de evidência:** nesta documentação, `A:linha` significa `mockup/fonte/AtendimentosV2.dc.html:linha`; `D:linha`, `mockup/fonte/DashboardV2.dc.html:linha`; `S:linha`, `mockup/fonte/SidebarV2.dc.html:linha`; `M:linha`, `mockup/fonte/Main.dc.html:linha`. São referências ao protótipo, nunca ao produto atual. “Inferido — proposta” indica decisão de projeto a validar. “Não verificado” indica ausência de prova; não significa ausência de funcionalidade no produto.

## 1. Resumo executivo

**Verificado — A:28, A:31, A:33; D:27, D:29:** a referência organiza o CRM com navegação lateral, superfícies arredondadas, lista de conversas e painéis de conteúdo.

**Inferido — proposta:** começar por Atendimentos, separar apresentação de dados e preservar os contratos e o estado existentes após sua identificação no código real. Dashboard será uma segunda etapa.

**Verificado — A:28; D:27; capturas:** o canvas é fixo em 1920×1080 e fica cortado em 1440×900 e em 390×844. Reproduzir esse comportamento não atende à proposta responsiva.

**Inferido — proposta:** adotar convivência por instância (alternativa A), inicialmente desativada, sem duplicar clientes de dados. Risco alto de integração enquanto o produto atual não for auditado; risco visual conhecido de corte e contraste insuficiente.

**Não verificado:** compatibilidade com as três instâncias, permissões, canais e tempo real. Não há autorização técnica de produção implícita neste estudo.

## 2. Lacunas entre mockup e sistema atual

**Não verificado — todas as células relativas ao produto:** o ZIP não fornece o backend nem o frontend de produção. “Sem dado no backend” exige leitura do backend; nenhum item pode receber esse diagnóstico confirmado nesta sessão. Até a comprovação, os elementos abaixo ficam fora do escopo de integração e não recebem valores substitutos fictícios.

| Elemento / referência verificada no mockup | Existe no sistema atual? | Origem real do dado | Veredito provisório |
|---|---|---|---|
| Navegação lateral recolhida/expandida — S:34, S:223, S:309 | Não verificado | Não verificado | Inferido: ajustar apresentação após inventário |
| Lista, busca por cliente/protocolo e filtros — A:38, A:44, A:63 | Não verificado | Endpoint/campos não verificados | Inferido: reaproveitar dados somente após prova |
| Abas, seleção e vazio filtrado — A:96, A:621, A:638 | Não verificado | Contadores e semântica não verificados | Inferido: ajustar; sem criar nova contagem local de conjunto paginado |
| Setor, atendente, espera e não lidas — A:77, A:78, A:621 | Não verificado | Relógio, vínculos e permissões não verificados | Inferido: bloquear badges sem fonte comprovada |
| Cabeçalho e busca na conversa — A:264 | Não verificado | Identidade e histórico não verificados | Inferido: adaptar somente a apresentação |
| Timeline e representação de áudio — A:287, A:288, A:289 | Não verificado | Mensagens, anexos e entrega não verificados | Inferido: preservar renderizadores reais quando identificados |
| Composer, áudio e anexos — A:309, A:311 | Não verificado | Envio/upload e capacidades não verificados | Inferido: adaptar por último, após trabalho concorrente |
| Resumo, notas e arquivos do lead — A:383, A:390, A:414 | Não verificado | Não verificado | Inferido: ocultar se não houver dado/permissão |
| Pedidos e histórico financeiro do lead — A:679 | Não verificado | Não verificado | Inferido: lacuna prioritária; excluir até prova |
| Equipe e indicadores ao vivo — A:107, A:112; D:118, D:124 | Não verificado | Agregados e eventos não verificados | Inferido: lacuna prioritária; não simular tempo real |
| Funil, passagem e tempo parado — D:192 | Não verificado | Histórico de etapas não verificado | Inferido: lacuna prioritária; excluir até prova |
| Vendas, conversão e ranking — D:240, D:242 | Não verificado | Agregados comerciais não verificados | Inferido: lacuna prioritária; excluir até prova |
| Avaliações e distribuição — D:208, D:310 | Não verificado | Escala, período e denominador não verificados | Inferido: validar semântica antes de exibir |
| Resolução por IA e transferências — D:554, D:557 | Não verificado | Eventos e critérios não verificados | Inferido: lacuna prioritária; excluir até prova |
| Mensagens por hora — D:261 | Não verificado | Agregação e fuso não verificados | Inferido: não calcular sobre página parcial |
| Chat interno — A:539 | Não verificado | Participantes, mensagens e acesso não verificados | Inferido: preservar fluxo atual após auditoria |

**Verificado — A:641, A:1095:** o envio de texto altera uma coleção local; o anexo de foto insere uma mensagem de demonstração. Essas rotinas não provam integração com API ou upload.

**Não verificado:** as cinco maiores candidatas a “sem dado no backend” são histórico financeiro do lead; funil com tempo por etapa; vendas/conversão por atendente; resolução e transferência de IA; presença/contadores agregados ao vivo. Não foi comprovada a ausência de nenhuma delas.

## 3. Tokens, tema e contraste

**Não verificado:** nenhum token de produção foi identificado. Não foram lidos arquivos de configuração de tema, textos, logo nem os handlers de leitura/escrita de marca citados no prompt. Não há evidência do caminho de configuração até CSS, dos tokens da sidebar ou do comportamento de valores ausentes. O enunciado sobre ausência de validação de schema não foi confirmado por código.

**Inferido — proposta:** os papéis abaixo são descrições, não novos identificadores CSS. Nomes de tokens, chaves e arquivos de implementação só serão definidos após busca no repositório. Nenhum hex deverá ser aplicado diretamente aos componentes.

| Papel visual | Valor observado / evidência verificada | Token existente | Regra proposta, inferida |
|---|---|---|---|
| Fundo geral / conteúdo | `#E9EBEF` / `#EEF0F3` — A:20, A:31 | Não verificado | Resolver pelo tema da instância |
| Superfície / borda | `#FFFFFF` / `#E8E9EC` — A:33 | Não verificado | Preservar o valor efetivo atual ao adicionar fallback |
| Texto principal | `#0E1220` — A:28 | Não verificado | Usar papel semântico; medir em cada fundo |
| Texto secundário | `#8B91A0` / `#9CA3B0` — A:96, A:104 | Não verificado | Corrigir pares que falham antes de aceitar a referência |
| Ação principal / hover | `#1F5FD6` / `#1A50B8` — A:40 | Não verificado | Herança da marca e contraste obrigatório |
| Perigo | `#C8352A` sobre `#FDEDEB` — A:77 | Não verificado | Não depender só de cor |
| Estado positivo | `#12855A` sobre `#E7F6EE` — A:107 | Não verificado | Ajustar contraste de texto pequeno |
| Fonte | Geist, pesos 400–700; Geist Mono 400–500 — A:17 | Não verificado | Manter fonte atual por padrão até decisão explícita |
| Espaçamento | gap/padding 10px; margens 10px — A:31 | Não verificado | Converter em escala existente, sem alterar globalmente |
| Raios | 24px na moldura, 18px nos painéis, 11px em botão — A:31, A:33, A:37 | Não verificado | Aplicar apenas no novo layout validado |
| Sombras | painel 0 1px 2px com alfa .04; moldura 0 30px 80px -40px com alfa .5 — A:31, A:33 | Não verificado | Centralizar no tema; avaliar custo gráfico |
| Sidebar | ícone 44px, raio 14px; linha 38px e largura variável — S:27, S:223 | Não verificado | Mapear todos os estados antes de migrar |

**Inferido — proposta:** o valor padrão de qualquer token novo deve ser exatamente o valor efetivo atual de cada instância, identificado por leitura e captura. Não é seguro escolher os valores do mockup como fallback global. Os valores concretos de fallback ficam não verificados até esse inventário; nomes de novos tokens não foram inventados.

**Verificado — cálculo executado com Node:** luminância sRGB linearizada: canal ≤0,04045 dividido por 12,92; demais canais elevados a 2,4 após `(canal+0,055)/1,055`; luminância ponderada por 0,2126/0,7152/0,0722. Razão `(Lclara+0,05)/(Lescura+0,05)`. Avaliação abaixo considera texto normal, limiar 4,5:1.

| Primeiro plano / fundo, observados nas referências acima | Razão calculada | AA texto normal |
|---|---:|---|
| `#0E1220` / `#FFFFFF` | 18,649:1 | Verificado: atende |
| `#3A4354` / `#FFFFFF` — A:37 | 9,949:1 | Verificado: atende |
| `#8B91A0` / `#FFFFFF` | 3,155:1 | Verificado: falha |
| `#9CA3B0` / `#FFFFFF` | 2,536:1 | Verificado: falha |
| `#FFFFFF` / `#1F5FD6` | 5,728:1 | Verificado: atende |
| `#1F5FD6` / `#EDF2FD` — A:50 | 5,106:1 | Verificado: atende |
| `#C8352A` / `#FDEDEB` | 4,624:1 | Verificado: atende |
| `#12855A` / `#E7F6EE` | 4,155:1 | Verificado: falha |

**Não verificado:** cobertura de todos os pares, transparências, estados de foco/seleção e modo escuro. Os cálculos acima não certificam a tela inteira.

**Verificado — busca:** não houve ocorrências para `dark`, `prefers-color` ou `@media` em A, D, S e M. **Não verificado:** existência de modo escuro em outras telas do ZIP. **Inferido — proposta:** projetar modo escuro após inventariar a implementação real; não inverter cores automaticamente nem apresentar uma captura clara como prova de modo escuro.

## 4. Convivência: A × B

**Inferido — propostas, sem implementação:**

| Alternativa | Custo / duplicação | Risco | Reversão |
|---|---|---|---|
| A — selecionar layout por instância, antigo preservado | Duas composições visuais temporárias; uma única camada de dados e regras. Quantidade de arquivos e linhas: não verificada, não estimável pelo ZIP | Menor exposição, mas exige testar ambos e impedir assinaturas duplicadas | Retornar à composição antiga pelo mecanismo real, quando identificado |
| B — substituir diretamente e validar em teste | Uma composição final; alterações e tamanho do diff não verificados | Maior risco de afetar instâncias que compartilhem a mesma imagem; marca/canais podem divergir | Reverter commit e imagem pelo processo real, ainda não verificado |

**Inferido — recomendação:** A, desativada por padrão e liberada apenas em teste até completar a matriz por instância. A seleção deve envolver a apresentação, sem montar dois consumidores de mensagens simultaneamente. A decisão final permanece com o responsável. Nome da flag e mecanismo de persistência: não verificados; não criar um identificador especulativo.

## 5. Arquitetura de apresentação e dados

**Inferido — proposta:** organizar responsabilidades compartilhadas de superfície, cabeçalho, botão de ícone, badge, abas, estado vazio/erro/carregando e diálogo. Separar a composição de lista, conversa, composer e painel do lead. Nomes formais e diretórios: não verificados; serão escolhidos somente após leitura das convenções reais.

**Inferido — proposta:** a camada de apresentação recebe dados e callbacks tipados. A camada real existente deve continuar responsável por consultas, mutações, reconexão, entrega e permissões. Não copiar a coleção de demonstração de A:641 nem adicionar outro cache para as mesmas entidades. Manter local apenas abertura de painéis, foco e estado transitório de interação; preservar o mecanismo real de rascunho quando identificado.

**Não verificado:** componentes, hooks, rotas, endpoints, eventos, papéis e chaves de tradução do produto. Os imports do protótipo (A:29; D:28) não demonstram que esses componentes existam em produção.

## 6. Etapa 1 — Atendimentos

**Inferido — proposta para todos os passos abaixo:** os arquivos prováveis são descritos por responsabilidade; nenhum caminho de código foi inventado. “Pronto” significa critério futuro, não teste executado. Cada alteração visual deve ser um commit reversível, sem alterar contratos. Os passos 2–7 dependem do inventário 1, mas devem evitar dependências acidentais entre apresentações.

| Ordem / escopo | Arquivos prováveis, ainda não verificados | Critério de pronto | Testes futuros | Reversão | Pode ir sozinho a produção? |
|---|---|---|---|---|---|
| 1. Auditar produto e registrar baseline | Documentação; leitura das entradas de Atendimentos, configuração, dados e permissões | Evidências arquivo:linha para cada dependência; matriz por instância; capturas atuais e cenário de teste | Subir frontend com seed autorizado; registrar resultados reais | Reverter documentação | Inferido: sim, somente documentação |
| 2. Tema e estrutura adaptável | Camada real de tema e composição, a localizar | Modo antigo idêntico; novo layout utilizável em 1440×900 e 390×844; fallback preserva marca | Componente, contraste de todos os pares, snapshots visuais e teclado | Reverter commit; manter layout antigo | Inferido: somente desativado e após matriz de regressão |
| 3. Lista, filtros, abas e badges | Apresentação e seletor de dados existentes, a localizar | Reusar semântica e paginação reais; status/setor/atendente sem falsa equivalência; seleção estável | Busca, paginação, troca de filtro, não lidas, permissão; nenhuma chamada extra por linha | Retornar só a lista anterior | Inferido: apenas após prova de paridade |
| 4. Cabeçalho e timeline | Renderizadores reais de conversa e mensagens, a localizar | Identidade, ações autorizadas, ordenação e entrega preservadas; marcador de template preservado | Histórico paginado, mídia, falha/reenvio, finalizado, grupo, reconexão | Restaurar composição anterior sem modificar entidades | Inferido: somente depois da compatibilidade com trabalho de backend |
| 5. Painel lateral do lead | Apresentação de detalhes existente, a localizar | Reusar campos comprovados; esconder campos sem dado; abrir/fechar sem perder seleção/foco | Papéis, vazios, erro, edição concorrente, mobile | Retirar painel novo; manter detalhe anterior | Inferido: sim, após validação de leitura/permissões |
| 6. Composer e ações | Composer real e pontos de capacidade, a localizar | Preservar template/janela por canal, envio, anexos e rascunho; nenhum botão fictício | Enter/Shift+Enter, composição de caracteres, áudio, upload, erro, reconexão e troca de conversa | Voltar ao composer anterior sem apagar rascunho | Inferido: não antes da integração e validação do trabalho concorrente |
| 7. Estados, acessibilidade e mobile completos | Todas as apresentações tocadas | Vazio/erro/loading explícitos em cada painel; foco visível; menu e modal por teclado; composer visível com teclado móvel | Regressão integrada, leitor de tela, viewport real, retorno de foco, offline | Reverter ajuste isolado ou layout por instância | Inferido: somente após aceitação integrada |

**Inferido — proposta de responsividade:** no celular, alternar entre lista e conversa; detalhe do lead como painel sobreposto com fechamento e retorno de foco; navegação compacta sem consumir a largura útil da conversa. No desktop, permitir reduzir/recolher o detalhe antes de comprimir a timeline. Definir pontos de quebra por espaço mínimo útil medido, sem extrapolar o canvas fixo do mockup.

**Verificado — A:44, A:309, A:640:** há indicação visual de atalho `/` na busca e tratamento de Enter sem Shift no composer. **Não verificado:** implementação global do atalho de busca e funcionamento com teclado móvel. **Inferido — proposta:** atalhos não devem disparar durante edição ou composição de caracteres, e Enter/Shift+Enter devem respeitar a preferência real de envio.

## 7. Etapa 2 — Dashboard, esboço

**Verificado — D:118, D:134, D:192, D:208, D:240, D:261:** a referência reúne faixa ao vivo, cartões de indicadores, funil, satisfação, equipe e distribuição horária.

**Inferido — proposta:** começar com indicadores que tenham agregado comprovado, unidade, período, fuso, denominador e política de atualização definidos. Depois acrescentar séries e detalhamento por equipe; gráficos e filtros só entram quando o backend entregar o recorte completo. Não derivar receita de valores textuais nem inferir venda pela simples finalização de atendimento.

**Não verificado:** disponibilidade de qualquer agregado real. O texto do próprio protótipo sobre faturamento e versões (D:284) é conteúdo de demonstração, sem autoridade para descrever o produto.

**Inferido — dependências:** reutilizar tema, superfícies, estados e navegação validados na Etapa 1. Não detalhar implementação de Dashboard até aceite da Etapa 1 e auditoria dos agregados. Não reaproveitar estado local de demonstração como analytics.

## 8. Trabalho concorrente

**Não verificado:** conteúdo, status e caminhos de E232, E233, E224/E225 e outros prompts abertos; a pasta de prompts do repositório não está disponível. As descrições do pedido são contexto do responsável, não prova de código.

**Inferido — ordem recomendada:** auditar contratos do marcador de template; estabilizar o trabalho de backend citado como E233; integrar o composer reativo citado como E232 contra esse contrato; validar comportamento; só então alterar a apresentação da mesma região na Etapa 1. Caso os trabalhos sejam independentes no código real, manter a ordem contratual como preferência, não dependência presumida.

**Inferido — prevenção de conflito:** executar inventário/tema/lista enquanto o composer é estabilizado; adiar mudanças em timeline/composer; atualizar a branch a partir da base integrada antes dessas alterações; não reformatar arquivos inteiros; rever o diff de comportamento e repetir os cenários de template após resolver conflitos. Confirmar os demais prompts antes de iniciar implementação.

## 9. Checklist de não regressão

**Inferido — critérios futuros, nenhum item aprovado nesta sessão:**

- [ ] Tempo real: uma assinatura por escopo; reconexão sem duplicar mensagens; ordenação e paginação preservadas.
- [ ] Rascunho preservado durante reconexão, falha de envio, mudança de painel e seleção de conversa, conforme regra real.
- [ ] Papéis e permissões: ações escondidas/bloqueadas coerentes com autorização real, sem depender só da apresentação.
- [ ] Canal com template/janela e canal sem essas restrições: mesma semântica real antes/depois, sem limitar canal indevido.
- [ ] Atendimento finalizado, grupo e chat interno: ações, identidade e isolamento preservados.
- [ ] Setor e vínculo: dados e filtros autorizados, sem agregar conteúdo fora da permissão.
- [ ] Marca, logo, textos e tema de cada instância: valores ausentes e configurações antigas também testados.
- [ ] Foco visível, semântica/role adequada, rótulos acessíveis, navegação por teclado e retorno de foco em diálogos.
- [ ] Celular: teclado aberto, rotação, viewport reduzido e área segura; envio sempre acessível.
- [ ] Claro e escuro: contraste calculado e capturas reais de todas as instâncias.

**Não verificado:** nomes concretos de capacidades, extensões, flags, papéis, eventos e chaves de texto. A matriz de canal oficial/Estrutural, Uzapi/Fêmina e setores/vínculo/Disbrel vem do pedido e precisa ser confirmada no código e em teste; não foi certificada pelo ZIP.

## 10. Desempenho

**Inferido — restrições propostas:** nenhuma requisição por linha; nenhum polling novo; contadores globais devem usar fonte agregada comprovada. Não montar duas camadas de assinatura quando alternar layout. Paginar e avaliar virtualização com listas grandes, preservando foco, seleção, alturas de mídia e posição de scroll.

**Inferido — medição futura:** registrar antes/depois no mesmo commit-base de dependências, ambiente e cenário: assets gerados pelo build, tamanho bruto/comprimido de entrada e chunks da rota, requisições de uma listagem e de uma conversa, renderizações e tempo de interação. Usar comandos reais de build identificados no manifesto; não há comando de projeto comprovado nesta sessão. Isolar bibliotecas de gráficos da entrada de Atendimentos se a arquitetura real permitir.

**Não verificado:** baseline, limiares aceitáveis, tamanho do bundle e histórico do gargalo citado como E224/E225. Nenhuma medição de produção foi executada.

## 11. Verificação executada e futura

**Verificado — sessão local:** ZIP listado e extraído em pasta temporária; fonte lida com busca numerada; tentativa de Chromium por `file://` bloqueada pelo navegador de automação. Renderização conseguida com servidor HTTP limitado a `127.0.0.1`, a partir dos mesmos arquivos sem alteração. Servidor e navegador encerrados após coleta.

**Verificado — capturas viewport, sem redimensionar o canvas do mockup:**

| Estado | Desktop | Celular |
|---|---|---|
| Visão geral de Atendimentos | [1440×900](mockup/AtendimentosV2-1440x900.png) | [390×844](mockup/AtendimentosV2-390x844.png) |
| Conversa selecionada após clique | [1440×900](mockup/Conversa-1440x900.png) | [390×844](mockup/Conversa-390x844.png) |
| Dashboard inicial | [1440×900](mockup/DashboardV2-1440x900.png) | [390×844](mockup/DashboardV2-390x844.png) |

**Verificado — logs do navegador:** favicon ausente (404); ao abrir Dashboard, ocorreram erros em atributos SVG contendo expressões de template literais, inclusive círculos e séries em D:350, D:351, D:386. **Não verificado:** fidelidade completa dos gráficos e resolução posterior desses erros pelo runtime. As capturas são evidência visual parcial, não aprovação de funcionamento.

**Não verificado / sem prova:** telas atuais de produção ou frontend real com mock/seed; modo escuro; todos os modais e ações; teclado móvel real; envio e reconexão; acessibilidade completa. Não foram rodados Vitest, typecheck, lint, build ou E2E do produto, pois ele não está no ZIP.

**Inferido — testes por passo:** usar testes de componente para comportamentos de estado/foco/permissão, testes integrados para interação com dados e E2E para reconexão/envio/canais; executar typecheck, lint e build pelos scripts reais do projeto quando encontrados. Comparar capturas em claro/escuro e desktop/celular com a referência aprovada, incluindo erro/loading/vazio. Sem criação de testes que apenas repitam valores estáticos de CSS.

**Inferido — exige instância real de teste:** autorização por papel, capacidades dos provedores, template/janela, reconexão e entrega, vínculos/setores, logos e configurações persistidas por instância. O protótipo isolado não demonstra esses comportamentos.

## 12. Instância de teste e imagem

**Não verificado:** arquivo de stack citado pelo pedido, uso de tag de imagem versus build local, workflows e condição de publicação por branch. Não existe evidência arquivo:linha disponível no ZIP para afirmar que publicação ocorra apenas na branch principal.

**Inferido — proposta condicionada à auditoria:** identificar em leitura o workflow publicador e a referência consumida em teste; se ele aceitar publicação seletiva por branch ou disparo manual, propor uma imagem com tag imutável e distinta da produção, consumível exclusivamente pela instância de teste. O menor diff, seus arquivos e gatilhos ficam indefinidos até inspeção do código. Não alterar CI, stack, contratos, secrets, servidor ou implantação nesta etapa.

## 13. Riscos, defaults e pendências

**Inferido — perguntas abertas com defaults assumidos neste estudo; não bloqueiam o plano visual:**

| Pergunta | Default recomendado |
|---|---|
| A ou B para convivência? | A, novo layout desativado e validado por instância |
| Copiar largura fixa ou adaptar? | Adaptar; uma área principal por vez no celular |
| Como tratar dados sem fonte comprovada? | Excluir da integração até auditoria; nunca inserir valores de demonstração |
| Adotar tipografia e marca do mockup em todas as instâncias? | Preservar valores atuais; mudança visual global só com decisão explícita |
| Corrigir contraste ou manter fidelidade literal? | Corrigir contraste, preservando o papel semântico e validando claro/escuro |
| Quando migrar composer/timeline? | Após estabilizar e validar os trabalhos de template citados no pedido |
| Quando detalhar Dashboard? | Após validação de Atendimentos e inventário dos agregados |
| Qual entrega vale antes de ter o repositório? | Documentação local de referência; branch/publicação ficam pendentes |

**Não verificado — pendências para concluir o plano de integração:** confirmar número livre em prompts; atualizar origem principal e inspecionar branches; ler instruções do repositório; mapear todas as dependências com arquivo:linha; aferir baseline das três instâncias; revisar o plano visual contra dados reais; criar a branch documental com sufixo se necessário; commit e publicação. Sem essas etapas, esta versão não deve ser considerada plano executável de produção.

**Verificado — limites da entrega local:** apenas arquivos dentro de `docs/refatoracao-frontend/` foram criados na pasta de trabalho por esta tarefa. Nenhum código de produto foi implementado. **Não verificado / sem prova:** `git diff --stat origin/main`, branch e hash de commit, pois a pasta não é checkout Git. Não houve merge, PR, deploy, alteração de infraestrutura ou acesso ao VPS/Dokploy.
