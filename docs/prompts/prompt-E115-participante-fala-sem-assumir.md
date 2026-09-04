# E115 — participante pode falar sem tomar o atendimento

## O sintoma, nas palavras da operação

Michele, recepcionista e subgestora: *"colocar outro atendente na conversa / tecla entrar no
atendimento está me transferindo, é para me colocar na mesma conversa."*

## A causa

Entrar **não** transfere. `GerenciarParticipacaoAtendimentoUseCase.entrar(...)` só grava a
participação e publica `ParticipanteEntrou`; nenhuma linha de dono muda. Quem transfere é o **envio**.

`EnviarMensagemUseCase.executarInterno` faz isto em toda mensagem manual, sem condição:

```java
LeadNoCaminhoDeMensagem.Transferencia transferencia = leads.transferirPara(leadId, remetenteId);
...
if (!aberto.pertenceA(remetenteId)) {
    aberto = atendimentos.salvar(aberto.transferirPara(remetenteId));
}
```

É a RN-CRM-06 — "quem envia assume o atendimento" — funcionando como escrita. O efeito colateral é
que **a participação vira inútil**: dá para entrar na conversa, mas no instante em que se fala, o
lead e o atendimento mudam de dono. Michele entra para ajudar com uma frase e sai levando o cliente
do colega.

## A regra nova

A RN-CRM-06 continua valendo — ela existe para que ninguém fale com um cliente e some, deixando a
conversa órfã. O que falta é a exceção que a participação pressupõe:

**Quem já é participante ativo do atendimento fala sem transferir.** O dono continua o dono.

Para quem **não** é participante, nada muda: envio manual assume o atendimento, como hoje.

Isso é coerente com o desenho que já existe. `atendimento_participante` e
`pedido_entrada_atendimento` foram criados justamente para alguém acompanhar a conversa de outro; se
falar rouba o atendimento, essas duas tabelas não servem para nada.

## O que fazer

- Em `EnviarMensagemUseCase`, condicione a transferência a **não** ser participante ativo. A
  consulta já existe: `ParticipacaoAtendimentoRepositorio.eParticipanteAtivo(atendimentoId, usuarioId)`.
- A RN-CRM-01 continua mandando: `leads.transferirPara` também é o que prova que o remetente alcança
  o lead. Se você deixar de chamá-lo para participantes, **precisa** manter uma verificação
  equivalente de alcance — não abra caminho para alguém enviar mensagem a um lead que não enxerga.
  Diga no relatório como você garantiu isso.
- A mensagem continua sendo gravada com o remetente real (`Remetente.atendente(remetenteId)`) — quem
  falou foi quem falou. Só a posse não muda.
- Registre na timeline/auditoria existente que houve mensagem de participante, se já não registrar.
- Na tela, não faça nada além do necessário para refletir o estado real: o cabeçalho precisa mostrar
  que quem entrou está **dentro** e que o dono continua sendo outro. Todo o resto do trabalho de
  interface desta queixa está na **E116**, que roda em paralelo — não duplique.

## Não fazer

- Não remova a RN-CRM-06 nem a enfraqueça para quem não é participante.
- Não mexa na janela de 24h nem no envio de template — outro agente está nesses arquivos agora.
- Sem migration: `atendimento_participante` já tem tudo.

## Testes obrigatórios

1. Participante ativo que **não** é dono envia mensagem: a mensagem é gravada com ele como remetente,
   e `lead.atendente_responsavel_id` e `atendimento.atendente_id` **não mudam**.
2. Não-participante envia mensagem: transfere, como hoje. O teste que hoje prova a RN-CRM-06 continua
   verde, sem alteração.
3. Participante sem visibilidade do lead (RN-CRM-01) continua bloqueado.
4. Dono envia mensagem no próprio atendimento: nada muda, sem transferência redundante.
5. Participante que saiu (`saiu_em` preenchido) volta a transferir ao enviar — ele não é mais
   participante.

## Decisão que precisa ir para o Lucas antes do merge

Com esta mudança, **gestor e subgestor passam a poder falar na conversa de um atendente sem tirar o
lead dele** — desde que entrem primeiro. É o que a Michele pediu.

Duas consequências que ele precisa confirmar:

1. Se ele conta atendimento por atendente para comissão ou meta, isto muda a conta: hoje o gestor que
   responde herda o lead; depois disto, não herda.
2. Existe um prompt em andamento fazendo o **envio de template** também assumir o atendimento,
   "inclusive gestor e subgestor". As duas regras precisam concordar: se participante não transfere
   ao mandar texto, também não pode transferir ao mandar template. **Quem integrar por último ajusta
   os dois caminhos e escreve um teste que prova que eles concordam.**

## Entrega

Branch própria, Conventional Commit, push, PR contra `main`. Relatório nos sete itens do `AGENTS.md`,
dizendo explicitamente como a RN-CRM-01 continua garantida.
