# E90 — Tag de atendimento em IA nos cards da lista

## Objetivo

Exibir nos cards da lista de Atendimentos uma tag compacta e inequívoca quando o lead estiver sendo atendido pela IA. A marca deve refletir o estado real do atendimento, não uma inferência do frontend nem dado mockado.

## Base

- Crie worktree da `origin/main` e branch `codex/e90-tag-atendimento-ia`.
- Leia `AGENTS.md`, `ItemInbox`, regras RN-CRM-01/RN-CRM-06 e a origem backend do painel/lista antes de editar.

## Obrigatório

- Audite o contrato atual: se ele já expõe estado confiável de automação/IA, reutilize-o; caso contrário, adicione somente um campo derivado mínimo no resumo da inbox, nunca entidade inteira ou dados de IA sensíveis.
- Renderize tag “Atendido pela IA” (texto no catálogo) somente para atendimento realmente ativo sob IA. Não mostrar para finalizado, humano, sem atendimento ou chat interno.
- A tag deve ser legível, compacta, acessível e sem cor hardcoded; não substituir etapa, canal, contagem ou responsável no card.
- Preserve regras de visibilidade: atendente não passa a descobrir lead de colega ou detalhes de automação por causa da tag.
- Atualização em tempo real/invalidação deve refletir transferência para humano, devolução para IA e finalização sem recarregar a página inteira desnecessariamente.

## Testes

- Backend: origem correta do campo, recorte de visibilidade e transições IA → humano, humano → IA e finalização.
- Frontend: tag aparece apenas no estado elegível, não aparece em chat interno/finalizado/humano, e usa catálogo/tokens.
- Navegador real em desktop e 390 px, sem quebrar prévia, badge ou horário do card.
- Rode backend `clean verify` se houver alteração backend; sempre npm ci/lint/typecheck/test/build e `git diff --check`.

## Entrega

- Commit/push em `origin/codex/e90-tag-atendimento-ia`, PR e CI antes de merge. Relatório nas sete seções de `AGENTS.md`.
