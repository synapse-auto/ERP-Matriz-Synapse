# 52. Auditoria — recebimento de mídias do cliente (Uzapi/Autotic)

Origem: relato da Fêmina de que arquivos `.JPG` apareceram com "O arquivo não chegou ao CRM." enquanto
um PDF próximo no tempo chegou normal. O relato orientou a auditoria; **não é prova de que `.JPG`
falha por ser `.JPG`**. Trabalho feito só com o repositório e testes locais — sem Dokploy, produção,
banco remoto ou painel da Uzapi.

Complementa o `docs/47` (410 do resolvedor) e o `docs/38` (contrato Uzapi).

## 1. Caminho completo (referências)

```
POST webhook → UzapiAutoticWebhookTradutor.traduzirComDescartes      (canal/…Tradutor.java:153)
  → ProcessadorDeWebhookEntradaOperacoes.rodada()                    (webhook/…Operacoes.java:165)
     → mensagemRecebidaDeMidia → CanalGateway.baixarMidiaRecebida    (…Operacoes.java:387)
        → UzapiAutoticAdapter.buscarMidiaRecebida                    (canal/…Adapter.java)
             GET /{version}/{mediaId}  → { id, url }                  (resolvedor)
             GET {url}                 → bytes + Content-Type         (download)
     → ArmazenamentoDeMidia.salvar (MinIO)  → registrar.executar     (mensagem com arquivo)
  → exibição: AtendimentoMensagensController gera URL assinada; bolha-mensagem.tsx
```

## 2. Matriz de comportamento — **comprovado pelo código e por teste**

### 2.1 Tradução do webhook (`UzapiAutoticWebhookTradutor.java:247-271`)

| Tipo do webhook | Tipo no CRM | Campos lidos (aliases) | Exigido |
|---|---|---|---|
| `image` | IMAGEM | `media_id`/`mediaId`/`id`, `mime_type`/`mimeType`/`mimetype`, `filename`/`fileName`/`name`, `caption` | id de mídia não vazio |
| `document` | DOCUMENTO | idem | idem |
| `audio` | AUDIO | idem | idem |
| `video` | VIDEO | idem | idem |
| `sticker` | IMAGEM | idem | idem |
| qualquer outro (`poll`, …) | — | — | descartado `TIPO_NAO_SUPORTADO` |

Comum a todos: exige `id` da mensagem e `from`/`sender`/`participant` (senão `SEM_IDENTIFICADOR`);
`type` é lido em minúsculas; **a extensão do arquivo nunca participa da decisão** — o tipo vem de
`type`. Mídia de grupo vira descarte `GRUPO_NAO_SUPORTADO`; Status/Story é ignorado de propósito
(E163). Mídia sem id utilizável vira descarte `SEM_IDENTIFICADOR` e **não** chama o provedor.

### 2.2 Obtenção dos bytes (`UzapiAutoticAdapter.buscarMidiaRecebida`)

Duas chamadas em sequência, sob o disjuntor `canal-uzapi-autotic-midia`: resolvedor
`GET /{version}/{mediaId}` (com Bearer) e `GET {url}` (sem Bearer). O mimetype gravado é o
`Content-Type` **da resposta do download** (`application/octet-stream` se vier sem); o `mime_type` do
webhook é lido pelo tradutor mas **não é usado** depois.

### 2.3 O que cada resposta produz

| Situação | Classe de erro | Resultado para o atendente |
|---|---|---|
| 200 + bytes | — | mensagem **com arquivo** |
| Resolvedor 410 | `MidiaRecebidaRemovida…` (terminal) | sem arquivo, na mesma tentativa (E218) |
| Resolvedor 400/404/5xx; download 403/404/410/5xx | `MidiaRecebidaTemporariamenteIndisponivel…` | retenta com backoff até `WEBHOOK_PRAZO_MIDIA` (10 min); depois, sem arquivo (E207) |
| **Resolvedor 200 sem `url` / corpo vazio / não-JSON** | era `IllegalStateException`/`RespostaInvalida…`; **agora** `…TemporariamenteIndisponivel…` | **corrigido nesta etapa** (ver §4) |
| **Download 200 sem bytes** | era `IllegalStateException`; **agora** `…TemporariamenteIndisponivel…` | **corrigido nesta etapa** (ver §4) |
| Timeout / erro de rede (resolvedor ou download) | `ResourceAccessException` (genérico) | retenta até o teto de 5 tentativas (~75 s); **na última, esgota a linha e o atendente não vê nada** — ver §5 |
| Falha do storage (MinIO) | `IllegalStateException` (genérico) | idem timeout |
| Disjuntor aberto | `ProvedorTemporariamenteIndisponivel…` | `adiar` sem gastar tentativa, até `prazo-absoluto` (2 h) |

### 2.4 Persistência e exibição

