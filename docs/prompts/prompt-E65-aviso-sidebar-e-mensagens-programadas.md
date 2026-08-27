# Prompt E65 — aviso dispensável, sidebars retráteis e destaque das programadas

> Leia `AGENTS.md`. Entrega em 25/08.
> Commite por bloco. Não faça `git push` sem autorização explícita do Marcondes.
> Ao encerrar, rode `cd backend && ./mvnw clean verify` se tocar o backend e informe o número da run do CI;
> sem push, escreva `CI não verificado`.

---

## Contexto — três problemas visíveis na tela de Atendimentos

Há três ajustes relacionados à usabilidade da tela, mas com causas diferentes:

1. O aviso de transferência/devolução continua aparecendo ou não desaparece quando o usuário tenta
   dispensá-lo. O código aparenta limpar o estado, então é necessário reproduzir o caminho real antes
   de alterar CSS ou aumentar timeout.
2. A sidebar principal e o painel lateral de detalhes do lead ocupam espaço permanentemente. O usuário
   precisa poder retrair e reabrir ambos sem perder a conversa, a seleção ou as ações disponíveis.
3. As mensagens programadas aparecem no painel do lead com o mesmo fundo neutro de outros blocos e não
   ficam visualmente identificáveis. A referência é a primeira imagem enviada: o cartão da mensagem
   programada precisa ter fundo/contorno próprio, discreto e legível.

O aviso atual está em `frontend/src/components/atendimentos/pagina-atendimentos-cliente.tsx`:

```tsx
const [notificacao, setNotificacao] = useState<NotificacaoTempoReal | null>(null);

useEffect(() => {
  if (!notificacao) return;
  const segundos = configuracao?.tempoNotificacaoSegundos ?? 8;
  const timer = window.setTimeout(() => setNotificacao(null), segundos * 1000);
  return () => window.clearTimeout(timer);
}, [notificacao, configuracao?.tempoNotificacaoSegundos]);
```

E o fechamento depende de um único botão:

```tsx
<button
  type="button"
  aria-label={textos.tempoReal.fechar}
  onClick={() => setNotificacao(null)}
>
  <X className="size-4" aria-hidden />
</button>
```

O teste existente em `frontend/src/components/atendimentos/pagina-atendimentos-cliente.test.tsx`
injeta o callback mockado diretamente e confirma o `setNotificacao(null)`. Isso não prova que uma
notificação real não seja emitida novamente por uma segunda assinatura, reconexão ou evento duplicado.
Em `frontend/src/lib/atendimento/tempo-real.ts`, a fila `/user/queue/notificacoes` entrega eventos sem
um `id` próprio; os campos disponíveis incluem tipo, recurso e `ocorridoEm`.

As duas superfícies de layout são hoje estas:

```tsx
// frontend/src/app/(shell)/layout.tsx
<Sidebar />
<div className="min-w-0 flex-1">
```

```tsx
// frontend/src/components/shell/sidebar.tsx
<aside className="flex w-[260px] shrink-0 flex-col overflow-hidden bg-sidebar ...">
```

```tsx
// frontend/src/components/atendimentos/pagina-atendimentos-cliente.tsx
conversa
  ? "relative grid h-full grid-cols-[346px_1fr_344px] overflow-hidden"
  : "relative grid h-full grid-cols-[346px_1fr] overflow-hidden"
```

```tsx
// frontend/src/components/atendimentos/painel-da-conversa.tsx
<aside className="flex h-full w-[344px] shrink-0 flex-col border-l border-border bg-background">
```

O cartão atual de cada programada é:

```tsx
<div className="rounded-lg border border-border bg-muted/30 p-2.5">
```

`PainelDaConversa` é o painel de detalhes inline da tela de Atendimentos. `PainelLateralLead`, em
`frontend/src/components/leads/painel-lateral-lead.tsx`, é outro componente: um painel sobreposto da
Agenda que já possui botão de fechar. Não confunda os dois nem remova o fluxo de fechamento da Agenda.

## Bloco 1 — o aviso precisa realmente ser dispensável

- Reproduza primeiro em navegador ou em um teste que simule o ponto de entrada real do WebSocket:
  conexão, assinatura de `/user/queue/notificacoes`, recebimento do evento, clique no controle visível
  e re-render/invalidação da lista.
- Faça o controle de dispensa ser inequívoco, visível e acessível. O botão de abrir a transferência,
  quando existir, deve continuar sendo uma ação diferente da dispensa.
- Depois da dispensa, o mesmo evento não pode ressuscitar por re-render, invalidação de
  `atendimentos`, reconexão ou entrega duplicada. Um evento novo e realmente distinto deve continuar
  aparecendo.
