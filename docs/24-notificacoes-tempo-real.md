# Notificações em tempo real

Esta entrega centraliza no navegador as notificações de mensagens novas e de mudança de responsável, mantendo o atendimento e o chat interno como origens distintas. O contrato de envio das mensagens não muda.

## Transporte e autorização

- O cliente mantém uma única conexão STOMP por sessão, compartilhada pelos componentes que usam tempo real.
- A fila pessoal é `/user/queue/notificacoes`, protegida pelo interceptor de autenticação.
- O publisher de atendimento continua em `@TransactionalEventListener(phase = AFTER_COMMIT)` e publica no canal Redis do atendimento. O subscriber faz a entrega pessoal somente para o dono e os participantes ativos do atendimento.
- O chat interno continua usando seu canal Redis e entrega a mensagem pessoal aos destinatários já calculados pelo caso de uso, sem incluir o autor.
- Edições usam o mesmo canal e o evento `CHAT_INTERNO_MENSAGEM_EDITADA`, com `mensagemId`, `conversaId`,
  conteúdo atual e `editadoEm`. A entrega ocorre após commit; o frontend invalida apenas o cache do chat
  interno e não cria um aviso de nova mensagem.
- O payload de mensagem externa inclui `eventoId`, `leadNome` e `destinatarios` apenas no backplane. A lista de destinatários é removida antes da entrega ao fio da conversa e antes da fila pessoal.

Não foi criada variável de ambiente nova. O transporte usa o mesmo `NEXT_PUBLIC_WS_URL` e as mesmas configurações de Redis já existentes.

## Decisão no frontend

`ServicoDeNotificacoesTempoReal` é o ponto único para decidir se um evento deve atualizar cache, mostrar aviso e tocar som. A recepção permanece em `ConexaoTempoReal`; a apresentação está em `NotificacoesTempoReal`.

O serviço:

- deduplica por `eventoId`, com fallback para o identificador da mensagem ou composição do evento;
- não mostra nem toca mensagem do próprio usuário;
- não mostra nem toca mensagem da conversa atualmente aberta, mas ainda invalida o cache;
- trata mídia com texto genérico do catálogo, sem tentar exibir conteúdo de mídia;
- invalida `atendimentos` para mensagens/transferências e `chat-interno` para eventos internos;
- coalesce sons dentro de 1,5 segundo para não produzir uma sequência agressiva durante rajadas.

O aviso visual informa origem, contato ou remetente, descrição e uma prévia segura de texto. Mídia não é convertida em preview. Clicar no aviso navega para o atendimento ou conversa interna por identificador, sem depender do filtro visível na tela.

## Som e acessibilidade

O som é curto e discreto, produzido com Web Audio API. A primeira interação de ponteiro ou teclado tenta desbloquear o contexto; se o navegador bloquear autoplay, o aviso visual continua funcionando e nenhum pedido de permissão nativa é feito.

A preferência é exposta em Configurações, começa habilitada por padrão e é persistida por usuário no armazenamento local do navegador. Os textos de título, descrição, acessibilidade, mídia e continuação de preview vêm de `textos.json`.

## Reconexão e leitura

O fluxo existente de reconexão e backfill HTTP permanece responsável por recuperar mensagens da conversa aberta. A fila pessoal serve como sinal de atualização e aviso fora da conversa. Ao receber mensagem no chat interno aberto, o componente solicita novamente a marcação como lida para que o badge não fique pendente.

## Cobertura

Os testes cobrem a decisão comum para atendimento e chat interno, deduplicação, autoria, conversa ativa, coalescência, reações/remoções e os envelopes Redis/STOMP com audiência. A execução local do conjunto de integração completo depende dos serviços Docker; a validação final deve ser feita no CI com Postgres, Redis e demais serviços do workflow.
