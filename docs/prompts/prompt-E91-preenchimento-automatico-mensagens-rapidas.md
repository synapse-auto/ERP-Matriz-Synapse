# E91 — Preenchimento automático de mensagens rápidas

## Objetivo

Ao escolher uma mensagem rápida no composer de um atendimento, preencher automaticamente as variáveis permitidas com dados reais do lead/atendimento antes de enviar. O usuário deve ver e poder editar o texto final; nenhum valor pode ser enviado de forma invisível.

## Base

- Crie worktree da `origin/main` e branch `codex/e91-preenchimento-mensagens-rapidas`.
- Leia `AGENTS.md`, contratos de mensagens rápidas, composer, campos customizados e regras de visibilidade antes de editar.

## Obrigatório

- Audite a sintaxe de variáveis existente e formalize-a sem quebrar mensagens já salvas. Reutilize/centralize o resolvedor entre preview e envio; não duplique regex em componentes.
- Permitir somente variáveis documentadas e com fonte autorizada, como nome do lead e campos customizados permitidos. Não expor CPF, notas, resumo de IA, token, telefone completo ou dados de outro lead por preenchimento automático.
- Ao selecionar a mensagem, resolver variáveis conhecidas no composer; variável sem valor permanece destacada/identificável e bloqueia o envio com explicação de catálogo, até o usuário corrigir/remover.
- O usuário pode editar o texto preenchido normalmente. Não alterar o template salvo, não enviar automaticamente e não mudar autoria, transferência de lead ou regras de template/24h.
- Preview administrativo de mensagens rápidas usa o mesmo resolvedor com estado neutro real, sem inventar dados de cliente.
- Caso a mensagem rápida seja marcada apenas para Chatbot/IA, preserve o filtro de visibilidade existente e não a entregue a atendente humano.

## Testes

- Domínio/backend: sintaxe válida/inválida, variável permitida/proibida, ausência de valor e isolamento de lead.
- Frontend: seleção preenche, usuário edita, pendência bloqueia envio, erro preserva texto e template original não é mutado.
- Teste negativo: atendente não consegue resolver dado de lead fora do seu recorte; nenhuma variável é interpolada no frontend a partir de conteúdo não autorizado.
- Navegador com dados de demonstração: selecionar mensagem rápida, conferir preenchimento, editar, enviar e validar comportamento em 390 px.
- Rode `./mvnw clean verify` se backend tocar; npm ci/lint/typecheck/test/build e `git diff --check`.

## Entrega

- Commit/push em `origin/codex/e91-preenchimento-mensagens-rapidas`, PR e CI remota antes de merge. Relatório completo conforme `AGENTS.md`.
