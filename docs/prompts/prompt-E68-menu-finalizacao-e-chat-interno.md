# Prompt E68 — menu de finalização na lista e paridade visual do chat interno

> Leia `AGENTS.md`, `docs/13-estado-do-projeto.md` e `docs/prompts/COMO-ESCREVER-PROMPTS.md` antes de
> alterar qualquer arquivo. Entrega em 25/08.
>
> Esta etapa deve ser executada em uma worktree/branch isolada. Não trabalhe no mesmo diretório em que
> outro agente estiver alterando a E65, E66 ou E67. Não faça rebase, merge, cherry-pick, commit ou push
> de alterações de outra etapa. Commite apenas a sua branch e não faça `git push` sem autorização
> explícita do Marcondes.
>
> Ao encerrar, rode `npm run lint`, `npm run typecheck`, `npm test -- --run` e `npm run build`. Se tocar
> o backend, rode também `cd backend && ./mvnw clean verify` com Java 21 e Testcontainers. Sem push,
> escreva `CI não verificado`; só chame CI de verde com o número da run.

---

## Contexto confirmado no repositório

Há dois problemas visuais/funcionais relacionados à tela de Atendimentos.

### 1. O menu de “Finalizar todos” está no cabeçalho errado

Na referência enviada, os três pontos que abrem “Finalizar todos os atendimentos visíveis” ficam ao
lado do título `Atendimentos`, na barra superior da lista de conversas. Hoje a ação está no cabeçalho
da conversa selecionada, em `frontend/src/components/atendimentos/cabecalho-conversa.tsx`, junto das
ações do atendimento atual.

O menu atual usa:

```tsx
const [finalizarTodosAberto, setFinalizarTodosAberto] = useState(false);
const finalizarTodos = useFinalizarAtendimentosVisiveis();
const quantidadeFinalizavel = useQuantidadeAtendimentosFinalizaveis();
```

e abre a confirmação global por meio de `DropdownMenu`, embora esteja visualmente no cabeçalho de uma
conversa. A operação é global e significa “todos os atendimentos visíveis do usuário”, não “o lead
selecionado”. O diálogo de confirmação e os hooks já existentes devem ser preservados.

O botão `Finalizar` do atendimento individual e as ações `Entrar no atendimento`, `Sair do
atendimento` e `Transferir` pertencem ao cabeçalho da conversa e não devem ser confundidos com a ação
global. Apenas o menu da operação global deve sair de `CabecalhoConversa`.

O ponto de destino é `frontend/src/components/atendimentos/lista-conversas.tsx`, na mesma linha do
título `Atendimentos`, próximo aos controles `+` e filtros. O posicionamento deve sobreviver a
viewport estreita sem deslocar o campo de busca nem cobrir o título.

### 2. O chat interno não tem a mesma experiência do chat normal

Na inbox unificada, `frontend/src/components/atendimentos/pagina-atendimentos-cliente.tsx` escolhe
`PainelConversaInterna` quando a conversa é interna. O componente atual, em
`frontend/src/components/chat-interno/painel-conversa-interna.tsx`, tem estrutura própria e mínima:

- cabeçalho com avatar baseado no id da conversa e nomes concatenados;
- indicação simples “Chat interno”;
- mensagens sempre renderizadas como bolhas simples à esquerda;
- nenhuma distinção visual confiável entre mensagem própria e mensagem recebida;
- composer apenas com `Textarea` e botão `Enviar`;
- nenhuma informação de perfil da pessoa da equipe além do texto de participantes.

Existe também a rota legada `frontend/src/components/chat-interno/pagina-chat-interno.tsx`, que possui
outra implementação visual. A tela unificada é o caminho principal, mas as duas rotas não podem
divergir indefinidamente: a mesma conversa interna precisa parecer o mesmo produto quando aberta por
qualquer entrada.

O tipo atual de mensagem já fornece dados suficientes para distinguir autoria:

```ts
interface ChatMensagem {
  id: string;
  conversaId: string;
  remetenteId: string;
  remetenteNome: string;
  conteudo: string;
  enviadoEm: string;
}
```

O contrato atual de conversa fornece apenas `participantes`, última mensagem, data e não lidas. O
contrato atual de contatos internos fornece somente `id` e `nome`, deliberadamente sem dados pessoais.
Não presuma que a string `participantes` seja um nome seguro para avatar ou perfil; ela pode ser uma
lista de nomes ou uma representação agregada.

As imagens anexadas são referência de composição e hierarquia, não fonte de dados. Não copie UUID,
nomes, horários, contagens ou textos das imagens para o frontend.

---

