# Prompt E07b — URGENTE: jobs agendados quebrados

> **Faça isto antes de qualquer outra coisa.** Etapa curta: ~2 horas.

---

## A gravidade

`PublicadorDaOutbox` e `ProcessadorDeWebhookEntrada` chamam `ContextoDeServico.executarComo(nome, this::metodoTransacional)`. Auto-invocação pula o proxy do Spring, o `@Transactional` nunca é aplicado, e o `TransacaoObrigatoria.exigir(...)` da E05 lança.

Consequência em produção:

- **A outbox nunca drena.** Mensagem enviada pelo atendente fica `PENDENTE` para sempre. O cliente nunca recebe.
- **Webhooks nunca são processados.** Mensagem do cliente nunca aparece na tela do atendente.

Ou seja: **o caminho de mensagens está quebrado nas duas direções** — exatamente o que a regra de precedência do `CLAUDE.md` existe para impedir.

## Por que passou despercebido

Este é o sétimo caso do mesmo padrão neste projeto, e o mais instrutivo, porque desta vez **a proteção funcionou**. O `TransacaoObrigatoria` detectou e lançou, a cada tick, em toda execução de teste.

O que falhou foram três coisas em série:

1. O `@Scheduled` do Spring engole a exceção e apenas loga — o agendador não morre, então nada chama atenção
2. **Nenhum teste exercita o método `@Scheduled`.** Todos chamam o método transacional direto pelo bean injetado, que é o caminho que funciona
3. O erro estava visível nos logs de todos os testes e virou ruído de fundo

A lição vale mais que a correção: **um alarme que dispara sempre é indistinguível de nenhum alarme.**

## O que fazer

### 1. Corrigir a auto-invocação

Extraia o método transacional para um bean próprio, injetado no agendador. Não use auto-injeção (`@Lazy self`) — funciona, mas mantém o desenho que causou o problema e o próximo leitor não vai perceber a armadilha.

Verifique **todos** os `@Scheduled` do projeto, não só esses dois. Onde mais houver `executarComo` chamando método do próprio bean, tem o mesmo defeito. Inclui o job de partições, o de alerta da `DEFAULT` e o de outbox esgotada.

### 2. Testar o ponto de entrada, não o método interno

Esta é a correção que realmente importa. Cada job agendado ganha um teste que chama **o método anotado com `@Scheduled`**, pelo bean do contexto Spring, e verifica o efeito observável:

- Publisher: outbox com pendente → chamar o método agendado → pendente publicado
- Webhook: entrada não processada → chamar o método agendado → mensagem criada
- Partições: chamar o agendado → partição do mês seguinte existe

Um teste que chama o método interno prova que a lógica funciona. Só o que chama o ponto de entrada prova que ela **é executada**.

### 3. Fazer o job falhar alto

Configure um `ErrorHandler` no agendador que, em vez de logar e seguir, emita alarme com marcador claro (mesmo padrão do `[ALERTA_OUTBOX_ESGOTADA]`). Um job de caminho crítico que morre em silêncio é a mesma classe de problema que a partição faltante e o `DoNotIncludeJars`.

Considere também uma métrica de "ticks bem-sucedidos" por job — zero sucessos em N minutos é o sinal que o `/health/critical` da E09b deve consumir.

### 4. Limpar o ruído

Se `exige transacao ativa` aparece nos logs de todo teste, o log virou ruído. Depois da correção, **confirme que ele sumiu** — e trate qualquer erro recorrente em log de teste como defeito, não como paisagem.

## Definição de pronto

- [ ] Nenhum `@Scheduled` chamando método transacional do próprio bean
- [ ] Teste por ponto de entrada agendado, verificando efeito observável
- [ ] `ErrorHandler` alarmando em vez de logar e seguir
- [ ] Logs de teste limpos de `exige transacao ativa`
- [ ] Envio ponta a ponta funcionando pelo caminho real (agendador drena a outbox)

Commit: `fix: jobs agendados nao executavam por auto-invocacao`.

Ao terminar, me diga há quantas etapas o defeito existia e se algum teste teria como pegá-lo antes — quero entender se falta uma categoria de teste no projeto ou se foi caso isolado.
