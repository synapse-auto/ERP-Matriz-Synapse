# Prompt E63 — Fechamento da inbox unificada em Atendimentos

## Contexto

Leia, nesta ordem, antes de alterar qualquer arquivo:

1. `AGENTS.md`
2. `docs/13-estado-do-projeto.md`
3. `docs/prompts/COMO-ESCREVER-PROMPTS.md`
4. `docs/prompts/prompt-E62-inbox-unificada-atendimentos-chat-interno.md`

O relatório da E62 não é evidência. Confira o código, os contratos, as migrations,
os testes e o comportamento real antes de declarar qualquer item concluído.

O objetivo desta etapa é fechar os gaps encontrados na revisão da E62. Não amplie
o escopo para criar o fluxo de novo contato WhatsApp: esse fluxo continua bloqueado
até existir decisão de negócio sobre opt-in, template, canal ativo e primeiro envio.

Estado confirmado na revisão:

- `HEAD` e `origin/main` estão em `7399b726`, na branch `main`.
- A E62 ainda está sem commit e sem push.
- O endpoint `GET /api/v1/atendimentos/inbox` e o DTO discriminado já existem.
- O backend atualmente compõe listas completas em memória e só depois ordena e corta a página.
- O endpoint devolve `proximoCursor`, mas `useAtendimentos` não o consome.
- A suíte frontend passa com 42 arquivos e 165 testes, porém não há cobertura específica
  das novas ramificações da E62.
- O backend possui teste unitário do caso de uso, mas não possui teste de integração
  do endpoint unificado com payload, visibilidade e participação reais.
- A tela invalida `inbox-unificada`, embora a query usada seja `['atendimentos', visao]`.

Preserve as alterações existentes da E60, E61 e E62. Não faça reset, não descarte
mudanças de terceiros, não faça commit e não faça push sem autorização explícita do
Marcondes.

## Critérios de aceite

### 1. Paginação e ordenação global

- O contrato de `GET /api/v1/atendimentos/inbox` deve continuar sendo:
  `visao`, `limite` e cursor opaco, com `tipo=CLIENTE|EQUIPE_INTERNA`.
- A ordenação deve ser global por `ultimaMensagemEm DESC`, usando desempate
  determinístico pelo identificador da origem.
- Uma mensagem interna nova que ultrapassa clientes antigos deve aparecer na posição
  correta.
- O cursor não pode duplicar nem perder itens entre páginas.
- Não carregue duas listas completas sem limite para depois cortar em memória. Use
  consultas limitadas/keyset ou outra composição realmente limitada e justificada,
  sem sacrificar a ordenação global.
- A solução não pode introduzir consulta pesada ou bloquear o caminho crítico da aba
  Atendimentos. Se a fronteira de módulos exigir composição em `crm-app`, mantenha
  portas existentes e não inverta dependências.
- Defina o comportamento para `ultimaMensagemEm` nula e empate de timestamps; cubra
  ambos com teste.

### 2. Consumo do cursor no frontend

- A lista de Atendimentos deve consumir `proximoCursor` quando houver outra página.
- Escolha uma interação coerente com a tela atual, como carregamento incremental ao
  rolar ou um carregamento explícito; não crie controle fantasma.
- A ordem recebida do servidor deve ser preservada na concatenação das páginas.
- Eventos de cliente, eventos `CHAT_INTERNO_MENSAGEM` e reconexão devem invalidar ou
  atualizar todas as páginas sem duplicação.
- A query key deve ser única e coerente. Remova a invalidação morta
  `['inbox-unificada']` ou passe a usar essa chave de forma consistente; não mantenha
  duas fontes de verdade.

### 3. Contrato, autorização e segurança

Crie um teste de integração real do endpoint, preferencialmente em
`backend/crm-app/src/test`, usando Testcontainers/Postgres e o caminho HTTP real.
O teste deve provar:

- JSON discriminado de cliente e equipe interna;
- campos específicos de cliente ausentes ou nulos conforme o contrato quando o item
  é `EQUIPE_INTERNA`;
- autenticação obrigatória;
- usuário participante consegue listar sua conversa interna;
- usuário não participante não lista a conversa interna, inclusive quando o usuário
  tem papel `GESTOR` ou `ADMINISTRADOR`;