## Bloco 1 — mover a ação global para a barra da lista

### Composição

- Remova de `CabecalhoConversa` somente o `DropdownMenu` que abre `Finalizar todos os atendimentos
  visíveis` e o estado que existe exclusivamente para essa abertura.
- Mova para `ListaConversas` o trigger, o diálogo de confirmação e a integração com
  `useFinalizarAtendimentosVisiveis`/`useQuantidadeAtendimentosFinalizaveis`, ou extraia um componente
  pequeno reutilizável se isso deixar as responsabilidades mais claras.
- O trigger deve ficar na linha do título `Atendimentos`, do lado dos controles da lista, antes ou junto
  do `+` conforme a ordem real que melhor preserve a referência e a acessibilidade. Não o deixe no
  cabeçalho central da conversa.
- Use `MoreHorizontal` e o componente de menu já existente. O botão precisa ter `aria-label` vindo do
  catálogo (`textosFinalizar.todosMenu` ou equivalente existente), não texto literal no JSX.
- A abertura deve funcionar com clique semântico completo. Não use `onMouseDown`, `onMouseUp`,
  `preventDefault`, estado compartilhado com outro dropdown ou lógica que só funcione enquanto o
  ponteiro estiver pressionado.
- O menu deve continuar desabilitando a ação quando a quantidade finalizável estiver carregando ou for
  zero, como ocorre hoje. Não invente uma nova contagem nem mude o significado de “visíveis”.
- Preserve o diálogo: quantidade real, confirmação explícita, cancelamento, estado de envio, retorno
  de sucesso e resultado de finalizados/recusados. Após concluir, invalide as queries que a implementação
  atual já invalida e mantenha a lista consistente.
- Preserve a autorização existente no backend. Não crie endpoint, permissão, migration ou regra de
  visibilidade nova para resolver posicionamento de UI.

### O que deve permanecer no cabeçalho da conversa

- Finalização individual do atendimento selecionado;
- entrada/saída do atendimento, quando aplicável;
- transferência;
- busca, tags, telefone e demais ações que já pertencem à conversa;
- comportamento e autorização existentes.

O cabeçalho não deve continuar exibindo um menu que sugira que a ação global é da conversa aberta.

### Integração com E65/E66/E67

Se a E65 tiver alterado `CabecalhoConversa`, `ListaConversas`, `sidebar.tsx` ou o catálogo, não copie
arquivos de outra worktree. Reaplique somente a mudança necessária sobre a base da sua branch e relate
os conflitos. A correção do botão `Nova conversa` da E66 é separada: não reutilize o estado do novo
menu para controlar o formulário de nova conversa. A E67 é responsável pela ficha/novidades; não
misture o conteúdo da sidebar ou da ficha nesta etapa.

---

## Bloco 2 — melhorar o card da conversa interna na lista

O card interno deve seguir o mesmo ritmo visual do card normal, sem fingir que é um atendimento de
cliente.

Em `frontend/src/components/atendimentos/cartao-conversa.tsx`:

- mantenha a discriminação por `tipo === "EQUIPE_INTERNA"`; não detecte chat interno comparando nome,
  prefixo, UUID ou texto da última mensagem;
- preserve avatar, nome/identificação, última mensagem, horário, contador de não lidas e estado
  selecionado;
- use a mesma altura, espaçamento, truncamento e alinhamento do card de cliente, para que a lista
  continue ordenável e escaneável;
- acrescente um indicador discreto e inequívoco de equipe interna, como ícone e/ou badge `Chat interno`,
  usando o catálogo de textos e tokens existentes;
- não exiba etapa, telefone, tags de lead, responsável, ações de atendimento ou qualquer informação de
  cliente em um card interno;
- o estado selecionado deve ser claramente distinguível do estado normal e continuar acessível por
  teclado, mantendo `aria-current`/`aria-selected` conforme a semântica já usada pela lista;
- mensagens longas, ausência de última mensagem e contagens nulas devem continuar sendo estados reais,
  sem textos de demonstração.

Na barra da lista, corrija também a apresentação do seletor de contato interno destacado na segunda
imagem: um valor longo/UUID não pode aparecer como rótulo quando existe um nome de exibição. O valor
interno pode continuar sendo o id enviado ao backend, mas a opção renderizada deve mostrar o nome
real do contato. Não esconda um problema de contrato com `slice`, truncamento do UUID ou substituição
por nome fixo.

O clique na conversa interna deve continuar selecionando `tipo + conversaId`, nunca somente o id. Isso
evita colisão conceitual entre uma conversa interna e um atendimento de cliente.

---

## Bloco 3 — paridade estrutural com o chat normal

