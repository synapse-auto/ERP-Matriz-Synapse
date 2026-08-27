# Prompt E62 — Inbox unificada em Atendimentos: equipe interna e WhatsApp

## Contexto

Leia, nesta ordem, antes de alterar qualquer arquivo:

1. `AGENTS.md`
2. `docs/13-estado-do-projeto.md`
3. `docs/prompts/COMO-ESCREVER-PROMPTS.md`
4. `docs/prompts/prompt-E44-chat-interno.md`
5. `docs/prompts/prompt-E59-ligar-e-validar-chat-interno.md`

O relatório do agente não é evidência. Confira o código, os contratos, as migrations, os testes e o comportamento real.

O pedido de produto é: a conversa interna deve aparecer dentro da tela `Atendimentos`, misturada à lista normal pela ordem da última mensagem. Cada item precisa indicar claramente se é cliente/WhatsApp ou equipe interna. O botão `+` ao lado do título de Atendimentos deve permitir iniciar conversa interna ou cadastrar/iniciar contato novo no WhatsApp.

## Estado confirmado antes desta etapa

- O chat interno atual vive em `frontend/src/components/chat-interno/pagina-chat-interno.tsx` e na rota `/chat-interno`.
- A lista atual de Atendimentos (`frontend/src/components/atendimentos/lista-conversas.tsx`) consulta apenas `GET /api/v1/atendimentos?visao=...` e trabalha exclusivamente com `leadId`.
- O chat interno possui API própria em `/api/v1/chat-interno/...`, tabelas próprias, participação individual e RLS.
- O backend não possui hoje um endpoint de criação de lead/contato novo equivalente a `POST /api/v1/leads`.
- A árvore pode conter alterações não commitadas da E60/E61. Preserve-as; não faça reset, commit ou push sem autorização explícita do Marcondes.

## Decisão arquitetural obrigatória

Crie uma inbox unificada de leitura, mas mantenha os domínios separados:

- conversa com cliente continua sendo `Atendimento`, com lead, canal, etapa, responsável, janela de 24h e ações de atendimento;
- conversa da equipe continua sendo `chat_interno`, sem lead, sem canal WhatsApp, sem transferência, sem finalização de atendimento e sem painel de detalhes do cliente;
- nunca grave uma conversa interna como linha de `atendimento` e nunca conceda ao chat interno acesso a dados de lead por conveniência de tela;
- a união deve ser feita no backend/read model ou em uma composição que preserve paginação e ordenação global. Não busque duas listas arbitrariamente no frontend e ordene um recorte incompleto;
- preserve as APIs específicas existentes enquanto o novo contrato é introduzido, salvo prova de que uma alteração compatível é suficiente.

## Contrato da inbox

Defina e documente um DTO discriminado para a lista unificada, com pelo menos:

- `tipo: CLIENTE | EQUIPE_INTERNA`;
- identificador próprio da origem (`atendimentoId` para cliente, `conversaId` para equipe);
- nome, avatar/identificador visual, prévia da última mensagem, `ultimaMensagemEm` e `naoLidas`;
- campos específicos de cliente somente quando `tipo = CLIENTE`;
- participante/equipe somente quando `tipo = EQUIPE_INTERNA`.

O endpoint deve:

- ordenar no servidor por `ultimaMensagemEm DESC`, com desempate determinístico por identificador;
- considerar a data da última mensagem, não a data de criação da conversa quando já houver mensagem;
- aplicar limite e cursor sem duplicar ou perder itens entre páginas;
- devolver conversa interna somente ao usuário que participa dela;
- manter para clientes exatamente o recorte de visibilidade existente, incluindo `RN-CRM-01`, `VisibilidadeLeadSpecification` e RLS;
- não usar `SELECT *`, entidade inteira ou campos pesados de detalhe em listagem;
- definir explicitamente como os filtros `ATIVOS`, `PENDENTES` e `POTENCIAIS` se comportam: a recomendação é que conversas internas apareçam em `TODOS` e não sejam contadas nos status exclusivos de atendimento. Registre a decisão no relatório e preserve os badges atuais de status de clientes;
- continuar degradando de forma segura se uma fonte falhar. A indisponibilidade do chat interno não pode derrubar a aba Atendimentos nem o caminho crítico de mensagens de clientes.

