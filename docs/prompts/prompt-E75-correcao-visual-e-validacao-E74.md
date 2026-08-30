# Prompt E75 — Correção visual efetiva e validação da E74

## Objetivo

Corrigir as lacunas verificadas na revisão da E74 antes da promoção da `hmlgc`. A E74 comprovou que a aplicação já utiliza Hanken Grotesk localmente e eliminou o overflow horizontal da sidebar, mas não entregou refinamento visual suficiente nas telas administrativas nem evidências visuais PNG verificáveis.

Esta etapa deve produzir melhoria visual real, comprovável no código e no navegador. Não aceite apenas reorganização de código, atualização de README ou testes estáticos como conclusão da tarefa.

## Árvore e estado obrigatório

Trabalhe exclusivamente em:

- branch: `hmlgc`;
- worktree: `C:\Users\marcondes\Desktop\projeto_matriz-hmlgc`.

Antes de editar:

1. confirme branch, `HEAD`, `MERGE_HEAD`, tracking remoto e worktree;
2. execute `git status --short --branch`, `git diff --stat` e `git diff --cached --stat`;
3. confirme que as quatro alterações não commitadas da E74 estão presentes;
4. preserve o diretório não rastreado preexistente `frontend/.playwright-cli/`;
5. não faça `reset`, `checkout`, `clean`, descarte de alterações, cherry-pick ou merge;
6. não faça commit, push ou deploy sem autorização explícita posterior.

Se o merge da `fixtwo` voltar a estar em andamento ou houver conflito, pare antes de editar. A E75 deve continuar sobre o merge já concluído em `36bb768`, sem alterar `main`, `hotfix` ou `fixtwo`.

## Leitura obrigatória

Leia integralmente, nesta ordem:

1. `AGENTS.md`;
2. `docs/13-estado-do-projeto.md`;
3. `docs/prompts/COMO-ESCREVER-PROMPTS.md`;
4. `docs/prompts/prompt-E74-identidade-visual-tipografia-global.md`.

Depois confira diretamente o diff atual da E74 e a implementação de:

- `frontend/src/app/layout.tsx`;
- `frontend/src/app/globals.css`;
- `frontend/src/app/identidade-synapse.css`;
- `frontend/src/components/shell/sidebar.tsx`;
- `frontend/src/components/administracao/layout-administracao.tsx`;
- páginas e componentes de visão geral, acessos, feedbacks e novidades;
- primitives de card, button, badge, tabs, table, dialog e tooltip;
- configuração do servidor de desenvolvimento e proxy WebSocket.

O relatório da E74 não é evidência. Cada item abaixo deve ser confirmado no código, no navegador e nos artefatos gerados.

## Referências

Use:

- `C:\Users\marcondes\Downloads\CRM_EstruturalVidros_App (1).html`;
- `C:\Users\MARCON~1\AppData\Local\Temp\codex-clipboard-70cc1559-0af5-4b89-aac9-4e4f831aeb34.png` — visão geral;
- `C:\Users\MARCON~1\AppData\Local\Temp\codex-clipboard-514a4317-765a-4a3e-a6ec-57d55cb993ff.png` — acessos;
- `C:\Users\MARCON~1\AppData\Local\Temp\codex-clipboard-5a4b3d6c-20c3-4d99-862c-f6d8018437d5.png` — feedbacks vazio;
- `C:\Users\MARCON~1\AppData\Local\Temp\codex-clipboard-133e9679-5d0d-4bab-8f26-e77912328e57.png` — acessos no layout completo.

O modelo é referência de composição e identidade, não fonte para copiar dados, funcionalidades ou textos. Não implementar “Entrar como”, tutoriais/documentação, CRUD novo, editor administrativo, integrações ou dados mockados.

## Constatações que a correção precisa resolver

### 1. A E74 não alterou efetivamente a identidade visual administrativa

O diff atual da E74 contém essencialmente:

- atualização da documentação sobre Hanken Grotesk;
- testes da fonte;
- teste de overflow;
- `overflow-x-hidden` e formatação na sidebar.

