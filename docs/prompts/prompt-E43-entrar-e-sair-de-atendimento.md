# Prompt E43 — entrar e sair de atendimento em andamento, com pedido de permissão

> Leia `AGENTS.md`, `CLAUDE.md`, `frontend/AGENTS.md`, `docs/01-arquitetura-geral.md` e
> `docs/05-rastreabilidade-requisitos.md`.
> **Depende da E42** (canal pessoal de notificação). Não comece antes de a E42 estar entregue.
> **Pode commitar localmente a qualquer momento** — trabalho solto no working tree já se perdeu
> neste projeto. **Não execute `git push` sem autorização explícita do Marcondes.**

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

### Como o atendente alcança uma conversa que não é dele

**Decidido: a `RN-CRM-01` se mantém, inteira. O caminho é a Agenda de Contatos.**

Leia `VisibilidadeLead.java` antes de mexer em qualquer coisa perto disso. A regra está em Java puro,
num tipo **selado**, e o comentário dela diz por que existe: *"Os atendentes trabalham por comissão e
disputam leads entre si. Um atendente enxergar o lead de outro não é bug de tela, é problema
comercial na casa do cliente."* Hoje um `ATENDENTE` enxerga os leads dele **mais os que ainda não têm
dono** — e nada além disso.

Nada nesta etapa amplia isso. Em particular:

- **A lista de Atendimentos continua exatamente como está.** Nenhuma conversa de colega aparece nela.
- **A Agenda não vira vitrine da carteira alheia.** O atendente não ganha uma lista navegável dos
  contatos dos outros; ele **procura por nome ou telefone** um cliente específico.
- O que a busca devolve, para um contato que é de outro atendente, é o **mínimo para pedir entrada**:
  nome, empresa e quem é o responsável. **Não** o histórico, **não** as mensagens, **não** a ficha
  completa, **não** a etapa. A partir daí o botão disponível é "pedir para entrar" — nada mais.

A diferença que sustenta isso: procurar um cliente que acabou de te ligar não é a mesma coisa que
folhear a carteira do colega. A primeira é socorro pontual; a segunda é o que a RN-CRM-01 existe para
impedir.

Se a implementação disso exigir alargar `VisibilidadeLead`, **pare e relate** — esse tipo é selado de
propósito, e o compilador quebra o build justamente para que uma variante nova não escape em silêncio
para o banco como "sem filtro". Uma alternativa nova ali é decisão de arquitetura, não detalhe de
implementação desta etapa.

---

## Bloco 0.5 — Um conserto de uma linha, para não voltar em deploy separado

O badge de pendentes que a E41 colocou ao lado de "Atendimentos" em `sidebar.tsx` aparece mesmo
quando a contagem é zero — a guarda ficou `contagemPendentes !== undefined`. Contador que mostra `0`
o tempo todo vira ruído, e o usuário aprende a ignorar o badge exatamente quando ele passar a
importar.

Troque para só renderizar acima de zero, e ajuste o teste que cobre esse caso.

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
- Não amplie a visibilidade da `RN-CRM-01`, nem acrescente alternativa em `VisibilidadeLead`.
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
- **Teste de que a busca da Agenda não vaza a conversa alheia:** para um contato de outro atendente,
  a resposta traz nome, empresa e responsável — e nenhuma mensagem, histórico, etapa ou ficha. Este é
  o teste que protege a regra que o cliente mais preza.
- Teste de que a lista de Atendimentos de um `ATENDENTE` continua idêntica ao que era antes desta
  etapa.
- Backend: `./mvnw -pl crm-atendimento -am verify` **com testes**.
- Frontend: `npm test -- --run`, `npm run lint`, `npm run build`.
- Verificação visual com o seed aplicado, com dois usuários diferentes. Se não conseguir, **diga**.
