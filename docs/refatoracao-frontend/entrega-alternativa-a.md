# Alternativa A — primeira entrega de Atendimentos

## 1. Estado e revisão

**Verificado:** implementação iniciada após autorização do responsável para a alternativa A. A fase anterior era exclusivamente documental; [plano.md](plano.md) preserva esse registro histórico. Esta entrega contém a apresentação inicial de Atendimentos, seus testes isolados e evidências visuais, sem implantação.

**Verificado:** base inspecionada: `origin/main` em `59fc322`. Branch de trabalho: `codex/frontend-alternativa-a`. O checkout limpo e desacoplado já existente foi reutilizado; o diretório tem nome histórico de outro trabalho. Nenhuma alteração do checkout do Desktop foi movida ou sobrescrita. O SHA final e o resultado do push constam no relatório da tarefa e no PR.

**Verificado:** o código do sistema foi localizado em um segundo momento em `Desktop/projeto_matriz`. A limitação anterior sobre ausência de repositório estava restrita à pasta original da conversa, não ao computador inteiro.

## 2. Definição de pronto desta entrega

| Critério | Estado / evidência |
|---|---|
| Convivência por instância, antigo como padrão | ✅ Verificado: `frontend/src/components/atendimentos/pagina-atendimentos-cliente.tsx:177` reutiliza a consulta de features; `:179` exige explicitamente a chave; `:1004` distingue legado/v2. |
| Uma só árvore de dados, mensagens e composer | ✅ Verificado: somente a classe da raiz muda em `pagina-atendimentos-cliente.tsx:1005`; o teste em `pagina-atendimentos-cliente.test.tsx:549` preserva identidade DOM e clientes STOMP durante ativação e reversão. |
| Novo visual em lista, abas, seleção, conversa, detalhes e composer | ✅ Verificado: `frontend/src/components/atendimentos/atendimentos-v2.module.css:7`; estilos limitados à classe local. Componentes reais e callbacks existentes reutilizados. |
| Responsivo sem copiar o canvas fixo | ✅ Verificado: geometria em Chromium para 1440, 1040, 720 e 390px; conversa ≥320px; excesso horizontal ≤1px. Reserva da moldura/vãos em `pagina-atendimentos-cliente.tsx:188`. |
| Composer em viewport móvel reduzido | ✅ Verificado: teste reduz a viewport de 390×844 para 390×520 e verifica que o textarea cabe. Não equivale a teclado físico real. |
| Sem N+1 de consultas nem polling novo | ✅ Verificado: consulta de features movida, não duplicada; teste E2E compara as rotas consumidas e a quantidade de chamadas de Atendimentos para legado com 1 contato e v2 com 15 contatos. Nenhum polling foi adicionado. |
| Marca e tokens da instância preservados | ✅ Verificado: CSS sem cores literais ou nova fonte; usa aliases existentes. Chromium com tema padrão do repo, dois arquivos Disbrel e tema escuro sintético. Não certifica configurações das instâncias reais. |
| Não alterar templates e envio | ✅ Verificado: nenhum diff em composer, bolhas, hooks de envio, mensagens, contratos ou backend. A mudança visual do composer é externa ao arquivo dele. |
| Backend, migrations, CI, compose e infraestrutura intactos | ✅ Verificado pelo diff desta branch. Nenhum deploy, merge, ativação de flag ou comando no VPS/Dokploy. |
| Homologação dos três clientes e canais reais | ⚠️ Não verificado: exige instância autorizada de teste. Flag segue ausente/desativada nas instâncias não alteradas. |
| Dashboard reformulado | Fora desta entrega: permanece Etapa 2, conforme plano. |

## 3. Decisões de implementação

**Inferido — decisão adotada:** a forma de menor risco para a alternativa A é uma apresentação CSS isolada sobre os mesmos componentes, em vez de duas páginas com assinaturas e caches próprios. Evita segunda fonte de verdade e mantém o trabalho concorrente de mensagens fora do diff. Também torna a reversão independente de dados.

**Verificado:** o backend já enumera genericamente as flags habilitadas: `backend/crm-automacao-config/src/main/java/com/synapse/crm/automacaoconfig/application/featureflag/FeatureService.java:30`. O frontend já consumia essa lista na mesma página. A nova chave é `atendimentos_layout_v2`; não existe habilitação automática, por URL, armazenamento local ou nome do cliente.

