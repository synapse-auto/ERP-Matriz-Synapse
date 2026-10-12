# Z-API — contrato verificado e desenho (Fase 1)

Verificado em 12/10/2026; código auditado na base `origin/main` e6bc480.
**Não implementado, não ativado e não homologado.** Este documento planeja a integração;
não registra endpoints do CRM como se já existissem. Fontes são exclusivamente oficiais,
descobertas pelo [índice principal](https://developer.z-api.io/llms.txt) e
[índice API](https://developer.z-api.io/_llms/pt-br/api-reference.txt).

As páginas `.md` omitem algumas rotas/métodos exibidos pelo componente de referência;
os métodos e caminhos abaixo foram conferidos também nas páginas HTML oficiais.
Exemplos de fornecedor não são evidência de uma instância real.

## 1. Matriz de contrato

Base documentada `https://api.z-api.io/instances/{instanceId}/token/{token}`.
Toda rota da matriz é relativa a essa base. Headers de saída: `Client-Token` e
`Content-Type: application/json`; são credenciais de **chamadas à API**, não de callbacks.
Fontes: [base/autenticação](https://developer.z-api.io/api-reference/introduction),
[ID/token](https://developer.z-api.io/security/introduction),
[Client-Token](https://developer.z-api.io/security/client-token).

| Capacidade CRM | Contrato oficial | Fonte | Estado |
|---|---|---|---|
| Texto | POST `/send-text`; `phone`, `message`; opcionais delayMessage/delayTyping/editMessageId | [texto](https://developer.z-api.io/message/send-text) | Comprovado documentalmente; não testado em conta |
| Imagem | POST `/send-image`; `phone`, `image` link/Base64; caption/messageId/viewOnce opcionais | [imagem](https://developer.z-api.io/message/send-message-image) | Comprovado |
| Áudio | POST `/send-audio`; `phone`, `audio` link/Base64; async/viewOnce/waveform opcionais; sem caption documentado | [áudio](https://developer.z-api.io/message/send-message-audio) | Comprovado |
| Vídeo | POST `/send-video`; `phone`, `video` link/Base64; caption/messageId/async opcionais | [vídeo](https://developer.z-api.io/message/send-message-video) | Comprovado; H.264 recomendado, limite documentado 100 MB |
| Documento | POST `/send-document/{extension}`; `phone`, `document` link/Base64; fileName/caption/messageId opcionais | [documento](https://developer.z-api.io/message/send-message-document) | Comprovado; não inventar upload multipart/mediaId |
| Resposta de envio | `zaapId` é ID Z-API; `messageId` é ID WhatsApp; `id` é alias de messageId | [mensagens](https://developer.z-api.io/message/introduction) | Comprovado; guardar separadamente, não pressupor igualdade zaapId/messageId |
| Conexão | GET `/status`; connected, smartphoneConnected, error | [status](https://developer.z-api.io/instance/status) | Comprovado; 200 não significa connected=true |
| Processamento do envio | DeliveryCallback: instanceId, zaapId, messageId, phone, momment; error em falha | [envio](https://developer.z-api.io/webhooks/on-message-send), [exemplos](https://developer.z-api.io/webhooks/on-message-send-examples) | Comprovado; não é entrega ao destinatário |
| Entrega/leitura | MessageStatusCallback: instanceId, status, ids[], momment, phoneDevice, phone, type, isGroup | [status callback](https://developer.z-api.io/webhooks/on-whatsapp-message-status-changes), [fluxo](https://developer.z-api.io/quickstart/introduction) | RECEIVED = destinatário recebeu; READ = destinatário leu |
| Entrada/eco | ReceivedCallback; instanceId/messageId/phone/fromMe/momment; text.message, flags isGroup/isNewsletter/isStatusReply/isEdit/waitingMessage | [entrada](https://developer.z-api.io/webhooks/on-message-received), [exemplos](https://developer.z-api.io/webhooks/on-message-received-examples) | Comprovado; eco não cria outra mensagem do CRM |
| Seleção de callback | URL HTTPS; ReceivedCallback com fromMe pode usar outra URL prioritária | [enviadas por mim](https://developer.z-api.io/webhooks/on-message-received-and-delivery) | Comprovado; não registrar URL nesta tarefa |
| Mídia recebida | image.imageUrl, audio.audioUrl, video.videoUrl, document.documentUrl; mimeType; documento fileName/title | [exemplos entrada](https://developer.z-api.io/webhooks/on-message-received-examples) | Comprovado; URL, não endpoint resolvedor de mediaId |
| Retenção de mídia | Arquivos recebidos disponíveis por 30 dias | [expiração](https://developer.z-api.io/tips/file-expiration) | Comprovado; não garante download infalível durante todo prazo |
| Autenticação de callback | POST HTTPS com JSON; assinatura, chave de entrada ou headers customizados não descritos nas páginas consultadas | [webhooks](https://developer.z-api.io/webhooks/introduction), [configuração](https://developer.z-api.io/webhooks/update-every-webhooks) | **Lacuna essencial G1** |
| Restrição por IP | Recurso restringe quem chama a API da Z-API, não prova os IPs de saída dos callbacks | [IP](https://developer.z-api.io/security/ip-block) | Não usar como allowlist de origem do CRM |
| Identidade privada | phone pode conter @lid; não existe conversão LID → telefone | [LID](https://developer.z-api.io/tips/lid) | **Incompatibilidade G2** com entrada do CRM baseada em telefone |
| Idempotência/reentrega | Não há chave de envio, exactly-once, ID único de evento nem SLA/backoff de callback nas fontes verificadas | [texto](https://developer.z-api.io/message/send-text), [webhooks](https://developer.z-api.io/webhooks/introduction) | Lacuna; não afirmar que fornecedor não tem o recurso, somente que não foi comprovado |
| Limites/erros | Introdução não fixa limite de quantidade, mas impõe uso compatível com WhatsApp; API descreve 400/405/415 | [introdução](https://developer.z-api.io/quickstart/introduction), [referência](https://developer.z-api.io/api-reference/introduction) | Sem quota/SLA global comprovados; aplicar limites existentes do CRM, não criar rate arbitrário |

## 2. Estados e IDs: não copiar Meta/Uzapi

HTTP 200 com IDs significa aceite na fila, não entrega. DeliveryCallback sem error comprova
processamento; com error comprova falha de envio, sem repassar o texto cru do erro ao usuário/log.
Fontes: fluxo e introdução de webhooks acima. `SENT` não se transforma em ENTREGUE.
`RECEIVED` e `READ` sustentam ENTREGUE e LIDO conforme o fluxo oficial. `READ_BY_ME` não prova
leitura pelo destinatário. `PLAYED` não deve ser convertido automaticamente para LIDO.
Estados desconhecidos não atualizam para um estado mais forte. Exemplos `ids[]` não especificam
formalmente qual namespace de ID usam: testar correlação com IDs distintos e pedir confirmação
antes de depender de zaapId/messageId como se fossem intercambiáveis.

`momment` é o nome documentado, não corrigir para `moment`. Callback de processamento documenta
milissegundos; exemplos de entrada/status também usam timestamps de 13 dígitos. Falta garantia
formal de unidade para todas as variantes: validação estrita e casos inválidos, sem heurística
que transforma data inválida no horário atual.

## 3. Arquitetura existente e lacunas locais

- `CanalGateway`/`TradutorDeCanal` são portas puras. `SeletorDeCanalGateway` escolhe entrada e
  saída pela mesma chave `synapse.canal.whatsapp.provedor`; não existem implementações Z-API.
- `WebhookCanalController` recebe `/webhook/canal`, verifica autenticação **antes** de escrita,
  valida destino pela credencial ativa, grava entrada e repasse duráveis. Reutilizar essa borda;
  não adicionar outro endpoint público para escapar da autenticação.
- V16 usa `webhook_entrada.id_externo` como PK; V46 usa `mensagem_id_externo.wamid` como PK,
  com UNIQUE por mensagem interna. A porta `MensagemIdExternoRepositorio` busca apenas String.
  `AplicarStatusDeEntregaDoCanalUseCase` ignora IDs desconhecidos; isso perde status que chegue
  antes da resposta de envio. **Não há prova de colisão real**, mas o modelo não satisfaz a
  correlação explícita canal/instância exigida por este planejamento (**G3**).
- `ResultadoDeEnvio` só tem Aceito/Recusado. `PublicadorDaOutboxTransacoes` grava ENVIADO ao
  aceitar, e retenta recusas temporárias. Não representa envio de resultado desconhecido (**G4**).
  Marcar timeout pós-POST como temporário causaria retry com risco de duplicação.
- Repasse para automação preserva o envelope do provedor; n8n não passa a compreender um JSON
  Z-API só porque o CRM o aceitou. Contrato a alinhar com Dylan, sem tradução Meta fictícia.

G1 é contrato essencial ausente; G2/G3/G4 são fronteiras arquiteturais a decidir antes do código.
Não editar V16/V46 aplicadas. Não criar migration na Fase 1.

## 4. Desenho proposto — ainda não implementado

1. Chave `z-api`; habilitação própria com default false, independente da seleção do provedor.
   Seleção explicitamente Z-API sem habilitação falha fechada, nunca cai em Meta/fake.
   Default existente continua Meta; ausência de credenciais de adaptador desligado não afeta boot.
2. Configuração opcional planejada: instanceId, referências de token da instância/Client-Token,
   timeout e habilitação. Banco guarda referências, valores ficam no resolvedor backend.
   Não reutilizar Client-Token como segredo de callback. Nomes de env serão definidos na fase de
   código; esta fase não acrescenta nenhuma variável ao stack.
3. Chave interna de correlação planejada `(canalId, instanceId, namespace, idExterno)`;
   namespace distingue zaapId/messageId; ID cru separado para chamar o fornecedor.
   Sem concatenação que altere messageId enviado em citação. Validar instanceId da credencial
   antes de persistir; nunca usar telefone/nome para correlacionar callbacks. Entrada/status/eco
   precisam deduplicação durável e retenção limitada para status antecipado, com índices avaliados.
4. Aceite exige IDs válidos e `id == messageId` quando ambos vierem. Preservar zaapId em paralelo.
   Retorno perdido, 2xx incompleto/contraditório ou timeout pós-POST ficam **ambíguos**, sem retry
   automático. Erro antes de qualquer envio, como breaker aberto, pode seguir recuperação atual.
   Acrescentar estado tipado de ambiguidade, persistência/auditoria e reconciliação, sem alterar
   política Meta/Uzapi silenciosamente. Isso depende de aprovação de G4, não disfarçar como FALHOU.
5. Mídia: preferir Base64 só no worker, com limites existentes de bytes e concorrência e perfil
   MIME verificado; nada de Base64 persistido ou bucket público. Custo de expansão ~4/3 exige
   orçamento de memória. URL assinada é alternativa somente após validar alcance/expiração.
   Recebimento baixa URL no executor durável existente, valida esquema/host/DNS/redirecionamentos,
   recusa rede privada/loopback/metadata, limita bytes/tempo, valida MIME real e grava no MinIO.
   Sem Authorization/Client-Token para host de mídia; sem URL temporária no navegador/log.
   Domínios de mídia/CDN autorizados são lacuna a confirmar, não liberados por `endsWith` genérico.
6. Não mapear LID para dígitos. Opções G2: rejeitar/quarentenar LID-only com diagnóstico e declarar
   suporte parcial; ou ampliar modelo de identidade de contato em etapa separada. Não criar lead
   com telefone fictício. Grupos/newsletters/Status e tipos não suportados seguem política de
   descarte rastreável; fromMe correlaciona sem assumir responsável ou emitir resposta duplicada.
7. Handoff Dylan: providerKey e identificadores de ciclo existentes continuam válidos; IA envia
   pelo `/internal/v1/.../responder` e worker, não diretamente ao fornecedor. Novo envelope de
   entrada/callback requer alinhamento explícito; API interna/X-Synapse-Token não muda.

## 5. Capacidades adicionais: não habilitadas

Citação: [reply-message](https://developer.z-api.io/message/reply-message) documenta
phone/message/messageId; página send-text não lista messageId entre seus campos, portanto
não misturar os contratos. Reação: [send-message-reaction](https://developer.z-api.io/message/send-message-reaction)
documenta phone/reaction/messageId. Contato: [send-message-contact](https://developer.z-api.io/message/send-message-contact)
documenta phone/contactName/contactPhone. Localização: [send-message-location](https://developer.z-api.io/message/send-message-location)
documenta phone/title/address/latitude/longitude (coordenadas como strings).
Exclusão: [delete-message](https://developer.z-api.io/message/delete-message) documenta DELETE
messages com messageId/phone/owner e deleteForMe opcional. Foto: [get-profile-picture](https://developer.z-api.io/contacts/get-profile-picture)
devolve link; links via get-contacts têm prazo diferente (48h), não confundir com mídia 30 dias.
Edição usa campos específicos de cada tipo; limites/capacidades completas ficam para a Fase 4.
Nenhuma dessas ações aparece no CRM nesta fase; templates oficiais não são presumidos.

## 6. Fixtures, validação e decisão de entrada

[Fixtures](./fixtures/README.md) são sintéticas: nenhum cliente, segredo, URL real de mídia ou
captura de produção. São insumos de testes futuros, **não homologação nem simulador executado**.

G1: páginas oficiais não especificam assinatura/header de autenticação do callback, lista de
IPs de saída ou preservação de um segredo na URL. HTTPS protege transporte, não autentica quem
envia. instanceId/phone são campos falsificáveis. Client-Token e o controle de IP descritos
protegem chamadas à API, não provam origem do callback.

Decisão do responsável em 12/10/2026: **obter contrato oficial de autenticação antes de implementar
a entrada**. A alternativa de proteção própria por segredo na URL não foi escolhida. O gate
continua aberto até receber e verificar a fonte oficial, sem implementar/ativar entrada.
Para G2 foi aprovado suporte inicial a telefones, com descarte/quarentena rastreável de LID-only
e limitação explícita; sem conversão fictícia ou ampliação do modelo. Ainda não implementado.
Resultados e limites do API Documentation Checker e perguntas para o fornecedor estão em
[autenticacao-callback-pendente.md](./autenticacao-callback-pendente.md).
Não substituir por HMAC inventado, cabeçalho Client-Token presumido ou acesso público sem proteção.

Nenhuma ação no Dokploy agora. Nenhuma chamada a api.z-api.io, número conectado ou callback registrado.