- **Não há allowlist de MIME nem de extensão no recebimento.** `MinioArmazenamentoDeMidia.salvar`
  aceita qualquer bytes/mimetype; a chave é `midia/<uuid><extensão do nome>` (mantém `.JPG` em
  maiúsculas). A allowlist (`TiposDeMidiaPermitidos`) existe só no **envio** (`EnviarMidiaUseCase`).
- O aviso "O arquivo não chegou ao CRM." (`bolha-mensagem.tsx:286`) só aparece quando
  `midiaUrl` é nulo **e** `indisponivel === true` — isto é, **somente nos caminhos "sem arquivo" do
  processador** (§2.3). Nenhuma regra de extensão no frontend ou no storage produz esse aviso.
- Classificação visual (`classificar-midia-visual.ts`): decide por `tipoMensagem`, depois mimetype,
  depois extensão (`.pdf`, `.mp4`/`.webm`/`.mov`).

## 3. Cobertura de teste por caminho

| Caminho | Teste |
|---|---|
| Tradução de image/document/audio/video/sticker, `.JPG`/`.jpg`/`.jpeg`/`.png`/`.pdf`, JPG como documento, aliases, tipo em caixa alta | `UzapiAutoticWebhookTradutorMidiaTest`, `UzapiAutoticWebhookTradutorTest.midiasUsamAliases…` |
| Mídia sem objeto / sem id / id vazio / id nulo → descarte, sem derrubar o PDF do mesmo POST | `UzapiAutoticWebhookTradutorMidiaTest` |
| Recebimento **pelo ponto de entrada** (`rodada()`, tradutor + adaptador reais): cada tipo/extensão chega ao storage e vira mensagem com arquivo | `RecebimentoDeMidiaUzapiPontaAPontaTest.anexoEntregueChega…` (10 variações) |
| Mídia sem `mediaId`: sem HTTP, sem storage, sem mensagem, descarte registrado | `…midiaSemMediaId…` |
| 200 sem `url` / sem bytes: sem arquivo vazio no storage, retenta (inclusive na última tentativa do teto), pós-prazo mantém aviso | `…resolvedorSemUrl…`, `…respostaSemBytes…` (2) |
| Erro temporário reagenda e a rodada seguinte entrega **uma** mensagem (sem duplicar) | `…erroTemporarioReagenda…` |
| Erro definitivo (410) mantém o aviso na mesma tentativa | `…erroDefinitivoMantemOAviso…` |
| URL temporária, token e bytes ausentes de log e de `ultimo_erro` | `…urlTemporariaTokenEBytes…` |
| Resolvedor/download: HTTP 400/403/404/410/5xx, timeout, id ausente, etapa e host | `UzapiAutoticAdapterTest` (pré-existente) |
| 200 sem url (7 corpos), download vazio, sem Content-Type | `UzapiAutoticAdapterMidiaRecebidaRespostaInvalidaTest` |
| Prazo de mídia, 410 terminal, teto de tentativas (processador isolado) | `ProcessadorDeWebhookEntradaOperacoesTest` (pré-existente) |
| Timeout no download na última tentativa esgota sem aviso (**caracterização**) | `…timeoutNoDownloadNaUltimaTentativaDoTeto…` |

**Lacuna que continua sem teste:** o `Content-Type` real que a CDN da Uzapi devolve por tipo de
arquivo (só dá para saber com resposta real); renderização no navegador (cobertura só por testes de
componente do frontend, não rodados nesta etapa).

## 4. Defeito reproduzido e corrigido

**Reprodução:** teste de ponto de entrada com resolvedor respondendo `200 {"id":…}` (sem `url`) ou
download respondendo `200` sem corpo, na última tentativa do teto. Antes da correção a linha esgotava:
`[ALERTA_WEBHOOK_ESGOTADO] … NAO virou mensagem na conversa … resposta da midia recebida sem bytes`.

**Causa:** `buscarMidiaRecebida` lançava `IllegalStateException` para essas respostas, que cai no
caminho genérico de `falhar()` (teto de 5 tentativas, ~75 s) e não no ciclo de indisponibilidade de
mídia (prazo de 10 min + aviso "sem arquivo"). Resultado: **o anexo do cliente sumia da conversa sem
nenhum aviso**, ao contrário de 404/410/5xx.

**Correção** (`UzapiAutoticAdapter.java`): essas respostas passam a lançar
`EtapaDaMidiaRecebidaFalhou` → `MidiaRecebidaTemporariamenteIndisponivelException`, com etapa (e host,
no download) no `ultimo_erro`. Corpo vazio no download também é recusado antes de o storage gravar
objeto de 0 byte. **Não mudam:** prazo de mídia, backoff, teto de tentativas, rota do resolvedor,
tratamento do 410, contrato da Uzapi.