A experiência interna deve usar a mesma linguagem visual do chat normal, com as diferenças necessárias
de conteúdo e de privacidade.

### Cabeçalho da conversa interna

O cabeçalho deve ter, na mesma hierarquia do chat normal:

- avatar real ou iniciais derivadas do nome real;
- nome de exibição da pessoa ou das pessoas da equipe;
- indicador textual/visual `Chat interno`;
- informação de perfil permitida pelo contrato, como cargo e presença, somente quando esses dados
  estiverem disponíveis de maneira autorizada;
- espaçamento, bordas, altura, tipografia e estados de carregamento coerentes com
  `CabecalhoConversa`.

Não renderize no cabeçalho ações de atendimento ao cliente, como `Finalizar`, `Transferir`, tags,
telefone do lead, janela de 24 horas ou ações de WhatsApp. Chat interno é comunicação entre equipe,
mas deve parecer parte do mesmo CRM.

### Mensagens

Extraia ou reutilize um renderer visual comum quando isso for viável, sem introduzir dependência de
domínio no componente de apresentação. Para cada `ChatMensagem`:

- mensagem enviada pelo usuário autenticado fica alinhada e colorida como mensagem própria no chat
  normal;
- mensagem de outra pessoa fica alinhada como recebida;
- a comparação deve usar o id real do usuário autenticado, não o nome e não uma posição fixa na lista;
- mensagens recebidas devem identificar o remetente quando necessário, especialmente em conversa com
  mais de duas pessoas;
- preserve conteúdo, horário real e ordem recebida do backend;
- mantenha estado vazio, carregamento, erro e paginação/cursor existentes;
- o fundo do canvas, a largura máxima das bolhas, o raio, o padding e a tipografia devem seguir os
  tokens e a cadência do chat normal;
- não use cor literal no JSX nem valores copiados dos screenshots;
- não transforme uma mensagem interna em mensagem WhatsApp nem a envie pelo canal externo.

### Composer

O composer interno deve se aproximar do composer normal em estrutura e comportamento:

- cartão/superfície, largura, posicionamento, foco e espaçamento coerentes;
- textarea com placeholder do catálogo;
- envio por clique e por `Enter`, preservando `Shift+Enter` para quebra de linha;
- estado desabilitado durante envio e prevenção de duplo envio;
- limpeza após sucesso, manutenção do texto após erro e feedback acessível;
- ícones/ações somente quando houver comportamento interno real. Não renderize anexo, áudio, respostas
  rápidas ou ação WhatsApp se o chat interno não tiver contrato para isso;
- se houver toolbar compartilhada com o composer normal, filtre as ações incompatíveis em vez de deixar
  botões fantasmas.

### Rota unificada e rota legada

Faça a paridade no caminho usado por `/atendimentos` e não deixe `/chat-interno` com uma segunda
aparência incompatível. Prefira extrair componentes de apresentação compartilhados para cabeçalho,
lista de mensagens e composer, recebendo dados/ações por props.

Não faça uma grande reescrita do fluxo de dados apenas para copiar o visual. Preserve as APIs atuais:

- `listarConversasChat`;
- `listarContatosChat`;
- `listarMensagensChat`;
- `enviarMensagemChat`;
- `marcarChatComoLido`;
- `abrirConversaDireta`.

Se os dois caminhos não conseguirem receber a mesma informação do usuário autenticado, pare antes de
inventar uma comparação de autoria. Identifique a lacuna no relatório e proponha o contrato mínimo.

---

## Bloco 4 — perfil da pessoa da equipe sem vazamento

O pedido de “info do perfil” não autoriza expor e-mail, telefone ou qualquer dado pessoal sem conferir
o contrato e a política de acesso.

Antes de alterar backend, investigue os contratos existentes:

- `GET /api/v1/usuarios` retorna `id`, `nome`, `email`, `papel`, `statusPresenca`, `ativo`,
  `disponivelParaIa`, `cargo` e `fotoUrl`, mas o próprio controller informa que a consulta é restrita
  aos papéis de gestão;
- `GET /api/v1/me` retorna os dados do usuário autenticado;
- `GET /api/v1/me/foto/{id}` entrega a foto processada quando autorizada pelo adaptador existente;
- os endpoints de `/api/v1/chat-interno` hoje retornam nomes e ids necessários à conversa, não um perfil
  completo.

Use um endpoint/hook existente somente se a autorização permitir que o usuário da conversa veja os
campos usados. Para o cabeçalho do chat interno, a preferência é mostrar apenas:

