# Prompt E84 — Sidebar dinâmica e reações reais nas mensagens

> Leia `AGENTS.md` por inteiro antes de agir. Leia também `docs/03-modelo-dados-postgres.md`, `docs/04-adrs-e-api.md`, `docs/13-estado-do-projeto.md` e `docs/prompts/COMO-ESCREVER-PROMPTS.md`.
>
> Trabalhe em um worktree novo, numa branch `codex/e84-sidebar-reacoes`, criada a partir de `origin/main` confirmado no início. Não reutilize uma branch ou worktree com alterações. Preserve arquivos não rastreados e trabalho de outras etapas. Registre no relatório o SHA de base realmente encontrado.
>
> Faça commits convencionais pequenos, um por bloco verificável. Faça push somente quando houver autorização explícita do responsável pela branch; sem push, CI remoto é **não verificado**.

---

## Objetivo de produto

Há dois comportamentos a entregar, em todas as telas que usam o shell:

1. A barra lateral **principal** deve iniciar retraída. Ao passar o mouse sobre ela, abre temporariamente; ao sair, volta a retrair. O botão do topo a mantém aberta por escolha explícita do usuário.
2. Mensagens devem ter a experiência estrutural do WhatsApp: ao passar o mouse ou focar uma bolha há uma seta de ações; a seta abre uma faixa de reações rápidas e um menu. Reagir deve ser real, persistido, autorizado e atualizado para todos que podem ver a conversa. O seletor precisa oferecer um catálogo amplo de emojis, por categorias e busca.

As referências visuais são o WhatsApp para **hierarquia e interação**, não para copiar o tema escuro, suas cores, marca ou controles que o CRM não possui.

## Contexto confirmado no código

Hoje a preferência de largura do shell só é um booleano local e começa aberta:

```tsx
// frontend/src/components/shell/shell-com-sidebar.tsx
const [sidebarRetraida, setSidebarRetraida] = useState(false);
...
<Sidebar
  retraida={sidebarRetraida}
  onAlternar={() => setSidebarRetraida((atual) => !atual)}
/>
```

`ShellComSidebar` envolve a aplicação inteira em `(shell)`. Portanto esta é a única origem de estado da barra lateral principal; não há justificativa para um comportamento por página.

As bolhas de atendimento não têm ações nem reações:

```tsx
// frontend/src/components/atendimentos/bolha-mensagem.tsx
export function BolhaMensagem({ mensagem, onReenviar, nomeDoRemetente }: Props) {
  ...
  return (
    <div className={cn("flex", doAtendente ? "justify-end" : "justify-start")}>
      <div className={cn("max-w-[70%] rounded-lg ...")}>
```

O chat interno tem apresentação e persistência próprias:

```sql
-- backend/crm-app/src/main/resources/db/migration/V8__chat_interno.sql
CREATE TABLE chat_interno_mensagem (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversa_id UUID NOT NULL REFERENCES chat_interno_conversa(id) ON DELETE CASCADE,
    remetente_id UUID NOT NULL REFERENCES usuario(id),
    ...
);
```

Já a mensagem de atendimento é particionada e a sua chave é composta:

```sql
-- backend/crm-app/src/main/resources/db/migration/V5__atendimento.sql
PRIMARY KEY (id, enviado_em)
) PARTITION BY RANGE (enviado_em);
```

Uma reação a `mensagem` precisa carregar `mensagem_id` **e** `mensagem_enviada_em` para ter FK e consulta que não varrem todas as partições. Não introduza uma tabela polimórfica sem FKs para juntar os dois chats.

---

## Bloco 0 — Inventário e limites dos comandos da mensagem

Antes de criar componentes, confirme por busca no código e nos contratos quais destes comandos já têm comportamento end-to-end: responder, copiar, reagir, encaminhar, fixar, perguntar à IA, favoritar, denunciar e apagar.

