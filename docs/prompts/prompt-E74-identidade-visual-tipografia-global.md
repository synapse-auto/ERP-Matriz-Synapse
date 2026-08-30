# Prompt E74 — Identidade visual e tipografia global

## Papel nesta etapa

Você é o agente de implementação responsável por aplicar o refinamento visual no Synapse CRM / Base PAI. O objetivo é aproximar a interface do modelo visual fornecido e substituir, de forma consistente em todo o sistema, a tipografia atual pela tipografia do modelo.

Implemente, teste e valide a alteração na árvore correta. Este prompt não autoriza commit, push, deploy ou merge; essas operações dependem de autorização explícita separada.

## Contexto e branch obrigatórios

Esta etapa ocorre antes da promoção da homologação para a `main`.

1. Trabalhe somente na árvore de homologação integrada (`hmlgc`) ou em uma branch de trabalho explicitamente derivada dela.
2. Antes de alterar qualquer arquivo, confirme branch, caminho absoluto do worktree, `HEAD`, tracking remoto, `git status --short --branch`, `git diff --stat` e `git diff --cached --stat`.
3. Se a árvore estiver com merge em andamento, conflitos não resolvidos ou alterações que não possam ser distinguidas com segurança, pare antes de editar e relate o bloqueio. Não faça `reset`, `checkout`, `clean`, cherry-pick, merge, cópia manual ou descarte de alterações por conta própria.
4. Não trabalhe em `main` nem em `hotfix`. Não reescreva histórico e não mova alterações de outras etapas sem autorização.
5. Preserve prompts, scripts e artefatos não rastreados existentes.

## Leitura obrigatória antes de codar

Leia integralmente, nesta ordem:

1. `AGENTS.md`;
2. `docs/13-estado-do-projeto.md`;
3. `docs/prompts/COMO-ESCREVER-PROMPTS.md`.

Depois, inspecione a implementação atual de `frontend/src/app/layout.tsx`, `frontend/src/app/globals.css`, configuração de tema/tokens, `frontend/src/components/shell/`, telas e componentes de `atendimentos`, `agenda`, `feedbacks`, `administracao` e `novidades`, o catálogo `textos.json` e seu schema, e `frontend/README.md`.

Não aceite relatório anterior como evidência. Confirme cada afirmação no código, no estilo computado do navegador e nos testes.

## Referências visuais

Use os arquivos abaixo como referência de identidade visual, proporção, hierarquia, espaçamento, bordas, estados e tipografia:

- Modelo HTML: `C:\Users\marcondes\Downloads\CRM_EstruturalVidros_App (1).html`;
- Administração — visão geral: `C:\Users\MARCON~1\AppData\Local\Temp\codex-clipboard-70cc1559-0af5-4b89-aac9-4e4f831aeb34.png`;
- Administração — acessos: `C:\Users\MARCON~1\AppData\Local\Temp\codex-clipboard-514a4317-765a-4a3e-a6ec-57d55cb993ff.png`;
- Administração — feedbacks vazio: `C:\Users\MARCON~1\AppData\Local\Temp\codex-clipboard-5a4b3d6c-20c3-4d99-862c-f6d8018437d5.png`;
- Administração — acessos no layout completo: `C:\Users\MARCON~1\AppData\Local\Temp\codex-clipboard-133e9679-5d0d-4bab-8f26-e77912328e57.png`.

O HTML e as imagens são referência visual, não autorização para copiar funcionalidades, dados ou textos que não existam no CRM. Não implemente nesta etapa:

- “Entrar como”;
- tutoriais e documentação;
- novos endpoints, CRUD, migrations ou integrações;
- dados fictícios para preencher cards, tabelas ou feedbacks;
- alterações de regra de acesso administrativo.

## Descoberta obrigatória da fonte

O modelo declara explicitamente a família visual:

```css
font-family: 'Hanken Grotesk', system-ui, sans-serif;
```

Faça a tipografia global convergir para `Hanken Grotesk`, respeitando a arquitetura de tema já existente.

Antes de decidir como carregar a fonte, verifique se há arquivos locais licenciados no repositório, em assets do frontend ou no ambiente já adotado pelo projeto. Não adicione `@import` de Google Fonts nem crie dependência de CDN. Se houver arquivo local, use o mecanismo já previsto pelo Next (`next/font/local`) e declare os pesos usados. Se não houver arquivo local, não baixe uma fonte silenciosamente: documente a ausência e implemente a melhor integração possível com a fonte aprovada já disponível, deixando explícita a limitação no relatório.

