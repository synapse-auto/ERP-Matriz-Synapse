# Prompt E42 — canal pessoal de notificação e aviso de transferência recebida

> Leia `AGENTS.md`, `CLAUDE.md`, `frontend/AGENTS.md` e `docs/01-arquitetura-geral.md`.
> **Não faça commit nem push sem autorização explícita do Marcondes.**
> Ao encerrar, informe os testes executados, o diff e o que ficou sem verificação.

---

## Contexto

Três funcionalidades pedidas dependem da mesma peça que hoje não existe: **um destino de tempo real
endereçado a uma pessoa, e não a um atendimento**. Esta etapa constrói essa peça e entrega, com ela,
o menor caso de uso possível — o aviso de transferência. As etapas E43 (entrar/sair de atendimento)
e E44 (chat interno) assentam em cima. **Não construa aqui nada das outras duas.**

### O que já existe (verificado no repositório, não presuma diferente)

- `RelayDeTempoRealListener.aoTransferir` publica um evento `TRANSFERENCIA` no canal **do
  atendimento**. A finalidade dele é **revogação**: fazer o dono anterior parar de receber.
- O payload já carrega `deAtendenteId`, `paraAtendenteId`, `quemTransferiu` e `atorTipo`. **O dado
  necessário para avisar o destinatário já está lá.**
- `pagina-atendimentos-cliente.tsx` já consome a revogação: se o dono anterior estava com aquela
  conversa aberta, ela fecha e aparece uma tarja com `textos.tempoReal.conversaEncerrada`.
- `RedisSubscriberDeAtendimento.enviarParaUsuario` já faz `convertAndSendToUser`, e o relay via
  Redis já espalha entre instâncias.

### O que não existe

**O atendente que RECEBE a transferência não é avisado de nada.** Ele não está inscrito em
`/user/queue/atendimento.{id}` — acabou de receber o atendimento — e o
`AutorizacaoDeAssinaturaInterceptor` barra esse prefixo para quem não alcança o atendimento. Hoje ele
só descobre quando a lista recarrega, e pode não perceber.

---

## Bloco 1 — O destino pessoal

Crie um destino de tempo real **por usuário, não por atendimento**.

Três exigências que vêm de armadilhas já documentadas no próprio código:

- **O nome do destino não pode começar com `/queue/atendimento.`** — `RedisSubscriberDeAtendimento`
  (linhas 38-43) explica por quê: o interceptor trata esse prefixo como assinatura de atendimento e
  a própria fila seria cancelada numa revogação. A fila de revogações já contorna isso; siga o mesmo
  padrão.
- **Reaproveite o transporte que existe.** O relay Redis + `convertAndSendToUser` já resolve entrega
  multi-instância. **Não crie um segundo mecanismo de tempo real.**
- **O interceptor precisa autorizar o novo destino, e só para o próprio dono.** Um usuário assinando
  a fila pessoal de outro é vazamento de dado entre atendentes.

O envelope precisa ser **extensível por tipo**: a E43 e a E44 vão publicar outros tipos de aviso
nessa mesma fila. Modele o tipo como campo do envelope, não como um destino novo por funcionalidade.

## Bloco 2 — O aviso de transferência recebida

Quando um atendimento é transferido, além da revogação que já existe, publique um aviso para
`paraAtendenteId`.

**O aviso é consequência do evento de domínio, não de uma chamada externa.** Isto é obrigatório e
não é detalhe de implementação:

- **A Automação (n8n) não ganha endpoint novo, não chama nada e não fica sabendo que a notificação
  existe.** Nenhuma rota em `/internal/v1` é criada ou alterada nesta etapa.
- O gatilho é `EventoDeAtendimento.AtendimentoTransferido`, publicado dentro do CRM em
  `TransferirAtendimentoUseCase.transferir(...)`, e consumido em `AFTER_COMMIT`. Só dispara depois
  que a transferência de fato foi gravada — nunca antes, nunca se a transação falhar.
- Verificado no repositório: **as quatro entradas de transferência afunilam nesse mesmo método
  privado** — `executar` (humano), `executarPelaAutomacao` (a IA passando para um atendente, que é o
  caso mais comum nesta operação), `devolverParaIaPelaAutomacao` e `devolverParaIaPeloSistema`.
  Amarrando no evento, o pop-up funciona nos dois caminhos sem código duplicado. **Não amarre no
  controller nem no caminho HTTP**, ou a transferência vinda da IA fica sem aviso.

Três recortes:

- **Não avise quem transferiu para si mesmo.** Um gestor que puxa a conversa para si não precisa de
  pop-up dizendo que ele fez o que acabou de fazer.
- **Não avise quando `paraAtendenteId` é nulo** (devolução para a IA).
- O aviso carrega o suficiente para a tela abrir a conversa sem uma segunda consulta: id do
  atendimento, id e nome do lead, e quem transferiu.

## Bloco 3 — A tela

- Assine a fila pessoal na conexão, junto com as revogações.
- Mostre o aviso como **pop-up/toast**, não como tarja fixa: é um evento pontual, e o atendente pode
  estar no meio de outra conversa. Clicar no aviso abre o atendimento recebido.
- **Invalide a lista e as contagens** quando o aviso chegar. O badge e a aba precisam refletir o
  atendimento novo sem F5.
- Todo texto no catálogo (`textos.json` + `schema.ts`). **Nenhum literal no JSX.**
- Se o atendente estiver com **outra** conversa aberta, ela não pode ser fechada nem trocada por
  baixo do pano. O aviso convida; ele decide.

## Bloco 4 — O que fazer quando o aviso se perde

Redis pub/sub é **at-most-once** — `RegistroDeAssinaturas` (linhas 24-25) já registra isso para a
revogação. Um aviso perdido não pode ser a única forma de o atendente saber que recebeu a conversa.

Garanta a convergência: ao reconectar, a lista e as contagens são recarregadas, então o atendimento
aparece de qualquer jeito. **O pop-up é conveniência, não é o mecanismo de entrega do trabalho.**
Diga no relatório como isso está garantido hoje — se não estiver, é isso que precisa ser feito
antes do pop-up.

---

## Verificação

- Teste de que o destinatário recebe o aviso e o transferidor não.
- Teste de que transferência para a IA (`paraAtendenteId` nulo) não gera aviso.
- **Teste de que a transferência feita pela Automação (`executarPelaAutomacao`) avisa o atendente
  destinatário exatamente como a feita por um humano.** É o caminho mais usado na operação e o mais
  fácil de esquecer.
- Teste de autorização: um usuário **não** consegue assinar a fila pessoal de outro.
- Teste de que a revogação do dono anterior continua funcionando exatamente como antes — essa é a
  regressão que mais dói, porque é o que impede o dono antigo de seguir lendo a conversa.
- Backend: `./mvnw -pl crm-atendimento -am verify` **com testes**. Se você rodar `clean` e compilar
  um módulo isolado, a compilação do `crm-app` falha por falta dos irmãos no reator — isso é erro de
  invocação, não defeito do projeto.
- Frontend: `npm test -- --run`, `npm run lint`, `npm run build`.
