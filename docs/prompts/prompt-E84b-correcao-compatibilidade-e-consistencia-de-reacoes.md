# E84b — Correção obrigatória: seletor de emojis e consistência de reações em tempo real

## Contexto e ponto de partida

Revise a implementação local da E84 na branch `codex/e84-sidebar-reacoes`, HEAD `46b9c67`, antes de qualquer commit ou push adicional. Não faça merge, rebase, reset, force-push, deploy ou alteração em `main`.

Esta etapa corrige dois problemas encontrados na revisão do diff. Preserve o restante da E84 (sidebar dinâmica, autorização, RLS, persistência, cópia, picker completo e os dois tipos de conversa).

Leia integralmente `AGENTS.md` e as skills relevantes antes de editar. Aplique o padrão de arquitetura vigente; não introduza dado mockado, string de UI fora de `textos.json`, cor fixa ou migração alterada.

## Problema 1 — dependência incompatível com React 19

O `package.json` adicionou `@emoji-mart/react@1.1.1`, cujo próprio `peerDependencies` declara `react: ^16.8 || ^17 || ^18`. A aplicação usa React `19.2.4` e o `overrides` atual falsifica essa compatibilidade.

Isso não é uma solução aceitável: `npm run build` e testes de renderização não provam que o picker funcionará no browser com React 19.

### Obrigatório

- Remova o `overrides` que força `@emoji-mart/react` a aceitar React 19.
- Use uma solução de picker que declare compatibilidade com a versão de React efetivamente instalada **ou** integre o picker como Web Component/adapter sem depender de peer React incompatível.
- Mantenha dados locais/versionados, sem CDN, e preserve busca, categorias, tons de pele e seleção de emoji Unicode completo.
- Mantenha lazy loading no cliente para não aumentar indevidamente a rota de Atendimentos.
- Não alegue “emoji estilo iPhone” em Windows: com renderização nativa, iOS usará o conjunto do sistema e Windows usará o seu. Se trocar para um conjunto gráfico fixo, registre sua licença e o motivo.
- Crie teste que falharia se voltar um peer override incompatível e teste de renderização/interação do picker escolhido.
- Valide em navegador real: abrir a ação de uma mensagem, abrir o picker completo, pesquisar, navegar por pelo menos duas categorias, escolher um emoji com modificador (por exemplo `👍🏽`) e confirmar que a reação é enviada/exibida. Use dados reais do ambiente local; se isso for inviável, declare a limitação e não marque a validação visual como concluída.

## Problema 2 — marcador `reagi` fica incorreto entre abas do mesmo usuário

O evento WebSocket de reação contém apenas `{ emoji, quantidade }`. O cache do frontend substitui esses totais e preserva o `reagi` anterior pelo emoji. Se o mesmo usuário trocar sua reação em uma segunda aba/sessão, a primeira recebe o novo resumo público, mas não tem informação suficiente para calcular qual é sua reação atual; ela pode deixar de marcar a reação própria até um reload.

### Obrigatório

- Corrija o contrato e o cache para que, após qualquer evento, o estado local de **cada** usuário seja correto — inclusive duas abas autenticadas como o mesmo usuário, troca de emoji e remoção.
- Não envie nomes de reatores, lista de reatores ou metadados privados a outros participantes.
- São aceitas duas estratégias:
  1. transportar no evento apenas o mínimo necessário sobre a alteração (ator e nova reação/remoção) e reconciliar com o usuário autenticado; ou
  2. invalidar/refazer a consulta de mensagens após evento de reação, sem somar contadores localmente.
- A estratégia deve ser igual para Atendimento e Chat interno, continuar pós-commit e não criar polling.
- Preserve resposta HTTP imediata para a aba que realizou a ação, autorização/RLS e ausência de N+1 no histórico.
- Adicione testes que reproduzam explicitamente: duas abas do mesmo usuário, uma troca `👍` por `❤️`, a outra recebe o evento e termina com apenas `❤️` marcada como própria; depois uma remoção deixa nenhuma reação própria. Cubra Atendimento e Chat interno ou extraia teste compartilhado que prove os dois adaptadores.
- Revalide que eventos duplicados não dobram quantidades e que eventos de outro usuário preservam corretamente a reação própria do destinatário.

## Não fazer

- Não implementar Responder, Encaminhar, Fixar, Favoritar, Apagar, Denunciar ou IA; continuam fora de escopo.
- Não tocar na finalização de atendimento, integrações externas, outbox de mensagens existente, permissões de lead ou dados de negócio não relacionados.
- Não alterar migration V44 já criada; se uma mudança de banco se provar inevitável, crie uma migration nova e explique por que a alternativa sem schema não atende. A expectativa é que não seja necessária.

## Validação obrigatória

- `cd frontend && npm ci`.
- `cd frontend && npm run lint`, `npm run typecheck`, `npm test -- --run` e `npm run build`.
- `cd backend && ./mvnw clean verify` com Java 21 e Testcontainers.
- `git diff --check`.
- Execute os testes direcionados de reações e de sidebar; identifique por nome os cenários novos.
- Registre a versão final da dependência e a evidência objetiva de compatibilidade com React 19, sem `overrides` de peer dependency.

## Commit e publicação

- Faça um commit Conventional Commit exclusivo desta correção e envie para `origin/codex/e84-sidebar-reacoes`.
- Aguarde a CI remota e informe URL/número da run e status por job. Não declare CI verde sem essa evidência.

## Relatório final

Siga exatamente as sete seções de `AGENTS.md`, na ordem. Inclua o SHA, branch, confirmação de push no `origin`, quantidade de arquivos, compatibilidade final da biblioteca, a estratégia escolhida para sincronização entre abas, provas de que nenhum dado de reator foi exposto, resultado da validação visual e CI remoto. Declare honestamente qualquer impedimento.