- Esta etapa deve entregar **Copiar** e **Reagir** de verdade.
- A faixa com as seis reações rápidas e o botão “mais” deve abrir o mesmo seletor completo de emojis.
- Só renderize um item adicional do menu se ele tiver fluxo real, autorização e teste ponta a ponta já existentes ou implementados integralmente neste mesmo escopo.
- Não renderize itens desabilitados, toasts “em breve”, handlers vazios, `console.log` ou controles decorativos para `Responder`, `Encaminhar`, `Fixar`, `Pergunte à IA`, `Favoritar`, `Denunciar` ou `Apagar`.

Essas ações exigem decisões que a referência não traz: resposta externa pelo provedor, destino do encaminhamento, se pin/favorito é pessoal ou compartilhado, moderação de denúncia, política de exclusão/auditoria e contrato com IA. Não as invente. Liste no relatório as que não existiam e ficaram de fora.

> **Ponto de parada.** Se o inventário revelar uma implementação parcial de algum desses comandos (por exemplo, botão sem autorização no backend), não a exponha como funcional. Pare e reporte o achado antes de expandir o escopo para consertá-la.

---

## Bloco 1 — Sidebar principal dinâmica, única e acessível

Altere somente o shell principal (`ShellComSidebar` + `Sidebar` e seus testes), sem duplicar estado nas páginas.

- Em desktop com hover disponível, a barra inicia retraída a cada entrada no shell. Ela expande enquanto o ponteiro está dentro do `<aside>` inteiro — inclusive logo, links, rodapé e popovers ancorados nele — e retrai ao sair.
- O mesmo comportamento temporário deve existir para teclado: `focus-within` expande, e a barra só retrai após o foco sair dela. Nenhum link pode desaparecer enquanto está focado.
- O botão no topo deixa a barra **fixada aberta na sessão atual**. Fixada, ela não retrai por `mouseleave` nem por perda de foco. Clicar novamente remove a fixação; fora de hover/foco, volta ao estado retraído.
- Não crie preferência persistida, endpoint, `localStorage` ou mutation de perfil: o requisito não pediu persistência entre recargas e o projeto não possui modelo de preferências do usuário. Ao recarregar, o padrão volta a ser retraído.
- O controle precisa refletir os dois estados de forma acessível (`aria-pressed` para fixação, rótulo/tooltip do catálogo e foco visível). Mantenha o nome acessível e os tooltips de cada atalho quando retraída.
- Em touchscreen/viewport estreito, não existe hover: preserve a navegação inferior atual e não esconda links atrás de um estado impossível de abrir. Não altere o breakpoint ou a regra de conversa em tela cheia sem uma regressão que prove necessidade.
- Use os tokens de tema e a transição já existente; não crie largura, cor, sombra ou duração arbitrária. A expansão não pode introduzir scroll horizontal nem deslocar/congelar o conteúdo principal de forma cumulativa.
- “Todas as abas” significa todas as rotas sob `frontend/src/app/(shell)`. A navegação secundária de Administração, painel de lead e listas internas **não** viram sidebars com hover nesta etapa.

Atualize `shell-com-sidebar.test.tsx`: a expectativa inicial atual de sidebar expandida está deliberadamente obsoleta e deve passar a provar o padrão retraído.

---

## Bloco 2 — Modelo e contratos de reações sem violar as fronteiras dos módulos

Implemente reações em atendimento e em chat interno como capacidades equivalentes na interface, mas com persistência separada e autorizada no respectivo módulo.

### Regras de negócio

- Uma pessoa autenticada pode manter **no máximo uma** reação por mensagem. Escolher outro emoji substitui a própria reação; remover é explícito e idempotente.
- Pessoas diferentes podem reagir com o mesmo emoji; a tela exibe agrupamentos `{ emoji, quantidade, reagi }` consistentes.
- Reações podem ser feitas em mensagens históricas, inclusive de atendimento finalizado, desde que o usuário ainda possa ler aquele histórico. Finalização não concede nem remove visibilidade.
- No atendimento, toda leitura/escrita passa pela mesma autorização/visibilidade que protege a conversa e o lead (RN-CRM-01). Não aceite um `atendimentoId` ou `mensagemId` vindo do cliente como prova de acesso.
- No chat interno, apenas participantes da conversa podem listar, criar, substituir ou remover reações. Gestor que não participa continua recebendo 403; não use o papel amplo para furar a conversa privada.