- usuário sem visibilidade não recebe o cliente de outro atendente;
- conversa interna só aparece em `TODOS`;
- `chat_interno` desligado não inclui itens internos;
- falha controlada de uma fonte não derruba a lista de clientes;
- limite, cursor, empate determinístico e ausência de duplicação entre páginas.

Não confie em usuário de teste superusuário/dono para validar RLS. O teste negativo
precisa realmente falhar se a proteção deixar de funcionar. Não altere migration já
aplicada; só crie migration nova se houver necessidade comprovada.

Atualize OpenAPI e `docs/04-adrs-e-api.md` para que a evidência cite os testes reais,
não apenas o controller. Não declare "paginada" se a implementação não garantir o
comportamento descrito.

### 4. Cobertura específica do frontend

Adicione testes que exerçam os componentes e hooks alterados, sem substituir a
suíte existente por mocks que escondam o contrato:

- item `CLIENTE` e item `EQUIPE_INTERNA` aparecem juntos na ordem recebida;
- tag/ícone e cabeçalho distinguem equipe interna de cliente;
- seleção interna usa `tipo + conversaId` e não dispara histórico, leitura ou ação
  de atendimento;
- seleção de cliente preserva o fluxo atual;
- o botão `+` aparece somente com a feature flag ligada;
- o fluxo de nova conversa interna lista usuários elegíveis, abre/reutiliza a
  conversa e atualiza a seleção/cache;
- evento interno atualiza o item correto sem marcar atendimento de cliente como lido;
- o carregamento da próxima página usa o cursor e não duplica itens;
- acessibilidade do botão `+`, menu, foco e nome alternativo;
- nenhum teste deve inventar lead, conversa ou contato que não venha do contrato.

Não implemente testes visuais frágeis baseados em classes ou coordenadas. Prefira
roles, nomes acessíveis e comportamento observável.

### 5. Validação visual

Se o ambiente estiver disponível, valide em navegador com:

1. uma conta participante de uma conversa interna;
2. uma conta não participante;
3. feature flag `chat_interno` ligada e desligada;
4. uma inbox contendo cliente e equipe interna;
5. abertura de cliente e abertura de equipe, confirmando que cada painel usa apenas
   seus próprios endpoints e ações;
6. rolagem/carregamento da próxima página e chegada de mensagem nova.

Registre no relatório se a validação foi feita, em qual ambiente e quais fluxos não
foram possíveis. Não chame build local de CI verde; CI só é verde com número de run
após push autorizado.

## Restrições arquiteturais

- Mantenha cliente e equipe interna como domínios separados.
- Não grave chat interno como `Atendimento`.
- Não conceda acesso a lead por conveniência da tela.
- Reutilize `ListarAtendimentosVisiveisUseCase`, `VisibilidadeLeadSpecification`,
  RLS e as políticas de participação existentes.
- Não crie broadcast WebSocket novo.
- Não coloque chamada externa síncrona no caminho da inbox.
- Não use dados mockados no frontend.
- Não introduza string de UI ou cor literal fora dos catálogos/tokens.
- Não introduza variável nova no Dokploy sem atualizar `.env.example`, `README.md` e
  o relatório com ação operacional.
- Java 21 é fixo.

## Validação obrigatória antes do relatório

- conferir branch, `HEAD`, `origin/main`, `git status` e diff antes e depois;
- `cd backend && ./mvnw clean verify` com Java 21 e Testcontainers;
- frontend: testes completos, `npm run typecheck`, `npm run lint` e `npm run build`;
- `git diff --check`;
- não fazer commit ou push.

## Relatório obrigatório

Siga exatamente o formato de `AGENTS.md`:

1. commit e estado, incluindo branch, SHA, quantidade de arquivos e confirmação de
   que não houve commit/push;
2. definição de pronto item a item, com evidência concreta e números;
3. decisões tomadas e por quê;
4. divergências entre documentação e realidade;
5. bugs encontrados, inclusive fora do escopo;
6. o que ficou de fora e por quê;
7. decisões necessárias do Marcondes.

Não declare CI verde sem número de run. Não declare a E63 concluída se a paginação
do frontend, o teste de integração de segurança ou a cobertura específica da nova
inbox não existirem.
