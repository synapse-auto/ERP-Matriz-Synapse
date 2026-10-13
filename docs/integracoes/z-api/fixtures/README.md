# Fixtures sintéticas Z-API — contrato, não homologação

Todos os nomes/telefones/IDs são fictícios; hosts `.invalid` não são destinos de download.
Os arquivos não contêm token, URL assinada ou payload de cliente. Não executar chamadas com
essas fixtures. Arrays representam **sequências de POSTs independentes**, não lote aceito pela API.

| Arquivo | Fonte dos campos | Finalidade futura |
|---|---|---|
| texto-requisicao.json | [send-text](https://developer.z-api.io/message/send-text) | phone/message com newline e Unicode |
| envio-resposta.json | [message/introduction](https://developer.z-api.io/message/introduction) | zaapId diferente de messageId; id igual a messageId |
| envio-negativos.json | [resposta de texto](https://developer.z-api.io/message/send-text) | 2xx vazio ou IDs contraditórios não é aceite comprovado |
| midia-requisicoes.json | [imagem](https://developer.z-api.io/message/send-message-image), [áudio](https://developer.z-api.io/message/send-message-audio), [vídeo](https://developer.z-api.io/message/send-message-video), [documento](https://developer.z-api.io/message/send-message-document) | corpos independentes dos quatro tipos, sem resolvedor/upload inventado |
| midia-recebida-sequencia.json | [exemplos entrada](https://developer.z-api.io/webhooks/on-message-received-examples) | imagem/áudio/vídeo chegam por URL; não expor no frontend |
| processamento-callback.json | [on-message-send](https://developer.z-api.io/webhooks/on-message-send) | ligar ambos os IDs sem confundir processamento com entrega |
| status-sequencia.json | [status](https://developer.z-api.io/webhooks/on-whatsapp-message-status-changes) | READ antes de RECEIVED, repetição; não regredir nem duplicar |
| texto-callback.json | [exemplos entrada](https://developer.z-api.io/webhooks/on-message-received-examples) | texto recebido, instância e identidade |
| documentos-sequencia.json | [exemplos entrada](https://developer.z-api.io/webhooks/on-message-received-examples) | PNG/JPEG como documento; variar MIME/nome sem reclassificar pelo sufixo |
| negativos-sequencia.json | [entrada](https://developer.z-api.io/webhooks/on-message-received-examples), [LID](https://developer.z-api.io/tips/lid) | eco, grupo, outra instância com mesmo ID, LID-only |

PNG/JPEG dentro de document são **variantes sintéticas candidatas**, não comprovação de que
um número real retornou esse formato. Confirmar no simulador/storage da Fase 4 e posteriormente
na homologação autorizada. Não inferir MIME dos bytes a partir do JSON.

Negativos a implementar após decisão: ausência/erro do segredo não grava; instanceId falso não
autentica; colisão de ID entre instâncias não atualiza mensagem alheia; eco não cria duplicata;
grupos não geram conversa privada; LID não vira telefone; status repetido/atrasado não regride;
2xx sem ID/IDs contraditórios e timeout pós-POST não causam retry cego; URL de mídia privada,
redirect inseguro, excesso de bytes e MIME falso são recusados.

Nesta fase são verificados JSON e relações sintéticas, não handlers/ACL ainda inexistentes.