- Preserve a expiração configurável existente. O timer é apresentação; não altere o evento persistido
  no backend nem transforme a notificação em estado de negócio.
- Ao abrir a transferência, o aviso também deve ser limpo. O comportamento deve ser consistente para
  `TRANSFERENCIA_RECEBIDA` e `ATENDIMENTO_DEVOLVIDO_PARA_IA`.
- Se a correção exigir deduplicação, derive uma chave estável somente dos campos disponíveis e
  documente a decisão. Não invente um identificador de evento no frontend se isso puder colidir com
  eventos legítimos.

> **Não faça um falso conserto.** Não esconda o aviso com `opacity`, `display`, z-index ou overlay,
> não remova o botão e não aumente o timeout para mascarar o reaparecimento.

> **Ponto de parada.** Se a reprodução demonstrar que o backend envia eventos legítimos indistinguíveis
> de duplicatas, pare antes de alterar o contrato ou criar uma migration e relate o caso com payload
> sanitizado. Não descarte notificações reais por heurística silenciosa.

## Bloco 2 — retração da sidebar principal

- Adicione um controle acessível para retrair e reabrir a sidebar principal em todas as rotas do shell.
- No estado retraído, mantenha os ícones, links, estado ativo, contagem de pendentes, configurações,
  presença e logout funcionais. Oculte apenas textos que não cabem; cada item icon-only precisa manter
  `aria-label` e `title` vindos do catálogo de textos.
- O controle deve expor `aria-expanded` e um nome diferente para retrair/reabrir. Use ícones já
  disponíveis no conjunto adotado ou um componente existente; não desenhe SVG manual.
- O estado pode ser local à sessão da tela/shell. Não crie coluna, endpoint, feature flag ou preferência
  persistida no banco para isso.
- O estado precisa ser compartilhado pelo dono do layout e pela sidebar; não use `document.querySelector`,
  evento global ou mutação de DOM para alterar a largura do irmão.
- Preserve o canvas e o conteúdo principal sem overflow horizontal. A transição não pode deslocar o
  foco para um link oculto nem quebrar o popup de presença.
- Strings novas entram no catálogo (`textos.json`) e no schema/tipos correspondentes. Cores continuam
  vindo dos design tokens; não introduza cor literal no JSX.

> **Não faça uma sidebar falsa.** Não remova itens no estado retraído, não deixe somente um espaço
> vazio de `260px` e não transforme a retração em navegação para outra rota.

## Bloco 3 — retração do painel de detalhes do lead

- Na tela `/atendimentos`, permita retrair o `PainelDaConversa` de `344px` e reabri-lo depois.
- O dono do estado deve ser `PaginaAtendimentosCliente`, porque ele já decide se a terceira coluna
  existe. Ao retrair, a grade deve devolver o espaço ao chat; ao reabrir, a mesma conversa, histórico,
  composer e scroll devem permanecer selecionados.
- Quando o painel estiver aberto, ofereça o controle no cabeçalho do próprio painel. Quando estiver
  fechado, ofereça o controle no `CabecalhoConversa`, para que o usuário não fique sem caminho de
  reabertura.
- Use `aria-expanded`, `aria-controls` e nomes do catálogo. O controle deve existir somente para
  conversa de cliente; não injete painel de lead nem ações de cliente em conversa `EQUIPE_INTERNA`.
- Trocar de lead, receber atualização da inbox, abrir mensagem programada ou usar transferência não
  pode reabrir o painel por acidente. O estado de retração é de apresentação, não muda autorização,
  histórico ou atendimento ativo.
- Mantenha o `PainelLateralLead` da Agenda com o fechamento que já existe. Se encontrar comportamento
  divergente entre os dois painéis, registre no relatório; não altere sua semântica sem necessidade.

> **Não use overlay para esconder o problema.** O painel retraído deve deixar de participar da grade;
> não cubra o chat com uma camada transparente nem deixe uma coluna invisível capturando foco/cliques.

> **Ponto de parada.** Se o cabeçalho não comportar o controle sem ocultar ações operacionais ou se o
> chat interno compartilhar uma API incompatível, pare e relate a opção de layout antes de mudar a
> hierarquia de ações.

## Bloco 4 — destacar visualmente as mensagens programadas

- Em `SecaoDeProgramadas` de `painel-da-conversa.tsx`, dê ao cartão de cada mensagem programada um
  fundo e/ou contorno que o diferencie claramente das notas, lembretes e do fundo do painel.
