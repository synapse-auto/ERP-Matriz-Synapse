# Z-API — avanço independente: saída de mídia e capacidades

24/10/2026. **F4 parcial; não conclui F3/F4/F5 nem libera troca.**
Branch `codex/z-api-midia-saida`, baseada em #322 (`codex/z-api-fase-2-saida`).
Autorização atual permite avançar em mídia/testes/capacidades/passagem mesmo com G1
pendente e auditoria frontend vermelha. Não transforma o gate em sucesso.
Ordem de merge: #321 → #322 → PR desta branch. Não criar PR vazia de F3.

## Referência e fontes

ZIP `Z-API-API-Reference-Pacote-Markdown.zip`, SHA-256
`901de4ef7567151d9390aaaebf78a1dd68c4517796dfd1c228e18628cb1fcee8`.
Enumerados 23 Markdown; lidos README, segurança, webhooks, mensagens e sticker/segurança
diretamente do ZIP, sem executar arquivos/extrair assets. Recomendações de segredo na URL,
timeout/variáveis e implementação de stickers **não são autorização nem contrato nativo**;
não adotadas. O pacote inteiro não é republicado no repositório.

Base oficial: `https://api.z-api.io/instances/{instanceId}/token/{token}`.

| Tipo registrado | POST relativo | JSON | Fonte oficial atual |
|---|---|---|---|
| IMAGEM | /send-image | phone, image=data URI, caption opcional | [imagem](https://developer.z-api.io/message/send-message-image) |
| AUDIO | /send-audio | phone, audio=data URI, sem caption | [áudio](https://developer.z-api.io/message/send-message-audio) |
| VIDEO | /send-video | phone, video=data URI, caption opcional | [vídeo](https://developer.z-api.io/message/send-message-video) |
| DOCUMENTO | /send-document/{extension} | phone, document=data URI, fileName, caption opcional | [documento](https://developer.z-api.io/message/send-message-document) |

Token da instância no path + Client-Token autenticam CRM → API, **não callbacks**.
Sem multipart, mediaId Uzapi, URL do bucket ou chave de idempotência fictícia do fornecedor.
O Checker nesta revisão extraiu um endpoint explícito de documento, sem campos estruturados;
extrações narrativas anteriores retornaram zero. Nenhum resultado certifica segurança ou
cobertura completa: conferência manual das fontes permanece necessária. G1 continua aberto.

## Implementação

- ACL `ZApiMidiaSaida`, desligada junto do adapter, reutiliza storage privado, porta
  `abrirLeitura`, limites configurados e tetos existentes do CRM. Sem mudança de upload,
  tabelas, download ou autorização. Upload normal continua verificando bytes reais.
- Worker recebe objeto já validado e MIME/tamanho persistidos; não usa extensão para
  contornar detecção. Não reclassifica DOCUMENTO pelo MIME image/png/jpeg. Rota/extensão
  derivam da allowlist MIME; filename sanitizado preserva nome/extensão compatíveis.
- Preflight **antes do POST** copia o stream para spool temporário de nome aleatório,
  limitado e com tamanho real conferido. Não usa baixar(byte[]), URL assinada ou Base64
  integral no heap. Buffer de I/O 8 KiB, Base64 incremental e campos escapados com Jackson.
- Fecha/remove spool no sucesso e falha. Falha de limpeza gera aviso sanitizado e tentativa
  na saída da JVM, sem inverter aceite nem provocar retry. Queda abrupta pode deixar resíduo:
  área temporária deve ser privada/efêmera, nunca montada publicamente. Não é dado no banco.
- Limite efetivo: menor entre configuração e teto já usado pelo CRM (imagem 5 MiB,
  áudio/vídeo 16 MiB, documento 100 MiB). Não aumenta para 100 MB só porque Z-API documenta
  vídeo maior. Mesmo worker/bulkhead; sem consulta pesada/polling/custo de rede no upload.
- Não converte codec nem cria preview. Áudio com legenda é recusado antes da rede, para não
  perder texto silenciosamente: caption não consta do contrato de áudio. MIME/tamanho
  inválidos são permanentes; falha de storage antes do POST é temporária com backoff existente.
- Depois de iniciar POST, timeout/erro/resposta incerta seguem Ambiguo/E209, sem retry cego.
  Logs do adapter não imprimem bytes/corpo/URL/segredos. Restrições wire logging/APM da F2
  continuam valendo. Não há chamada de negócio à conta real nesta entrega.

## HTTP 200, PENDENTE e não redisparo

2xx com IDs válidos grava Enfileirado e **publicado_em** na outbox; mensagem continua
PENDENTE, pois processamento/entrega não estão comprovados. Reserva exige publicado_em
IS NULL, não status PENDENTE. O IT repete a API antes/depois do aceite, força lease vencido
e chama o job mais duas vezes: transporte exige um único POST. Isso não é exactly-once
diante de perda de commit após efeito externo; despacho incerto permanece para conferência.

## Capacidades reais nesta branch

| Recurso | Estado / limite |
|---|---|
| Texto sem citação e saúde | Saída experimental F2, HTTP simulado |
| Imagem/vídeo/documento com legenda | Saída implementada; aceite em fila não confirma entrega |
| Áudio anexo/composer | Preserva artefato do upload, inclusive OGG/Opus; sem caption; codec/reprodução reais pendentes |
| PNG/JPG como documento | DOCUMENTO preservado, inclusive encaminhamento CRM |
| Encaminhamento CRM | Usa bytes/referência interna; não usa forward nativo Z-API |
| Citação ao fornecedor | Recusada antes da rede até G3; não usa IDs históricos Meta/Uzapi |
| Templates | gerenciaTemplates=false, lista vazia/operações recusadas pelo contrato existente |
| Contato/localização | Sem nova variante de envio; indisponíveis nesta entrega |
| Reação/edição/exclusão externa | Não implementadas; ação local CRM não comprova ação no WhatsApp |
| Foto do contato | Gateway mantém Optional.empty, sem consulta Z-API |
| Entrada/status/eco/mídia recebida | Não implementados, bloqueados G1; sem verificador permissivo |
| Stickers/grupos/newsletter/Status | Fora desta etapa; documento anexo não ampliou escopo |

GET /api/v1/config/canal hoje só expõe exigeTemplateForaDaJanela/gerenciaTemplates; não existe
catálogo abrangente por provedor. Não foram criados controles fantasmas. Selecionar Z-API
continua falhando fechado por falta de tradutor de entrada. Antes da troca, capacidades/
controles devem ser conferidos na aplicação real após F3, não presumidos pela saída parcial.

## Evidências e limites

Testes novos precederam implementação (compilação RED por ausência da ACL). Dirigidos HTTP
cobrem categorias, nome/legenda/bytes/Base64, limites, metadata inválida, storage, timeout e
breaker antes de I/O. `ZApiSaidaIT` usa PostgreSQL/Redis/MinIO reais, API/job reais e HTTP
externo simulado: quatro uploads, documentos PNG/JPG encaminhados, download nome/MIME/
attachment/nosniff/bytes e 404 para outro atendente. Fake de storage não primário só nesse
contexto. Primeira iteração corrigiu fixtures: AAC sintético irreconhecível e destino igual
à origem (proibido); substituídos por M4A reconhecível e outra conversa, sem alterar regras.

Resultados dirigidos, clean verify Java 21 e CI do último SHA no relatório/PR de fechamento.
Sem homologação de codec/duração/reprodução/carga/conta/workflow real. Frontend inalterado;
auditoria atual reprova também a base. [Checklist](./bloqueios-troca.md).
Sem migration, endpoint ou variável nova. **Dokploy: nenhuma ação; Z-API OFF.**
