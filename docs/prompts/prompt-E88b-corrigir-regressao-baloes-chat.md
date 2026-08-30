# E88b — Corrigir regressão visual dos balões de chat

## Problema

Após a E88, os balões da conversa regrediram visualmente. Na captura, a mensagem no topo do histórico fica recortada sob o cabeçalho e a composição de largura/alinhamento/ações da mensagem não preserva a geometria esperada de um chat.

Corrija a regressão visual sem remover as funcionalidades da E88, E84 ou E86 já integradas.

## Base e branch

1. Atualize referências e crie worktree da `origin/main` atual na branch `codex/e88b-corrigir-baloes-chat`.
2. Audite o diff de `85d81dc` e os componentes reais de lista de mensagens, painel da conversa, `BolhaMensagem`, interações/reactions e composer antes de editar.
3. Não faça merge, rebase, reset, deploy ou mudança de backend/contrato/migration.

## Resultado obrigatório

- Nenhuma bolha pode ficar escondida, recortada ou renderizada por baixo do cabeçalho fixo da conversa. O histórico precisa ter padding/scroll container correto no topo e no fundo, inclusive depois de carregar páginas, receber WebSocket, abrir painel de lead ou alternar de conversa.
- Nenhuma bolha pode ficar escondida atrás do composer. A última mensagem deve continuar acessível ao rolar até o fim.
- Restabeleça geometria de chat consistente: largura proporcional ao conteúdo, limite máximo responsivo, cantos arredondados e canto de direção distinguível para recebida/enviada, sem largura mínima artificial que deixe textos curtos com caixa grande.
- Mensagens recebidas mantêm borda/superfície discreta; enviadas mantêm superfície do tema. Não usar cor fixa.
- Ícone/seta de ações e reações não pode empurrar, cobrir, cortar ou alterar a largura da bolha. Em mouse, aparece como affordance; em toque, continua acessível. Não pode ficar visível sem foco/hover de forma indevida nem provocar salto de layout.
- Preservar texto, citação, mídia, áudio, documento, status de entrega, horário, reações, responder/encaminhar quando integrarem, e chat interno. Não recriar componente paralelo.
- Mobile (390 px) não pode ter overflow horizontal nem bolha maior que o viewport útil.

## Testes obrigatórios

- Testes de `BolhaMensagem`/lista/painel que provem mensagens curtas e longas, recebidas e enviadas, mídia, ações/reação e chat interno sem alterar largura/posição indevidamente.
- Teste estrutural do scroll: primeira mensagem não fica sob o cabeçalho; última não fica sob composer; troca de conversa e carregamento incremental preservam a área útil.
- Navegador autenticado com dados de demonstração em 1440, 1024 e 390 px: captura da conversa no topo, meio e fim; mensagens curtas/longas, recebidas/enviadas e com ações. Compare diretamente com a captura do problema.
- `cd frontend && npm ci`, lint, typecheck, testes completos, build e `git diff --check`.

## Restrições

- Não alterar endpoints, RLS, histórico backend, mídia/storage, regras de IA, templates, finalização ou estrutura global de layout.
- Não desfazer tag de IA, seção de mídias, preenchimento de mensagens rápidas ou compactação da E88.
- Não esconder o problema com `overflow-hidden` em elemento pai que elimine conteúdo ou interação.

## Entrega

- Commit Conventional Commit e push em `origin/codex/e88b-corrigir-baloes-chat`.
- Abra PR contra main, aguarde CI remota e informe URL/número da run e resultado dos jobs.
- Não faça merge/deploy sem autorização. Relatório completo conforme `AGENTS.md`, incluindo screenshots de antes/depois e causa técnica identificada.