- nome de exibição;
- cargo, se o contrato realmente o disponibilizar para aquele usuário;
- presença, se a fonte for autorizada e o status for necessário para a UX;
- foto processada, se a rota segura existente funcionar para o usuário autorizado;
- nenhum e-mail/telefone por padrão, salvo decisão explícita e contrato já existente.

Não faça `GET /api/v1/usuarios` a partir do frontend de um atendente se o endpoint for restrito a
gestor/subgestor/administrador. Não resolva isso filtrando no frontend uma lista que o usuário não
deveria ter recebido.

Se os endpoints existentes não permitirem obter o perfil mínimo para todos os participantes:

1. não invente campos no payload de chat;
2. não relaxe autorização;
3. não inclua e-mail/telefone em `ChatContato` ou `ChatConversa` por conveniência;
4. entregue o layout com nome/identificador já autorizado, se isso for suficiente;
5. registre no relatório exatamente qual contrato mínimo ficou pendente e pare qualquer alteração de
   backend para decisão do Marcondes.

Se, e somente se, o repositório já provar que a mudança de contrato é necessária e segura, não implemente
silenciosamente: descreva endpoints, DTO, autorização, testes negativos, impacto em OpenAPI e necessidade
de migration (a expectativa desta etapa é **nenhuma**). A decisão de expor novos dados de perfil pertence
ao proprietário do produto.

---

## Catálogo, tokens e acessibilidade

- Toda string nova deve entrar no catálogo existente e no schema correspondente. Não escreva rótulos,
  tooltips, `aria-label`s, estados vazios ou mensagens de erro diretamente no JSX.
- Use tokens semânticos e variantes dos componentes existentes. Não use `#`, `rgb()`, nomes de cores
  literais ou estilos inline para reproduzir a imagem.
- Botões icon-only precisam de `aria-label`; dropdowns e diálogos devem manter foco, Escape e nomes
  acessíveis.
- O menu global deve ser distinguível do botão de filtro, do `+` de nova conversa e das ações do
  cabeçalho central.
- Estados selecionado, foco, hover, carregamento, desabilitado, erro e vazio precisam ser visíveis sem
  depender apenas de cor.
- Não altere a regra de visibilidade de leads, a feature flag `chat_interno`, RLS, WebSocket ou o
  comportamento do canal WhatsApp.

---

## Testes — a proteção nasce com um teste que a viola

### Menu global

Atualize `frontend/src/components/atendimentos/cabecalho-conversa.test.tsx` e
`frontend/src/components/atendimentos/lista-conversas.test.tsx`, ou crie testes separados, cobrindo o
ponto de entrada real:

- o botão de três pontos da ação global existe na barra da lista, ao lado de `Atendimentos`;
- ele tem nome acessível do catálogo e abre com `userEvent.click` completo;
- o menu global não existe mais no cabeçalho da conversa;
- o botão de finalização individual continua no cabeçalho;
- a confirmação usa a quantidade retornada pelo hook, não uma constante;
- quantidade zero/carregamento desabilita a ação sem esconder filtros ou conversas;
- cancelar fecha somente a confirmação;
- confirmar chama a mutation uma vez, mantém estado de pending e trata sucesso/recusa;
- re-render da lista não move o menu de volta nem fecha incorretamente o diálogo;
- a ordem visual/DOM não associa a ação global ao lead selecionado.

### Lista interna

Em `cartao-conversa.test.tsx`/`lista-conversas.test.tsx`:

- conversa interna mostra indicador de equipe interna;
- nome, última mensagem, horário e não lidas vêm do payload real;
- card selecionado permanece selecionado por `tipo + conversaId`;
- card interno não exibe etapa, tags, telefone, responsável ou ação de lead;
- contato selecionado no formulário mostra o nome real, não UUID cru;
- ausência de nome/última mensagem continua sendo estado vazio honesto;
- flag `chat_interno` desligada continua removendo conversas e controles internos;
- mensagens de cliente não desaparecem quando a fonte interna falha.

### Painel interno

Crie testes para `PainelConversaInterna` e para os componentes compartilhados, cobrindo:

- cabeçalho com nome/indicador interno e perfil somente quando autorizado/disponível;
- mensagem própria à direita e mensagem de outra pessoa à esquerda usando id do usuário atual;
- remetente, horário e conteúdo reais;
- estado vazio, loading, erro e retry;
- envio por clique e por Enter, Shift+Enter, pending, erro e limpeza após sucesso;
- leitura marcada ao abrir a conversa;
- invalidação/atualização das conversas após envio sem duplicar mensagens;
- ausência de ações de cliente/WhatsApp no chat interno;
- não vazamento de e-mail/telefone/campos de perfil quando a fonte não os autoriza;
- rota unificada e rota `/chat-interno` usando a mesma linguagem visual ou o mesmo componente de
  apresentação.