### Banco e portas

- Crie uma migration Flyway nova, nunca edite `V5`, `V8` ou migrations já aplicadas.
- Para atendimento, crie tabela de reação com FK composta para `mensagem(id, enviado_em)`, FK para `usuario` e unicidade de uma reação por `(mensagem_id, mensagem_enviada_em, usuario_id)`. Indexe o caminho de leitura por mensagem sem induzir varredura de partições.
- Para chat interno, crie tabela de reação própria com FK para `chat_interno_mensagem`, FK para `usuario` e unicidade por `(mensagem_id, usuario_id)`.
- Não use JSONB para guardar reações, não persista HTML/imagem do picker e não faça tabela polimórfica com `tipo + id` sem integridade referencial.
- Estenda RLS somente onde o modelo atual já a exige, e prove-a com teste negativo usando papel/usuário realmente restrito — não com conexão proprietária do banco.
- Crie portas e casos de uso pequenos, por módulo e por intenção (listar resumo, definir a própria reação, remover a própria reação). Domínio sem Spring/JPA; adaptadores JDBC pacote-privados. Não acople `crm-atendimento` a `crm-equipe` nem o contrário.

### API

Projete endpoints REST versionados dentro dos recursos já existentes, com `PUT` para **definir** a reação e `DELETE` para removê-la. O `PUT` deve ser idempotente: repetir o mesmo emoji mantém a reação; o frontend chama `DELETE` para o toggle de uma reação já selecionada.

- O DTO recebe apenas o emoji Unicode, limitado e validado no servidor. Deve aceitar sequências legítimas com modificador de tom, variation selector e ZWJ; não reduza a validação a ASCII ou a uma lista fixa de seis emojis.
- Rejeite conteúdo vazio, texto comum, múltiplas reações concatenadas e payloads grandes com RFC 7807/400, sem gravar linha.
- As listas de mensagens de ambos os chats devem devolver o resumo das reações junto da mensagem. Faça a agregação em lote na consulta da página; é proibido um `SELECT` por mensagem ou por emoji.
- Atualize frontend, OpenAPI e testes de contrato junto com o endpoint. Não exponha identidade de quem reagiu nesta etapa: apenas quantidade e se o usuário atual reagiu.

Documente em `docs/03-modelo-dados-postgres.md` e `docs/04-adrs-e-api.md` a nova estrutura, endpoints, autorização e decisão de uma reação por usuário.

---

## Bloco 3 — Tempo real após commit

O usuário não deve precisar recarregar para ver uma reação de alguém que compartilha a conversa.

- Publique um evento de reação **após o commit**, pela infraestrutura de tempo real já existente. Não publique para Redis/STOMP dentro da transação de `PUT`/`DELETE`.
- Atendimento: use o canal já autorizado por atendimento e acrescente um tipo explícito de evento. Preserve o contrato e a deduplicação existentes para `MENSAGEM`, `STATUS`, transferência e finalização.
- Chat interno: mantenha a entrega individual aos participantes no fluxo `RelayDeChatInterno` → `RedisSubscriberDeAtendimento`; não use `/topic` de broadcast nem exponha a conversa a quem não participa.
- O payload contém identificador da conversa/mensagem necessário para atualizar a bolha e o resumo novo; não contém token, URL de mídia, conteúdo adicional ou nomes de reatores.
- Atualize os caches TanStack de forma imutável. Eventos duplicados e a reconexão não podem incrementar contadores duas vezes; o recarregamento HTTP continua sendo a fonte de reconciliação.
- Falha de Redis/STOMP após o commit deve ser registrada e não reverter nem acusar falha na reação já persistida.

