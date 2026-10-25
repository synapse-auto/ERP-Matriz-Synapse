# Z-API — conclusão de código para homologação protegida

## Decisão autorizada em 25/10/2026

Implementar recebimento de produção com segredo exclusivo em URL HTTPS, mantendo
OFF por padrão. O suporte mencionou autenticação por header, mas seu nome e sua
configuração ainda não foram confirmados: preparar validação configurável/testes,
sem supor Client-Token e sem habilitar header inventado. Preferir o mecanismo
nativo quando comprovado; a alternativa URL continua autorizada para homologação.
Segredo bearer não é assinatura nativa e não prova integridade criptográfica do corpo.

## Plano desta entrega

1. Autenticação antes da leitura/persistência, comparação de hashes em tempo constante,
   HTTPS, limite de corpo e ocultação de credenciais antes de observabilidade/MVC.
2. Registrar ACL somente com configuração explícita; reutilizar fila, deduplicação,
   correlação antecipada/monótona e isolamento de provedor existentes.
3. Receber URLs de mídia dos campos documentados; download isolado com DNS validado,
   hosts exatos configurados, HTTPS, redirects recusados, tetos/timeout, MIME real e
   spool privado. Persistir no storage sem Base64/arquivo inteiro desnecessário no heap.
4. Testes do HTTP real/fila/job/storage, negativos SSRF/autorização/segredos e regressões
   Meta/Uzapi. Não simular namespace de ids[] como confirmação do fornecedor.
5. Roteiro operacional para operador e documento para automação; coletor somente leitura
   sem telefone, conteúdo ou credenciais. clean verify Java 21, commit/push/PR/CI.

## Limites operacionais preservados

Não acessar VPS, conectar número, cadastrar callback, publicar n8n ou trocar produção.
O operador confirma em conta de homologação a preservação da query escolhida, o header
nativo quando conhecido, namespace dos status, CDN real, codecs e fluxo ponta a ponta.
Não adicionar chamadas, grupos externos ou outros recursos fora do chat.

## A. Codigo entregue (ver resultados no fechamento da PR)

- `ZApiEntradaConfig` registra o ACL apenas com adaptador+webhook habilitados e identidade
  consistente. `/webhook/canal` continua recusando Z-API; nova rota dedicada
  **POST `/webhook/z-api`**. Instancia/canal ativos conferidos antes de enfileirar.
- Filtro antes de MVC/observabilidade: HTTPS, segredo exclusivo base64url de 43–256 caracteres,
  SHA-256 + `MessageDigest.isEqual` em buffers de mesmo tamanho, corpo limitado inclusive
  sem Content-Length, JSON sem chaves duplicadas/trailing tokens. Sem autenticacao nao le o
  corpo nem grava fila/outbox. Query e headers sensiveis removidos do request downstream.
- Modos exclusivos: `SEGREDO_URL` usa `?secret=...`; `HEADER_NATIVO` usa somente o nome
  configurado/confirmado, nunca fallback nem header inventado. Header sintetico existe so
  nos testes. **Ambos sao autenticacao por posse de segredo**, nao HMAC/assinatura do corpo.
  Quem obtiver o segredo pode forjar eventos; dedup nao e prova criptografica contra replay.
- Worker/fila existentes processam texto/status e eco/LID/grupos. `messageId`/`zaapId` ficam
  separados. `ids[]` desconhecido nao altera mensagem; identidades orfas ficam duraveis,
  colisoes bloqueadas, status monotono, callback precoce reconciliado ao aceite.
- Download usa `image.imageUrl`, `audio.audioUrl`, `video.videoUrl`, `document.documentUrl`.
  HTTPS/443, host exato configurado, DNS publico validado e fixado na propria conexao,
  sem proxy/redirect/retry HTTP automatico e sem credenciais API. CB/bulkhead/timeout separados.
  Spool privado limitado pelos tetos/configuracoes existentes, MIME por bytes, trilhas de
  audio/video verificadas. Documento conserva DOCUMENTO inclusive PNG/JPEG. HTML/ZIP/executavel
  disfarcados recusados. Bytes vao em fluxo ao MinIO privado; sem Base64 na entrada.
  Os caminhos antigos Meta/Uzapi continuam usando sua porta anterior; default aditivo.
