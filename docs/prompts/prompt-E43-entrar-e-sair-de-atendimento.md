# Prompt E43 — entrar e sair de atendimento em andamento, com pedido de permissão

> Leia `AGENTS.md`, `CLAUDE.md`, `frontend/AGENTS.md`, `docs/01-arquitetura-geral.md` e
> `docs/05-rastreabilidade-requisitos.md`.
> **Depende da E42** (canal pessoal de notificação). Não comece antes de a E42 estar entregue.
> **Não faça commit nem push sem autorização explícita do Marcondes.**

---

## Bloco 0 — A regra de propriedade, já decidida

**Leia antes de escrever qualquer código. A decisão de negócio já foi tomada pelo Marcondes com o
cliente — não a reabra, não a "melhore".**

A `RN-CRM-06` está implementada em `EnviarMensagemUseCase`: **quando um atendente envia uma mensagem
manual, o `atendimento.atendente_id` passa a ser dele.** É assim que o CRM sabe de quem é a conversa
— e, na operação da Estrutural Vidros, de quem é a comissão.

**Decisão: a conversa é de quem atendeu por último.** O convidado que entra **pode responder**, e a
resposta dele transfere a propriedade normalmente, pela mesma RN-CRM-06 que já existe. Não crie
exceção, não crie modo leitura, não crie flag para desligar a regra no convidado.

Disso decorrem três exigências:

1. **Entrar, sair, pedir e aprovar não mudam `atendente_id`.** Só o envio de mensagem muda — pela
   regra que já existe — e a transferência explícita. Se o convidado entrar e não falar nada, a
   conversa continua sendo de quem era.
2. **A tela precisa ser honesta sobre isso.** Ao aprovar uma entrada, o dono está aceitando que o
   outro pode assumir a conversa ao responder. Isso tem que estar escrito no pedido de aprovação, em
   uma linha, no catálogo de textos. Um dono que perde a conversa sem ter entendido o que aprovou é
   um problema de comissão que vira discussão entre pessoas.
3. **A troca de dono continua sendo um evento auditável.** Ela já publica `AtendimentoTransferido` e
   já avisa o novo dono pela E42. Confira que isso continua valendo quando a troca acontece por
   resposta de convidado, e não por transferência explícita — é o caminho novo que esta etapa cria.

Um segundo ponto **continua em aberto** e não deve ser inventado: pela `RN-CRM-01`, um `ATENDENTE` só
enxerga os leads dele. Se ele não vê a conversa dos outros, ele não tem como pedir para entrar. Até
que exista decisão em contrário, valha o menor escopo possível:

- `GESTOR`, `SUBGESTOR` e `ADMINISTRADOR` enxergam tudo e **entram sem pedir**;
- `ATENDENTE` só pode pedir para entrar em conversa que ele **já enxerga** hoje.

Nada nesta etapa pode ampliar a visibilidade que o servidor concede hoje. Se essa limitação tornar a
funcionalidade inútil na prática para o papel `ATENDENTE`, **diga isso no relatório** em vez de
resolver por conta própria.

---

## Bloco 1 — Modelo

Duas coisas distintas, não misture numa tabela só:

- **quem está dentro agora** — participação corrente de um atendimento, além do dono;
- **o pedido de entrada** — solicitante, dono, estado (pendente, aprovado, recusado, expirado),
  quando foi pedido e quando foi respondido.

Exigências:

- Migração **nova**, com número novo. Nunca edite migração já aplicada.
- Um pedido pendente por solicitante e atendimento — garantido por índice único parcial no banco,
  não por checagem na aplicação. Dois cliques no botão não podem virar dois pedidos.
- Sair não apaga histórico: quem entrou e quando é **auditoria**, e o CRM já tem
  `AuditoriaDeAtendimentoListener` e `TimelineDeAtendimentoListener` para isso. Use-os; não invente
  um terceiro registro paralelo.

**Expiração sem agendador.** A `RN-CRM-07` diz que o CRM configura automação e não a executa — este
projeto **não tem scheduler e não vai ganhar um aqui**. Um pedido pendente velho é considerado
expirado **no momento da leitura**, comparando a idade com um limite configurado. Não crie job, não
crie `@Scheduled`.

## Bloco 2 — Casos de uso

Pedir entrada, aprovar, recusar, entrar (direto, para quem tem alçada) e sair.

- A autorização vive **no caso de uso**, não só no controller — é a convenção deste projeto
  (`docs/04-adrs-e-api.md`), e foi onde um agente anterior já procurou no lugar errado.
- Só o **dono** do atendimento aprova ou recusa. Nem o solicitante, nem um terceiro que também esteja
  dentro.
- Aprovar um pedido que já expirou, já foi recusado, ou cujo atendimento já foi transferido ou
  finalizado, **falha com erro claro** — não entra em silêncio.
- Se o atendimento for transferido enquanto há pedido pendente, o pedido morre com o dono antigo.
  Aprovação de quem não é mais dono não vale.

## Bloco 3 — Tempo real

Use **a fila pessoal da E42**. Não crie um terceiro transporte.

- O dono recebe o aviso de que alguém pediu para entrar, com nome de quem pediu e a conversa.
- O solicitante recebe o aviso de aprovação ou recusa.
- **Ao entrar, o convidado passa a enxergar as mensagens em tempo real** — ou seja, o
  `AutorizacaoDeAssinaturaInterceptor` precisa passar a considerar a participação, e não só a posse.
- **Ao sair, a assinatura é revogada.** Esse caminho já existe (é o mesmo da transferência); reuse-o
  em vez de escrever outro. Convidado que saiu e continua recebendo mensagem do cliente é vazamento.

## Bloco 4 — Tela

- No cabeçalho da conversa: quem está dentro, além do dono. Reaproveite `AvatarIniciais` e
  `tomDoAvatar` — a mesma pessoa tem o mesmo tom em todas as telas.
- Botão de pedir entrada / entrar / sair, com o estado correto em cada papel e em cada situação
  (sem pedido, pedido pendente, dentro, recusado).
- O pedido pendente aparece para o dono como ação aceitar/recusar, não como um aviso que some.
- Todo texto no catálogo. Nenhum literal no JSX.

## Bloco 5 — O que NÃO entra

- Não mexa em transferência, finalização ou rodízio.
- Não amplie a visibilidade da `RN-CRM-01`.
- Não implemente chat interno (é a E44), mesmo que pareça natural conversar sobre o atendimento.

---

## Verificação

- Teste de que entrar, sair, aprovar e recusar **não alteram** `atendimento.atendente_id`.
- Teste de que a mensagem enviada pelo convidado **transfere** a propriedade para ele, pela
  RN-CRM-06, e que o antigo dono é avisado pelo caminho da E42. Este par de testes é o que protege a
  comissão: um garante que ninguém perde a conversa à toa, o outro que quem atendeu por último de
  fato ficou com ela.
- Teste de que dois pedidos simultâneos do mesmo solicitante não criam duas linhas.
- Teste de que sair revoga a assinatura de tempo real.
- Teste de expiração por leitura, sem relógio de produção: injete o tempo, não use `now()` solto.
- Backend: `./mvnw -pl crm-atendimento -am verify` **com testes**.
- Frontend: `npm test -- --run`, `npm run lint`, `npm run build`.
- Verificação visual com o seed aplicado, com dois usuários diferentes. Se não conseguir, **diga**.
