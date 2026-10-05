# Prompt E221 — reduzir o custo de CPU dos healthchecks do RabbitMQ na VPS

Número E221 provisório: confirmar que é o próximo livre em `docs/prompts/` antes de salvar. Mudança de infraestrutura (compose/Dokploy). Não altera código de aplicação. O deploy em produção é feito pelo responsável, de madrugada.

> Confirmação feita na execução: o maior número em `docs/prompts/` era E219 e `E221` não aparecia em nenhum arquivo do repositório (o E220 existe no código e nos docs, nas campanhas de template, mas sem arquivo de prompt).

## Leitura obrigatória antes de qualquer coisa

Ler `AGENTS.md` e `docs/13-estado-do-projeto.md`. Depois o runbook (`docs/18`), os documentos de infraestrutura/deploy e qualquer doc do repo sobre Dokploy, Swarm e RabbitMQ. Localizar onde os compose/templates dos três stacks estão versionados (`erp-matriz-hml`, `fmnaprod-uzapi`, `disbrel-dbl`) e se o RabbitMQ é usado como relay STOMP do tempo real (E193/E203).

## O que o agente consegue e não consegue verificar

O agente não tem acesso à VPS. Ele só lê o repositório. Tudo o que depende de estado em produção (imagem em execução, plugins habilitados, filas duráveis, configuração real do healthcheck no Swarm, medições de CPU) vem da seção "Dados da VPS" abaixo, preenchida pelo responsável. Se um dado dessa seção estiver vazio, marcar o item como não verificado no relatório e não supor. Nenhum comando de produção deve ser executado pelo agente, e o deploy e a medição são do responsável.

## Origem e evidência (medida em produção, 05/10, fora do expediente)

VPS Hostinger, 4 vCPU, hostname `matriz`, Docker Swarm + Dokploy, tudo em produção.

- `vmstat` a cada ~15 s, 754 amostras (~3 h): uso médio da VM (us+sy) **27,0%** e steal médio **23,0%** (máx. 79%). A Hostinger informou que steal alto indica a VPS limitada por uso sustentado. Existe um chamado aberto com ela; não é escopo deste prompt.
- Consumo médio por container, de `docker stats` (valores são piso, o coletor só guarda os 4 maiores por amostra; 100% = 1 núcleo): Traefik 29,7%, Postgres do HML 26,9%, Postgres do FMNA 18,4%, RabbitMQ FMNA 10,8%, RabbitMQ HML 10,7%, RabbitMQ Disbrel 10,4%.
- Os três RabbitMQ têm consumo quase idêntico mesmo com cargas muito diferentes. Um RabbitMQ parado gasta perto de 1%. O custo fixo e periódico é o healthcheck `rabbitmq-diagnostics -q check_running` a cada 15 s: cada execução sobe uma VM Erlang inteira (`beam.smp` observado com 65% a 86% de um núcleo e 1,0 a 1,2 s de CPU acumulada). São 12 execuções por minuto no total, ~0,32 núcleo, cerca de 30% do uso médio da VPS.
- `dockerd` + `containerd` gastam só ~5 s de CPU por minuto (~0,08 núcleo), então o custo de disparar healthchecks em si é pequeno. O problema é o que o RabbitMQ executa.

Inventário dos healthchecks (medido com `docker inspect`):

| Intervalo | Containers |
|---|---|
| 10 s | frontend (wget), backend (curl `/health/live`), postgres (`pg_isready`), minio (`mc ready`), redis (`redis-cli ping`), nos três stacks (15 containers) |
| 15 s | n8n x3 (`node -e fetch healthz`), rabbitmq x3 (`rabbitmq-diagnostics -q check_running`) |
| 30 s | dokploy |

## Dados da VPS (preenchidos pelo responsável antes de enviar ao agente)

**A. Healthcheck atual do RabbitMQ em cada serviço do Swarm** (os três stacks estão idênticos, medido em 05/10): `Test: CMD rabbitmq-diagnostics -q check_running`; Interval 15 s, Timeout 10 s, StartPeriod 45 s, Retries 5. Detecção de falha atual: 15 s x 5 = ~75 s.

