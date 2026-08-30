# E88 — Mídias e documentos na ficha do lead, com salvar imagem

## Objetivo

Entregar uma seção real de **Mídias e documentos** na ficha lateral do lead e permitir que imagens do histórico sejam salvas/baixadas de forma autorizada. Reaproveite o storage e as mensagens já existentes; não duplique arquivo nem exponha URL privada.

## Base

- Crie worktree da `origin/main` e branch `codex/e88-midia-ficha-e-download`.
- Leia `AGENTS.md`, o fluxo atual de `EnviarMidiaUseCase`, histórico, storage e painel do lead. Não altere migrations existentes.

## Obrigatório

- Adicione ao painel lateral do lead uma seção com mídias e documentos reais vinculados a atendimentos visíveis daquele lead: imagem, áudio e documento, com nome, tipo, tamanho, data e origem quando disponível.
- Paginação/carregamento incremental, vazio e erro reais; não carregar binário nem conteúdo integral em lista.
- Imagens abrem visualização segura e possuem ação “Salvar imagem”; documentos possuem ação de download; áudio preserva player existente.
- Se URL assinada/cross-origin não permitir download confiável, crie endpoint backend autorizado que valida lead + atendimento + mensagem antes de devolver `Content-Disposition`. Nunca use URL externa ou id arbitrário do frontend como autorização.
- Atendente A não lista, visualiza nem baixa anexos do lead de B. Gestor/subgestor seguem a regra de visibilidade já existente.
- Não implemente banco de arquivos global, exclusão, compartilhamento público, upload novo no painel ou duplicação de storage.
- Textos no catálogo/schema, cores por tokens e UI responsiva. Não bloquear o chat enquanto mídia é consultada/baixada.

## Testes

- Testcontainers: listagem paginada, download autorizado, 401/403, A não acessa B, mensagem sem mídia e URL inexistente/expirada sem vazamento.
- Frontend: estados vazio/carregando/erro, renderização dos três tipos, botão salvar imagem, download de documento, paginação e ausência de card fantasma.
- Navegador autenticado: conferir desktop e 390 px, abrir imagem e salvar arquivo; anexos reais de demonstração apenas.
- Rode `./mvnw clean verify`, `npm ci`, lint, typecheck, testes, build e `git diff --check`.

## Entrega

- Commit Conventional Commit, push para `origin/codex/e88-midia-ficha-e-download`, PR contra main e CI remota antes de solicitar merge.
- Relatório nas sete seções de `AGENTS.md`, incluindo qualquer variável Dokploy nova (espera-se nenhuma).
