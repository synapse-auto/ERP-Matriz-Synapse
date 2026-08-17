# 20. Roteiro de deploy — 17/08, apresentação ao cliente às 14h

Objetivo: E27 + E27b no ar, Dashboard ativa com dados, WhatsApp recebendo e enviando.

Decisão tomada pela equipe: **permanecer na mesma WABA**, com isolamento pelo filtro do CRM
(E27) e, se aplicável, filtro no n8n.

**Healthcheck verificado:** Swarm e Traefik apontam para `/health/liveness`, não
`/health/critical`. O fail-closed da E27 não derruba o backend nem o tira do balanceamento.

---

## Fase 1 — Banco, antes de qualquer deploy (10 min)

Rode as duas consultas de colisão do `prompt-E27b`. **Qualquer linha retornada: pare.**
Migration que aborta faz o Flyway falhar no boot e o backend não sobe.

```bash
PG=$(docker ps -q --filter name=_postgres.1)

docker exec $PG psql -U matriz_app -d matriz_hml -c \
  "SELECT id, canal_id, numero, identificador_externo, ativo FROM canal_credencial;"
```

`identificador_externo` nulo → gravar `1307417749115229` antes do deploy. Sem ele o webhook
recusa tudo (fail-closed).

## Fase 2 — Dokploy e deploy (10 min)

| Variável | Valor |
|---|---|
| `TELEFONE_DDI_PADRAO` | `55` |
| `WHATSAPP_NUMERO` | `1307417749115229` (conferir) |
| `SYNAPSE_IMAGE_TAG` | `c3206fc81b684c1b55d7a829211133d715c71f1c` ou `latest` |

Deploy. Verificação:

```bash
docker exec $PG psql -U matriz_app -d matriz_hml -c \
  "select version, description, success from flyway_schema_history order by installed_rank desc limit 5;"

curl -s -o /dev/null -w '%{http_code}\n' https://crm.187.77.47.30.sslip.io/health/critical
```

Esperado: V24, V25 e V26 com `success = t`, e `200` no `/health/critical`.

## Fase 3 — Dashboard (10 min)

```bash
docker exec $PG psql -U matriz_app -d matriz_hml -c "\d feature_flag"
docker exec $PG psql -U matriz_app -d matriz_hml -c \
  "update feature_flag set habilitada = true where chave = 'dashboard';"
```

**O seed decide a demonstração.** Flag ligada com banco vazio piora a tela em vez de melhorar.
Agora é seguro: a E27b confirmou que o seed grava telefone já com `55`, sem colidir com a V26.

```bash
docker exec -i $PG psql -U matriz_app -d matriz_hml < docker/provisionamento/seed-demonstracao.sql
```

## Fase 4 — Mensagens, somente após o deploy (10 min)

Reinscrever antes do deploy repete o incidente de 16/08: sem o filtro no ar, conversa real
volta a ser gravada.

```powershell
curl.exe -X POST "https://graph.facebook.com/v26.0/1574679126928884/subscribed_apps" -H "Authorization: Bearer TOKEN"
curl.exe -s "https://graph.facebook.com/v26.0/1574679126928884/subscribed_apps" -H "Authorization: Bearer TOKEN"
```

O `GET` deve mostrar `Estrutural-Synapse` e `f-bot`.

Teste ponta a ponta, **nos dois sentidos**:

1. Celular → número de teste: aparece em Atendimentos
2. Resposta pela tela: chega no celular

## Fase 5 — n8n

O CRM filtra **antes** de repassar: o n8n recebe apenas eventos do canal cadastrado. O filtro
no n8n só é requisito se houver webhook cadastrado direto na Meta, recebendo por fora do CRM.
Confirmar com o Dylan.

Se o n8n voltou de indisponibilidade, os repasses esgotados na outbox não são reenviados
sozinhos.

## O que esperar durante a apresentação

**Log com `WARN` de `phone_number_id` desconhecido** — é o filtro descartando conversa do
número oficial. Comportamento correto.

**Payload misto é descartado inteiro.** Se a Meta agrupar num mesmo POST uma mensagem do
número oficial e uma de teste, as duas se perdem, e o sintoma é "mandei e não chegou".
Probabilidade baixa; se ocorrer, reenviar em vez de diagnosticar na frente do cliente.

## Pendências que este roteiro não cobre

- Smoke RLS (Fase 3 do `docs/18`) — nunca executado
- Medição do que entrou durante o incidente de 16/08 (`webhook_entrada`, `outbox_evento`)
- E28: o tradutor Meta processa apenas `entry[0].changes[0]` e `messages[0]`; payload legítimo
  com múltiplas mensagens perde as demais, em silêncio
- Backup com restauração testada, watchdog externo, subdomínios reais
