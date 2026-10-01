# 50 — Envio único da Automação e atendimento ativo

Complementa o diagnóstico `docs/49-diagnostico-automacao-duplicidade.md` (PR #242, ainda fora da
`main` quando este foi escrito). **Separe as duas coisas:**

- **Proteção implementada (esta etapa, no CRM):** contratos que tornam possível ao n8n enviar no
  máximo uma vez por evento e achar o atendimento certo sem paginação.
- **Causa histórica: não comprovada.** Nenhum caso real foi reconstruído (sem acesso à produção nem
  ao workflow). As hipóteses H1–H4 do diagnóstico continuam hipóteses.

**A duplicidade só fica resolvida quando o workflow do n8n adotar o protocolo abaixo.** O workflow
não está no repositório e não foi acessado nesta etapa; a seção final lista o que precisa mudar nele.

## 1. Repasse CRM → n8n (implementado)

Cada entrega do webhook cru leva, além de corpo e `X-Hub-Signature-256` intactos:

| Cabeçalho | Valor |
|---|---|
| `X-Synapse-Evento-Id` | id da linha da outbox: **o mesmo em todas as retentativas** do evento (e em reentregas do mesmo POST do provedor) |
| `X-Synapse-Tentativa` | 1, 2, 3… |

As retentativas continuam: sem elas, um n8n fora do ar perderia eventos. Timeout de **leitura**
(o n8n recebeu e não respondeu em `AUTOMACAO_WEBHOOK_TIMEOUT`) agora é registrado como **entrega
incerta** no log e em `outbox_evento.ultimo_erro`. O evento é reentregue com o mesmo
`X-Synapse-Evento-Id`, e é esse id que o consumidor usa para deduplicar.

## 2. Qual atendimento usar (implementado)

| Contrato | Quando |
|---|---|
| `GET /internal/v1/mensagens-recebidas/atendimento?idExterno=<wamid da ENTRADA>` | resposta a uma mensagem do cliente (caso normal) |
| `GET /internal/v1/leads/{leadId}/atendimento-ativo` | fluxo que não nasce de uma mensagem recebida |

- Pela mensagem recebida: devolve o atendimento em que o CRM gravou **aquela** mensagem (vínculo do
  mesmo commit). Nunca outro lead, nunca id antigo. `ativo=false` ⇒ atendimento finalizado: **não
  responder**. `404` + `Retry-After: 2` ⇒ o CRM ainda não processou a entrada (corrida com o
  repasse): consultar de novo **no máximo 5 vezes** e então parar e alertar, sem responder.
- Pelo lead: `EM_IA` ou `EM_ATENDIMENTO` mais recente, sem depender de página. `404` diferencia
  "Lead inexistente" de "Lead sem atendimento aberto".
- `/em-andamento` continua existindo para listagens; **não** serve para achar um lead (é paginado).
  A busca do EV-05 (`porLeadEmAtendimento`, só `EM_ATENDIMENTO`) não mudou.

## 3. Envio único (implementado no CRM; depende do n8n)

Dois caminhos de envio, cada um com sua proteção:

**a) O CRM envia (`POST /atendimentos/{id}/responder`).** Já exigia `Idempotency-Key`, reservada em
transação: a mesma chave não gera segundo envio; a mesma chave com outro texto (IA regenerou) dá
`409`. A proteção só vale se a chave for **estável**: use `<X-Synapse-Evento-Id>:<passo>`. Chave
aleatória por execução anula a proteção.

**b) O n8n envia direto ao provedor.** Protocolo obrigatório:

```
1. POST /internal/v1/atendimentos/{id}/envios-automacao/reservas   {"chave": "<X-Synapse-Evento-Id>:<passo>"}
     201 novaReserva=true   -> pode enviar
     200 novaReserva=false  -> NAO enviar (reexecucao/retry/entrega simultanea; ver estado e wamidSaida)
     409                    -> atendimento finalizado ou chave de outro atendimento: nao enviar
2. envia ao provedor (sem retry automatico no no de envio)
3. POST /internal/v1/atendimentos/{id}/mensagens-enviadas   {..., "wamid": "<saida>", "chaveDeEnvio": "<mesma chave>"}
```

