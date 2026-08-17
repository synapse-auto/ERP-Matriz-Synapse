# Prompt E22 — Saúde crítica, alerta de indisponibilidade e limpeza do `docs/04`

> Leia `AGENTS.md`. Absorve a E09b, que nunca rodou, mais a decisão sobre contratos fictícios tomada depois da E21b.
> Commite e faça push por bloco. Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## Bloco 1 — `docs/04` só descreve o que existe

A varredura da E21b encontrou, além dos contratos já corrigidos, mais um conjunto apresentado como "amostra representativa" e nunca implementado: importação/exportação CSV, Campanhas, troca de credencial de canal, `/health/critical` e `/api/v1/automacao/status`.

**Decisão: remova.** A expressão "amostra representativa" foi lida como "existe" seis vezes seguidas neste projeto. Ela protegeu o autor, não o leitor.

Aplique ao `docs/04` a mesma regra que o `docs/05` recebeu, escrita no topo do arquivo:

> Um endpoint só é documentado aqui com **evidência nomeada**: o controller que o implementa e o teste que o cobre. Contrato planejado não mora neste documento.

O que for projeto futuro vai para `docs/14-pendencias-de-funcionalidade.md`, que é explicitamente a lista do que **não** existe. Um documento por estado de verdade.

Percorra o arquivo inteiro contra o código — não só os itens já listados. Espere encontrar mais.

## Bloco 2 — `/health/critical`

Distinto do `liveness` e do `readiness` que já existem. Valida especificamente o **caminho de mensagens**:

- banco acessível pelo `chatDataSource`, não pelo geral
- fila conectada e consumindo
- credencial de canal ativa e autenticada no provedor
- WebSocket aceitando conexão
- partição de `mensagem` do mês corrente e do próximo existindo
- outbox sem acúmulo anormal de pendentes

Responde com **qual componente falhou**. Um `DOWN` genérico obriga alguém a investigar do zero às 9h da manhã, que é exatamente quando ninguém tem tempo.

**Não inclua no `liveness`.** Decidido na E00 e vale repetir: uma oscilação do Postgres reiniciando o container é o oposto da regra de precedência. O healthcheck do orquestrador continua apontando para `liveness`.

Este endpoint agora entra no `docs/04` — com controller e teste, conforme a regra do Bloco 1.

## Bloco 3 — Watchdog externo

**Fora do deploy do CRM, e de preferência em outro provedor** (`docs/10` §1.3). Um monitor que morre junto com o monitorado não é monitor.

- polling em `/health/critical` a cada 30 s
- **duas falhas consecutivas** disparam alerta, não a primeira
- Uptime Kuma num VPS mínimo resolve. **Não construa monitor próprio** — é exatamente o tipo de coisa que o requisito interno manda não reinventar

O alerta sai pelo `ALERTA_WEBHOOK`, que já existe como variável opcional na stack e hoje está vazio. Não invente outro caminho de configuração.

Entregue como documento de operação em `docs/`, com o passo a passo do que precisa ser configurado fora do repositório — não como código.

## Bloco 4 — Dois níveis de severidade

O requisito é avisar o cliente antes de ele reclamar. Mas avisar demais treina o cliente a ignorar o alerta.

| Nível | Exemplo | Destino |
|---|---|---|
| **Crítico** | Atendimentos fora, WhatsApp desconectado, banco inacessível | grupo do cliente + Synapse |
| **Degradado** | Outbox acumulando, breaker aberto, consulta lenta | só Synapse |

O cliente precisa saber que não consegue atender. Não precisa saber que uma consulta está lenta — isso é problema seu, e você resolve antes de ele notar.

**Janela de horário:** a regra de precedência é 08:00–18:30. Fora dela, crítico continua indo para a Synapse, mas o grupo do cliente pode esperar o horário comercial. Acordar o cliente às 3h por um problema que ele só sentiria às 8h não ajuda ninguém.

Isso é configuração em `configuracao_automacao`, não constante em código.

## Testes

- derrubar o Postgres faz `/health/critical` falhar **identificando o componente**
- derrubar a fila, idem
- credencial de canal inválida, idem
- **`liveness` não falha quando só o banco caiu** — este é o que protege contra o loop de restart
- alerta degradado não chega ao grupo do cliente
- fora do horário, crítico não vai ao cliente

## Definição de pronto

- [ ] `docs/04` com a regra de evidência nomeada no topo; contratos inexistentes removidos e migrados para o `docs/14`
- [ ] Varredura completa do `docs/04`, não só dos itens já listados — diga quantos removeu
- [ ] `/health/critical` cobrindo os seis pontos, nomeando o componente que falhou
- [ ] `liveness` intocado
- [ ] Documento de operação do watchdog
- [ ] Dois níveis de severidade com destinos distintos, saindo pelo `ALERTA_WEBHOOK`
- [ ] Janela de horário configurável, não hardcoded
- [ ] Testes de queda **real**, não simulada por mock
- [ ] CI verde com **número da run**

Commit por bloco: `docs: contratos com evidência nomeada`, `feat: saúde crítica do caminho de mensagens`, `docs: operação do watchdog externo`.

Ao terminar: **derrube o ambiente de homologação de propósito e confirme que o alerta chega.** Um alerta que nunca disparou não é alerta — e este projeto já encontrou onze vezes o padrão da proteção que existia e não protegia.