- Reaproveite tokens semânticos existentes (`primary`, `muted`, `border`, ou equivalentes do tema),
  com contraste suficiente para texto e data. Não use `#hex`, `rgb(...)`, nomes de cores Tailwind
  arbitrários ou estilo inline para uma cor nova.
- A mudança deve atingir o cartão da programada, não o painel inteiro nem a seção de lembretes.
  Conteúdo, data local, editar, remover, confirmação, contagem e estado vazio permanecem iguais.
- Confira também a tela global de mensagens programadas. Se ela usa outro renderer, aplique o mesmo
  significado visual apenas onde houver cartão/lista de programada real; não crie dados nem controles
  novos para imitar a referência.
- A hierarquia visual deve continuar sóbria: destaque suficiente para reconhecer a mensagem agendada,
  sem parecer alerta de erro ou mensagem já enviada.

## Testes — a proteção nasce com um teste que a viola

### Aviso

- evento realista `TRANSFERENCIA_RECEBIDA` aparece uma vez;
- clicar no botão de fechar remove o aviso do DOM;
- depois de invalidar `atendimentos`, re-renderizar e simular reconexão/entrega duplicada do mesmo
  evento, o aviso continua dispensado;
- evento novo com outra ocorrência aparece normalmente;
- `Abrir atendimento` limpa o aviso e abre o lead;
- `ATENDIMENTO_DEVOLVIDO_PARA_IA` também pode ser dispensado e expira pelo tempo configurado;
- não existe mais de um aviso concorrente ou mais de uma assinatura que faça o mesmo evento reaparecer.

O teste não pode apenas chamar um setter ou o callback final da página: use o ponto de entrada do
adaptador WebSocket fake existente, ou acrescente um fake de STOMP que emita o frame pela assinatura.

### Sidebar principal

- estado aberto mostra marca, textos, links e badges;
- clicar em retrair muda `aria-expanded`, reduz a largura e preserva os links por ícone;
- clicar em reabrir restaura textos e largura;
- link ativo, contagem de pendentes, configurações, presença e logout continuam acessíveis;
- não há overflow horizontal nem foco em conteúdo visualmente removido.

### Painel do lead

- abrir uma conversa começa com painel aberto ou com a decisão explicitada no relatório;
- retrair remove a terceira coluna e libera o espaço do chat;
- reabrir mostra o mesmo lead sem refazer/alterar o histórico selecionado;
- troca de conversa preserva a retração;
- conversa interna não renderiza controle/painel de detalhes do cliente;
- o painel da Agenda mantém seu botão de fechar e seu fluxo atual.

### Mensagens programadas

- cartão de programada possui as classes/tokens de destaque esperados;
- lembrete, estado vazio, editar, remover e confirmação não recebem o destaque por engano;
- teste visual ou screenshot em viewport de desktop confirma que texto, data e ações continuam legíveis.

## Definição de pronto

- [ ] O aviso desaparece ao clicar no controle e não ressuscita por duplicata, reconexão ou re-render.
- [ ] Um evento novo continua aparecendo e o aviso expira pelo tempo configurado.
- [ ] A sidebar principal retrai/reabre sem remover funcionalidade ou quebrar o shell.
- [ ] O painel de detalhes do lead retrai/reabre sem perder conversa, histórico ou composer.
- [ ] Conversa interna não recebe ações de lead.
- [ ] Mensagens programadas têm diferenciação visual por tokens, sem cor literal ou mock.
- [ ] Strings novas estão no catálogo e no schema/tipos, se houver.
- [ ] Testes de regressão cobrem os negativos descritos acima.
- [ ] `npm run lint`, `npm run typecheck`, `npm test -- --run` e `npm run build` passam.
- [ ] Se houver alteração backend, `cd backend && ./mvnw clean verify` passa com Java 21/Testcontainers.
- [ ] O relatório informa o SHA final, variáveis novas no Dokploy (expectativa: nenhuma), decisões,
      divergências, bugs, fora de escopo e evidência visual.
- [ ] CI só é chamado de verde com o número da run; sem push, registrar `CI não verificado`.

---

## Fora desta etapa

- Não alterar backend, WebSocket, contrato de eventos, banco, migration, autorização ou regras de
  negócio para resolver um problema puramente de apresentação sem antes atingir um ponto de parada.
- Não criar preferência persistente de usuário, endpoint ou feature flag para o estado das sidebars.
- Não redesenhar a lista de conversas, o composer, a Agenda ou o painel de conversa interna.
- Não transformar mensagens programadas em mensagens enviadas, alerta de erro ou componente de chat.
- Não commitar nem enviar prompts não rastreados automaticamente; preserve os prompts já existentes.

