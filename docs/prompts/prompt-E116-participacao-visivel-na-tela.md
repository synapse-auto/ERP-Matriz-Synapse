# E116 — a participação precisa aparecer na tela

## Por que esta etapa existe

A queixa da Michele — *"tecla entrar no atendimento está me transferindo, é para me colocar na mesma
conversa"* — tem duas metades. A regra está na **E115** (participante fala sem tomar o atendimento).
Esta é a outra: **a tela não conta o que está acontecendo**, então mesmo quando o sistema acerta, a
operação não sabe.

O ponto de partida é `frontend/src/components/atendimentos/cabecalho-conversa.tsx`. Leia o arquivo
inteiro antes de mexer — o fluxo de participação está todo lá.

Nota: os rótulos **existem** e estão corretos no `textos.json`
(`entrar: "Entrar no atendimento"`, `pedirEntrada: "Pedir para entrar"`, `sair: "Sair do
atendimento"`). O problema não é o texto dos botões. É tudo que acontece em volta deles.

## O que está errado, verificado no código

**1. Erro do backend é engolido — o mais grave.**

```js
async function executarParticipacao(acao, proximo) {
  setProcessandoParticipacao(true);
  try {
    await acao();
    setEstadoLocal(proximo);
    invalidarParticipacao(conversa.atendimentoId);
    await participantes.recarregar();
  } finally { setProcessandoParticipacao(false); }
}
```

Não há `catch`. `entrar` devolve **403** quando quem clica não tem alçada
(`SecurityException("sem alçada para entrar diretamente")`); `aprovar` estoura quando o pedido
expirou (`"pedido expirado"`); `sair` devolve 404 quando a pessoa não é participante ativo. Em todos
esses casos a tela **não mostra nada** — o botão pisca e continua igual. Quem usa conclui que o
sistema travou.

**2. Nada diz o que "Entrar no atendimento" faz, nem antes nem depois.**

Não há descrição antes de clicar e não há confirmação depois. O único sinal de que funcionou é o
botão trocar para "Sair do atendimento" — e para descobrir isso a pessoa precisa reparar no botão.

**3. Os avatares de participantes estão rotulados errado.**

```jsx
<div className="mt-1 flex items-center gap-1" aria-label={textos.atendidoPor}>
```

`atendidoPor` é `"Atendido por"`. Aquilo não é o atendente — são os participantes. Não há rótulo
visível nenhum: só bolinhas coloridas embaixo do nome do cliente. Ninguém sabe o que são.

**4. Não há indicação de "você está participando".**

O subtítulo mostra `Atendido por Fulano` e continua igual depois que você entra. A distinção entre
"eu sou o dono" e "eu estou acompanhando" não existe visualmente — e é exatamente essa distinção que
a Michele tentou descrever.

**5. "Pedido pendente" não diz nada sobre o pedido.**

Um botão desabilitado escrito "Pedido pendente". Não diz para quem foi, se a pessoa foi avisada, nem
quanto tempo vale (existe validade configurada — `participacoes.validadeConfigurada()` — e o pedido
expira em silêncio).

**6. Quem aprova não sabe quem pediu.**

```jsx
{pedidosPendentes.map((pedido) => (
  <span key={pedido.id}>
    <Button ...>{textos.aprovarEntrada}</Button>
    <Button ...>{textos.recusarEntrada}</Button>
  </span>
))}
```

Dois botões por pedido, sem o nome do solicitante. Com dois pedidos ao mesmo tempo, o dono vê quatro
botões idênticos e tem que adivinhar.

**7. Ninguém avisa que falar transfere o atendimento.**

Depois da E115, participante fala sem transferir — mas quem **não** é participante continua
assumindo o atendimento ao enviar (RN-CRM-06, e é assim que deve ser). Hoje nada na tela avisa isso.
A pessoa digita para ajudar e descobre depois que levou o cliente do colega.

## O que fazer

Trate os sete pontos acima. Diretrizes:

- **Erro precisa aparecer.** Use o mecanismo de feedback que o projeto já tem — procure como outras
  telas mostram falha de mutação e siga o mesmo padrão. Não invente um sistema de toast novo, não
  use `alert`, e traduza o erro para linguagem de operação ("você não tem permissão para entrar
  direto neste atendimento"), não o texto cru da exceção.
- **Nomear é metade do trabalho.** Os participantes precisam de rótulo visível, e o `aria-label`
  errado precisa sair. Quem está vendo precisa distinguir dono de participante sem passar o mouse.
- **Todo texto novo vai para o `textos.json`**, na seção que já existe. Nenhuma string solta em
  componente — é a regra do projeto e é o que permite o cliente ajustar o vocabulário sem deploy.
- **Aviso do item 7 só onde ele é verdade.** Para quem já é participante depois da E115, falar não
  transfere — então o aviso não pode aparecer para essa pessoa. Se a E115 ainda não tiver integrado
  quando você chegar aqui, implemente o aviso condicionado a "não sou participante ativo", que é
  correto nos dois mundos.
- Respeite os tokens e o Base UI do projeto (`data-active:`, nunca `data-[state=active]:`), e o
  comportamento no celular — o cabeçalho é apertado lá.

## Não fazer

- Não mexa em `EnviarMensagemUseCase` nem em nenhuma regra de backend. Se algo só se resolve com
  dado que a API não devolve, **relate em vez de mudar o contrato** — outro agente está no backend
  de envio agora.
- Não mexa na janela de 24h nem no composer (E114 está nesse caminho).
- Sem migration.

## Testes

Cubra, nos testes de componente:

1. Falha na ação de entrar mostra mensagem de erro legível e o estado não avança.
2. Sucesso mostra confirmação e o botão passa a oferecer sair.
3. Participante é apresentado com rótulo, distinto do atendente responsável.
4. Dono com dois pedidos pendentes vê o nome de cada solicitante ao lado dos botões.
5. Quem não é participante vê o aviso de que enviar assume o atendimento; quem é participante não vê.
6. O cabeçalho continua legível na largura de celular.

## Entrega

Branch própria, Conventional Commit, push, PR contra `main`. No relatório, além dos sete itens do
`AGENTS.md`, diga qual mecanismo de erro você reusou e por quê.