Não considere suficiente alterar apenas `--font-sans`. Prove que o `@font-face` ou mecanismo equivalente foi carregado, que o `body` alcança todas as telas e que títulos, textos, labels, botões, tabs, badges, menus, tabelas, modais, drawers, toasts e estados vazios usam a família esperada. Não deixe uma família antiga aplicada por seletor mais específico. Fonte monoespaçada permanece apenas para dados técnicos quando isso for intencional.

Se `frontend/README.md` ou documentação equivalente afirmar que a aplicação usa outra fonte, atualize a documentação na mesma etapa.

## Escopo de implementação

### 1. Tipografia global

Aplicar a tipografia do modelo em todo o sistema, sem limitar a mudança à Administração:

- shell principal e sidebar;
- Atendimento, lista de conversas, cabeçalho, mensagens e composer;
- Agenda de Contatos, filtros, tabela, chips e ficha do lead;
- Dashboard;
- Feedbacks enviados pelo usuário;
- Administração: visão geral, acessos e feedbacks;
- Novidades & Em Breve;
- formulários, tabelas, cards, tooltips, dropdowns, dialogs, drawers, alertas e estados vazios.

Defina uma fonte de verdade no sistema de tokens. Se existirem `font-sans` e `font-heading`, ambos devem ser coerentes com o modelo, salvo diferença visual comprovada. Não espalhe nomes de fonte em componentes individuais.

### 2. Identidade visual do shell

Aproxime o shell do modelo observado:

- sidebar principal em azul-marinho profundo, com contraste e estados ativo/hover/foco consistentes;
- marca, navegação, agrupamentos, item de Administração e área do usuário com hierarquia semelhante à referência;
- cabeçalho da Administração branco, separado do canvas por borda sutil;
- navegação interna da Administração em superfície branca, com item selecionado em lavanda/violeta suave;
- canvas administrativo em azul-acinzentado muito claro;
- cards brancos, borda discreta, raio e sombra leves;
- ações primárias em azul da identidade ou violeta da área administrativa, usando tokens;
- status positivo em token semântico verde;
- labels restritos, badges, contadores e erros com proporção e contraste próximos ao modelo.

Use tokens semânticos existentes (`background`, `card`, `border`, `primary`, `muted`, `destructive` e estados). Se a comparação exigir novos valores, altere tokens/CSS variables em um único ponto e explique a decisão. Não coloque hex, rgb, hsl ou nomes de cor diretamente em componentes quando houver token disponível.

### 3. Administração

Refine visualmente, sem alterar a função:

- cabeçalho com ícone, título, selo de acesso restrito, subtítulo e indicador de estado;
- navegação lateral interna e estados selecionado, hover, foco e desabilitado;
- cards de visão geral, ícones, números, labels e espaçamento;
- tabela de acessos: cabeçalho, linhas, avatar, papel, presença, situação e ações;
- tela de Feedbacks, filtros e estado vazio real;
- alinhamento e largura útil conforme o modelo em desktop.

Não crie métricas, usuários, feedbacks, contagens ou integrações fictícias para preencher a tela. Quando o endpoint não fornecer dados, mantenha o estado vazio real.

### 4. Estados de interação e ícones

Padronize em toda a aplicação os estados hover, active, selected, focus-visible, disabled, loading e erro. Reutilize Lucide ou a biblioteca já aprovada, mantendo espessura, tamanho e alinhamento consistentes.

Ícones de exclusão e ações destrutivas devem usar o token `destructive` no estado apropriado, com foco acessível. Ícones em itens selecionados precisam ter contraste e tratamento equivalentes ao texto. Estados de teclado devem ser visíveis e não depender apenas de hover.

Não altere ícones ou textos apenas para preencher espaço. Toda mudança deve ter correspondência observável no modelo ou melhorar a coerência do design system.

### 5. Responsividade e geometria

Valide ao menos `1440x900`, `1536x960`, `1024x768` e `390x844`.

Corrija, dentro deste escopo visual, scrollbar horizontal não intencional, sidebar ou navegação cortada, cards estourando o canvas, tabela causando rolagem da página inteira, cabeçalho sobreposto e modal/drawer que não cabe na altura disponível. Em telas pequenas, use colapso, rolagem localizada ou empilhamento previsível, sem esconder ações essenciais sem alternativa acessível.

## Restrições funcionais