---

## Bloco 4 — UI WhatsApp-like, picker robusto e cópia real

Crie um componente compartilhado de **interação de mensagem** e adapte as duas apresentações de bolha, sem tentar forçar `BolhaMensagem` de atendimento a conhecer as entidades do chat interno.

- Em mouse, revele uma pequena seta/chevron de ações ao passar sobre a bolha. Em teclado, revele-a quando a bolha ou seu controle receber foco. Em touch/mobile, mantenha um botão acessível por toque; hover não pode ser o único caminho.
- A seta abre um popover com: faixa de seis reações rápidas configuradas, botão “mais” e menu de ações realmente disponíveis. A posição precisa virar/ajustar ao limite da viewport e não pode ficar cortada pela lista virtualizada, por uma bolha perto do topo/fim ou pelo painel lateral.
- Mostre as reações existentes junto à bolha, com contagem e estado “minha reação”. Clique/toque numa reação própria remove; em reação de outro usuário define a escolha para o usuário atual. Preserve rótulos acessíveis que anunciem emoji, quantidade e estado.
- **Copiar** só aparece para mensagens com texto copiável. Use `navigator.clipboard` com fallback seguro quando o navegador não disponibilizá-lo; sucesso e erro devem ser informados por texto do catálogo, não por silêncio ou `alert`.
- Integre um picker mantido e compatível com Next 16/React 19 (preferência: `@emoji-mart/react` + `@emoji-mart/data`, carregado localmente e sem CDN). Ele deve oferecer busca, navegação por teclado, tom de pele e categorias amplas (recentes, pessoas, natureza, comida, atividades, viagens, objetos, símbolos e bandeiras, conforme o pacote suportar).
- Não implemente uma grade própria de centenas de emojis e não deixe um array de 10 emojis como catálogo completo. As seis reações rápidas podem vir do catálogo de textos/configuração da instância; o conjunto amplo vem dos dados versionados da biblioteca.
- Todos os rótulos, mensagens de erro, título de menu, categorias expostas pela integração e `aria-label`s entram em `backend/crm-app/src/main/resources/textos.json` e no schema Zod correspondente. Nenhuma string visível nova fica no TSX.
- Use somente tokens do tema e componentes de UI existentes. Não copie o fundo escuro, o wallpaper, cores fixas ou marca do WhatsApp.

### Sobre “emoji estilo iPhone”

Emojis Apple são ativos proprietários e não podem ser copiados/servidos pelo CRM. Não prometa visual de iPhone em Windows ou Android. Com emoji Unicode nativo, iOS usará naturalmente o conjunto Apple; em outras plataformas será o conjunto do sistema. Se a biblioteca oferecer um conjunto visual licenciado, local e compatível, documente qual foi escolhido e a licença. Não carregue sprite de CDN de terceiros para cada usuário.

> **Ponto de parada.** Se a biblioteca escolhida não puder carregar seus dados de forma local, não for compatível com Next 16/React 19 ou introduzir dependência sem licença verificável, pare e apresente alternativas antes de instalar outro pacote. Não substitua por emojis de imagem de origem desconhecida.

---

## Testes — a proteção nasce com um teste que a viola

### Sidebar

- Shell desktop começa retraído.
- `mouseenter`/`mouseleave` e foco de teclado expandem/retraem transitoriamente; pin mantém aberto após saída; unpin volta ao padrão correto.
- Todos os links, badge pendente, presença, configurações, logout e visibilidade por papel continuam acessíveis nos dois estados.
- Em viewport estreito, sidebar principal continua ausente e navegação inferior continua presente; não há regressão de conversa em tela cheia.
- Navegador real em desktop e em 390 px prova que não existe overflow horizontal e que o conteúdo da aba não fica inacessível.

### Reações e API

