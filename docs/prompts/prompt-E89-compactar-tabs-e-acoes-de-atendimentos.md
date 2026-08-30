# E89 — Compactar tabs e ações do cabeçalho de Atendimentos

## Objetivo

Reduzir em aproximadamente **15%**, sem comprometer toque, foco ou legibilidade, os tabs/filtros da lista de Atendimentos (Todos, Ativos, Pendentes, Potenciais) e os ícones de ação no cabeçalho dessa lista (menu, iniciar conversa/equipe e filtros). Esta é somente uma tarefa de densidade visual, não uma mudança funcional.

## Base

- Crie worktree da `origin/main` e branch `codex/e89-compactar-atendimentos`.
- Leia `AGENTS.md` e os componentes reais de tabs/cabeçalho/lista. Não altere fonte global, sidebar, contratos ou backend.

## Obrigatório

- Meça as dimensões atuais e aplique redução proporcional próxima de 15% apenas aos elementos listados, mantendo alvo clicável mínimo acessível de 40x40 px para ícones.
- Preserve textos, badges, estados ativo/hover/focus/disabled, contagens, atalhos de teclado e tooltip/aria-label.
- Em 390 px, tabs não podem cortar, sobrepor ou gerar overflow horizontal; use rolagem/compactação já prevista pelo design somente se necessária.
- Use classes/tokens existentes, sem cor fixa, número mágico espalhado ou mudança global de `Button`/`Tabs` que afete páginas alheias.
- Não alterar o comportamento do novo atalho de equipe da E86; se E86 ainda não estiver na base, preserve o ícone/ação equivalente atual.

## Validação

- Testes dos componentes garantem dimensões/classes de compactação e preservação dos nomes acessíveis/ações.
- Navegador real em 1440, 1024 e 390 px: lista, tabs, badges, menu e filtros sem overflow; screenshots antes/depois com dados locais.
- `npm ci`, lint, typecheck, testes, build e `git diff --check`.

## Entrega

- Commit/push em `origin/codex/e89-compactar-atendimentos`, PR e CI verde antes de merge. Relatório nas sete seções de `AGENTS.md`.
