# E84c — Sidebar dinâmica sem sobrepor o chat e com hover suave

## Contexto

Na E84, a sidebar retraída abre temporariamente em `absolute`/sobreposição. Isso cobre a lista de atendimentos e o chat, como na captura. Além disso, a expansão começa rápido demais e parece brusca.

Corrija apenas esse comportamento visual/interativo. Preserve a E84: por padrão a sidebar começa retraída, abre ao hover/foco, o botão fixa/desafixa, e em tela estreita continua usando navegação inferior.

## Base

1. Trabalhe sobre a branch que contém a E84 integrada; confirme SHA/base antes de editar.
2. Crie worktree e branch `codex/e84c-sidebar-hover-suave` a partir de `origin/main` atual.
3. Leia `AGENTS.md`, `ShellComSidebar`, `Sidebar` e os testes existentes. Não copie a implementação de outra branch manualmente.

## Resultado obrigatório

- Quando a sidebar abrir temporariamente por hover ou foco em viewport largo, ela **não pode cobrir** a lista, o chat, composer, modal ou painel lateral. O layout deve reservar/animar a largura de 76 px para 260 px, deslocando o conteúdo de forma previsível.
- Remova o uso de sobreposição/`absolute` para esse caso. `z-index` não é solução para esconder o problema.
- A animação de largura deve ser suave, com duração/easing perceptivelmente mais calmos que o atual, sem piscar ao entrar/sair e sem recálculo contínuo de layout.
- Inclua uma pequena intenção de hover antes de abrir, para não expandir ao atravessar a sidebar acidentalmente. Fechamento deve ser estável e não recolher enquanto o cursor/foco ainda estiver na sidebar, no botão de fixar ou em popup dela.
- Acessibilidade: foco por teclado abre a sidebar sem atraso indevido, blur para fora a recolhe quando não fixada, Escape e interações existentes não são quebrados, e o botão de fixar continua com `aria-pressed` correto.
- Se fixada, mantém 260 px e não recolhe ao mouse sair. Ao desafixar fora da área, retorna a 76 px.
- Mobile não renderiza sidebar desktop e permanece sem regressão.

## Restrições

- Não altere rotas, autorização, itens de menu, Novidades, presença, logout, chat, painéis de lead, backend ou design tokens.
- Não use timeout não cancelável: limpe timers em saída, unmount e mudança de estado; teste com fake timers.
- Não use `window.location`, CSS global ou números espalhados. Mantenha a configuração de timing no componente/hook de shell, com comentário curto do motivo.

## Testes e validação

- Testes de `ShellComSidebar`/`Sidebar`: começa em 76 px; hover só abre após a intenção; ao abrir o slot/layout tem 260 px e o conteúdo não recebe sobreposição; mouse leave recolhe após atraso; foco abre; fixar/desafixar funciona; timers são cancelados; mobile não monta sidebar.
- Teste de regressão estrutural deve falhar se voltar `absolute`/sobreposição para a expansão temporária.
- Navegador real autenticado em 1440, 1024 e 390 px: passe o mouse pela sidebar, aguarde abertura, confirme que lista/chat são deslocados e clicáveis, não ficam cobertos; grave captura/GIF curto ou screenshots de retraída/expandida/fixada.
- Rode `cd frontend && npm ci`, lint, typecheck, testes, build e `git diff --check`. Backend não deve ser tocado.

## Entrega

- Commit Conventional Commit e push para `origin/codex/e84c-sidebar-hover-suave`.
- Abra PR contra main após validações locais, aguarde CI e informe URL/número de run e resultado por job.
- Sem merge/deploy sem autorização posterior. Relatório nas sete seções de `AGENTS.md`, incluindo tempos de abertura/fechamento escolhidos e motivo.