## 5. Lacuna comprovada, não corrigida (decisão pendente)

Timeout ou erro de rede, e falha do storage, seguem o caminho genérico: se ocorrerem de forma
consistente nas 5 tentativas (~75 s), a linha esgota e o anexo **não aparece nem como aviso**. O
payload cru permanece em `webhook_entrada` para reprocessamento manual. O `docs/47` decidiu manter
isso; o teste de caracterização documenta o comportamento. Tratar timeout como indisponibilidade
temporária mudaria a política de retentativa (prazo efetivo de ~75 s para 10 min) — decisão de
produto, não tomada aqui.

## 6. O que o código **permite concluir** / o que **não permite**

**Permite concluir**

1. O recebimento **não rejeita nem trata diferente** `.JPG`, `.jpg`, `.jpeg`, `.png` ou `.pdf`: não há
   allowlist de extensão/MIME no caminho de entrada, e o tipo da mensagem vem de `type`, não do nome.
   Com bytes válidos, todos chegam ao storage e viram mensagem com arquivo (teste de ponto de entrada).
2. O aviso "O arquivo não chegou ao CRM." só é gerado quando o processador grava a mensagem como
   `indisponivel`: resolvedor 410, ou falhas temporárias por mais de 10 min. Respostas 200 sem
   url/bytes, antes desta etapa, **não** geravam o aviso: o anexo sumia.
3. Portanto, os `.JPG` da Fêmina chegaram ao CRM com o aviso porque **a Uzapi não entregou os bytes**
   dentro do prazo (410 no resolvedor, ou 404/5xx/erro no download por ≥10 min). A extensão em si não
   é causa de nada no código.

   Limite dessa conclusão: ela identifica o caminho que gera o aviso, mas não prova, sozinha,
   que a referência fornecida ao resolvedor era a referência correta do webhook. A correlação
   de IDs e o status de cada incidente precisam de evidência operacional, como na seção 8.

**Não é possível concluir sem dados de produção ou resposta da Uzapi**

- Qual dessas falhas ocorreu em cada `.JPG` (o `ultimo_erro` das linhas de `webhook_entrada` e o log
  `[MIDIA_NAO_RECEBIDA] … etapa=…` dizem; as consultas estão no `docs/47` §4).
- Se a Uzapi trata fotos recebidas de forma diferente de PDFs (retenção, expiração da URL, CDN).
  Hipótese, sem evidência: documentos e imagens seguem caminhos distintos no provedor.
- Se existe diferença sistemática por extensão no lado do provedor.
- Qual `Content-Type` a CDN devolve para cada arquivo (ver §7, hipótese 2).

## 7. Hipóteses (não comprovadas)

1. Latência/retenção curta da mídia no provedor para imagens (mesma linha do `docs/47`).
2. **Mimetype vindo da CDN.** Se a CDN devolver `application/octet-stream` para um JPG enviado *como
   documento*, `classificarMidiaVisual` o trata como "documento" genérico (não "imagem"), pois só
   reconhece `.pdf`/`.mp4`/`.webm`/`.mov` por extensão. O arquivo chega e abre; só a pré-visualização
   difere. Não é a causa do aviso "não chegou". Pode ser melhorado usando o `mime_type` do webhook como
   fallback — **não implementado**: não foi demonstrado como defeito.

## 8. JPG/PNG enviados como documento — reprodução de 07/10/2026

### Evidência operacional recebida

O operador coletou somente a imagem da especificação do serviço `fmnaprod-uzapi-36acu4_backend`
e campos técnicos dos logs, entre 15:35 e 16:00 UTC (12:35–13:00 em Brasília). A imagem configurada
é `ghcr.io/synapse-auto/erp-matriz-synapse-backend:1f1bcf0`, com digest SHA-256 presente; `1f1bcf0`
é também a base desta investigação. O JSON completo e os IDs do incidente devem permanecer no
canal privado de suporte, não no repositório público.

| Horário UTC | Horário Brasília | Tipo CRM | Etapa | HTTP |
|---|---|---|---|---|
| 07/10/2026 15:41:55.503098 | 12:41:55.503098 | DOCUMENTO | resolvedor | 410 |
| 07/10/2026 15:42:14.227878 | 12:42:14.227878 | DOCUMENTO | resolvedor | 410 |

**Comprovado:** a chamada ao resolvedor recebeu HTTP 410 nos dois casos. Esse caminho não
executa o download da URL nem chama o storage; registra a mensagem sem arquivo na mesma tentativa,
conforme a política E218. Portanto, corrupção de Base64, recusa dos bytes por MIME e falha do MinIO
não explicam esses dois registros: nenhum byte chegou a essas etapas.

