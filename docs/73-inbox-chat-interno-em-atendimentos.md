# Inbox de Atendimentos com conversas do Chat Interno

## Fontes e acesso

`GET /api/v1/atendimentos/inbox` é a fonte paginada da lista padrão. O DTO diferencia `CLIENTE` de `EQUIPE_INTERNA`; a consulta interna usa o usuário autenticado para exigir participação, sem ampliar acesso por papel de gestor. A identidade, histórico e autorização do chat interno continuam separados dos atendimentos e dos canais WhatsApp.

Conversas internas aparecem em `TODOS` e `ATIVOS`, não nos recortes exclusivos de clientes (`PENDENTES`, `POTENCIAIS` e `FINALIZADOS`). A inbox padrão combina as fontes no servidor e consome seu cursor. A variante do painel operacional mantém a leitura de conversas internas pela API existente, sempre filtrada pelo usuário autenticado; exibe-as em `TODOS` e `ATIVOS`, fora de filtros operacionais específicos.

## Atualização sem recarregar

Mensagens internas, edições e remoções chegam pela fila pessoal WebSocket existente. Esses eventos invalidam somente a lista da inbox por meio do agendador compartilhado de Atendimentos, que coalesce rajadas e limita recargas; não recalculam contagens de atendimentos de clientes e não adicionam polling. Reconexão usa refetch do cache HTTP, que continua sendo a fonte de verdade.

O autor não recebe uma cópia do próprio evento. Por isso, mutations locais do chat interno atualizam também o resumo da inbox no sucesso. Marcar uma conversa como lida também atualiza o resumo: o endpoint atual não publica evento de leitura. Erros nessa atualização não bloqueiam o histórico e serão reconciliados no próximo carregamento/reconexão.

## Validação e limitações

Os testes frontend cobrem decisões de invalidação e exibição em `TODOS`/`ATIVOS`. `InboxUnificadaControllerIT` cobre a API real, paginação, conversa direta/grupo e exclusão de não participantes, inclusive gestor/administrador. A validação de duas sessões autenticadas em instância implantada depende de homologação; não foi executada contra produção.

