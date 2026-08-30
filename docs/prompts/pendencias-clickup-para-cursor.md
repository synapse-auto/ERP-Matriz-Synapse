# Pendências do ClickUp para executar no Cursor

Registrado em 29/08/2026. Estas tarefas ainda não foram concluídas e serão executadas posteriormente no Cursor.

## Em andamento no ClickUp, mas ainda pendentes

1. **Colocar função de emojis robusta, com a maioria dos emojis existentes**
   - A base de reações/picker da E84 existe e a CI passou, mas a tarefa visual ainda precisa de validação/aceite conforme o card do ClickUp.
2. **Deixar ícones de cima da aba de leads 15% maiores**
   - Atenção: o card atual diz **maiores**. Um prompt anterior mencionava menores; confirmar “maiores” como requisito antes de implementar.
3. **Deixar abas 15% menores**
   - Confirmar quais abas o card quer compactar; a interpretação provisória foi tabs/filtros da lista de Atendimentos, não abas do navegador nem a sidebar inteira.

## Outras pendências já identificadas

- Salvar/baixar imagem no chat.
- Campo de mídias e documentos na sidebar/ficha do lead.
- Tag indicando que o lead está sendo atendido pela IA.
- Preenchimento automático de mensagens rápidas.
- Correção do download/visualização de mídia que retorna HTTP 401.
- Correção da regressão visual dos balões de chat.
- Responder e Encaminhar: E87 implementada, mas aguardando validação visual/documentação antes do merge.
- Avaliação automática/remoção da nota manual: E85 ainda não executada.
- Iniciar chat interno pela lista da equipe: E86 ainda não executada.

## Observação

Não considerar esta lista como autorização para alterar código, fazer commit, push, merge ou deploy. Cada execução deve usar branch/worktree próprio e atualizar o status com evidências de testes e validação visual.
