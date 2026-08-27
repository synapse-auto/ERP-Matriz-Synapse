# Prompt E61 — Fechamento da E60: MIME de áudio e validação final

## Contexto

Você está fechando a E60 do Synapse CRM / Base PAI. Leia antes de agir:

1. `AGENTS.md`
2. `docs/13-estado-do-projeto.md`
3. `docs/prompts/COMO-ESCREVER-PROMPTS.md`
4. `docs/prompts/prompt-E60-correcoes-mensagens-programadas-atendimentos-midia.md`

O relatório do agente não é evidência: confira o código, os testes e, quando possível, o fluxo real.

## Bloqueio encontrado na revisão

O backend passou a inspecionar as trilhas ISO-BMFF e aceita contêiner áudio-only mesmo quando a detecção inicial informa `video/quicktime` ou `video/mp4`. Porém, o frontend ainda rejeita a gravação antes do upload quando `MediaRecorder.mimeType` não começa com `audio/`, em:

`frontend/src/components/atendimentos/use-gravador-audio.ts`

Isso contradiz a decisão aprovada na E60: não confiar somente no MIME declarado pelo navegador. Um áudio-only com MIME declarado como `video/quicktime` pode ser descartado no cliente e nunca chegar ao parser do backend.

## Objetivo

Corrigir o caminho de gravação/upload para que a autoridade sobre o conteúdo seja a validação dos bytes no backend, sem abrir uma brecha para vídeo real.

## Requisitos

- Remover a rejeição baseada exclusivamente em `MediaRecorder.mimeType` no frontend.
- Preservar validações de estado, tamanho e existência de Blob; não enviar gravação vazia.
- Fazer o backend continuar rejeitando conteúdo com trilha de vídeo antes de persistir ou publicar.
- Fazer áudio-only chegar ao tipo de mensagem `AUDIO`, com MIME normalizado conforme o contrato já adotado (`audio/mp4` quando aplicável).
- Em qualquer falha de upload/validação, limpar o estado transitório do composer e exibir o erro do catálogo existente; não deixar preview quebrado persistente na tela.
- Não adicionar transcodificação, binário externo ou permissão genérica para `video/*`.
- Não alterar contrato público, migration, regra de privacidade, outbox ou a semântica aprovada da E60.

## Testes obrigatórios

Crie ou ajuste testes que provem o ponto de entrada real:

1. Frontend: uma gravação áudio-only cujo MIME declarado pelo `MediaRecorder` seja `video/quicktime` não é descartada pelo hook antes do upload.
2. Backend/integrado: os bytes áudio-only são aceitos, armazenados como áudio e publicados no canal fake como `AUDIO`.
3. Backend/integrado: vídeo real continua rejeitado, inclusive quando recebe extensão ou MIME falsos de áudio.
4. Frontend: erro do upload/validação remove preview, arquivo e controles transitórios, permitindo nova tentativa.
5. Não use `Thread.sleep`; para efeitos assíncronos, aguarde uma condição com Awaitility ou equivalente já adotado no projeto.

Se não for possível obter bytes reais de `MediaRecorder`, deixe explícito no relatório que a fixture é estrutural/sintética e não declare o incidente real validado. Não transforme essa limitação em permissão ampla por extensão ou MIME.

## Validação final

- `cd backend && ./mvnw clean verify`, com Java 21 e Testcontainers ativo.
- Suíte frontend relevante, `npm run typecheck`, `npm run lint` e `npm run build`.
- Conferir `git diff --check`.
- Se houver ambiente acessível, executar o fluxo visual de gravação, falha, limpeza e reenvio; registrar evidência.
- Não chamar execução local de CI verde. Só informar CI verde com número da run após push autorizado.
- Não fazer commit ou push sem autorização explícita do Marcondes.

## Relatório exigido

Entregar no formato de `AGENTS.md`: commit/estado, definição de pronto item a item com evidência, decisões, divergências, bugs, fora de escopo e decisões necessárias. Informar especialmente se o MIME `video/quicktime` foi observado em bytes reais ou apenas em fixture sintética.