- Definir, substituir e remover a própria reação no atendimento; duas pessoas no mesmo emoji contam duas; repetir `PUT` não duplica.
- Negativos no ponto de entrada HTTP: não autenticado recebe 401; atendente sem visibilidade não lê nem altera reação de colega; ID/timestamp de mensagem inexistente ou incompatível não cria reação; payload inválido não grava nada.
- Chat interno: participante consegue reagir; não participante recebe 403 para listagem, `PUT` e `DELETE`; reação não aparece em outra conversa.
- Consulta de histórico com várias mensagens prova que os resumos estão corretos sem N+1 (teste de query/integração apropriado ao adaptador JDBC).
- Concorrência de duas escolhas da mesma pessoa deixa uma única reação final; não trate violação de unicidade como 500.
- RLS, se alterada, é violada deliberadamente no teste com identidade restrita e a operação falha.
- Evento de reação só é entregue após commit; rollback não publica; evento duplicado não duplica contagem no cache; usuário sem assinatura/participação não recebe o evento.
- UI: hover/foco/touch revelam a seta; menu abre sem clipping; picker busca e categorias são navegáveis por teclado; selecionar emoji chama o contrato correto; copiar tem sucesso e erro testados; não há ação fantasma para os comandos fora de escopo.

Rode ao final:

```powershell
cd backend; ./mvnw clean verify
cd ../frontend; npm run lint
cd ../frontend; npm run typecheck
cd ../frontend; npm test -- --run
cd ../frontend; npm run build
git diff --check
```

Inclua uma verificação visual em navegador real das duas conversas (atendimento e chat interno), desktop e 390 px. Não use dados mockados para provar o fluxo funcional.

---

## Definição de pronto

- [ ] A sidebar principal inicia retraída em todo o shell, abre temporariamente por hover/foco e só permanece aberta quando fixada pelo botão superior.
- [ ] Mobile conserva navegação utilizável sem depender de hover.
- [ ] Reações são persistidas, autorizadas, agregadas sem N+1 e funcionam em atendimento e chat interno.
- [ ] Uma pessoa mantém no máximo uma reação por mensagem; definir/remover é idempotente e concorrência não duplica.
- [ ] Reações chegam em tempo real somente a quem já pode ver a conversa, depois do commit.
- [ ] Popover WhatsApp-like, seis reações rápidas, picker amplo por categorias/busca e cópia funcionam com mouse, teclado e touch.
- [ ] Não há ação visual sem comportamento real; comandos de WhatsApp sem contrato não aparecem.
- [ ] Catálogo, Zod, OpenAPI e documentação refletem todos os textos e contratos novos.
- [ ] `clean verify`, lint, typecheck, suíte frontend, build e `git diff --check` passam.
- [ ] CI remoto tem número de run e resultado, se houver push autorizado; sem push, o relatório diz **não verificado**.

## No relatório final

Além dos sete itens obrigatórios de `AGENTS.md`, informe:

1. SHA de base, SHA de cada commit, branch/worktree, quantidade de arquivos e confirmação de push ao `origin` (ou motivo de não haver push).
2. O pacote de emoji escolhido, versão travada, licença, como os dados são carregados e a limitação de aparência por sistema operacional.
3. Os endpoints, tabelas/índices/FKs e estratégia usada para evitar N+1 e respeitar a chave particionada de `mensagem`.
4. Evidência dos testes negativos de visibilidade, participação, concorrência, rollback e evento duplicado.
5. Evidências visuais desktop/mobile e qualquer limitação local de WebSocket.
6. Variáveis novas no Dokploy — expectativa: **nenhuma**.

---

## Fora desta etapa

- Responder, encaminhar, fixar, perguntar à IA, favoritar, denunciar e apagar mensagem, até existir política de produto, contrato e autorização reais.
- Wallpaper, tema escuro, marca e quaisquer ativos proprietários do WhatsApp/Apple.
- Preferência persistente de largura da sidebar.
- Alteração de regras de visibilidade de leads, transferência, finalização, mídia, canal WhatsApp, automação ou provedor externo.