**Verificado:** não foi criada migration nem registro de flag no banco. A ausência da chave mantém o legado. A aprovação desta entrega não ativa nenhuma instância. A futura ativação deve ser realizada pelo operador na configuração da instância de teste, usando o processo autorizado para `feature_flag`; não há endpoint PUT de flags nesta camada (`FeatureFlagRepositorioJpa.java:11`, no mesmo módulo).

**Inferido — reversão operacional proposta:** desabilitar/remover a chave da lista habilitada da instância e recarregar a sessão para reler a configuração. Não assumir desligamento instantâneo em todas as abas, pois a consulta usa o cache existente. No nível de código, reverter o commit desta entrega; nenhum rollback de schema ou dados é necessário.

**Verificado:** tokens existentes são resolvidos em `frontend/src/lib/config/fetch-config.ts:65`; aliases seguem o tema no layout do shell em `frontend/src/app/(shell)/layout.tsx:24`. O novo CSS reutiliza esses aliases e não altera o schema do tema. Raios, sombras e fonte também vêm dos tokens atuais.

**Inferido — decisão adotada:** preservar cores e fonte atuais de cada instância; extrair do ZIP a organização das superfícies, hierarquia e seleção. Não copiar o azul, a fonte remota nem números e nomes do mockup para componentes de produção. Não criar cartões de indicadores sem contrato comprovado.

**Verificado:** os [pares calculados de contraste](contraste-alternativa-a.md) usam sRGB linearizado. A medição não certifica toda a aplicação nem todas as cores dinâmicas recebidas do servidor.

## 4. Verificações e reprodução

**Verificado:** typecheck passou; lint terminou com 0 erros e 4 avisos em arquivos fora do diff (virtualizador, sidebar, navegação inferior e cliente HTTP). Build de produção passou na última execução (Next 16.4.2, compilação, TypeScript e geração de páginas).

**Verificado:** rodada focada inicial: 7 arquivos, 154 testes aprovados, incluindo os dois testes novos de seleção do layout e preservação da conversa/composer/conexão.

**Verificado:** rodada ampliada de Atendimentos e configuração: 35 arquivos, 540 testes; 530 passaram e 10 falharam em `frontend/src/lib/config/aplicar-marca.test.ts`. As 10 falhas persistiram ao executar esse arquivo sozinho: processos filhos encerrados com código 3221226505 no Windows. Esse arquivo e o script testado não estão no diff. A causa exata não foi diagnosticada; a suíte ampliada não está verde.

**Verificado:** suíte Chromium dedicada: 8 cenários aprovados (geometria em quatro larguras; fallback/quantidade de consultas; tema escuro sintético; temas Disbrel A e B). Após ajustar a cor primária apenas na paleta sintética para corrigir o contraste, o cenário escuro foi reexecutado e passou (1/1). A API e o handshake STOMP desta suíte são fixtures locais, não prova de integração com provedores reais.

**Verificado:** `backend/mvnw.cmd clean verify` foi executado com JDK 21 e falhou no módulo de Atendimento em `MinioArmazenamentoDeMidiaIT.iniciarMinio`, pois Testcontainers não encontrou Docker. O ciclo completo não passou; os módulos seguintes não foram certificados.

**Verificado — comandos de reprodução**, a partir de `frontend/`:

```text
npm ci --no-audit --no-fund
npm run typecheck
npm run lint
npm run build
npm test -- --maxWorkers=2 src/components/atendimentos src/lib/atendimento/modo-da-grade.test.ts src/lib/config/schema.test.ts src/lib/config/temas-disbrel.test.ts src/lib/config/aplicar-marca.test.ts
npx playwright test --config=playwright.frontend-v2.config.ts
```

**Verificado:** a suíte visual sobe dois servidores limitados a loopback (3105 e 38981), lê os arquivos de tema/textos do repo e substitui requisições de negócio somente dentro do navegador de teste. Não usa credenciais reais, não roda seed e não grava banco ou configuração de cliente. O único controle HTTP adicional pertence ao servidor da fixture e mantém o tema apenas em memória.

**Não verificado:** CI remoto antes do push. “Build local aprovado” não significa “CI verde”; conferir a run correspondente ao SHA publicado.

## 5. Evidência visual

**Verificado:** capturas da implementação real com dados sintéticos exclusivos da suíte; não são capturas de produção. As imagens do protótipo original estão separadas em `mockup/`.

