# 47. Investigação — mídia `tipo=DOCUMENTO` com HTTP 410 na Uzapi/Autotic (FMNA, 25–26/09/2026)

Prompt de origem: "E210 — investigar atraso no processamento de mídia tipo=DOCUMENTO". O número
E210 já foi usado pelo cabeçalho responsivo da conversa (`fix/e210-header-conversa-responsivo`);
este documento usa o título do prompt para não confundir os dois.

**Resultado:** o Bloco 0 foi feito a partir do código e da documentação oficial. **Não há acesso a
produção neste workspace** (sem chave SSH, sem contexto Docker remoto, sem credencial de banco —
mesma limitação registrada em `docs/13`). Os itens 3 e 5 dependem das consultas da seção 4, que
precisam ser executadas por quem opera a FMNA. **O Bloco 1 não foi implementado**: o código não
mostra atraso sistemático nem tratamento diferente para documento, e a hipótese dos 31 minutos
contradiz o código (seção 2.3).

O documento `claude/incidente-postgres-backend-refused-e-rabbitmq-n8n-parados-24-26-09.md` citado
no prompt não existe neste repositório nem em nenhuma branch; a Parte 2 não pôde ser lida.

## Decisão pós-Bloco 0 (26/09/2026)

- Bloco 1 original (intervalo menor, processamento síncrono, prioridade para DOCUMENTO):
  **descartado** — a primeira tentativa já ocorre em ~1 s; o que pesa é quanto tempo o sistema
  insiste antes de desistir.
- **A — implementada.** O erro de mídia recebida da Uzapi agora diz a etapa. Formato (no
  `ultimo_erro`, precedido de `tipo=…;` pelo processador, e no log de retentativa):
  - `midia recebida uzapi-autotic: etapa=resolvedor respondeu HTTP 410; midiaId=…`
  - `midia recebida uzapi-autotic: etapa=download respondeu HTTP 410; host=…; midiaId=…`

  Do download sai só o host da URL, nunca caminho ou query. O aviso
  `Midia do evento … ainda indisponivel no provedor; sera retentada.` passou a trazer
  `tentativa=N` e esse motivo. Classificação, backoff, prazo e disjuntor não mudaram. Linhas
  gravadas antes do deploy continuam com o texto antigo `resolvedor de midia uzapi-autotic
  respondeu HTTP …`, que não distingue as etapas.
- **B — condicionada à 4.1** confirmar o padrão nos três casos; se confirmar, só o 410 vira
  terminal e timeout/5xx/404 continuam com retentativa.
- **C — não fazer** sem a 4.4 mostrar trava do agendador.
- **Breakers da Uzapi** (`CANAL_CB_*`): item separado, fora do escopo.

## 1. Como o pipeline funciona (itens 1 e 2)

| Ponto | Valor em `main` | Onde |
|---|---|---|
| Agendamento | `@Scheduled(fixedDelay = WEBHOOK_INTERVALO_MS)`, padrão **1000 ms** | `ProcessadorDeWebhookEntrada.java:35` |
| Lote | `WEBHOOK_LOTE`, padrão **50** linhas por rodada | `ProcessadorDeWebhookEntradaOperacoes.java:130` |
| Ordem | `ORDER BY recebido_em`, `FOR UPDATE SKIP LOCKED`, só `proxima_tentativa_em <= now()` | `WebhookEntradaRepositorioJdbc.java:43-52` |
| Paralelismo | **nenhum dentro da rodada**: as linhas do lote são processadas em sequência, cada uma na sua transação | `rodada()`, linhas 164-170 |
| Pool do agendador | `max(2, núcleos)` threads `agendado-N`, **compartilhado** por ~15 jobs `@Scheduled` (outbox, repasse, avaliação, estado a cada 250 ms, mensagens programadas, finalização, saúde, partições, métricas) | `AgendamentoConfig.java:46` |
| Download | duas chamadas HTTP em sequência: `GET /{version}/{mediaId}` (resolvedor) e `GET {url}` (bytes) | `UzapiAutoticAdapter.java:770-796` |
| Erro HTTP do provedor | **qualquer** 4xx/5xx das duas chamadas vira `MidiaRecebidaTemporariamenteIndisponivelException` com o texto "resolvedor de midia uzapi-autotic respondeu HTTP {status}" | `UzapiAutoticAdapter.java:517-524` |
| Retentativa de mídia | backoff `5s · 2^tentativas` (teto 30 min), sem teto de tentativas, até `WEBHOOK_PRAZO_MIDIA` (**10 min**) | `falhar()`, linhas 474-485 |
| Fim do prazo | a linha é registrada **sem arquivo** (`indisponivel: true`) e o log `[MIDIA_NAO_RECEBIDA]` sai nessa mesma transação | linhas 229-244 |

