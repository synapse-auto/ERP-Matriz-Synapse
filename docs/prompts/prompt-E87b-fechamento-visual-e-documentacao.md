# E87b — Fechamento visual e documentação de Responder/Encaminhar

## Contexto

A E87 está implementada na branch `codex/e87-responder-encaminhar`, PR #19, SHA `1072301`, com CI remota verde na run `33245124040`. O código e os testes automatizados cobrem Responder/Encaminhar, mas o relatório deixou duas pendências antes do merge.

## Pendências obrigatórias

1. Validar os fluxos em navegador autenticado, com backend e dados de demonstração reais:
   - abrir o menu de uma mensagem recebida;
   - clicar Responder;
   - confirmar citação de autor/trecho/tipo no composer;
   - cancelar e confirmar que rascunho/contexto permanecem corretos;
   - responder uma mensagem com `wamid` e confirmar a citação na bolha;
   - tentar origem antiga sem `wamid` e confirmar erro 422 sem link falso;
   - abrir Encaminhar, buscar um único destino visível, confirmar e validar nova mensagem no destino;
   - confirmar origem intacta e ausência de destinos não autorizados;
   - testar desktop e 390 px, sem recorte da bolha sob cabeçalho/composer e sem quebra das ações/reactions.
2. Atualizar `docs/04-adrs-e-api.md` e qualquer runbook/contrato relacionado com:
   - endpoint POST de encaminhamento;
   - referência persistida de resposta;
   - regras de visibilidade de origem/destino;
   - `wamid` obrigatório para resposta real ao WhatsApp;
   - diferença entre resposta com contexto do Meta e encaminhamento como novo envio;
   - erros 422 e idempotência/ausência de gravação parcial.

## Regras

- Trabalhe na branch da E87 ou em uma branch de correção baseada nela; não altere `main` diretamente.
- Não faça chamada real à Meta/WhatsApp; use provedor fake/local para teste.
- Se a validação visual encontrar a regressão dos balões, corrija somente a causa, preserve as ações e atualize os testes.
- Não enfraqueça autorização/RLS, não permita origem/destino arbitrários e não altere regras RN-CRM-01/RN-CRM-06.

## Validação e entrega

- Rode testes afetados e, se houver código, `./mvnw clean verify`, `npm ci`, lint, typecheck, testes, build e `git diff --check`.
- Gere screenshots sem dados reais de cliente.
- Faça commit Conventional Commit e push da correção na branch da E87; informe a nova run de CI. Não faça merge/deploy sem autorização.
- Relatório final nas sete seções de `AGENTS.md`, declarando explicitamente se os dois fluxos foram clicados no navegador ou se o ambiente impediu a validação.