A reserva é atômica (PK em `envio_automacao_reserva`, V84): com entregas simultâneas do mesmo evento,
exatamente uma recebe `201`.

### Resultado incerto do provedor

Se o passo 2 falhar **sem resposta** (timeout, queda do n8n depois de chamar o provedor), o envio pode
ter acontecido. **Não reenviar.** A reserva fica `RESERVADO`, e uma nova execução recebe
`novaReserva=false`. Para conciliar:

1. `GET /internal/v1/envios-automacao/pendentes?reservadosAntesDe=<agora − 5 min>` lista reservas sem
   resultado (até 100, mais antigas primeiro).
2. Para cada uma, confira no provedor ou na execução do n8n se a mensagem saiu.
3. Saiu ⇒ `POST /mensagens-enviadas` com o `wamid` real e a `chaveDeEnvio`: registra sem reenviar
   (repetir é idempotente). Não saiu ⇒ decisão humana; reenviar exige **outra** chave, conscientemente.

O mesmo passo 3 é a recuperação de "provedor aceitou, registro no CRM falhou".

## 4. O que ainda precisa mudar no n8n (pendente)

Sem acesso ao workflow, os nós são descritos pela função, não pelo nome:

1. **Gatilho Webhook que recebe o repasse do CRM:** confirmar ao CRM só depois de uma aceitação
   durável e sem esperar IA e envio. Opção recomendada: o primeiro nó grava `X-Synapse-Evento-Id`
   numa tabela persistida com chave única (Data Table/Postgres); em seguida "Respond to Webhook" 200;
   o restante do fluxo segue depois. Chave já existente ⇒ responder 200 e encerrar. **Confirmar no
   n8n instalado** que a resposta só sai depois da gravação (não verificado aqui). Não usar
   "Respond immediately" sem persistência: perderia eventos numa queda.
2. **Busca de lead/atendimento:** trocar pelo §2 (pela mensagem recebida), com repetição limitada
   no `404` e parada quando `ativo=false`. Identificar no workflow qual chamada existe hoje: o
   repositório não mostra.
3. **Todo nó que envia ao cliente** (texto, mídia, template, botões/lista, e qualquer sub-workflow
   chamado) precisa de: nó de reserva antes; IF `novaReserva`; envio; `mensagens-enviadas` com
   `chaveDeEnvio`. Um passo por mensagem: fluxos que mandam 2+ mensagens usam `:<passo>` distintos.
4. **`/responder`:** `Idempotency-Key` = `<X-Synapse-Evento-Id>:<passo>`, nunca aleatória.
5. **Retry automático** do nó que chama o provedor: desligar. Timeout ou erro sem resposta ⇒ deixar
   a reserva pendente para conciliação.

Até esses itens entrarem, a duplicidade **não** está resolvida: o CRM oferece a proteção, mas é o
workflow que decide chamar o provedor.

## Mensagens proativas (E219)

A reserva desta página deduplica **reentregas do mesmo evento** num atendimento. Mensagens que a
Automação manda por iniciativa própria (follow-up, fidelização, festiva, aniversário…) usam a reserva
proativa por lead, que também aplica cooldown, teto diário e liga/desliga:
[`51-origem-e-frequencia-das-mensagens-automaticas.md`](./51-origem-e-frequencia-das-mensagens-automaticas.md).
As duas fecham pelo mesmo `chaveDeEnvio` de `/mensagens-enviadas`.

## Variáveis

Nenhuma variável nova. `AUTOMACAO_WEBHOOK_TIMEOUT` (existente, padrão 5s) continua valendo.