**Item 2 — DOCUMENTO não tem tratamento diferente.** O mesmo método (`mensagemRecebidaDeMidia`)
atende IMAGEM, AUDIO, VIDEO e DOCUMENTO; não há fila, prioridade, atraso ou timeout por tipo. O tipo
só aparece no texto do log (`e.comTipo(mensagem.tipo())`). Nada no código torna documento
estruturalmente mais lento. A única diferença possível é de **conteúdo** (documento tende a ser
maior, então o `GET {url}` dos bytes demora mais), mas isso afeta a duração da chamada, não quando
ela começa. Com 3 casos não dá para separar coincidência de causa; a consulta 4.3 mede a proporção
por tipo.

## 2. Linha do tempo esperada de um 410

### 2.1 Sem backlog

Com a fila vazia, a primeira tentativa acontece cerca de **1 s** após `recebido_em`. Um 410 nessa
tentativa aciona o backoff abaixo (o cálculo usa o valor de `tentativas` anterior ao incremento):

| Tentativa | Instante aproximado | Resultado com 410 persistente |
|---|---|---|
| 1 | R + 1 s | reagenda +5 s |
| 2 | R + 6 s | +10 s |
| 3 | R + 16 s | +20 s |
| 4 | R + 36 s | +40 s |
| 5 | R + 76 s | +80 s |
| 6 | R + 156 s | +160 s |
| 7 | R + 316 s | +320 s |
| 8 | R + 636 s (≈ 10 min 36 s) | prazo estourado → registra sem arquivo + `[MIDIA_NAO_RECEBIDA]` |

Ou seja: **o log `[MIDIA_NAO_RECEBIDA]` marca a última tentativa, não a primeira.** Até ela, a
mensagem **não aparece** na conversa, porque a transação da linha inteira é desfeita a cada
tentativa. São ~8 chamadas ao provedor para um arquivo que o próprio status HTTP diz que não volta.

### 2.2 Com backlog ou banco indisponível

O job de webhook só atrasa se: (a) houver mais de 50 linhas prontas à frente (FIFO), (b) cada linha
à frente demorar (download lento, timeout do provedor), ou (c) as threads `agendado-N` estiverem
ocupadas ou bloqueadas — por exemplo, esperando conexão de um Postgres que recusa conexões. Nesses
cenários a **primeira** tentativa pode acontecer minutos depois de `recebido_em`, e aí um arquivo com
retenção curta já pode ter expirado. Isso só é verificável com as consultas 4.1 e 4.4.

### 2.3 A hipótese "a mensagem aparecia às 09:06 e a busca foi às 09:37" contradiz o código

Mensagem recebida é gravada com `enviado_em = Instant.now()` **no momento do processamento**
(`RegistrarMensagemRecebidaUseCase.java:75,103`), não com o timestamp do WhatsApp. E a mensagem
"sem arquivo" é gravada na **mesma transação** que emite o `[MIDIA_NAO_RECEBIDA]`. Portanto, para a
mesma entrada, o horário mostrado na tela e o horário desse log coincidem. Uma bolha às 09:06 não
pode ser a mesma entrada logada às 09:37. Explicações compatíveis com o código, a confirmar com a
consulta 4.1:

- a bolha das 09:06 era **outra** mensagem do mesmo cliente (texto ou outro anexo, possivelmente
  processado com sucesso);
- o horário das 09:06 foi lido de outro lugar (card da lista, notificação, celular do cliente);
- o webhook das duas entradas "×2" das 09:37 chegou ao CRM por volta de **09:26–09:27**
  (09:37 − ~10,6 min). Se o cliente enviou às 09:06, o atraso de ~20 min foi **do provedor até o
  nosso endpoint**, não do nosso processamento.

## 3. Documentação do provedor (item 4)

- `docs/38-contrato-uzapi-autotic.md`: **não menciona** prazo de retenção nem expiração de mídia
  recebida. Documenta apenas que 400/404/5xx do resolvedor são tratados como indisponibilidade
  retentável (o 410 cai no mesmo caminho, sem ser citado).
