# Chat interno — download, visualizador e auditoria de paridade

## Base auditada e estratégia

Base `origin/main` em `e259017`, em 26/09/2026. Recursos em branches não foram tratados como publicados. Os PRs #214, #215, #217, #218 e #219 estavam mergeados; #222, de reconciliação de mídia externa, ainda aberto. Esta entrega é o primeiro PR por capacidade, priorizando download/visualizador. Não implica deploy nem conclusão das etapas seguintes.

| Capacidade | Atendimentos na main | Chat interno antes | Lacuna comprovada / encaminhamento |
|---|---|---|---|
| Texto e links | Links HTTP(S) seguros | Texto sem linkificação | Reutilizar `TextoComLinks`, inclusive na legenda |
| Imagem e legenda | Upload e visualizador | Upload persistido; imagem sem abertura | Abrir o visualizador existente com origem interna autorizada |
| Áudio | Player e mídia persistida | Player; sem download | Download binário autenticado independente da prévia |
| Vídeo | Upload MP4/3GP e reprodução | Render parcial de registros; upload recusado | Download/reprodução nesta etapa; aceitação e validação de upload em PR próprio |
| Documento | Mídia e visualizador | Link abria URL em outra aba | Separar abrir no visualizador de baixar arquivo real |
| Contato compartilhado | Card estruturado externo | Sem card/fluxo interno equivalente | PR próprio; identidade interna explícita e autorizada, sem inferência por telefone/nome |
| Prévia com imagem de link | Sem contrato confiável de prévia | Sem destino de prévia persistido | Não inventar imagem/destino nem fazer fetch Open Graph |
| Reação | Persistida e tempo real | Endpoints e cache de evento existentes | Preservados; validação entre duas abas ainda necessária |
| Responder/citar | Fluxo persistido | Fluxo persistido e busca pontual da origem | Preservado; não recriado |
| Encaminhar | Fluxo explícito externo | Valida origem/destino e reutiliza objeto | Preservado; verificar retry idempotente em etapa própria |
| Editar/excluir | Ações autorizadas | Ações próprias existentes | Preservadas; não acrescentar privilégios de gestão |

## Contrato de download

`GET /api/v1/chat-interno/conversas/{id}/midias/{mensagemId}/arquivo`

