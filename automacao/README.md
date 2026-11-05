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
## Chaves de ativacao por filho

| Variavel | Padrao | Efeito |
| --- | --- | --- |
| `AUTOMACAO_HABILITADA` | `false` | Chave geral da automacao TypeScript |
| `AUTOMACAO_FOLLOWUP_HABILITADO` | `false` | Solicita a ativacao do follow-up |

O follow-up so fica habilitado quando as DUAS chaves sao `true`. Ausencia ou
valor vazio significa desligado. Qualquer outro valor diferente de `true` e
`false` impede a inicializacao, mesmo se a outra chave estiver desligada.
`followUpHabilitado` na configuracao e no healthcheck e o estado efetivo,
ja combinado com a chave geral. O HTTP/healthcheck permanece disponivel.

No Dokploy, cada filho conserva seus valores. Nao e necessario adicionar as
variaveis nos filhos existentes para mante-los desligados. Na homologacao,
ativar ambas explicitamente apenas quando o fluxo estiver pronto para testes.
Mudancas nas envs exigem reiniciar/reimplantar o servico. Nao desligam o n8n
nem desfazem efeitos externos que ja aconteceram.

Nesta base, os toggles ainda so controlam o estado exposto: nao existem jobs,
envios ou painel. Ao implementar esses pontos, agendadores, consumidores,
reprocessamento e efeitos externos devem verificar a capacidade efetiva;
o painel de consulta deve continuar acessivel. Nao basta ocultar controles.
O futuro Compose deve repassar ambas com default `false`, sem exigir novas
envs dos filhos. Nenhum servico TypeScript foi adicionado ao Compose ainda.

## Proxima etapa

Inventariar os follow-ups ativos da Femina e da Estrutural antes de definir as
interfaces de dominio. Configuracoes e regras variaveis serao recebidas por
portas/adaptadores, nao por condicionais com o nome do cliente.

O banco do n8n serve apenas como referencia de estrutura para o responsavel
pelo provisionamento, nao como integracao runtime desta aplicacao.
Estruturas proprias, inclusive as do pg-boss, precisam de schema e
migracoes versionadas, com isolamento por filho. O Redis
existente do CRM podera ser compartilhado quando houver necessidade real.

Docker nao esta disponivel neste computador nesta etapa. Testes de integracao
com PostgreSQL e Redis ainda nao foram executados. Nenhum Dockerfile ou Compose
foi alterado.
