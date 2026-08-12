# 15. Operação do watchdog externo

Runbook para vigiar o caminho de mensagens sem depender do mesmo host do CRM. O monitor recomendado é o Uptime Kuma em um VPS mínimo de **outro provedor**. Ele não entra em `docker/dokploy-stack.yml`: monitor e monitorado não podem compartilhar a mesma falha.

## 1. Contrato observado

Alvo público:

```text
https://<dominio-da-instancia>/health/critical
```

O endpoint responde:

| HTTP | `status` | Significado |
|---|---|---|
| `200` | `UP` | seis sinais normais |
| `200` | `DEGRADED` | operação ainda disponível, mas há acúmulo anormal na outbox |
| `503` | `DOWN` | ao menos um componente crítico falhou |

Cada item de `componentes` traz `nome`, `status`, `severidade` e `detalhe`, sem token ou corpo bruto do provedor. O endpoint não é liveness e nunca deve substituir `/health/liveness` no healthcheck do container.

## 2. Criar o monitor no Uptime Kuma

1. Hospede o Kuma fora do provedor e da rede do Dokploy monitorado.
2. Crie um monitor do tipo **HTTP(s)** com o nome `<tenant> · caminho de mensagens`.
3. Informe a URL pública de `/health/critical`; não use hostname da overlay Docker.
4. Configure **Heartbeat Interval = 30 segundos**.
5. Configure **Retries = 1** e **Heartbeat Retry Interval = 30 segundos**. No Kuma, zero retries notifica na primeira falha; uma tentativa adicional faz o estado mudar para DOWN apenas depois de duas falhas consecutivas.
6. Use timeout menor que o intervalo (10 segundos é o ponto inicial recomendado).
7. Considere apenas HTTP `2xx` como sucesso. Assim `DEGRADED` continua online — o backend já o envia só à Synapse — e `503 DOWN` aciona o watchdog.
8. Habilite notificação de recuperação, para a Synapse saber quando o caminho voltou.

Referências da ferramenta: [Notification Methods](https://github.com/louislam/uptime-kuma/wiki/Notification-Methods) e os campos atuais de monitor no [catálogo de interface](https://github.com/louislam/uptime-kuma/blob/master/src/lang/en.json).

## 3. Uma saída: `ALERTA_WEBHOOK`

Cadastre no Kuma uma notificação do tipo **Webhook** apontando para o mesmo destino secreto configurado como `ALERTA_WEBHOOK` no backend. Não grave a URL neste repositório, no nome do monitor ou em screenshot de chamado.

O receptor de `ALERTA_WEBHOOK` é o roteador de notificações. Ele precisa aceitar duas origens:

- Uptime Kuma: queda total do processo, host, proxy ou rede; destino imediato `SYNAPSE`;
- backend: falha de componente ainda diagnosticável; o corpo já traz `severidade`, `destinos` e `componentes`.

O roteador entrega `SYNAPSE` ao canal interno de operação e `CLIENTE` ao grupo do cliente. Não derive destino pelo texto da mensagem. Para alertas emitidos pelo backend, `DEGRADADO` nunca inclui `CLIENTE`; `CRITICO` só inclui `CLIENTE` dentro da janela vigente.

## 4. Janela do cliente

A fonte da verdade fica em `configuracao_automacao`, editável pelo CRUD existente:

| Chave | Valor inicial | Semântica |
|---|---|---|
| `alerta.horario_cliente.inicio` | `08:00` | início inclusivo no fuso da instância |
| `alerta.horario_cliente.fim` | `18:30` | fim exclusivo no fuso da instância |

A Synapse recebe alertas em qualquer horário. Fora da janela, o alerta crítico emitido pelo backend não carrega o destino `CLIENTE`. A notificação do Kuma vai primeiro à Synapse porque, durante queda total, o processo indisponível não consegue consultar a configuração nem rotear por ela; a operação da Synapse agenda o aviso ao cliente para a abertura da janela.

## 5. Teste de aceitação em homologação

Não marque o watchdog como operacional antes deste teste:

1. confirme `/health/critical` em `UP` e o monitor em `UP`;
2. anote horário, operador e serviço que será interrompido;
3. interrompa o backend da homologação de forma reversível, sem remover volume ou stack;
4. confirme que a primeira falha não notificou;
5. após a segunda falha, confirme a chegada no canal da Synapse e registre o horário;
6. religue o backend e espere `/health/critical` voltar a `UP`;
7. confirme a notificação de recuperação;
8. repita uma falha diagnosticável dentro da aplicação e confira o envelope `destinos`; fora do horário, `CLIENTE` não pode aparecer.

Registre a evidência no chamado de operação: monitor, timestamps das duas falhas, entrega, recuperação e responsável. Não cole a URL de `ALERTA_WEBHOOK`.

## 6. Diagnóstico rápido

| Componente | Primeira investigação |
|---|---|
| `banco-chat` | conectividade e saturação do pool reservado ao chat |
| `fila-outbox` | heartbeat do `PublicadorDaOutbox` e acesso a `outbox_evento` |
| `canal` | credencial ativa no banco e autenticação no provedor |
| `websocket` | disponibilidade do broker STOMP e upgrade no proxy |
| `particoes-mensagem` | partições do mês corrente e próximo |
| `acumulo-outbox` | idade/volume das pendências e erro do provedor |

O RabbitMQ da stack não é o consumidor do envio atual. O backend envia por `outbox_evento` + `PublicadorDaOutbox`; portanto, é essa fila efetiva que `fila-outbox` mede.