- JWT/Bearer obrigatório. Participação é conferida antes do acesso ao storage, usando o caso de uso existente; gestor e administrador não possuem bypass.
- IDs devem pertencer à mesma conversa; não há pesquisa global por mensagem.
- `200`: bytes reais do objeto privado, `Content-Type` persistido, `Content-Length`, `Content-Disposition: attachment` com nome UTF-8 sanitizado e extensão correspondente ao MIME, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`.
- `401`: autenticação ausente/inválida. `403`: não participante. `404`: mensagem inexistente/removida ou sem mídia nessa conversa. `503`: storage indisponível, objeto ausente no storage ou circuit breaker/bulkhead sem capacidade. Erro recuperável RFC 7807 sem referência do objeto, URL assinada ou credencial.
- A transação de autorização encerra antes da leitura de storage. Download usa porta compartilhada, adaptador dedicado em equipe, bulkhead e circuit breaker; não importa infraestrutura de atendimento.
- Operação somente de leitura: nenhum novo upload, mensagem, evento, histórico ou retry de envio.

O navegador só cria o download após receber o blob inteiro. Nome vem do cabeçalho confirmado; fallback preserva compatibilidade com respostas antigas. URLs blob são apenas temporárias para o download e nunca fonte persistida de uma mensagem. O CORS preserva as origens existentes, admite o `Idempotency-Key` já previsto no upload e expõe somente `Content-Disposition` adicionalmente.

## Interface e compatibilidade

A bolha e o painel de mídias em conversas diretas e grupos separam abrir/baixar. Imagem abre o overlay acessível sem navegar para fora; a URL é autorizada novamente ao abrir. Áudio/vídeo mantêm seus players e ganham download. Legendas mantêm quebras de linha e links seguros. Erro de prévia permite renovação explícita; erro de download mantém a mensagem e permite tentar de novo, sem polling nem anúncio falso de sucesso. Metadados atuais `nome_original`/`tamanho_bytes` e antigos `nome`/`tamanho` são aceitos.

Prévia clicável com imagem de link não está implementada: não existe metadado confiável desse destino no contrato interno auditado. Imagem anexa continua sendo mídia, não link externo.

## Operação e limites

Sem migration, alterações Meta/UZAPI ou dados de produção. Nenhuma ação obrigatória no Dokploy. Ajustes opcionais de capacidade constam de `.env.example` e README: `CHAT_INTERNO_DOWNLOAD_CONCORRENCIA`, `CHAT_INTERNO_DOWNLOAD_CB_JANELA`, `CHAT_INTERNO_DOWNLOAD_CB_MINIMO`, `CHAT_INTERNO_DOWNLOAD_CB_LIMIAR`, `CHAT_INTERNO_DOWNLOAD_CB_ESPERA`. Defaults usam duas leituras simultâneas e espera de bulkhead zero. Os valores precisam ser repassados à aplicação caso o operador escolha overrides; o stack não foi modificado.

Validação operacional, sem mensagens a clientes: abrir conversa interna autorizada, enviar imagem/áudio/documento de teste; abrir imagem com mouse/Enter/touch, fechar com Escape; baixar e comparar bytes/MIME/nome; recarregar e repetir. Usar grupo existente, e confirmar 403 em usuário fora da conversa. Upload de vídeo depende da próxima capacidade e não deve ser anunciado como disponível nesta etapa.

## Evidências e pendências

Testes de integração usam PostgreSQL real e storage de teste em memória; isso prova autorização/contrato/bytes, mas não substitui validação MinIO real. `DownloadMidiaChatIT` passou 13/13, incluindo negativo gestor/administrador e CORS de outra origem; OpenApiIT passou 6/6. `clean verify` completo Java 21 terminou com sucesso (750 integrações, sem falhas). Frontend dirigido passou 101/101; após a correção de M4A no seletor, 59/59 adicionais, typecheck, lint sem erros (4 avisos preexistentes) e build. A tentativa da suíte frontend inteira não concluiu localmente e foi interrompida; não é apresentada como verde.

A validação Playwright CLI headed usou aplicação real local e MinIO, base isolada `synapse_chat_validacao_fresh`, com contas de desenvolvimento. Imagem, áudio MP4 e PDF foram enviados pelo composer, persistidos, recarregados e baixados; SHA-256 do download coincidiu com os originais. Player de áudio reproduziu o arquivo. Imagem abriu com Enter, fechou por Escape restaurando foco, e abriu/fechou por toque em contexto `hasTouch`. Desktop 1440×1000 e celular 390×844. Nenhum endpoint/provedor foi mockado nessa validação. Vídeo teve contrato de download testado, mas upload/player real de vídeo fica para a próxima capacidade.

O navegador identificou M4A como `audio/x-m4a`, recusado pelo filtro antigo; o seletor interno agora aceita extensão `.m4a`, sem mudar Atendimentos nem a validação real do backend. A base local antiga falhou na validação de checksum; não houve repair, alteração de histórico ou migration. A base vazia isolada foi preparada explicitamente com as migrations e seed de desenvolvimento atuais.

Screenshots sanitizados da execução:

- [Bolha desktop](assets/chat-interno-midia/chat-midia-desktop.png), [imagem ampliada desktop](assets/chat-interno-midia/chat-imagem-ampliada-desktop.png).
- [Bolha celular](assets/chat-interno-midia/chat-midia-mobile.png), [imagem ampliada celular](assets/chat-interno-midia/chat-imagem-ampliada-mobile.png), [contexto touch](assets/chat-interno-midia/chat-imagem-touch.png).
- [Áudio/documento desktop](assets/chat-interno-midia/chat-audio-documento-desktop.png), [áudio/documento celular](assets/chat-interno-midia/chat-audio-documento-mobile.png).

PR #223, primeira CI verde: run `36222394426` (pull_request) no commit `6fe193f`. A correção posterior do seletor M4A e documentação exige conferir a run do HEAD atual; não extrapolar o verde para outro SHA. Não houve deploy nem validação em instância de cliente. Skills clean-code/architecture-patterns/api-design-principles/supabaseboaspraticas não estavam disponíveis; Playwright CLI foi usado.

Pendências deliberadas desta primeira capacidade: upload de vídeo, card de contato interno, idempotência de retry de encaminhamento e E2E de reações entre abas/reconexão. Não marcar a task inteira concluída a partir deste PR.

## Continuação por capacidades

PR #223: mídia/download, HEAD `d4f7193`, CI pull_request `36222861636` e push `36222859384` aprovadas. PR #224: upload e reprodução de vídeo, SHA `73beee3`, CI `36223500990` e `36223498307` aprovadas; `clean verify` local completo passou 757 integrações. Evidências e limites em [46 — vídeo](46-chat-interno-envio-video.md).

PR #225 adiciona compartilhamento e card de contato externo/interno, sem inferência por telefone/nome; ver [47 — contato](47-chat-interno-contato-compartilhado.md). A task global ainda não está concluída: permanecem retry idempotente de encaminhamento, reconciliação das mensagens nas abas do remetente e prova conclusiva de reconexão. A falta de metadados reais de prévia de link continua documentada, sem imagem inventada.

Continuação de 26/09: [48 — reconciliação e encaminhamento](48-chat-interno-reconciliacao-encaminhamento.md)
implementa os três gaps acima. PostgreSQL/STOMP reais cobrem replay concorrente e autorização;
Playwright headed comprova resposta perdida/retry sem duplicação, reações em duas abas,
resposta persistida e recuperação após novo CONNECTED. Backend completo e frontend completo
aprovados localmente. PR #225: CI `36246914257` e `36246912172` aprovadas no HEAD `a7dcb19`.
Os PRs anteriores continuam abertos: validação local não significa merge ou deploy.

Matriz final da implementação empilhada:

| Capacidade | Resultado interno | Evidência / limite |
|---|---|---|
| Texto, imagem, áudio, documento | Fluxos existentes preservados; links seguros, overlay e download adicionados | #223; MinIO real, reload e SHA-256 dos arquivos |
| Vídeo | Upload MP4/3GP validado; player e download | #224; MP4 real no navegador; 3GP somente integração |
| Contato | Externo com vários números; interno por UUID explícito autorizado | #225; direta/grupo, persistência e duas contas reais locais |
| Prévia de link | URL textual clicável; não existe imagem/destino de prévia no contrato | Sem OG automático nem imagem inventada |
| Reações | Existentes; substituição/remoção e persistência confirmadas | Testes e duas abas reais; não reimplementadas |
| Responder/citar | Existente; destinatário autor corrigido | HTTP/STOMP, grupo e resposta ao vídeo após reload |
| Encaminhar | Mesmo objeto privado; chave opcional compatível, CRM sempre envia | Concorrência, replay, retry após resposta perdida e bytes iguais |
| Editar/excluir | Regras existentes preservadas; edição alcança abas do autor | Integração com negativo de autoria e tombstone |
| Reconexão | Histórico recuperado em cada ciclo conectado | Socket real fechado, mensagem durante interrupção, sem F5 |

Limites remanescentes: ausência de metadados de prévia de link, preview vazio da lista para
mídia/contato sem legenda e textarea comprimido em 390px. Não houve validação/deploy em cliente,
transplante de regras WhatsApp nem envio externo automático.