- Falha temporaria conserva backoff existente; 410 e removido; tipo/URL invalido vira anexo
  indisponivel, nunca download falso. Spool fechado/removido e compensacao do storage em rollback.
- Repasse n8n opt-in, outbox com origem, token proprio opcional para canais antigos e exigido
  para Z-API. JSON sem segredo de callback e sem falsa assinatura Meta. Eventos de outro
  provedor/legado na entrada Z-API bloqueados antes de HTTP. Nenhuma migration nova.

O worker de midia segue assincrono, fora do request do webhook. Reutiliza a transacao existente
do processador; nao ha chamada ao fornecedor ao abrir conversa/menu. Concorrencia default 1 e
timeout existente limitam ocupacao. Carga representativa e codecs reais ainda precisam homologacao.

## B. Configuracao/comandos a cargo do operador

### Cadastro da credencial e limite dos ciclos antigos

O provisionador generico atual ainda exige Phone Number ID numerico da Meta e usa
`env://WHATSAPP_TOKEN`: **nao reexecuta-lo com ID Z-API**. Na base isolada de homologacao,
o cadastro precisa de `canal.tipo=WHATSAPP`, credencial ativa/versionada com
`identificador_externo=Z_API_INSTANCIA_ID`, `token_ref=env://Z_API_TOKEN` e UUID igual a
`Z_API_CREDENCIAL_ID`. A coluna `numero` guarda o telefone controlado (ate 30 caracteres),
nao o ID de instancia de 32 caracteres. Nunca gravar o token real no banco.

Atendimentos ja abertos conservam a credencial original. O envio novo ainda usa essa
ancora; o guard recusa se nao corresponder a credencial Z-API configurada. Nao trocar
IDs historicos em massa para contornar. Para homologar, usar **novo ciclo com credencial
Z-API**. Para corte com ciclos antigos abertos, definir/validar explicitamente seu
tratamento antes de trocar: esta entrega nao migra silenciosamente novas saidas desses
ciclos. O bloqueio seguro nao deve ser confundido com continuidade operacional homologada.

URL do callback a cadastrar em modo proprio: `https://<dominio-hml>/webhook/z-api?secret=<segredo-exclusivo>`.
Os placeholders sao apenas documentais; nunca compartilhar a URL preenchida.

**Nao executar em producao antes de C.** Nada foi aplicado na VPS nesta entrega.

1. Usar ambiente isolado/numero controlado e release cujo SHA/CI foram aprovados. Backend privado
   sem porta publica. Registrar imagem/digest anterior. Defaults continuam OFF em todas as instancias.
2. Gerar **tres segredos distintos**: API/Client-Token existentes, callback novo e, se usar repasse,
   token CRM→n8n separado. Exemplo de geracao local Linux em arquivo privado, nao no chat:

   ```bash
   umask 077
   openssl rand -base64 48 | tr '+/' '-_' | tr -d '=\n' > z-api-webhook.secret
   ```

   Transferir por gerenciador de segredos/painel autorizado; nao colar URL/segredo em ticket/print.