**B. Imagem do RabbitMQ e se há bash/nc nela:** `rabbitmq:3-management-alpine@sha256:606d8c0d6b3c18d1da9afc53bc7cdb2a8d5486df91b5a9830e9e07626c9ae281` (nos três). `/bin/bash` e `/usr/bin/nc` presentes nos três.

**C. Plugins habilitados:** `[E] rabbitmq_management`, `[E] rabbitmq_prometheus`; `[e] rabbitmq_federation, rabbitmq_management_agent, rabbitmq_web_dispatch` (implícitos). Nenhum plugin STOMP nos três brokers.

**D. Filas em cada RabbitMQ:** `rabbitmqctl list_queues` no vhost `/` não retornou nenhuma fila nos três brokers. `rabbitmqctl list_vhosts`: só existe o vhost `/` nos três. `rabbitmqctl list_connections`: nenhuma conexão ativa nos três no momento da coleta (05/10, fora do expediente). Ou seja, os três RabbitMQ estão, neste instante, sem filas e sem clientes.

## Bloco 0 — auditoria (sem alterar nada)

Para cada um dos três stacks, responder com arquivo e linha:

1. Onde o healthcheck do RabbitMQ está definido (compose do repo, template do Dokploy ou configuração só no painel) e quais valores têm `interval`, `timeout`, `retries`, `start_period`.
2. Qual imagem do RabbitMQ o repo referencia. Se a imagem tem bash/nc (para avaliar a alternativa leve abaixo) não dá para saber pelo repo: usar "Dados da VPS"; sem o dado, marcar como não verificado.
3. Quem depende da saúde do RabbitMQ (`depends_on`, `condition: service_healthy`, políticas de restart, `update_config` do Swarm). Calcular o tempo de detecção de falha atual (`interval x retries`) e o novo com 60 s.
4. Os plugins habilitados (item C) não incluem STOMP, então o RabbitMQ não é o relay do tempo real. Confirmar no código qual broker o WebSocket usa e que recriar o RabbitMQ não derruba as conexões em tempo real.
5. Como o backend se recupera quando o broker reinicia (reconexão automática do cliente AMQP, retentativa da outbox), pelo código.
6. Quem usa o RabbitMQ. Os três brokers não têm filas no vhost `/` (item D). Procurar no repo (Spring AMQP e `spring.rabbitmq`, compose, variáveis `RABBITMQ_*`, nós RabbitMQ do n8n, outbox, `canal.mensagem.enviar`) quem publica e consome, em qual vhost, e se há filas efêmeras. Relatar com clareza se algum serviço depende do RabbitMQ em runtime ou se ele pode estar sem uso. Se nada usar, apresentar ao responsável a opção de remover o serviço do compose (economia de pelo menos ~0,32 núcleo e da memória dos três brokers), com riscos e como reverter, mas sem aplicar: a decisão é do responsável.

## Bloco 1 — mudança proposta

**Obrigatória (risco mínimo):** nos três stacks, reduzir a frequência do healthcheck do RabbitMQ. Hoje: `interval` 15 s, `timeout` 10 s, `start_period` 45 s, `retries` 5 (detecção de falha ~75 s). Trocar só o `interval` para 60 s mantendo `retries` 5 levaria a ~5 min de detecção, o que é longo. Calcular e propor combinações, por exemplo `interval` 60 s com `retries` 3 (~3 min, corta ~75% das execuções, ~0,24 núcleo estimado) e `interval` 30 s com `retries` 3 (~90 s, corta ~50%), com recomendação e trade-off. A escolha é do responsável. Manter a semântica do comando atual.

