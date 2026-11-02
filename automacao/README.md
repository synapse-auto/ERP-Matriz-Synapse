# Automacao TypeScript

Base local da automacao da Synapse, no mesmo monorepo do CRM. Nesta etapa existe
apenas o servidor HTTP de desenvolvimento e o toggle de follow-up. Nao ha
agendamento, worker, acesso ao banco ou envio de mensagens implementados.

## Rodar localmente

Requer Node.js 22.12+ e npm. Dentro de `automacao/`:

```sh
npm ci
npm test
npm run typecheck
npm run dev
```

O servidor fica em `http://127.0.0.1:3100/health/liveness`. O arquivo `.env` e
opcional e deve ficar somente na maquina local; use `.env.example` como referencia.
`AUTOMACAO_FOLLOWUP_HABILITADO` so aceita `true` ou `false` e comeca desligado.
No momento, ligar o toggle muda apenas o estado exibido no healthcheck: nao
inicia jobs nem envios.

## Proxima etapa

Inventariar os follow-ups ativos da Femina e da Estrutural antes de definir as
interfaces de dominio. Configuracoes e regras variaveis serao recebidas por
portas/adaptadores, nao por condicionais com o nome do cliente.

O banco de desenvolvimento sera o banco da integracao/n8n em PostgreSQL, mas
as tabelas internas do n8n nao serao alteradas ou reutilizadas como contrato da
automacao. Estruturas proprias, inclusive as do pg-boss, precisam de schema e
migracoes versionadas, com uma instancia/banco isolado por filho. O Redis
existente do CRM podera ser compartilhado quando houver necessidade real.

Docker nao esta disponivel neste computador nesta etapa. Testes de integracao
com PostgreSQL e Redis ainda nao foram executados. Nenhum Dockerfile ou Compose
foi alterado.
