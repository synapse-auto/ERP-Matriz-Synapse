# Chat interno — envio de vídeo validado

Segunda capacidade da auditoria em `docs/45-chat-interno-midia-e-paridade.md`, dependente do PR #223. A branch parte do HEAD `d4f7193` daquele PR para não duplicar as correções de download na revisão.

## Contrato e proteção

Reutiliza `POST /api/v1/chat-interno/conversas/{id}/mensagens/midia`, multipart `arquivo`, `legenda` opcional e `Idempotency-Key` opcional para compatibilidade; o composer envia a chave e a mantém em retry. JWT e participação obrigatórios, inclusive gestor/administrador. Resposta 201 com a mensagem única `VIDEO`, legenda e metadados persistidos; replay da chave devolve a mesma mensagem sem novo evento/upload. Histórico, URL assinada, evento pós-commit e download são os existentes. Nenhum endpoint paralelo ou migration.

Vídeo MP4/3GP é identificado pelo conteúdo, não por nome ou MIME declarado. A estrutura ISO-BMFF deve ser íntegra e conter trilha de vídeo; a marca principal deve pertencer aos contêineres aceitos. M4A com vídeo, QuickTime e contêiner desconhecido continuam recusados. Áudio MP4 continua exigindo estrutura válida sem trilha de vídeo. A nova classificação é do chat interno; classificadores e adaptadores Meta/UZAPI não foram alterados.

Limite vem de `LimiteDeAnexoRepositorio`, categoria `VIDEO`, chave existente `anexo.tamanho_maximo_video_mb`. Preserva o fallback interno preexistente de 100 MiB quando falta configuração e os limites HTTP/multipart globais (o menor limite efetivo prevalece). Não copia o limite de provedor WhatsApp. Erros: 400 para conteúdo não permitido, 401 sem autenticação, 403 não participante, 409 para chave reaproveitada com outra requisição, 413 para tamanho acima do limite; RFC 7807 existente. Falha preserva anexo/legenda no composer; limpeza acontece após confirmação. Storage privado e compensação existente continuam intactos.

## Evidência

`VideoChatInternoIT` passou 7/7 em PostgreSQL real: envio pela rota real em direta/grupo, legenda multilinha, persistência/histórico, download byte a byte e nome/MIME, replay sem mensagem duplicada, gestor não participante recusado, três marcas inválidas, arquivo disfarçado e limite configurado. Integração dirigida com `ChatInternoMidiaIT`: 9/9. Frontend dirigido 20/20, typecheck, lint (sem erros; avisos preexistentes) e build aprovados. `clean verify` Java 21 completo está em execução; este texto não o apresenta como concluído nem como CI verde.

Playwright CLI headed na aplicação real local, PostgreSQL isolado e MinIO: envio de MP4 gerado para teste pelo composer, legenda preservada, reprodução por `<video>`, download com SHA-256 igual ao original (`7814E81F93198B1F753707DD3F85E2EE7F8D459FB8FC4117E10E07FF994E1F04`), recarregamento e reprodução posterior. Desktop 1440×1000 e celular 390×844; screenshots em [desktop](assets/chat-interno-video/chat-video-desktop.png) e [celular](assets/chat-interno-video/chat-video-mobile.png). Testes de grupo são de integração; não alegar E2E de grupo com navegador nesta etapa. 3GP tem classificação implementada, mas não houve arquivo 3GP real na validação visual.

Sem deploy, mensagem a cliente real, nova variável obrigatória ou ação no Dokploy. Contato estruturado, retry de encaminhamento e E2E de reações entre abas/reconexão permanecem capacidades separadas. O PR #223 teve backend/frontend/stack verdes na run `36222861636` no SHA `d4f7193`; isso não certifica a CI desta segunda branch.
