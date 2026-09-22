# Notificações em tempo real

Esta entrega centraliza no navegador as notificações de mensagens novas e de mudança de responsável, mantendo o atendimento e o chat interno como origens distintas. O contrato de envio das mensagens não muda.

## Transporte e autorização

- O cliente mantém uma única conexão STOMP por sessão, compartilhada pelos componentes que usam tempo real.
- A fila pessoal é `/user/queue/notificacoes`, protegida pelo interceptor de autenticação.
- Mensagens e avisos legados continuam em `@TransactionalEventListener(phase = AFTER_COMMIT)`. Mudanças
  de estado usam a outbox transacional `tempo-real.atendimento.estado.v1`: a linha é gravada junto da
  ação e um worker publica no Redis somente após o commit. O subscriber calcula na entrega os usuários
  ativos autorizados pela mesma regra da RLS; isso evita que uma conversa nova fique invisível sem
  ampliar o recorte de acesso.
- O chat interno continua usando seu canal Redis e entrega a mensagem pessoal aos destinatários já calculados pelo caso de uso, sem incluir o autor.
- Edições usam o mesmo canal e o evento `CHAT_INTERNO_MENSAGEM_EDITADA`, com `mensagemId`, `conversaId`,
  conteúdo atual e `editadoEm`. A entrega ocorre após commit; o frontend invalida apenas o cache do chat
  interno e não cria um aviso de nova mensagem.
- O payload de mensagem externa inclui `eventoId`, `leadNome` e `destinatarios` apenas no backplane. A lista de destinatários é removida antes da entrega ao fio da conversa e antes da fila pessoal.

O transporte usa o mesmo `NEXT_PUBLIC_WS_URL` e as mesmas configurações de Redis já existentes.
O backend anuncia no frame STOMP `CONNECTED` o perfil de reconexão configurado por instância,
`WS_RECONEXAO_ATRASO_INICIAL_MS`, `WS_RECONEXAO_FATOR` e
`WS_RECONEXAO_ATRASO_MAXIMO_MS`; assim a imagem genérica do frontend não precisa conter variável
pública de cada filho.

## Decisão no frontend

`ServicoDeNotificacoesTempoReal` é o ponto único para decidir se um evento deve atualizar cache, mostrar aviso e tocar som. A recepção permanece em `ConexaoTempoReal`; a apresentação está em `NotificacoesTempoReal`.

O serviço:

- deduplica por `eventoId`, com fallback para o identificador da mensagem ou composição do evento;
- não mostra nem toca mensagem do próprio usuário; eventos de saída (atendente/IA) também atualizam o cache, mas não são tratados como nova mensagem recebida;
- não mostra nem toca mensagem da conversa atualmente aberta, mas ainda invalida o cache;
- trata mídia com rótulo seguro do catálogo (Imagem, Áudio, Documento, Vídeo ou Localização), sem tentar exibir conteúdo, URL ou token;
- invalida `atendimentos` para mensagens/transferências e `chat-interno` para eventos internos;
- coalesce sons dentro de 1,5 segundo para não produzir uma sequência agressiva durante rajadas.

O aviso visual informa origem, contato ou remetente, descrição e uma prévia segura de texto. Mídia não é convertida em preview. Clicar no aviso navega para o atendimento ou conversa interna por identificador, sem depender do filtro visível na tela.

## Som e acessibilidade

O som é curto e discreto, produzido com Web Audio API. A primeira interação de ponteiro ou teclado tenta desbloquear o contexto; se o navegador bloquear autoplay, o aviso visual continua funcionando e nenhum pedido de permissão nativa é feito.

A preferência é exposta em Configurações, começa habilitada por padrão e é persistida por usuário no armazenamento local do navegador. Os textos de título, descrição, acessibilidade, mídia e continuação de preview vêm de `textos.json`.

## Reconexão e leitura

O `Client` do stompjs continua com o seu reconector fixo desabilitado (`reconnectDelay: 0`) para não
somar dois reconectores. `ConexaoTempoReal` agenda uma única nova conexão por vez com backoff
exponencial e jitter entre 50% e 100% do atraso calculado. A primeira tentativa nunca é imediata,
e um `CONNECTED` reinicia a contagem; assim, muitas abas não retornam ao mesmo instante depois de
um restart, mas uma conexão que voltou a ficar estável não herda atrasos antigos.

O fluxo de atendimento usa o sinal mínimo `ATENDIMENTO_ESTADO` e o snapshot autorizado
`GET /api/v1/atendimentos/{atendimentoId}/estado`. Após cada reconexão, o snapshot termina antes de o
cliente aceitar incrementais; o backfill HTTP recupera mensagens recebidas durante a queda. A fila
pessoal também atualiza a inbox fora da conversa, enquanto a assinatura selecionada revalida a RLS a
cada mudança de estado. Ordem, deduplicação, payload e diagnóstico estão documentados em
[Consistência em tempo real dos atendimentos](40-consistencia-tempo-real-atendimentos.md).

Ao receber mensagem no chat interno aberto, o componente solicita novamente a marcação como lida para
que o badge não fique pendente. O contrato de chat interno não foi alterado.

## Cobertura

Os testes cobrem a decisão comum para atendimento e chat interno, deduplicação, autoria, conversa ativa, coalescência, reações/remoções e os envelopes Redis/STOMP com audiência. A execução local do conjunto de integração completo depende dos serviços Docker; a validação final deve ser feita no CI com Postgres, Redis e demais serviços do workflow.
