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

Testes de integração usam PostgreSQL real e storage de teste em memória; isso prova autorização/contrato/bytes, mas não substitui validação MinIO real. A validação visual usa aplicação e storage locais separados, nunca produção. Números finais, screenshots e CI serão registrados no relatório do PR.

Pendências deliberadas desta primeira capacidade: upload de vídeo, card de contato interno, idempotência de retry de encaminhamento e E2E de reações entre abas/reconexão. Não marcar a task inteira concluída a partir deste PR.
