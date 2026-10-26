# Auditoria Z-API: mensagens e mídias — 26/10/2026

Escopo: Fêmina na instância existente. Nenhuma operação produtiva ou envio real pelo agente.
Os 95 atendimentos transicionados são informação do operador, não nova medição independente.

## Fontes verificadas

Repositório oficial `Z-API/z-api-docs`, commit `0b8d420f1f592a7b5e55d28f3e9412ed785880d6`:
`docs/message/send-message-{text,image,audio,video,document}.md`,
`docs/security/client-token.md`, `docs/instance/status.md`,
`docs/webhooks/on-message-{received,send}.md` e `on-whatsapp-message-status-changes.md`.
Conferência adicional no site oficial: [delivery](https://developer.z-api.io/webhooks/on-message-send),
[exemplos de delivery](https://developer.z-api.io/webhooks/on-message-send-examples),
[recebimento](https://developer.z-api.io/webhooks/on-message-received),
[texto](https://developer.z-api.io/message/send-text),
[status](https://developer.z-api.io/instance/status),
[token da conta](https://developer.z-api.io/security/client-token).

O API Documentation Checker recebeu os cinco documentos de envio, mas devolveu sucesso com
zero endpoints. Isso não valida o contrato. As conferências abaixo foram feitas diretamente
nas fontes, sem executar seus scripts ou exemplos contra a API.

## Caminho auditado e decisões

Saída: composer → multipart/JSON autenticado → `EnviarMidiaUseCase`/`EnviarMensagemUseCase`
→ Specification e participação → armazenamento privado → mensagem/outbox atômicas →
`PublicadorDaOutbox` → `ZApiAdapter`/`ZApiMidiaSaida` → HTTP isolado por bulkhead/disjuntor.
O POST ocorre fora da transação da outbox. O snapshot de origem e a rota de transição
preservam credencial histórica, autoria, responsável e participantes; não se reescreve histórico.

| Conteúdo | Endpoint/campo verificado | Serialização atual |
|---|---|---|
| Texto | POST `send-text`, phone/message | JSON escapado, Unicode e newline preservados |
| JPEG/PNG | POST `send-image`, image/caption | data URI + Base64 em fluxo, sem URL pública |
| Áudio anexo/gravado | POST `send-audio`, audio | data URI; sem caption não documentado |
| Vídeo | POST `send-video`, video/caption | data URI, MP4/3GP dentro da allowlist atual |
| PDF/documento | POST `send-document/{extension}`, document/fileName/caption | extensão do MIME persistido, nome sanitizado |
| JPEG/PNG como documento | POST `send-document/jpg` ou `png`, document | mantém DOCUMENTO; não vira image |

Token de instância no path e Client-Token são autenticação de saída, não do callback.
O operador confirmou 400 `your client-token is not configured` nas duas operações da conta
sem Client-Token. Não foi contornado. Recusas explícitas encerram a tentativa sem redisparo;
respostas incertas continuam suspensas. Os logs seguros contêm somente etapa, HTTP e código
enumerado/validado. Corpos limitados a `Z_API_LIMITE_RESPOSTA_BYTES`; nenhum corpo/URL/token
ou conteúdo é propagado como causa ao disjuntor. Não se reprocessa outbox anterior.

Mídia de saída usa spool limitado antes da rede, confere tamanho, aplica limites existentes e
remove temporário no sucesso/erro. Gravação do composer é normalizada pelo conversor existente
para OGG/Opus mono/48 kHz; o worker agora revalida o contêiner completo do storage antes do
POST. Anexo manual não sofre conversão nova. Teste HTTP simulado comprova os bytes, não a
reprodução no dispositivo WhatsApp nem compatibilidade de todo codec possível.
O teste com fixture OGG completa revelou recusa anterior ao adaptador: Tika retorna
application/ogg e o upload não normalizava o contêiner. A classificação comum agora normaliza
somente aliases Ogg/Opus após validar páginas completas/Opus/EOS/duração. Não adiciona formato
novo à allowlist. O alcance é o upload de Atendimentos em todos os canais, necessário para o
mesmo composer; Meta/Uzapi precisam passar a regressão. O antigo teste M4A simplificado foi
substituído por OGG, PDF e JPEG sintéticos válidos.

Entrada: Traefik TLS → `ZApiWebhookProtegidoFilter` → `WebhookZApiController` →
`webhook_entrada` → job → tradutor/correlação → download CDN → MinIO → mensagem/evento →
WebSocket/API de histórico. `SOMENTE_COLETA=true` **não processa** entradas Z-API, não baixa
bytes nem gasta tentativas. HTTP 200 apenas confirma ingresso durável nesse modo.
Foi corrigida uma lacuna: antes o worker parava, mas o controller ainda podia agendar
repasse ao n8n se a flag independente de repasse estivesse ligada. A coleta agora bloqueia
também esse agendamento, com teste HTTP e controle positivo fora da coleta. Eventos antigos
coletados não são repassados retroativamente ao trocar a flag.

O filtro precede MVC/Spring Security; CSRF está desativado e `/webhook/**` é permitAll na
cadeia geral. O filtro dedicado continua exigindo TLS reconhecido pelo container, modo ativo
e segredo exclusivo em comparação constante. Query/headers sensíveis são removidos antes
da próxima camada. O novo diagnóstico distingue TLS, segredo, payload e instância. `{}` com
segredo válido passa autenticação mas retorna 400 de payload, sem persistir; esse teste não
é um callback válido. Não confiar em `X-Forwarded-Proto` cru ou expor porta do backend.

URLs de mídia: somente HTTPS, host exato configurado, DNS público aplicado no próprio
transporte, sem proxy/redirect/retry automático/credenciais da API. Timeouts e tetos existentes,
spool limitado, tipo dos bytes e storage privado. O tipo de mensagem vem do objeto document,
image, audio ou video: documento-imagem permanece DOCUMENTO. `downloadError` vazio não é
falha; valor não vazio/malformado continua recusado. Arquivo sem bytes continua indisponível.

DeliveryCallback: o repositório oficial omite momment no exemplo; o site atual o inclui.
Ambas as formas são suportadas. Na ausência completa desse campo, usa-se o `recebido_em`
durável como horário local da observação, não como horário declarado pelo fornecedor.
Campo presente mas inválido não recebe fallback. Status normais ainda exigem timestamp e
namespace explicitamente confirmado; não se equiparam zaapId, messageId e ids[].

## Matriz de evidências automatizadas

Os testes citados precisam ter seu resultado real registrado na PR; esta matriz descreve o
que exercitam, não é declaração antecipada de aprovação nem de homologação no fornecedor.

| Tipo | Envio | Recebimento | Reprodução/download e persistência |
|---|---|---|---|
| Texto | ZApiSaidaIT: API, clique duplicado, outbox, IDs e PENDENTE sem redisparo | ZApiWebhookProtegidoIT: HTTP autenticado, dedup e evento de tempo real | PostgreSQL e leitura subsequente |
| JPEG | ZApiSaidaIT: upload JPEG real, data URI e bytes | ZApiWebhookProtegidoIT: image-jpeg | MinIO real, download autenticado e negativo de outro atendente |
| PNG | ZApiSaidaIT: imagem | ZApiWebhookProtegidoIT: image | idem |
| Áudio anexo | ZApiSaidaIT: OGG sintético válido | ZApiWebhookProtegidoIT: audio | bytes/MIME/MinIO; dispositivo real pendente |
| Áudio gravado | FfmpegConversorDeAudioTest + ZApiMidiaSaidaTest: perfil e OGG truncado recusado | mesmo tipo AUDIO | FFmpeg/ffprobe somente quando instalados; gravação pelo navegador e reprodução real separadas |
| Vídeo | ZApiSaidaIT: MP4 sintético | ZApiWebhookProtegidoIT: video | bytes/MIME/MinIO, dispositivo real pendente |
| PDF | ZApiSaidaIT: PDF sintético | ZApiWebhookProtegidoIT: document-pdf | bytes/MIME/nome/disposition/nosniff |
| PNG/JPEG DOCUMENTO | ZApiSaidaIT: encaminhamento da mensagem DOCUMENTO | ZApiWebhookProtegidoIT: document e document-jpeg | categoria preservada e download exato |

Negativos: ZApiAdapterTest (400 token, 401/403, 429, 5xx, timeout, 2xx malformado, tamanho
da resposta, log sem segredos); ZApiMidiaEntradaTest/ZApiDnsPublicoTest (URL/host/DNS,
redirect, MIME real, vazio/limite/OGG truncado, 410, falha transitória); ZApiMidiaSaidaTest
(storage antes da rede, tamanho, legenda, spool e corrupção de gravação). Integração:
ZApiSaidaIT/ZApiWebhookProtegidoIT e TransicaoProdutivaZApiIT (PostgreSQL/MinIO reais,
autorização negativa, coleta, continuidade, filas antigas, callback antecipado e monotonia).

## Verificacao local e limites da evidencia visual

- Rodada dirigida: 52 integracoes aprovadas com PostgreSQL/MinIO reais, antes de acrescentar
  o teste opcional de conversao real do composer. A suite completa inclui o fonte final;
  seu resultado e a run de CI do ultimo SHA ficam no fechamento da PR.
- Frontend: typecheck, lint e build aprovados; `npm audit --omit=dev` sem vulnerabilidades.
  A primeira suite completa no Node Windows 24.12.0 teve 1397/1407 aprovados: os dez erros
  ficaram em `aplicar-marca.test.ts`, subprocesso encerrado com 3221226505. Teste e script
  sao identicos a origin/main; a repeticao isolada reproduziu 10/18 falhas. Nao corrigidos
  por esta task. Nova rodada no runtime empacotado 24.22.1 separada dessa evidencia.
- Playwright, primeira rodada: 1/1 aprovado na aplicacao Next real, API real e dados sinteticos,
  incluindo reproducao OGG pelo evento playing, download exato do audio, indisponivel sem
  acao Baixar e recarga. Fontes aguardadas e font-family computado Inter/sans-serif.
  O spec foi ampliado depois para preview, video e downloads por teclado; a repeticao dessa
  extensao depende de iniciar novamente o frontend local. Nao confundir com a rodada 1/1.
- Capturas de desenvolvimento: [1920px](../../../frontend/output/playwright/z-api-auditoria/midias-1920.png)
  e [390px](../../../frontend/output/playwright/z-api-auditoria/midias-390.png). Nao sao
  aprovacao visual: incluem indicador de desenvolvimento do Next e, em 390px, o composer
  existente cobre parcialmente o documento inferior. Nenhum layout/fonte do produto foi
  alterado para ocultar isso. A ferramenta recusou iniciar o servidor local de producao;
  nao se contornou a restricao. Renovacao pedida ao operador, sem dependencia de producao.
- FFmpeg/ffprobe nao encontrados no PATH local. Os dois testes condicionais de conversao
  de `FfmpegConversorDeAudioTest` e o novo teste `gravacaoDoComposerConverteComFfmpegRealEDespachaOggDoStorage`
  ficam sem execucao real nesse ambiente. Validacao de container/bytes nao substitui codec
  nem reproducao no WhatsApp. O CI informa separadamente seus skips/resultado.

## Roteiro curto do operador — não executado pelo agente

1. Conferir SHA/digest implantado. Manter token da instância e segredo de callback independentes.
   Não inventar Client-Token. O 400 comprovado nas duas rotas exige regularização da conta
   pelo fornecedor/responsável; a opção false não supera uma recusa do servidor Z-API.
2. Após release revisada, coletar apenas diagnóstico seguro (VPS):

   ```bash
   docker service logs --since 15m fmnaprod-uzapi-36acu4_backend 2>&1 \
     | grep -E 'Z_API_FALHA|Z_API_WEBHOOK_REJEITADO' | tail -n 40
   ```

   HTTPS_NAO_RECONHECIDO → revisar rota TLS/estratégia NATIVE/proxy confiável;
   SEGREDO_INVALIDO → comparar privadamente configuração efetiva e callback;
   INSTANCIA_DIVERGENTE → conferir instanceId sem trocar identidade histórica.
   Não publicar URL de callback, envs completas ou payload de paciente.
3. Com callback autenticado e CDN real identificada na coleta sanitizada existente, configurar
   somente hosts exatos em `Z_API_MIDIA_HOSTS_PERMITIDOS`. Somente então o operador muda
   `Z_API_WEBHOOK_SOMENTE_COLETA=false`. Não autoriza drenar ou trocar filas Uzapi antigas.
4. No próprio contato da equipe, testar cada linha da matriz nos dois sentidos; gravar áudio no
   composer, tocar no CRM e no WhatsApp, abrir vídeo/imagem, baixar e comparar SHA-256,
   recarregar e confirmar estados. Verificar enviado/entregue/lido apenas por callback real.
5. Não testar com pacientes. Erro/timeout não autoriza novo clique/reenvio sem conferir IDs e
   efeito externo. Programadas/automação seguem o roteiro de transição já existente.

Ação no Dokploy: nenhuma env nova. Implantação manual apenas da release aprovada, mantendo
segredos e isolamento. Configuração efetiva/proxy, entrega preservando secret, hosts CDN,
namespace de status, reprodução no dispositivo e operação da conta permanecem evidência real.