Não faça testes que apenas chamem setters internos. Use `userEvent`, renderização real e mocks das
portas HTTP existentes. Para comportamento assíncrono, espere condições com as utilidades do runner;
não use `Thread.sleep`, timeout cego ou asserção imediata.

### Validação visual/manual

Faça uma validação no navegador, preferencialmente em desktop e viewport estreita, registrando o
viewport e screenshots:

1. lista de Atendimentos com o menu de três pontos ao lado do título;
2. diálogo de finalização aberto, com quantidade real;
3. cabeçalho de uma conversa sem o menu global, mas com finalização individual preservada;
4. lista contendo cliente e equipe, com badge/ícone interno e seleção;
5. chat interno com perfil autorizado, bolhas próprias/recebidas e composer equivalente;
6. chat normal sem regressão de layout;
7. formulário de nova conversa após um clique completo, verificando que não depende de manter o mouse
   pressionado (a correção específica é da E66, mas a integração não pode quebrá-la).

Se não houver ambiente com dados reais ou se a execução paralela impedir a captura na composição final,
registre isso como `⚠️` e diga exatamente o que foi validado na branch isolada.

---

## Definição de pronto

- [ ] O menu de três pontos da ação global está na barra da lista, ao lado de `Atendimentos`.
- [ ] A ação global não aparece mais no cabeçalho da conversa selecionada.
- [ ] A finalização individual continua no cabeçalho e não foi confundida com a global.
- [ ] Quantidade, confirmação, autorização, pending, sucesso, recusa e invalidação continuam reais.
- [ ] O card de equipe tem a mesma densidade do card de cliente, com indicador interno claro.
- [ ] O card interno usa tipo/id reais, exibe nome sem UUID cru e não mostra ações de lead.
- [ ] O chat interno tem cabeçalho, canvas, bolhas e composer coerentes com o chat normal.
- [ ] Mensagens próprias e recebidas são posicionadas pelo id do usuário autenticado.
- [ ] O perfil exibido não vaza e-mail/telefone nem relaxa a autorização existente.
- [ ] A rota unificada e a rota legada não apresentam duas experiências incompatíveis.
- [ ] A flag `chat_interno` e a regra de visibilidade permanecem intactas.
- [ ] Não há strings ou cores literais novas em componentes React.
- [ ] Testes cobrem positivos e negativos do menu, seleção, autoria, perfil e ausência de ações indevidas.
- [ ] `npm run lint`, `npm run typecheck`, `npm test -- --run` e `npm run build` passam.
- [ ] Se o backend foi alterado, `cd backend && ./mvnw clean verify` passa com Java 21/Testcontainers.
- [ ] Validação visual/manual foi realizada ou a limitação foi registrada com viewport e evidência.
- [ ] O relatório contém SHA-base, SHA-final, branch, commits, quantidade de arquivos, decisões,
      divergências, bugs, fora de escopo, evidência visual e `CI não verificado` quando não houver push.
- [ ] O relatório traz o item separado `ação necessária no Dokploy antes do próximo deploy`; expectativa:
      nenhuma variável nova. Se surgir variável, informar nome e valor de exemplo e atualizar `.env.example`
      e a tabela do `README.md`.

---

## Fora desta etapa

- Não implementar novo contato WhatsApp.
- Não criar ou alterar migration sem necessidade comprovada.
- Não relaxar autorização de `GET /api/v1/usuarios` para fazer o perfil aparecer.
- Não expor e-mail, telefone ou dados pessoais no chat interno por conveniência visual.
- Não alterar RLS, regra de visibilidade de leads, canal WhatsApp, WebSocket ou feature flag.
- Não recriar a sidebar inteira nem misturar a Administração/Novidades da E67.
- Não absorver a implementação de E65/E66/E67; apenas resolver conflitos de integração e relatá-los.
- Não usar dados mockados ou valores das screenshots como conteúdo de produção.
- Não fazer commit ou push sem autorização explícita do Marcondes.

## Perguntas que devem voltar no relatório, se permanecerem abertas

1. O endpoint atual permite mostrar cargo/presença/foto para um atendente participante? Qual evidência?
2. Se não permite, qual é o menor contrato seguro necessário e quais campos ficaram fora?
3. A rota `/chat-interno` foi alinhada por componente compartilhado ou apenas por CSS? Qual o custo de
   manutenção da escolha?
4. A ação “Finalizar todos” manteve exatamente o escopo de atendimentos visíveis e a autorização
   anterior?
