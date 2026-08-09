# Prompt E09b — Saúde crítica e alerta de indisponibilidade

> Etapa curta: ~meio dia. **O deploy de homologação já existe** — rode esta etapa contra ele.
> Pré-requisito: E14b concluída (os scripts operacionais existem e o RLS foi verificado).

---

## Nota de calibração pós-deploy

A stack de homologação está no ar no Dokploy com sete serviços. Isso muda duas coisas neste prompt:

- **O watchdog tem alvo real.** `/health/critical` deve ser exercitado contra o ambiente hospedado, não contra `localhost`. Lembre que o CI já foi enganado uma vez por um Redis que existia só na máquina de quem testava.
- **O watchdog vai em outro provedor.** O VPS da homologação é o monitorado; um Uptime Kuma nele cai junto. Um VPS mínimo de outro fornecedor, ou serviço gratuito de uptime, resolve — `docs/10` §1.3.
- **`ALERTA_WEBHOOK` já existe como variável opcional na stack** e hoje está vazia. É por ela que o alerta sai; não invente outro caminho de configuração.

---

**Etapa E09b — `/health/critical` e watchdog externo.**

## Por que só antes do deploy

Diferente da auditoria, isto não fica mais caro com o tempo — é um endpoint e um monitor externo, ambos aditivos. E, mais importante: **um watchdog só tem sentido quando existe algo hospedado para vigiar.** Construir antes do primeiro deploy é escrever código que ninguém consegue exercitar de verdade.

## 1. `/health/critical`

Distinto do `liveness` e do `readiness` que já existem. Valida especificamente o **caminho de mensagens**:

- Banco acessível pelo `chatDataSource` (não pelo geral)
- Fila conectada e consumindo
- Credencial de canal ativa e autenticada no provedor
- WebSocket aceitando conexão
- Partição de `mensagem` do mês corrente e do próximo existindo
- Outbox sem acúmulo anormal de pendentes

Responde com o detalhe de qual componente falhou — um `DOWN` genérico obriga alguém a investigar do zero às 9h da manhã.

**Não inclua no `liveness`.** Já foi decidido na E00 e vale repetir: uma oscilação do Postgres reiniciando o container é o oposto da regra de estabilidade.

## 2. Watchdog externo

**Fora do deploy do CRM, e de preferência em outro provedor** (ver `docs/10` §1.3). Um monitor que morre junto com o monitorado não é um monitor.

- *Polling* em `/health/critical` a cada 30 s
- Duas falhas consecutivas ⇒ alerta
- Alerta vai para o grupo do cliente **e** para o canal interno da Synapse

Uptime Kuma num VPS mínimo resolve. Não construa um monitor próprio — é exatamente o tipo de coisa que o requisito interno manda não reinventar.

## 3. Distinguir degradação de queda

O requisito é avisar o cliente antes de ele reclamar. Mas avisar demais treina o cliente a ignorar o alerta.

Separe em dois níveis:

| Nível | Exemplo | Destino |
|---|---|---|
| **Crítico** | Aba Atendimentos fora, WhatsApp desconectado, banco inacessível | Grupo do cliente + Synapse |
| **Degradado** | Outbox acumulando, relatório lento, breaker aberto | Só Synapse |

O cliente precisa saber que não consegue atender. Não precisa saber que o relatório está lento — isso é problema seu, e você resolve antes de ele notar.

## 4. Janela de horário

A regra de precedência é 08:00–18:30. Fora dela, alerta crítico continua indo para a Synapse, mas o grupo do cliente pode esperar o horário comercial — acordar o cliente às 3h para um problema que ele só sentiria às 8h não ajuda ninguém.

Isso é configuração (`configuracao_automacao`), não constante em código.

## 5. Testes

- Derrubar o Postgres faz `/health/critical` falhar identificando o componente
- Derrubar a fila idem
- Credencial de canal inválida idem
- `liveness` **não** falha quando só o banco caiu
- Watchdog alerta após duas falhas consecutivas, não na primeira
- Alerta degradado não chega ao grupo do cliente
- Fora do horário, crítico não vai ao cliente

## Definição de pronto

- [ ] `/health/critical` cobrindo os seis pontos, com detalhe do componente
- [ ] Watchdog rodando em outro host e alertando
- [ ] Dois níveis de severidade com destinos distintos
- [ ] Janela de horário configurável
- [ ] Teste de queda real, não simulada por mock
- [ ] CI verde

Commit: `feat: saúde crítica e alerta de indisponibilidade`.

Ao terminar: derrube o ambiente de homologação de propósito e confirme que o alerta chega. Um alerta que nunca disparou não é um alerta — é a sexta vez que este projeto encontra esse padrão.