- Não alterar backend, migrations, RLS, autenticação, autorização, WebSocket, outbox ou contratos.
- Não criar endpoint inexistente.
- Não inserir mock ou fixture de produção no frontend.
- Não adicionar strings literais de UI em componentes React; use o catálogo existente quando uma string for necessária.
- Não adicionar cores literais fora do sistema de tokens.
- Não introduzir flag específica da Estrutural Vidros.
- Não modificar a regra que limita Administração a `ADMINISTRADOR`.
- Não implementar “Entrar como”, tutoriais/documentação ou editor de conteúdo.
- Não remover funcionalidades para obter uma captura parecida.
- Não trocar Java 21.

## Estratégia de execução

1. Mapear a fonte efetivamente carregada e os tokens atuais.
2. Comparar o HTML de referência com a árvore atual e listar desvios visuais concretos.
3. Corrigir primeiro tipografia e tokens; depois shell e Administração; por fim componentes compartilhados e responsividade.
4. Reutilizar primitives e componentes existentes em vez de criar variações paralelas.
5. Criar testes focados em variáveis/classes, estados semânticos, família tipográfica e ausência de overflow. Evite snapshots grandes como única evidência.
6. Rodar o fluxo real no navegador com dados disponíveis, sem fabricar dados.
7. Gerar capturas finais das rotas principais para auditoria.

## Validação obrigatória

### Frontend

Na raiz do frontend, execute:

- `npm run lint`;
- `npm run typecheck`;
- `npm test -- --run`;
- `npm run build` em instalação limpa, sem junction ou dependência apontando para fora da raiz.

Se o build padrão falhar por limitação do ambiente, corrija o ambiente ou registre exatamente o motivo e o comando alternativo. Não declare build verde apenas porque uma variante diferente compilou.

### Backend

Como esta etapa não deve tocar no backend, registre explicitamente que ele permaneceu inalterado. Se qualquer arquivo backend, catálogo compartilhado, configuração de aplicação ou contrato for modificado, execute obrigatoriamente:

```text
cd backend
./mvnw clean verify
```

Informe Java usado, quantidade de testes do `crm-app`, falhas/erros/skips e se Testcontainers, Spotless e ArchUnit executaram.

### Navegador e evidências visuais

Use o navegador real para validar `/atendimentos`, a rota real da Agenda, `/feedbacks`, `/administracao`, `/administracao/acessos`, `/administracao/feedbacks`, o modal de Novidades & Em Breve e pelo menos um formulário e um dialog/drawer.

Para cada rota, confira família tipográfica computada, hierarquia comparada ao modelo, hover/selected/focus/destructive, ausência de overflow horizontal, desktop/mobile e ausência de erro novo no console ou na rede. Registre os caminhos absolutos das capturas no relatório. Não chame uma validação de visual apenas porque o build passou.

## Critérios de aceite

- [ ] `Hanken Grotesk` do modelo está aplicada globalmente e comprovada no navegador.
- [ ] Não há aplicação residual de fonte antiga nem conflito entre `font-sans` e `font-heading`.
- [ ] Shell, Administração, Atendimento, Agenda, Feedbacks e Novidades apresentam a mesma linguagem visual.
- [ ] Administração se aproxima das quatro referências em hierarquia, espaçamento, superfícies, estados e proporções.
- [ ] Cores e estados usam tokens semânticos; não há hex/rgb/hsl novo espalhado em componentes.
- [ ] Ícones selecionados, focados e destrutivos têm estados visíveis e acessíveis.
- [ ] “Entrar como”, tutoriais/documentação e funcionalidades fora deste prompt não foram implementados.
- [ ] Não há dados fictícios, endpoints novos ou mudanças de backend.
- [ ] Não existe scrollbar horizontal não intencional nos viewports validados.
- [ ] Lint, typecheck, testes e build frontend foram executados e reportados com evidência.
- [ ] Capturas reais de validação foram geradas e listadas.
- [ ] `git diff --check` passou.

## Commit, push e relatório

Não faça commit, push ou deploy sem autorização explícita posterior. Antes de encerrar, informe:

1. branch, worktree, `HEAD`, status e quantidade de arquivos alterados;
2. cada critério de aceite com ✅, ⚠️ ou ❌ e evidência concreta;
3. decisões de design tomadas sem especificação literal;
4. divergências entre documentação, HTML de referência e código;
5. bugs preexistentes encontrados;
6. o que ficou de fora e por quê;
7. decisões necessárias para promoção/merge;
8. se backend foi tocado e resultado do `clean verify`;
9. se houve CI remoto — “CI verde” só pode ser usado com o número da run;
10. se houver variável nova, nome, valor de exemplo e ação necessária no Dokploy antes do próximo deploy.

Um relatório dizendo apenas “feito” ou “testes passando” não encerra a etapa.