**Correlação confirmada pelo operador em consulta somente leitura:** os dois `document.id`
coincidem exatamente com os `midiaId` dos respectivos logs. Os aliases `media_id` e `mediaId`
estão ausentes; o tipo recebido é `document`, com MIME `image/png` e `image/jpeg`, respectivamente.
O primeiro webhook chegou às 15:41:53.994286 UTC e foi processado às 15:41:54.810928 UTC
(aproximadamente 0,82 segundo); o segundo chegou às 15:42:13.392091 UTC e foi processado às
15:42:13.921453 UTC (aproximadamente 0,53 segundo). Ambos registram zero tentativas anteriores.
Isso exclui troca de referência nesses registros e não indica atraso de fila como causa.

**Pendente do fornecedor:** explicar por que essas referências novas responderam 410.
Não inferir expiração, remoção ou ausência definitiva dos bytes no sistema interno da Uzapi
apenas pelo status recebido pelo CRM.

O Swagger oficial foi consultado novamente em 07/10/2026:
[`GET /{version}/{mediaId}`](https://api.uzapi.com.br/docs/swagger.json) continua documentando
`Authorization: Bearer`, sem número/username no path, com resposta 200 `{id, url}`. O webhook
`/webhook/message/document` usa `messages[].document.id`, `filename` e `mime_type`; não há
conversão Base64 documentada nesse caminho nem uma resposta 410 documentada pelo fornecedor.

### Coleta e correlação sem dados de cliente

- `docs/diagnosticos/coletar-midia-uzapi-20261007.py`: lê somente a imagem do serviço e os logs.
  Exporta um JSON privado (0600), sem sobrescrever arquivo, com whitelist de etapa, status,
  tipo, tentativa e IDs. Não grava a linha bruta, telefone, token, URL, payload ou conteúdo.
  `docker service logs` não tem `--until`; o script aplica o limite superior ao timestamp.
  O digest ausente na especificação fica `null`, sem resolver uma tag mutável no registry.
- `docs/diagnosticos/conferir-referencias-uzapi-20261007.sql`: consulta por duas chaves de entrada,
  dentro de `BEGIN READ ONLY`, com timeout de 5 segundos. Substituir os placeholders pelos IDs
  privados do incidente. Não imprimir o payload inteiro ou o `ultimo_erro` bruto.

Ausência de logs não prova ausência de chamada: tarefas antigas, retenção ou logging driver
podem impedir a coleta. O script registra o exit code de `docker service logs` para distinguir
coleta incompleta de resultado vazio.

### Validação local adicional

`WebhookDocumentoImagemUzapiIT` chama o POST real `/webhook/canal`, o método agendado real
`ProcessadorDeWebhookEntrada.processarPendentes()`, a persistência PostgreSQL e o adaptador
MinIO real. Só o provedor/CDN é simulado por HTTP local. As fixtures JPEG e PNG são imagens
completas de 2×2 pixels, geradas e lidas por ImageIO; o PDF contém uma página em branco e xref.

O teste cobre documento JPG/JPEG/PNG, foto JPEG/PNG, PDF, nome e bytes no storage/download JWT,
histórico relido, segredo errado, acesso de outro atendente, lead divergente, ausência de JWT,
deduplicação, 410 antes de download, recuperação após 503 e recusa de download vazio como
indisponibilidade retentável. Não converte documento em foto nem interpreta o nome como mediaId.

Isso demonstra o comportamento do CRM quando o fornecedor entrega bytes; **não comprova
recebimento na Uzapi real**. Não houve alteração de código de produção, rota, retry, 410,
autorização, frontend ou configuração. Não há uma correção segura para tornar bytes disponíveis
quando o resolvedor os recusa com 410; a integração continua pendente do fechamento com o fornecedor.

### Encaminhamento ao suporte e homologação

Compartilhar por canal seguro: os dois horários, IDs de entrada/mídia correlacionados, versão
implantada, método/path do resolvedor e HTTP 410. Solicitar confirmação de que `document.id`
é resolvível por essa rota para JPEG/PNG enviados como Documento e a causa exata do 410 em mídia
nova. Não enviar token, telefone, arquivo do cliente, URL temporária ou payload completo.

Após confirmação do fornecedor, homologar, em atendimento de teste autorizado: JPG como documento,
PNG como documento, a mesma imagem como foto e PDF como documento. Correlacionar webhook,
`mediaId`, resolução, download, storage privado e download JWT; conferir bytes e nome preservados.

Arquivos antigos só podem ser recuperados se o fornecedor comprovar que o mediaId original voltou
a servir os bytes corretos. Nenhuma recuperação é garantida após 410. O replay de uma entrada já
processada é deduplicado e não substitui a mensagem sem arquivo: uma recuperação exige procedimento
específico, IDs aprovados e autorização operacional. Não reprocessar histórico em massa.

**Dokploy:** nenhuma variável nova e nenhuma ação de configuração proposta nesta investigação.