- Swagger oficial (`https://api.uzapi.com.br/swagger`, spec embutida em
  `swagger/swagger-ui-init.js`, `info.version = 1.0`, lido em 26/09/2026): o
  `GET /{version}/{mediaId}` ("Retrieve Media URL", tag `Midias`) documenta **somente a resposta
  200** `{ id, url }`. Não há 404, 410, TTL, validade da `url` nem prazo de retenção. Busca no spec
  inteiro: 0 ocorrências de "expir", "retenção", "retention", "410", "Gone" ou "TTL".

**Não existe documentação de tempo de expiração/retenção da Uzapi.** Nenhum número foi presumido.
O 410 vem de uma das duas chamadas HTTP ao provedor, e o log atual **não diz qual** (seção 5,
proposta A).

## 4. Consultas read-only para a FMNA (itens 3 e 5)

Todas são `SELECT` dentro de `BEGIN READ ONLY; ... ROLLBACK;`. Substitua os `<entrada-N>` pelos
valores `entrada=` das três linhas `[MIDIA_NAO_RECEBIDA]`.

### 4.1 Os três casos: de onde veio o atraso

```sql
BEGIN READ ONLY;
SELECT w.id_externo,
       m->>'type'                                              AS tipo,
       to_timestamp((m->>'timestamp')::bigint)                 AS enviado_no_whatsapp,
       w.recebido_em,
       w.recebido_em - to_timestamp((m->>'timestamp')::bigint) AS atraso_provedor_ate_crm,
       w.processado_em,
       w.processado_em - w.recebido_em                         AS atraso_no_crm,
       w.tentativas,
       w.ultimo_erro
  FROM webhook_entrada w
 CROSS JOIN LATERAL jsonb_array_elements(w.payload::jsonb -> 'entry')  e
 CROSS JOIN LATERAL jsonb_array_elements(e -> 'changes')              c
 CROSS JOIN LATERAL jsonb_array_elements(c -> 'value' -> 'messages')  m
 WHERE w.id_externo IN ('<entrada-1>', '<entrada-2>', '<entrada-3>');
ROLLBACK;
```

Leitura: se `atraso_no_crm` ≈ 10–11 min e `tentativas` ≈ 7–8, o CRM tentou desde o primeiro
segundo e o provedor já devolvia 410 — **não há margem do nosso lado**. Se
`atraso_provedor_ate_crm` for grande, o atraso é anterior ao nosso endpoint. Só se
`atraso_no_crm` for bem maior que ~11 min há atraso nosso.

### 4.2 Latência típica da fila de entrada

```sql
BEGIN READ ONLY;
SELECT date_trunc('hour', w.recebido_em)                        AS hora,
       count(*)                                                 AS linhas,
       percentile_cont(0.5)  WITHIN GROUP (ORDER BY extract(epoch FROM w.processado_em - w.recebido_em)) AS p50_s,
       percentile_cont(0.95) WITHIN GROUP (ORDER BY extract(epoch FROM w.processado_em - w.recebido_em)) AS p95_s,
       max(extract(epoch FROM w.processado_em - w.recebido_em)) AS max_s,
       count(*) FILTER (WHERE w.tentativas > 0)                 AS com_retentativa
  FROM webhook_entrada w
 WHERE w.recebido_em >= timestamptz '2026-09-24 00:00-03'
   AND w.processado_em IS NOT NULL
 GROUP BY 1
 ORDER BY 1;
ROLLBACK;
```

Se o p50 fica em ~1–2 s na maior parte do período, o processamento não é sistematicamente lento.
Horas com p95/max altos e sem retentativa indicam backlog ou bloqueio do agendador.

### 4.3 Resultado por tipo de mídia

```sql
BEGIN READ ONLY;
SELECT m->>'type'                                               AS tipo,
       count(*)                                                 AS itens,
       count(*) FILTER (WHERE w.tentativas = 0)                 AS de_primeira,
       count(*) FILTER (WHERE w.tentativas > 0)                 AS com_retentativa,
       percentile_cont(0.5) WITHIN GROUP (ORDER BY extract(epoch FROM w.processado_em - w.recebido_em)) AS p50_s
  FROM webhook_entrada w
 CROSS JOIN LATERAL jsonb_array_elements(w.payload::jsonb -> 'entry')  e
 CROSS JOIN LATERAL jsonb_array_elements(e -> 'changes')              c
 CROSS JOIN LATERAL jsonb_array_elements(c -> 'value' -> 'messages')  m
 WHERE w.recebido_em >= timestamptz '2026-09-20 00:00-03'
   AND m->>'type' IN ('image', 'audio', 'video', 'document')
 GROUP BY 1
 ORDER BY 1;

-- Mensagens registradas sem arquivo, por tipo (o que o atendente viu como "não chegou"):
SELECT tipo, count(*)
  FROM mensagem
 WHERE remetente_tipo = 'LEAD'
   AND enviado_em >= timestamptz '2026-09-20 00:00-03'
   AND midia_metadados::jsonb ->> 'indisponivel' = 'true'
 GROUP BY 1;
ROLLBACK;
```