| Cenário | Captura |
|---|---|
| Novo layout, desktop | [1440×900](implementacao/atendimentos-v2-1440.png) |
| Novo layout, largura intermediária | [1040×900](implementacao/atendimentos-v2-1040.png) |
| Novo layout, largura reduzida | [720×900](implementacao/atendimentos-v2-720.png) |
| Novo layout, celular | [390×844](implementacao/atendimentos-v2-390.png) |
| Legado sem flag, fixture com 1 contato | [Desktop](implementacao/atendimentos-legado-1440.png) |
| Tema escuro sintético | [Desktop](implementacao/atendimentos-v2-escuro-1440.png), [celular](implementacao/atendimentos-v2-escuro-390.png) |
| Arquivos de exemplo Disbrel | [Sidebar vinho](implementacao/atendimentos-v2-disbrel-a-sidebar-vinho.png), [sidebar branca](implementacao/atendimentos-v2-disbrel-b-sidebar-branca.png) |

## 6. Divergências, achados e limites

**Verificado:** a análise inicial tinha acesso somente ao ZIP. Agora há evidência real da configuração, dos componentes e dos testes; os itens do plano histórico rotulados “não verificado” não devem ser lidos como prova de ausência no sistema.

**Verificado:** o ZIP possui canvas fixo de 1920×1080 (`mockup/fonte/AtendimentosV2.dc.html:28`) e renderização parcial de gráficos com erros SVG no Dashboard. Nenhum desses comportamentos foi copiado para o produto.

**Verificado:** a classe global `.dark` isoladamente não venceu os aliases injetados pelo tema claro no teste exploratório. Não foi alterado o mecanismo global de tema. A evidência escura desta entrega usa um tema escuro sintético fornecido pelo fluxo de configuração, não comprova existência de seletor de modo escuro na aplicação.

**Verificado:** o histórico STOMP e suas assinaturas continuam no código existente (`frontend/src/lib/atendimento/tempo-real.ts:47`, `:49`, `:361`). As APIs de lista, contadores e estado continuam em `frontend/src/lib/atendimento/api.ts:40`, `:43`, `:82`. Nenhum endpoint novo de produção foi criado.

**Não verificado:** quantidade de linhas/arquivos de uma segunda composição completa, tamanho do bundle antes/depois, homologação real da Fêmina/Uzapi, Estrutural/canal oficial e Disbrel/vínculos. Não há promessa de paridade visual absoluta com o protótipo; o escopo é a primeira base visual integrada, respeitando marca e regras existentes.

**Não verificado:** status efetivo de E232 e demais prompts abertos fora da base integrada. O histórico Git já contém o commit de E233 (`2eb8e89`), mas sua região não foi modificada. A próxima etapa deve reler os trabalhos em andamento antes de alterar o composer ou a timeline.

**Verificado:** a stack usa imagem com tag em `docker/dokploy-stack.yml:166` e `:498`; publicação de imagens é restrita a push na principal em `.github/workflows/ci.yml:170`. Nenhum workflow foi alterado para produzir imagem desta branch. Publicar a branch/PR não implanta a mudança.

**Inferido — proposta futura:** se houver necessidade de imagem para homologação, decidir um mecanismo de publicação de branch com tag distinta e imutável, limitado à instância de teste. A definição do gatilho e sua implementação ficam fora desta entrega.

## 7. Próxima decisão do responsável

**Inferido — defaults:** manter a alternativa A desligada; revisar primeiro as capturas e o PR; depois escolher uma instância de teste e o mecanismo de ativação. Não habilitar as três instâncias juntas. Validar por papel e canal, mídia, template/janela, finalização, grupo, chat interno, reconexão, rascunho e teclado móvel real antes da produção.

**Inferido — sequência seguinte:** homologar essa base; detalhar os elementos visuais restantes de Atendimentos que o responsável priorizar; estabilizar integrações concorrentes; somente depois iniciar Dashboard. Não há decisão urgente necessária para revisar esta entrega.

**Verificado — ferramentas:** a skill Playwright foi lida e aplicada à renderização local. A skill clean-code solicitada pelo AGENTS não foi encontrada no catálogo nem nas pastas de skills consultadas. Foram seguidas as convenções do repositório e lidas as instruções locais do Next sobre CSS Modules antes das alterações. Nenhuma variável nova foi adicionada; nenhuma ação no Dokploy é necessária para esta entrega em branch.