A Hanken Grotesk e os tokens principais já estavam presentes antes da E74. Portanto, não declare como “refinamento visual concluído” apenas a existência desses tokens.

Faça os ajustes visuais reais necessários, preferencialmente em tokens e componentes compartilhados:

- proporção, altura e espaçamento do cabeçalho administrativo;
- largura e separação da sidebar principal e navegação interna;
- canvas administrativo azul-acinzentado;
- superfícies brancas, bordas, raios e sombras dos cards;
- hierarquia de título, subtítulo, labels, métricas e estados;
- navegação interna selecionada em lavanda/violeta suave;
- alinhamento da tabela de acessos, avatares, badges e ações;
- filtros e estado vazio da tela de Feedbacks;
- modal de Novidades & Em Breve;
- estados hover, active, selected, focus-visible, disabled e destructive.

As alterações devem aproximar a geometria e o ritmo visual das referências sem quebrar as telas já existentes. Não transforme cada tela em uma exceção visual isolada.

### 2. A captura visual informada não foi comprovada

O diretório `frontend/output/playwright` não possuía PNG/JPG verificáveis na revisão. Nesta etapa, gere capturas reais, em formato PNG, e confirme sua existência após a execução.

Crie um diretório de evidências dentro do worktree, preferencialmente `frontend/output/playwright/e75/`. Se esse diretório for ignorado pelo Git, tudo bem: as imagens servem como evidência local e não devem ser forçadas ao commit.

As capturas mínimas são:

- Administração — visão geral;
- Administração — acessos;
- Administração — feedbacks com estado vazio;
- Atendimento;
- Agenda;
- Feedbacks do usuário;
- modal de Novidades & Em Breve;
- uma captura mobile de Administração e uma de Atendimento.

Use nomes descritivos, por exemplo `e75-administracao-visao-geral-1440.png`. O relatório deve listar o caminho absoluto de cada arquivo e o viewport usado.

### 3. WebSocket local

O fluxo local de Atendimento registrou repetidamente falhas de conexão em `ws://localhost:3000/ws`, porque o frontend não estava encaminhando a conexão para o backend em `8080`.

Investigue a configuração real do Next.js e do backend. Se o projeto já possuir um ponto de proxy/rewrite apropriado, configure o encaminhamento WebSocket somente para desenvolvimento/homologação local, sem alterar o contrato ou o proxy de produção de forma arriscada. Preserve a origem correta do token e não coloque segredo em código.

Se a arquitetura local não permitir proxy WebSocket pelo servidor atual, não simule uma conexão nem silencie o erro. Registre a limitação, informe o comando/infra necessária e marque a validação de console como ⚠️. Não declare console limpo sem prova.

Qualquer alteração em configuração de proxy deve ser testada com:

- conexão HTTP normal ao backend;
- abertura real de `/ws` pelo navegador;
- recebimento ou tentativa controlada de evento sem erro repetitivo;
- preservação do comportamento de produção.

## Tipografia

Não duplique a implementação da fonte: ela já está versionada em `frontend/src/app/fonts/` e carregada por `next/font/local`.

Confirme e preserve:

- `HankenGrotesk-Variable.woff2` como fonte base;
- `font-sans` e `font-heading` apontando para a mesma família;
- JetBrains Mono somente para dados técnicos;
- ausência de Geist, Inter ou Roboto em estilos efetivamente usados;
- fallback seguro sem CDN ou Google Fonts;
- compatibilidade com os tokens de tema existentes.

Se houver alteração de fonte, ela deve ser justificada com evidência do estilo computado; não altere a fonte só para gerar diff.

## Restrições de implementação

- Não alterar backend funcional, migrations, RLS, autenticação, autorização, WebSocket de negócio, outbox ou contratos.
- Não criar endpoint ou dado fictício.
- Não colocar strings literais novas em componentes React; use o catálogo existente.
- Não colocar hex, rgb ou hsl novos em componentes; use tokens semânticos.
- Não criar condição específica para Estrutural Vidros.
- Não alterar a regra de Administração exclusiva para `ADMINISTRADOR`.
- Não implementar “Entrar como”, tutoriais/documentação ou editor administrativo.
- Não remover funcionalidades para fazer a tela parecer com o modelo.
- Não usar `overflow-x-hidden` como correção cega de conteúdo que deveria estar acessível; confirme que o problema é overflow visual indevido.
- Não alterar `main`, `hotfix` ou `fixtwo`.
- Não fazer commit, push ou deploy.