Se a fronteira de módulos impedir uma consulta direta, implemente uma porta de read model no lugar correto e faça a composição na aplicação. Não crie dependência invertida de `crm-equipe` para `crm-atendimento` nem coloque SQL de um módulo dentro do outro sem justificar.

## Lista e seleção na tela de Atendimentos

- A lista deve exibir clientes e equipe interna na mesma hierarquia de recência global.
- O item interno precisa ter tag, ícone ou outro símbolo visual inequívoco, vindo do catálogo de textos e dos design tokens. O item de cliente deve continuar identificável como WhatsApp/canal externo.
- A diferenciação deve existir também no cabeçalho e no composer quando uma conversa interna estiver aberta, para evitar envio equivocado ao cliente.
- A seleção deve ser discriminada por tipo e id. Não use `leadId` como chave universal.
- Ao abrir equipe interna, renderize apenas o histórico/composer do chat interno. Não chame `PainelDaConversa`, detalhes de lead, transferência, finalização, tags, janela de 24h ou endpoints de atendimento.
- Ao abrir cliente, preserve integralmente o fluxo existente, inclusive leitura, tempo real, transferência, finalização, composer, mídia, mensagens programadas e painel do lead.
- A rota `/chat-interno` pode permanecer como compatibilidade, mas a navegação principal deve levar à inbox de Atendimentos. Se for redirecionada, preserve deep links e não deixe duas listas divergentes como fontes de verdade.
- O item da sidebar não deve criar uma segunda experiência concorrente. A feature flag `chat_interno` continua sendo a capacidade que controla se os itens e o botão interno aparecem.
- Não introduza dados mockados. Se o endpoint não estiver disponível, mostre estado vazio/erro real e não um contato inventado.

## Botão `+` no cabeçalho da lista

Posicione um botão acessível ao lado de Atendimentos, como na referência visual. O menu deve ter dois fluxos reais:

### Nova conversa interna

- listar somente usuários ativos elegíveis, sem senha, token ou dados desnecessários;
- abrir/reutilizar a conversa direta idempotente existente;
- selecionar imediatamente a conversa interna na inbox;
- atualizar a lista e os não lidos sem F5;
- manter a regra de participação: nem gestor nem administrador podem ler uma conversa da qual não participam apenas por causa do novo painel;
- reaproveitar o endpoint existente de chat interno ou criar somente o contrato mínimo faltante, com teste de autorização negativo.

### Novo contato WhatsApp

Este fluxo não existe hoje no backend. Não entregue um botão fantasma.

Faça primeiro o levantamento dos casos de uso e contratos existentes para:

1. normalizar telefone no formato canônico já usado pelo projeto;
2. localizar ou criar lead de forma idempotente;
3. associar o lead ao canal WhatsApp ativo sem escolher canal por nome de cliente;
4. abrir ou reutilizar um atendimento válido;
5. permitir o próximo passo de envio somente pelas regras reais do canal, janela de 24h, templates e outbox.

Se for necessário criar endpoint, migration ou contrato novo, implemente em hexagonal, com autorização por recurso, RLS/Specification quando aplicável, RFC 7807, OpenAPI e teste de contrato. Toda chamada externa ao WhatsApp deve continuar fora do request síncrono e protegida pelo fluxo de outbox/circuit breaker existente.

O formulário deve exigir telefone válido e tratar duplicidade de telefone sem criar dois leads. Nome/empresa só podem ser persistidos se houver caso de uso e campos existentes para isso. Se não houver canal WhatsApp ativo ou a política não permitir o envio inicial, o sistema deve explicar o bloqueio e não criar uma linha que pareça uma conversa ativa.

