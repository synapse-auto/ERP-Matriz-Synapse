# E86 — Iniciar chat interno pela lista de atendimentos

## Objetivo

Na barra superior da lista de **Atendimentos**, substituir o botão visual `+` pelo ícone de **Equipe** já usado na navegação (`UsersRound` ou o componente equivalente existente). Esse atalho passa a abrir uma lista de pessoas da equipe para iniciar uma conversa de chat interno.

A escolha deixa de depender de digitar/colar contato: o usuário vê uma lista real da equipe, com foto/avatar, nome e presença `Online` ou `Offline`, em um card/modal com a mesma linguagem visual do diálogo de “Novo atendimento” da referência. Ao selecionar alguém, o CRM abre ou reutiliza a conversa direta interna e a mostra no painel de conversa.

## Base e branch

1. Atualize referências remotas sem alterar `main`.
2. Crie um worktree a partir de `origin/main` e branch `codex/e86-chat-interno-pela-equipe`.
3. Leia integralmente `AGENTS.md` e os componentes/contratos atuais de chat interno antes de editar.
4. Não faça merge, rebase, reset, deploy ou chamada externa real.

## Comportamento obrigatório

### Atalho no cabeçalho de Atendimentos

- Troque somente o ícone `+` pelo ícone de Equipe, com rótulo acessível e tooltip vindos de `textos.json`.
- O botão deve abrir o seletor de pessoa para **chat interno**, não um novo atendimento de WhatsApp.
- Preserve a capacidade existente de criar “Novo atendimento” externo. Se o `+` era sua única entrada, mova essa ação para o menu de reticências existente, sem duplicar controles e sem alterar seu contrato/fluxo.
- Não transforme o botão de filtro/ajustes ao lado em ação de chat.

### Seletor de pessoa

- Reaproveite a fonte/endpoint e os tipos já existentes de contatos do chat interno. Primeiro audite se já retornam foto, nome, id e presença.
- Se foto ou presença não estiverem disponíveis no contrato atual, estenda-o de forma mínima e compatível, com autorização no backend e testes; não crie dados mockados nem calcule presença no frontend.
- Exiba apenas pessoas com quem o usuário autenticado pode abrir conversa interna, nunca o próprio usuário e nunca dados de cliente/lead.
- Apresente um diálogo/card centralizado com backdrop, cabeçalho, fechar, busca por nome, lista com foto/avatar e indicador/rótulo de presença. Siga os raios, superfícies, espaçamentos, foco e responsividade do modal “Novo atendimento” existente.
- Carregamento, lista vazia e erro devem usar estados reais e textos de catálogo. O botão de retry, se existir, apenas refaz a consulta.
- Ordene online primeiro e, dentro de cada grupo, por nome. Não hardcode nomes, cores ou status.
- A presença deve refletir a fonte de verdade atual e atualizar/invalidate pelo mecanismo já usado pelo shell/chat, sem polling novo.
- Teclado e leitor de tela: foco inicial no diálogo, Escape fecha, navegação de tab não escapa enquanto aberto, cada item possui nome e presença acessíveis, e a seleção é possível por teclado.

### Abrir a conversa

- Selecionar uma pessoa chama o fluxo existente de abrir/reutilizar conversa direta. Não crie conversa duplicada sob clique duplo ou concorrência.
- Feche o seletor somente após sucesso; em erro, mantenha-o aberto e mostre uma mensagem útil de catálogo.
- Após sucesso, selecione a conversa interna no mesmo layout de Atendimentos e carregue seu histórico/composer.
- Não envie mensagem automática, não transfira lead e não crie atendimento externo.
- Preserve o comportamento de chat interno já entregue: autoria, mensagens, anexos, reações e autorização.

## Segurança e arquitetura

- A listagem e abertura de conversa devem respeitar a autorização existente no backend. Não exponha e-mail, telefone, cargo, dados de perfil adicionais ou presença de usuários que o solicitante não possa contatar.
- Não use `window.location.href` para uma navegação interna nova; reutilize o estado/roteamento do shell existente.
- Não adicione WebSocket, tabela, migration ou endpoint se os contratos atuais já resolvem o caso. Se qualquer um for inevitável, explique a lacuna e cubra autorização e testes negativos.
- Textos em `textos.json` + schema Zod; cores exclusivamente por tokens semânticos; sem mocks ou dados fictícios.

## Validação obrigatória

### Testes

- Teste o botão com o novo ícone, nome acessível e ausência do `+` como iniciador de chat.
- Teste que “Novo atendimento” externo continua alcançável e preserva seu fluxo.
- Teste loading, vazio, erro/retry, busca, ordenação online/offline, exclusão do usuário atual e foto/avatar de fallback.
- Teste que selecionar um contato abre/reutiliza a conversa correta, não envia mensagem e não cria atendimento externo.
- Se houver alteração backend, cubra autorização positiva e negativa, isolamento e concorrência com Testcontainers.

### Navegador real

- Em ambiente local autenticado, abra Atendimentos em desktop e 390 px; abra o seletor, pesquise, valide foto/avatar e status, selecione uma pessoa online e uma offline em execuções separadas e confirme a conversa interna.
- Confirme ausência de overflow horizontal, foco visível, fechamento por Escape e que o fluxo de Novo atendimento externo ainda está acessível.
- Gere screenshots de desktop e mobile usando somente dados de demonstração/local.

### Comandos

- `cd frontend && npm ci`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build`.
- Se backend for tocado: `cd backend && ./mvnw clean verify` com Java 21 e Testcontainers.
- `git diff --check`.

## Commit, push e CI

- Faça commits Conventional Commits e envie a branch para `origin/codex/e86-chat-interno-pela-equipe`.
- Abra PR contra `main` somente após verificações locais.
- Aguarde CI remota e informe URL/número da run e o resultado dos jobs. Não faça merge ou deploy sem autorização posterior.

## Relatório final

Siga as sete seções de `AGENTS.md`. Inclua SHA, branch, confirmação do push, arquivos/contratos alterados, evidências de cada fluxo, screenshots, CI, decisões de reutilização/extensão de contrato e qualquer limitação do ambiente local.