## Testes e validação

### Frontend

Execute na raiz do frontend:

```text
npm run lint
npm run typecheck
npm test -- --run
npm run build
```

O build deve ser executado em instalação limpa, sem junction apontando para fora da raiz. Informe quantidade de arquivos/testes, erros e warnings.

Inclua testes que comprovem, sem depender somente de snapshot:

- tokens/classe de tipografia global;
- estados visuais administrativos relevantes;
- cor semântica de ações destrutivas;
- ausência de overflow indevido;
- preservação de textos e dados reais nos estados vazio/carregado.

### Backend

Se somente frontend e configuração local forem alterados, registre que o backend funcional não foi tocado. Se qualquer arquivo backend, catálogo compartilhado ou contrato for alterado, execute:

```text
cd backend
./mvnw clean verify
```

Informe Java, testes do `crm-app`, falhas/erros/skips, Testcontainers, Spotless e ArchUnit.

### Navegador

Valide nos viewports `1440x900`, `1536x960`, `1024x768` e `390x844`:

- login;
- `/atendimentos`;
- rota real da Agenda;
- `/feedbacks`;
- `/administracao`;
- `/administracao/acessos`;
- `/administracao/feedbacks`;
- modal de Novidades & Em Breve.

Para cada tela, verifique família tipográfica computada, comparação visual com o modelo, estados de interação, overflow, clipping, console e rede. Em Atendimento, confirme especificamente se o WebSocket não continua em reconexão infinita.

## Critérios de aceite

- [ ] Há mudanças visuais efetivas, além de README/testes/overflow, aproximando Administração e shell do modelo.
- [ ] As quatro telas administrativas têm hierarquia, superfícies, espaçamentos, estados e proporções coerentes entre si.
- [ ] Atendimento, Agenda, Feedbacks e Novidades continuam visualmente coerentes após a alteração global.
- [ ] Hanken Grotesk é comprovadamente a fonte efetiva global; não há regressão para Geist/Inter/Roboto.
- [ ] Tokens semânticos continuam sendo a fonte das cores, raios, sombras e estados.
- [ ] Hover, selected, focus-visible, disabled e destructive são visíveis e acessíveis.
- [ ] Não há overflow horizontal ou clipping não intencional nos quatro viewports.
- [ ] O fluxo local de WebSocket foi corrigido e comprovado, ou a limitação foi reproduzida e documentada como ⚠️ sem mascarar erro.
- [ ] PNGs reais foram gerados para todas as telas mínimas e seus caminhos absolutos estão no relatório.
- [ ] Não foram implementados “Entrar como”, tutoriais/documentação, endpoints, mocks ou regras novas.
- [ ] Lint, typecheck, testes, build e `git diff --check` foram executados e reportados.
- [ ] Backend permanece inalterado ou foi validado com `clean verify` se tocado.
- [ ] Nenhum commit, push ou deploy foi feito.

## Relatório final obrigatório

Informe, nesta ordem:

1. branch, worktree, `HEAD`, status, diff e quantidade de arquivos;
2. cada critério de aceite com ✅, ⚠️ ou ❌ e evidência concreta;
3. arquivos e tokens visuais alterados, com justificativa;
4. evidência da fonte computada no navegador;
5. lista de PNGs com caminhos absolutos e viewports;
6. resultado do WebSocket local e limitações;
7. testes frontend e, se aplicável, backend;
8. divergências entre modelo, documentação e implementação;
9. bugs preexistentes encontrados;
10. o que ficou de fora;
11. decisões necessárias antes do merge/promoção;
12. confirmação explícita de que não houve commit, push ou deploy.

Não use “CI verde” sem número de run remoto. Sem push, informe “CI não verificado”.