Se a regra de negócio para “novo contato WhatsApp” exigir template aprovado, opt-in ou outro dado que não esteja definido no repositório, pare essa parte no ponto correto, registre a decisão necessária e não invente uma política.

## Tempo real, leitura e cache

- Mensagem nova de cliente continua invalidando/atualizando a lista de clientes.
- Mensagem nova de equipe interna deve atualizar a inbox unificada, o item correto e o contador individual sem F5.
- A assinatura do WebSocket deve continuar usando a fila pessoal existente; não crie broadcast novo nem reutilize o tópico de atendimento para chat interno.
- Reconexão deve recarregar a inbox e convergir; o evento em tempo real é conveniência, não fonte de verdade.
- Leitura interna e leitura do cliente são independentes. Abrir uma conversa interna não pode marcar atendimento como lido, e vice-versa.
- Evite corrida de cache entre as duas origens e não duplique uma mensagem quando houver evento seguido de recarga HTTP.

## Segurança e estabilidade

- Teste que usuário não participante não lista, abre, lê ou envia mensagem interna, inclusive com papel `GESTOR` e `ADMINISTRADOR`.
- Teste que usuário sem visibilidade não recebe cliente de outro atendente na inbox unificada.
- Não faça uma consulta pesada ou chamada externa síncrona no caminho de abertura da aba Atendimentos.
- O chat interno desligado pela feature flag não pode aparecer na lista nem no menu `+`; quando ligado, a lista deve continuar acessível mesmo se não houver conversas internas.
- Não coloque lógica de isolamento por nome da Estrutural Vidros; use capacidade/feature flag.

## Testes obrigatórios

### Backend

- contrato do DTO discriminado `CLIENTE`/`EQUIPE_INTERNA`;
- ordenação global por última mensagem, incluindo atualização de uma mensagem interna que ultrapassa clientes mais antigos;
- desempate determinístico e paginação/cursor sem duplicação;
- conversa interna sem mensagem e conversa de cliente sem mensagem, se ambas forem permitidas pelo contrato;
- visibilidade negativa de cliente e participação negativa de chat interno;
- abertura idempotente da conversa interna;
- criação/reuso de contato WhatsApp, caso o contrato seja implementado;
- falha de uma fonte não torna a aba inteira indisponível;
- integração com Testcontainers/Postgres real, RLS e tempo real conforme as proteções existentes.

### Frontend

- item interno e item cliente aparecem juntos na ordem recebida do servidor;
- tag/ícone e cabeçalho diferenciam equipe interna de cliente;
- seleção interna não dispara chamadas de atendimento;
- seleção cliente não perde o comportamento atual;
- botão `+` abre as duas opções e cada opção só aparece quando há fluxo real;
- nova conversa interna abre/reutiliza e atualiza cache;
- novo contato WhatsApp valida telefone, trata duplicidade e exibe bloqueios reais;
- evento `CHAT_INTERNO_MENSAGEM` e evento de cliente atualizam a inbox sem F5;
- feature flag desligada esconde o conteúdo interno;
- testes de acessibilidade para botão, menu, foco e nomes alternativos.

Use Awaitility/espera por condição para assíncrono; não use `Thread.sleep` nem asserção imediata em efeito de fila/WebSocket.

## Validação e relatório

- conferir branch, HEAD, `git status` e diff antes e depois;
- `cd backend && ./mvnw clean verify` com Java 21 e Testcontainers;
- frontend: suíte relevante, `npm run typecheck`, `npm run lint` e `npm run build`;
- `git diff --check`;
- validar visualmente em navegador com uma conta participante e uma conta não participante, se o ambiente estiver disponível;
- não chamar build local de CI verde. CI só é verde com número da run após push autorizado;
- não fazer commit ou push sem autorização explícita do Marcondes.

Relate no formato de `AGENTS.md`: commit/estado, pronto item a item com evidência, decisões, divergências, bugs, fora de escopo e decisões necessárias. Informe claramente se o fluxo “Novo contato WhatsApp” foi realmente implementado ponta a ponta ou ficou bloqueado por contrato/política ausente.