3. Configurar `.env.example`/README, todos opcionais para outras instancias:

   | Variavel | Homologacao |
   |---|---|
   | `WHATSAPP_PROVEDOR` | `z-api` somente no ambiente isolado |
   | `WHATSAPP_NUMERO` / `Z_API_INSTANCIA_ID` | mesmo ID da instancia Z-API; nao telefone |
   | `Z_API_TOKEN` / `Z_API_CLIENT_TOKEN` | credenciais de SAIDA distintas do segredo de callback |
   | `Z_API_CREDENCIAL_ID` | UUID da credencial CRM ativa, versionada; nao reciclar identidade antiga |
   | `Z_API_HABILITADO` / `Z_API_WEBHOOK_HABILITADO` | `true` apenas apos validacao local/proxy |
   | `Z_API_WEBHOOK_AUTENTICACAO` | `SEGREDO_URL`, ou `HEADER_NATIVO` quando confirmado |
   | `Z_API_WEBHOOK_SEGREDO` | segredo novo gerado, nunca token interno/API |
   | `Z_API_WEBHOOK_HEADER_NOME` | vazio ate suporte informar nome e procedimento reais |
   | `Z_API_WEBHOOK_LIMITE_CORPO_BYTES` | default `1048576`, orcamento do CRM, nao limite do fornecedor |
   | `Z_API_WEBHOOK_NAMESPACE_STATUS` | `NAO_CONFIRMADO` ate comparar ids[] com IDs preservados |
   | `Z_API_MIDIA_HOSTS_PERMITIDOS` | hosts exatos da CDN observada/documentada; vazio recusa midia |
   | `Z_API_MIDIA_ENTRADA_CONCORRENCIA` | default `1`; nao aumentar sem medir pool/memoria/disco |
   | `Z_API_REPASSE_AUTOMACAO_HABILITADO` | `false` ate parser/token/dedup n8n homologados |
   | `AUTOMACAO_WEBHOOK_EVENTOS_URL` | destino HTTPS do n8n preparado para envelope Z-API |
   | `AUTOMACAO_WEBHOOK_REPASSE_TOKEN` | segredo proprio CRM→n8n, validado antes do workflow |
   | `SERVER_FORWARD_HEADERS_STRATEGY` | default `NONE`; `NATIVE` somente atras de proxy confiavel |

