# Chat interno — contato compartilhado

## Contrato e segurança

`POST /api/v1/chat-interno/conversas/{id}/mensagens/contato` exige JWT, participação na conversa e `Idempotency-Key` UUID. Não concede acesso especial a gestores ou administradores.

Payload externo: `{"nome":"...","telefones":["..."]}`. Payload interno: `{"usuarioId":"UUID"}`. A identidade interna é validada no catálogo existente de usuários ativos; o nome persistido é o nome canônico. Não existe busca de usuário/lead por telefone ou nome. Telefones fornecidos junto de identidade interna são recusados.

Resposta 201: o mesmo DTO de mensagem do histórico e WebSocket, com `tipo=CONTATO`, `conteudo=null`, `midiaUrl=null` e `midiaMetadados.contatos`. Cada contato possui nome, origem (`INTERNO`/`EXTERNO`), telefones e, exclusivamente quando interno, `usuarioId`. Não há upload nem objeto de storage.

Erros RFC 7807: 400 para payload/chave inválidos ou identidade indisponível; 401 sem autenticação; 403 sem participação; 409 para reutilização da chave com conteúdo/remetente/conversa diferentes. Limites de validação do payload: nome 255 caracteres, até 20 telefones de até 64 caracteres; cada telefone deve conter 8–15 dígitos e somente pontuação telefônica aceita. O endpoint não normaliza números para descobrir usuários.

## Persistência e idempotência

Usa o tipo CONTATO já disponível desde V81, o repositório e tabela de idempotência existentes, com namespace `contato:`. Reserva, mensagem e conclusão são atômicas na mesma transação. Repetições devolvem a mensagem existente; chamadas concorrentes não criam outra mensagem. Apenas a criação publica o evento existente, entregue depois do commit.

O fingerprint usa o contato canônico. Caso um usuário interno seja renomeado entre a primeira chamada e seu replay, a mesma chave pode resultar em 409; não se deve criar outra chave automaticamente para contornar uma resposta ambígua.

## Interface

O composer permite compartilhar usuário interno do catálogo ou contato externo com vários números. Erro mantém campos e chave para nova tentativa; envio em andamento bloqueia duplicação. Histórico renderiza card com copiar/ligar usando o componente existente. A ação de abrir conversa interna requer UUID explícito, origem INTERNO e presença atual no catálogo autorizado. Sua chamada usa o endpoint existente de conversa direta somente após clique; não cria conversa ao renderizar.

Catálogos antigos recebem os valores padrão do schema. Não existem regras por cliente, migrations, alterações nos canais externos nem configuração nova no Dokploy.

## Evidências e pendências

- ContatoChatInternoIT: 5/5, executados em Surefire e Failsafe com PostgreSQL real; inclui conversa direta/grupo, replay, concorrência e gestor sem participação.
- OpenApiIT: 6/6; novo path JWT, resposta e erros documentados.
- Frontend direcionado: 28/28; preservação de campos/chave em falha, identidade explícita e ausência de abertura por telefone.
- Typecheck sem erros; lint sem erros, quatro avisos preexistentes; build aprovado.
- Playwright CLI headed na aplicação local real: externo enviado com HTTP 201, dois números persistidos após reload, screenshots desktop 1440x1000 e celular 390x844. Interno enviado com HTTP 201, UUID do payload igual ao metadado salvo; clique abriu conversa pelo mesmo UUID com HTTP 200. Screenshots em `docs/assets/chat-interno-contatos/` e `output/playwright/`.
- A comparação visual levou a dois ajustes: rótulo legível do seletor em vez de código INTERNO/EXTERNO e cor de texto do botão interno usando token `text-foreground`, sem herdar texto branco da bolha.
- Uma repetição dos testes frontend sob carga concorrente atingiu timeout de 5 segundos em dois testes existentes. Nova execução com um worker passou 28/28 em 28,8 segundos, sem alterar timeout/assertions nem desativar teste. Não se afirma ter corrigido a sensibilidade geral da máquina à carga.
- Suíte completa Java 21 em andamento. Não considerar a paridade global concluída antes das evidências restantes.
- Bug encontrado: a listagem de conversas usa apenas o conteúdo textual da última mensagem. Mensagem de contato/mídia sem legenda pode exibir "Nenhuma mensagem ainda" mesmo existindo mensagem; requer distinguir tipo/metadados no read model, não inferência no frontend.
- Playwright headed com duas contas locais distintas (Ana/Bruno), contextos autenticados separados e STOMP CONNECTED observado: contato recebido pelo outro participante sem reload e exatamente uma bolha. Screenshot `chat-contato-outro-participante.png`.
- A prova com duas abas do mesmo remetente não recebeu a nova mensagem: os casos de uso existentes excluem o remetente da lista de destinatários de mensagem, diferentemente de reações. Esta lacuna não é apresentada como resolvida; será tratada na capacidade de reconciliação.
- Emulação offline não comprovou fechamento/reabertura consistente do WebSocket. Isso não prova defeito de reconexão: a queda do socket precisa ser observada para validar o cenário. Nenhum retry/reconector foi modificado nesta capacidade.
- Reply/forward e reconexão permanecem capacidades separadas; a prova de reconexão após queda real de rede ainda não concluiu. Nenhum deploy foi realizado.