**Opcional, só como relatório (não aplicar sem validar):** avaliar um probe mais leve que não suba Erlang, por exemplo checagem TCP da porta 5672 com o `nc`/`bash` da própria imagem (ambos presentes em `rabbitmq:3-management-alpine`), ou o endpoint do plugin `rabbitmq_prometheus` (habilitado). Reportar o trade-off (porta aberta não prova que o nó está saudável) e a economia esperada.

Não alterar os healthchecks de 10 s nem os do n8n neste prompt: o ganho medido não justifica o risco agora.

## Bloco 2 — plano de rollout (documentar, não executar)

- Janela de madrugada (00h–05h no fuso do responsável, UTC-4, que corresponde a 04h–09h UTC), um stack por vez, começando pelo que o responsável indicar.
- Alterar o `interval` no Swarm recria a task: o RabbitMQ reinicia. Não existe como mudar o healthcheck sem reinício.
- Antes de cada stack: conferir fila da outbox pendente (baixa ou zero), nenhuma campanha ou envio em massa em andamento, e que o front reconecta o STOMP sozinho.
- Depois de cada stack: confirmar `docker service ps` saudável, backend consumindo, mensagens fluindo e reconexão do tempo real.
- Plano de volta atrás por stack (valor anterior do compose).

## Bloco 3 — medição antes e depois (o responsável roda na VPS)

Entregar no relatório os comandos exatos e o critério de sucesso. Linha de base: 27,0% de uso, 23,0% de steal, RabbitMQ 10,4% a 10,8% cada. Esperado depois: RabbitMQ perto de 2% a 3% cada e uso médio ~5 pontos percentuais menor. Comandos do coletor (já rodando em `/root/coleta-cpu.log`):

```bash
awk '$1 ~ /^[0-9]+$/ && NF>=17 {n++; u+=$13+$14; s+=$17} END{printf "n=%d uso_medio_us+sy=%.1f st_medio=%.1f\n", n, u/n, s/n}' /root/coleta-cpu.log
awk '$1 ~ /^[0-9]+$/ && NF>=17 {n++} NF==2 && $2 ~ /%$/ {v=$2; gsub("%","",v); s[$1]+=v} END{for(k in s) printf "%.1f%% de 1 núcleo  %s\n", s[k]/n, substr(k,1,50)}' /root/coleta-cpu.log | sort -rn | head -10
```

A comparação deve usar janelas de mesmo horário (fora do expediente) antes e depois, e reiniciar o coletor com log novo na hora da mudança.

## Testes e verificação

- Validar a sintaxe dos compose alterados (`docker compose config` ou equivalente do projeto).
- CI verde. Mudança só de compose não gera imagem nova; não citar tag sem confirmar o job `imagens` quando houver imagem envolvida.
- Registrar no relatório o que não foi possível testar fora de produção.

## Não-objetivos

- Não alterar código de aplicação, Traefik, Postgres, healthchecks de 10 s ou n8n.
- Não fazer deploy nem reiniciar nada em produção; quem faz é o responsável, na janela combinada.
- Não mexer em nada da Hostinger (limite de CPU e migração são tratados à parte).

## Pendências humanas (não bloqueiam o prompt)

- Escolher a janela e a ordem dos stacks.
- Verificar nos workflows do n8n (não estão no repo) se algum usa nó RabbitMQ, e rodar o contador de conexões abertas do Prometheus nos brokers (desde o boot) para confirmar se algum dia foram usados.
- Depois do rollout, rodar a medição e decidir sobre o probe leve e sobre os próximos consumidores (Traefik ~0,30 núcleo e Postgres do HML ~0,27 núcleo, em investigação separada).

## Relatório final exigido

1. Confirmação de leitura de `AGENTS.md` e `docs/13-estado-do-projeto.md`.
2. Tabela do Bloco 0 com arquivo/linha por stack e as dependências de saúde encontradas.
3. Diff dos compose, valores finais de `interval`/`timeout`/`retries`/`start_period` e o tempo de detecção calculado.
4. Plano de rollout, de volta atrás e comandos de medição.
5. Atualização do runbook (`docs/18`).
6. O que ficou sem prova.
