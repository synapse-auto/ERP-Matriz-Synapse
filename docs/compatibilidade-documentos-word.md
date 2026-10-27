# Documentos Microsoft Word — Base PAI

Auditoria de 27/10/2026, base `bc27720` (inclui a PR #332). Sem regra por cliente,
migration, alteração de permissão, aumento de limite ou ativação de módulo.

## O que já existia e o que foi corrigido

| Caminho | Existente | Complemento desta entrega |
| --- | --- | --- |
| Seletor externo/interno | `.doc`, `.docx` e MIME oficiais na lista | Testes de maiúsculas/MIME genérico; extensão correta no clipboard |
| Classificação | Allowlist compartilhada já classificava ambos como DOCUMENTO | DOC não pode ser qualquer OLE; DOCX não pode ser qualquer ZIP/declarar o tipo num comentário |
| Upload externo | Tika, storage privado, outbox/idempotência | Coerência extensão/bytes e nome Word com espaços/acentos preservado |
| Chat interno direto/grupo | Upload, participação, fanout, histórico e download | Mesma detecção segura; nome e bytes preservados |
| Recebimento Meta/Uzapi | Worker durável baixava/persistia pelo MIME informado pelo provedor | Reidentificação Word pelos bytes no worker, inclusive MIME genérico/divergente |
| Recebimento Z-API | Download em spool e detecção real | Mesmo detector estrutural e coerência do nome, sem novo download |
| Encaminhar dentro do chat e interno → cliente | Reutiliza objeto privado e valida os dois acessos/capacidade do destino | Nome Word preservado; sem cópia/conversão ou novo despacho no retry |
| Cliente → Chat Interno | Não foi encontrado um caminho implementado equivalente | Não foi criado um novo fluxo de encaminhamento nesta tarefa |
| Apresentação/download | Card DOCUMENTO, nome/tamanho, ícone, download autenticado | Sem editor, conversão para PDF ou pré-visualização Word |

## Identificação e limites da inspeção

MIME canônico: `application/msword` e
`application/vnd.openxmlformats-officedocument.wordprocessingml.document`.
O nome e o MIME do navegador não autorizam o arquivo. Um texto/PDF renomeado
para `.doc`/`.docx`, ou DOC renomeado para DOCX, é recusado no upload.

- DOC: POIFS do Apache POI 6.0.0 lê o recipiente OLE, stream `WordDocument`,
  assinatura FIB `0xA5EC` e tabela indicada pelo FIB. Confere a cadeia dos streams;
  OLE genérico/Excel, stream forjado sem FIB e truncamento não viram Word.
- DOCX: diretório ZIP conferido antes de alocar o índice; orçamento de metadados
  de 1 MiB, igual ao fallback OOXML existente. Confere declaração OPC real,
  relacionamento interno officeDocument e parte principal WordprocessingML.
  Lê somente os XML de metadados e até 64 KiB do início da parte principal;
  imagens/objetos não são descomprimidos para procurar o tipo. XML pequeno é
  lido até o fim; XML principal grande tem somente inspeção estrutural inicial.
- DTD, entidades externas, relação externa, nomes duplicados e pacote DOCM/VBA
  disfarçado não são aceitos como DOCX. ZIP64 ou diretório fora do orçamento não
  são promovidos a Word. DOC criptografado/template não é promovido a DOC.
- Não é antivírus nem validação semântica integral do Word. Não executa macros,
  extrai texto, abre links/objetos, renderiza ou decripta arquivos. Não adiciona
  `.docm`; XLSM permanece exclusivamente no Chat Interno, como antes.
- Spool temporário é removido ao terminar, sem segundo byte array do documento
  inteiro. A biblioteca POI fica somente na infraestrutura; domínio usa a porta
  existente. Não há parser HWPF/XWPF ou extração de conteúdo.

Limites de upload/documento continuam sendo os já configurados na instância e
nos adaptadores. Não foi ampliado multipart, categoria, storage nem limite global.
Recebimento continua no worker; não faz inspeção pesada no controller do webhook,
download ao abrir menu, consulta adicional por mensagem de texto nem polling.

## Contratos dos fornecedores

Fontes oficiais consultadas em 27/10/2026:

- Meta: [referência de mídia](https://developers.facebook.com/docs/whatsapp/cloud-api/reference/media/)
  e [SDK oficial — documentos](https://whatsapp.github.io/WhatsApp-Nodejs-SDK/api-reference/messages/document/).
  A página atual de referência retornou HTTP 429 nesta sessão; o SDK é arquivado,
  portanto não é apresentado como confirmação operacional atual. Mantido o contrato
  existente de upload multipart `/PHONE_NUMBER_ID/media`, envio `document.id`/
  `filename` em `/messages`, callback document com media ID e resolução/download
  autenticados. Nenhum endpoint alternativo foi inventado.
- Uzapi/Autotic: [Swagger oficial](https://api.uzapi.com.br/docs/swagger.json), HTTP 200,
  conferido: `/{version}/{phone_number_id}/media`, `.../messages`,
  `CaptionedFileMessage.id/caption/filename` e `/{version}/{mediaId}`. O Swagger
  não fornece uma garantia específica de formatos Word nem um novo teto por extensão;
  não se inventa essa garantia a partir da ausência de enum. Ver também docs/38.
- Z-API: [enviar documento](https://developer.z-api.io/message/send-message-document)
  (`/send-document/{extension}`, `document`, `fileName`, `caption`) e
  [ReceivedCallback](https://developer.z-api.io/webhooks/on-message-received-examples)
  (`document.documentUrl`, `fileName`). DOC/DOCX já estavam no mapa do adaptador.
  Mantido o transporte Base64 por streaming no worker e o download por URL documentada,
  nunca pelo resolvedor mediaId da Uzapi. A página não publica um teto específico Word;
  limites do CRM não são anunciados como garantia do fornecedor.

Testes HTTP usam as formas documentadas, sem chamadas reais. Aceite HTTP não prova
entrega ao WhatsApp. A homologação externa com contato próprio continua distinta.

## Verificação e operação

Fixtures pequenas: DOC vazio público do Apache POI, reconstruído sem streams de
autor/propriedades/VBA, e DOCX mínimo sintético. Origem em
`backend/crm-atendimento/src/test/resources/word/README.md`; não há arquivo de cliente.

Cobertura: detector real por bytes/spool e negativos; upload/download HTTP com
PostgreSQL/MinIO reais, direto/grupo, outro participante, recarga, idempotência,
encaminhamento interno e ao cliente; callbacks Meta assinados, Uzapi autenticado
e Z-API protegida; contratos de saída com bytes Word reais. Transportes externos
simulados. Resultados finais e CI do último SHA constam da PR, não são inferidos
de uma execução parcial.

Homologação visual/externa: numa instância de teste autorizada, usar `vazio.doc` e
`sintetico.docx`, enviar/receber por contato próprio, conferir nome/tamanho e baixar
comparando SHA-256; recarregar, abrir como outro participante, repetir no grupo e
encaminhar pelos caminhos disponíveis. Usuário fora do lead/grupo não baixa.
Conferir também desktop/tela menor. Não usar pacientes nem reprocessar produção.

**Dokploy: nenhuma variável ou configuração nova.** Apenas a release revisada pelo
procedimento normal, quando autorizada pelo operador. Esta tarefa não faz deploy.