### 4.4 Correlação com backlog e com o incidente do Postgres (item 5)

```sql
BEGIN READ ONLY;
SELECT alvo.id_externo,
       alvo.recebido_em,
       (SELECT count(*)
          FROM webhook_entrada f
         WHERE f.recebido_em <= alvo.recebido_em
           AND (f.processado_em IS NULL OR f.processado_em > alvo.recebido_em)
           AND f.esgotado_em IS NULL)                       AS fila_no_instante,
       (SELECT max(f.processado_em - f.recebido_em)
          FROM webhook_entrada f
         WHERE f.recebido_em BETWEEN alvo.recebido_em - interval '15 minutes'
                                 AND alvo.recebido_em + interval '15 minutes') AS pior_atraso_na_janela
  FROM webhook_entrada alvo
 WHERE alvo.id_externo IN ('<entrada-1>', '<entrada-2>', '<entrada-3>');
ROLLBACK;
```

Nos logs do backend, para a mesma janela (±30 min de cada `recebido_em`):

```bash
docker logs --since "2026-09-26T09:00:00-03:00" --until "2026-09-26T10:00:00-03:00" <container-backend> 2>&1 \
  | grep -E "ALERTA_JOB_AGENDADO_FALHOU|Connection is not available|refused|HikariPool|Midia do evento|MIDIA_NAO_RECEBIDA|Disjuntor aberto"
```

Linhas `Midia do evento <entrada> ainda indisponivel` a partir do primeiro minuto após `recebido_em`
provam que o CRM tentou cedo. Erros de conexão/Hikari na janela, antes da primeira dessas linhas,
provam atraso nosso por bloqueio do agendador.

## 5. Conclusão e propostas (não implementadas)

**Conclusão com o que é verificável sem produção:** o pipeline não tem atraso sistemático por
desenho (intervalo de 1 s, FIFO, mesmo código para todo tipo). A hipótese de 31 minutos entre a
bolha na tela e a busca da mídia é incompatível com o código para a mesma entrada. O mais provável,
**a confirmar com 4.1**, é que o CRM tenha tentado no primeiro segundo e recebido 410 em todas as
~8 tentativas. Se for isso, é limitação do provedor e o Bloco 1 (processar mais rápido) não
recuperaria esses arquivos. O Bloco 1 só se justifica se 4.1/4.4 mostrarem `atraso_no_crm` bem acima
de ~11 min, ou a primeira linha "ainda indisponivel" minutos depois de `recebido_em`.

Propostas separadas, para decisão (nenhuma foi implementada):

- **A — diagnóstico:** separar no log e no `ultimo_erro` qual chamada falhou (`resolvedor` ou
  `download`) e o host da URL de download (sem query string, que pode conter token). Hoje as duas
  saem com o mesmo texto "resolvedor de midia … respondeu HTTP 410". Sem isso, não dá para saber se
  a Uzapi não tem mais o arquivo ou se a URL que ela devolveu já estava expirada.
- **B — 410 como terminal:** HTTP 410 significa "Gone" (permanente). Tratá-lo como fim imediato
  (registrar "sem arquivo" na primeira resposta 410) evitaria ~7 chamadas inúteis e faria a mensagem
  aparecer para o atendente em ~1 s em vez de ~10,6 min. Não recupera o arquivo e não é retry
  pós-410 — é o oposto. Exige confirmar antes que a Uzapi nunca devolve 410 transitório (o docs/38
  registra que ela já devolveu 404 para arquivo "ainda não disponível").
- **C — isolar o job de entrada:** o drenador de webhook divide `max(2, núcleos)` threads com ~15
  jobs. Se 4.4 mostrar atraso por bloqueio do agendador, dar a ele um executor próprio (bulkhead,
  como já existe para WebSocket e outbox), para que um job lento ou um banco degradado não segure a
  busca de mídia.
- **Observação:** os breakers `canal-uzapi-autotic*` não têm configuração explícita em
  `application.yml` e usam os padrões do Resilience4j (janela e mínimo de 100 chamadas); os da Meta
  usam `CANAL_CB_*`. Improvável que abram com o volume da FMNA, mas é uma assimetria.
