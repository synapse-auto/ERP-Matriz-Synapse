# E124 — tirar a avaliação do caminho do atendente

## O pedido

Card: *"a avaliação de atendimento está aparecendo para o vendedor, não para o cliente (retirar
botão de avaliar)"*. E, junto: **parar de pedir avaliação ao finalizar**. A gestão ainda está
definindo como a avaliação vai funcionar, e ela passará a ser conduzida pelo n8n. Por enquanto,
fica sem.

São duas remoções, em lugares diferentes:

**1. O botão "Avaliar" no cabeçalho.** `cabecalho-conversa.tsx` renderiza
`catalogo.atendimentos.avaliacao.registrar` e abre o `DialogoAvaliacao`. O texto do diálogo é
*"Registre a nota de 1 a 5 que o cliente…"* — ou seja, é o **atendente** registrando a nota à mão.
É isso que o card chama de "aparecendo para o vendedor".

**2. O pedido automático ao finalizar.** `FinalizarAtendimentoUseCase`:

```java
if (origem == Origem.INDIVIDUAL) {
    avaliacao.preparar(finalizado);
}
```

Toda finalização individual dispara a solicitação de avaliação para o cliente. É isso que faz o
cliente receber "nota de 0 a 10 para esse atendimento?" sempre que alguém fecha a conversa.

## O que NÃO pode ser removido

Esta é a parte que decide se a etapa é boa ou destrutiva. **Isto é uma pausa, não uma exclusão.**
O n8n vai conduzir a avaliação, e quando conduzir, o CRM precisa continuar sabendo receber e
mostrar. Portanto **preserve**:

- a tabela `avaliacao` e o índice único por atendimento (V43);
- `RegistrarAvaliacaoUseCase` e o `AvaliacaoRepositorio`;
- o caminho que captura a resposta do cliente pelo webhook — `WebhookAvaliacaoIT` tem de continuar
  verde, **sem alteração de asserção**;
- os KPIs de avaliação no dashboard e na tela de equipe (`avaliacaoMedia`, `rankingAvaliacao`,
  `equipe.avaliacoes`), que passam a mostrar o que já existe e o que o n8n trouxer;
- qualquer endpoint interno de avaliação usado pela automação.

Se você concluir que algum desses caminhos fica órfão sem o gatilho, **relate em vez de apagar**.

## O que já está na fila fica

`avaliacao.preparar(...)` alimenta uma outbox. Pode existir solicitação **já enfileirada** que ainda
vai disparar depois do deploy, mesmo com o gatilho removido.

**Decisão tomada: não limpe a fila.** Não escreva script de limpeza, não sugira DELETE, não trate
isso como pendência. O que já foi enfileirado sai; o que importa é não enfileirar mais.

Só informe no relatório **quantas** solicitações estão pendentes, se conseguir ver isso sem acesso à
produção — é para ninguém se assustar quando alguns clientes ainda receberem o pedido de avaliação
nas horas seguintes ao deploy.

## Sobre o `DialogoAvaliacao`

Remova o ponto de entrada. Se o componente ficar sem nenhum uso, decida entre apagá-lo ou mantê-lo
e justifique — daqui a pouco a avaliação volta com outro desenho, e apagar hoje para reescrever
amanhã pode não compensar. O que não pode é ficar botão morto na tela.

## Testes obrigatórios

1. Finalizar atendimento individual **não** enfileira nem envia solicitação de avaliação. O teste
   que hoje prova o contrário precisa ser invertido, não apagado — e o relatório diz qual era.
2. Finalizar em lote continua sem enviar, como já era.
3. Avaliação que chega pela resposta do cliente **continua sendo registrada** — `WebhookAvaliacaoIT`
   verde, sem mudança.
4. Os KPIs de avaliação continuam lendo e exibindo o que existe no banco.
5. O cabeçalho não tem mais o botão, e nenhum caminho da tela abre o diálogo.

## Escopo

Sem migration — nada é apagado do banco. Sem mudança em RLS. Não encoste no envio de mensagem, na
janela de 24h nem no status de entrega.

## Entrega

Branch própria, Conventional Commit, push, PR contra `main`. Relatório nos sete itens do `AGENTS.md`.
No relatório, liste **o que ficou preservado** para a volta da avaliação pelo n8n — é essa lista que
o próximo agente vai usar.
