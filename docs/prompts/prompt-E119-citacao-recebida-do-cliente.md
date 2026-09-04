# E119 — quando o cliente responde citando, a citação tem de aparecer

## O sintoma

Card da operação: *"cliente marca a mensagem e responde mas não aparece a marcação nem que foi
respondido"*.

No WhatsApp o cliente segura uma mensagem e responde a ela. No CRM chega só o texto solto. O
atendente perde o fio: não sabe a qual das últimas cinco mensagens o "pode ser" se refere.

## A causa

O `MetaCloudWebhookTradutor` não lê `context` em lugar nenhum — busca por `context` no arquivo não
retorna nada. É o campo em que a Meta manda `context.id`, o `wamid` da mensagem citada.

Citação de **saída** existe (E87: o atendente responde e encaminha). Citação de **entrada** nunca foi
implementada.

## A fundação já existe

A V46 criou as duas tabelas de que esta etapa precisa:

- `mensagem_id_externo (wamid PK → mensagem_id, mensagem_enviada_em, atendimento_id)` — resolve o
  `context.id` para a mensagem local. É escrita tanto no envio quanto na entrada
  (`ProcessadorDeWebhookEntradaOperacoes` a usa), então dá para citar mensagem nossa **e** mensagem
  do próprio cliente.
- `mensagem_referencia` com `tipo IN ('RESPOSTA','ENCAMINHAMENTO')` e os campos `citacao_autor`,
  `citacao_tipo`, `citacao_previa` **desnormalizados de propósito** — o comentário da migration
  explica: se a origem sumir, a bolha ainda mostra um resumo, sem telefone, token ou payload do
  provedor.

E o front já tem `citacao-mensagem.tsx` renderizando citação. **Isto é ligação, não modelagem.**
Confirme cada afirmação antes de codificar.

## O que fazer

Ao traduzir uma mensagem recebida, se houver `context.id`, resolva o `wamid` e grave a referência
como `RESPOSTA`, preenchendo os campos desnormalizados a partir da mensagem de origem.

- **Origem não encontrada não é erro.** O cliente pode citar mensagem anterior ao CRM, ou do tempo do
  chat antigo. Nesse caso grave a mensagem normalmente, **sem** referência. Não invente prévia, não
  falhe o processamento da mensagem — o texto do cliente chegar é mais importante que a citação.
- A prévia é resumo, não conteúdo integral, e segue o que a E87 já faz para a citação de saída.
  **Reutilize a mesma montagem**; não escreva uma segunda.
- Idempotência: a Meta reentrega. Processar o mesmo `wamid` duas vezes não pode duplicar referência.
- Contexto de serviço, como todo o caminho de webhook.

O card diz duas coisas: não aparece **o que foi citado** e não aparece **que foi respondido**. A
primeira é esta etapa. Para a segunda, verifique se a bolha da mensagem original já mostra que houve
resposta; se não mostrar e for barato, faça — se custar mudança de contrato, **relate em vez de
alargar o escopo**.

## Testes obrigatórios

1. Mensagem recebida com `context.id` de uma mensagem **nossa**: referência gravada, autor e prévia
   corretos.
2. Mensagem recebida com `context.id` de uma mensagem **do próprio cliente**: idem.
3. `context.id` desconhecido: a mensagem entra normalmente, sem referência, sem erro.
4. Reentrega do mesmo webhook não duplica referência.
5. Mensagem sem `context` continua igual — nenhuma regressão no caminho normal.
6. A bolha renderiza a citação recebida com o mesmo componente da citação enviada.

## Escopo

Sem mudança de contrato público. Sem migration — as tabelas existem. Não encoste no envio nem no
adaptador da Meta.

## Entrega

Branch própria, Conventional Commit, push, PR contra `main`. Relatório nos sete itens do `AGENTS.md`.