4. Proxy: `/webhook/z-api` nao foi incluido no router generico. Overlay opt-in
   `docker/dokploy-z-api-homologacao.override.yml` cria router **HTTPS-only** com accessLogs e
   tracing desativados nessa rota. Conferir antes que a versao implantada do Traefik suporta
   [observability por router](https://doc.traefik.io/traefik/reference/routing-configuration/http/routing/observability/).
   Se nao suporta, configurar redacao/exclusao equivalente no proxy antes de expor; nao aplicar
   labels desconhecidas nem ampliar logging global. O backend continua sem porta publicada.
   `NATIVE` so confia nos proxies do container configurados como confiaveis; Traefik precisa
   sobrescrever proto e nao aceitar forwarded headers arbitrarios do cliente.
5. Tomcat usa pattern sem query (`%m %U %H`), access log desativado por default. Verificar agentes
   APM/WAF/LB/CDN externos: podem capturar query/header **antes** do filtro Java. Desativar captura
   nessa rota e testar canario. O codigo nao controla um agente/proxy fora deste repositorio.
   Evitar logging detalhado do SDK/requests e nao colocar o segredo em labels/rotas/metricas.
6. Somente o operador registra os tres callbacks (recebimento/processamento/status) na conta
   controlada usando a URL HTTPS aprovada. Em modo URL, incluir a query `secret` e confirmar que
   cadastro **e entrega** a preservam. Em modo nativo, nome/configuracao oficiais + teste negativo.
   Se query for removida, capturar apenas status/horarios/nomes de campos e demonstrar a
   incompatibilidade; nao mudar para header adivinhado.

## C. Homologacao real necessaria

- [ ] Token ausente/incorreto: 403, nenhuma entrada/outbox/efeito. HTTP direto: recusado.
- [ ] Canary: segredo/URL nao aparecem em proxy, app, tracing/APM, erros ou evidencias.
- [ ] Cadastro/entrega preservam query, ou header nativo oficialmente confirmado chega correto.
- [ ] Numero controlado: texto, imagem, audio, video, PDF, PNG/JPG como documento, nos dois sentidos.
      Conferir nome, MIME, hash/bytes, reproducao/codec e download pelo CRM apos recarregar.
- [ ] IDs: guardar `messageId` e `zaapId` do aceite; comparar **literalmente** com `ids[]` real.
      So entao escolher namespace. Alias nao comprovado fica sem vinculo; nao adivinhar igualdade.
- [ ] Entrega/leitura reais, duplicado, ordem inversa e callback precoce: persistencia/WebSocket/
      recarga sem regressao, mensagem errada ou redisparo. 200+IDs/PENDENTE nao reabre despacho.
- [ ] Eco/fromMe nao cria outra mensagem/IA. LID-only fica diagnosticavel sem telefone inventado.
- [ ] Outra instancia/atendente sem acesso nao alcanca conversa/arquivo; participacao consentida
      preservada. Meta/Estrutural nao muda.
- [ ] Equipe de automacao: token/dedup/parser e inventario de nos diretos/timers/reservas/registros.
- [ ] Medir pool/latencia/disco/memoria com arquivos representativos, sem carga em horario comercial.

Coleta de evidencias: `docs/diagnosticos/coletar-z-api-homologacao.sql`, somente leitura,
janela curta informada por parametros, timeout 5s/limite 100. Retorna nomes dos campos,
IDs tecnicos, estados/horarios; nao telefone, texto, URL, token ou payload completo. Executar
localmente com fixtures antes de usar em homologacao; compartilhar IDs so em canal privado.

## D. Corte Femina e rollback — bloqueios concretos

Codigo local/CI nao comprovam cadastro real, CDN, namespace/entrega de callbacks ou workflow publicado.
O header nativo permanece pendente de **nome + configuracao + escopo/rotacao**. Isso nao impede
validar a alternativa de URL explicitamente aprovada, mas ambas exigem C antes de producao.

Antes do corte: inventariar/drenar outbox de envio, repasse, entrada antiga e mensagens programadas
**no provedor original**, conferir chamadas em voo e suspender produtores/nos diretos por mecanismo
operacional comprovado. Nao usar intervalo alto como garantia de pausa nem derrubar Atendimentos.
Snapshots divergentes bloqueiam envio/repasse; legado sem origem bloqueia ao selecionar Z-API.
Esgotados/ambiguos/PENDENTE aceitos nao sao lista de reenvio. Nao editar payload, status ou
credencial em massa. Programadas ainda nao materializadas precisam decisao explicita de destino.

O seletor continua unico: callbacks tardios do provedor anterior nao serao processados pelo novo.
Conservar a entrada/identidade antiga para conciliacao controlada antes de retomar operacao;
se nao houver forma comprovada de encerrar/drenar eventos em voo, **nao cortar**.

Rollback: parar produtores novos com controle autorizado, reconciliar aceites/ambiguos em voo,
guardar IDs/snapshots, restaurar imagem/configuracao/credencial anterior e confirmar filas separadas.
Voltar a variavel nao reativa linhas bloqueadas nem autoriza reenviar por Uzapi o que Z-API aceitou.
Manter a release com guardas de origem durante rollback de provedor; voltar binario antigo sem
essas guardas exige filas zeradas/reconciliadas. Nenhuma rotina de replay/backfill foi criada.

## Fontes e limites

- [Recebidos e URLs por tipo](https://developer.z-api.io/webhooks/on-message-received-examples)
- [IDs do aceite](https://developer.z-api.io/message/introduction)
- [Status](https://developer.z-api.io/webhooks/on-whatsapp-message-status-changes)
- [Client-Token de SAIDA](https://developer.z-api.io/security/client-token)
- [Traefik por router](https://doc.traefik.io/traefik/reference/routing-configuration/http/routing/observability/)

Revalidadas as referencias oficiais de recebidos/proxy em 25/10. Nao ha nome de header nativo
confirmado nas evidencias desta task. Fixtures oficiais foram sanitizadas e os transportes dos
testes sao locais/simulados; nao equivalem a homologacao no fornecedor.

Resultados finais de testes, SHA, PR e CI ficam no relatorio/progresso desta entrega.
