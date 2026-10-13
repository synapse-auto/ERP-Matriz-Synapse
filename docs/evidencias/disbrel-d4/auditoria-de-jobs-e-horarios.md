# Disbrel D4 — auditoria de jobs e horários (Etapa 1 do E229 PR D)

Objetivo: antes de tocar a janela de aviso ao cliente, listar **tudo que roda por relógio** e dizer o que cai no
sábado **08:00–12:00** (horário da instância). Rótulos: **verificado** (arquivo:linha), **inferido**, **não verificado**.

## Fuso em que cada coisa roda
- Janela de aviso ao cliente: `synapse.tenant.timezone` = `${SYNAPSE_TIMEZONE:America/Sao_Paulo}` — **verificado**
  (`application.yml:179`; usado em `HorarioComercialConfiguravel.java`, construtor).
- `@Scheduled(cron=…)` sem `zone`: usa o fuso padrão da JVM — **inferido** (comportamento do Spring). O container do
  backend recebe `TZ: ${SYNAPSE_TIMEZONE:?obrigatoria}` — **verificado** (`docker/dokploy-stack.yml:169`), logo os
  crons rodam no horário local da instância (a instância já exige a variável; nada novo aqui).

## Jobs por relógio no backend
| Job | Gatilho | Onde | Cai no sáb 08–12? | Observação |
|---|---|---|---|---|
| Finalização automática de atendimentos inativos | `fixedDelay` 5 min (`…intervalo-ms:300000`) | `AgendadorDeFinalizacaoDeAtendimentosInativos.java:50` | **Sim (contínuo)** | Só age se `atendimento.finalizar_inativos.habilitado`; lote limitado; não é por janela. |
| Finalização em massa (tick de fila e de avisos) | `fixedDelay` 1 s e 2 s | `AgendadorDeFinalizacaoEmMassa.java:33,42` | Sim (contínuo) | Só trabalha quando um gestor cria um pedido (**inferido** do nome/uso do job); `horaInicio/horaFim` do pedido são filtro do usuário, não agendamento (`PeriodoDeFinalizacao.java`). |
| Mensagens programadas | `fixedDelay` 1 s | `AgendadorDeMensagensProgramadas.java:25` | Sim (contínuo) | Envia quando `dataEnvio` vence, qualquer dia/hora. |
| Campanhas de template | `fixedDelay` 10 s | `AgendadorDeCampanhas.java:25` | Só se a campanha permitir | Cada campanha tem `JanelaDeEnvio` com dias e horário próprios; padrão 09:00–18:00 em dias úteis (`MontadorDeCampanha.java:27`, `JanelaDeEnvio.java`), então **por padrão não envia no sábado**. |
| Monitor de saúde crítica (usa a janela do cliente) | `fixedDelay` 30 s (`SAUDE_INTERVALO_MONITORAMENTO`) | `AgendadorDaSaudeCritica.java:24`, `application.yml:400` | Sim (contínuo) | É o único consumidor de `permiteAvisarCliente` (`MonitorarSaudeCriticaUseCase.java:35`). |
| Publicadores (outbox, webhook, repasse, avaliação, resumo IA, estado) | `fixedDelay` 250 ms–1 s | `PublicadorDaOutbox.java:38`, `ProcessadorDeWebhookEntrada.java:35`, `PublicadorDeRepasseWebhook.java:22`, `PublicadorDeAvaliacao.java:39`, `PublicadorDeSolicitacaoResumoIa.java:18`, `PublicadorEventoEstadoOutbox.java:18` | Sim (contínuo) | Caminho de mensagem; não dependem de horário. |
| Precificação Meta | `fixedDelay` 30 s | `ProcessadorDePrecificacaoMeta.java:36` | Sim (contínuo) | |
| Presença automática | `fixedDelay` 15 s | `PresencaAutomatica.java:144` | Sim (contínuo) | |
| Métricas de pool / WebSocket | `fixedDelay` 1 min (e amostra 1 s) | `ObservabilidadeDosPoolsDeConexao.java:97,112`, `ObservabilidadeDeSessoesWebSocket.java:41` | Sim (contínuo) | Somente métrica. |
| Alarmes de "esgotadas" (outbox, webhook, repasse) | **cron** `0 */15 * * * *` | `PublicadorDaOutbox.java:48`, `ProcessadorDeWebhookEntrada.java:40`, `PublicadorDeRepasseWebhook.java:27` | **Sim: 08:00, 08:15 … 11:45** | Uma contagem por job a cada 15 min (preexistente, não alterada aqui; o plano desse SQL **não foi verificado**); só loga `ERROR` se houver esgotadas. |
| Manutenção de partição de mensagem (mensal) | **cron** `0 0 3 1 * *` | `ManutencaoParticaoMensagem.java:72`, `application.yml:470` | Não (03:00 do dia 1) | |
| Alerta de partição DEFAULT (diário) | **cron** `0 15 7 * * *` | `ManutencaoParticaoMensagem.java:117`, `application.yml:474` | Não (07:15, 45 min antes da janela) | |

## Fora do backend
- **Backup horário**: Dokploy Server Job `0 * * * *`, `America/Sao_Paulo` — **verificado** (`docker/backup/README.md:57`).
  Roda às 08:00, 09:00, 10:00 e 11:00 do sábado; é `pg_dump` (carga no Postgres), operação da VPS, fora do app.

## Conclusão para a Etapa 2
- Nada **agendado por relógio no app** muda de comportamento por esta PR. Só o consumidor `permiteAvisarCliente`
  (monitor de saúde, 30 s) lê a janela, e ela só decide **se um alerta crítico também vai ao grupo do cliente**;
  a Synapse continua recebendo todos os alertas em qualquer horário.
- Nenhum `@Scheduled` foi adicionado ou alterado, nenhum intervalo mudou.
- Dentro de sábado 08–12 rodam, além dos jobs contínuos, os alarmes de 15 min e o backup horário. Esta PR não altera
  nenhum deles; se a Disbrel quiser manutenção pesada fora dessa janela, o backup é o item a reagendar (decisão de operação).
- **Não verificado**: carga real dos jobs na VPS durante o sábado (não há acesso a produção).

## SQL novo (periódico)
`HorarioComercialConfiguravel` agora faz, por tick de 30 s do monitor, até **4 buscas por chave primária** em
`configuracao_automacao` (antes: 2): a janela padrão (2) + a chave do dia de hoje + a de amanhã. Tabela global pequena,
sem RLS, chave é `PRIMARY KEY` (V7) — Index Scan por construção. Não é SQL por requisição nem por mensagem.
**Sem EXPLAIN real** (sem acesso à VPS); a prova de custo é só essa leitura do esquema (**inferido**).
