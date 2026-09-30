# 49 — Diagnóstico: mensagens repetidas e mensagens da Automação ausentes no CRM

Estado em 30/09/2026: **nenhuma causa foi comprovada com dados reais.** Esta etapa parou no
ponto de parada do prompt, porque não havia caso rastreável nem acesso de leitura à produção
nesta máquina (SSH recusou por falta de chave). O que está aqui são fatos lidos no código, as
hipóteses que eles permitem e o script somente leitura que separa uma hipótese da outra.

Não trate os dois relatos como uma causa única: nada abaixo liga um ao outro.

## Fluxo real, lido no código

```
Provedor ──webhook──▶ CRM WebhookCanalController
                        ├─ grava webhook_entrada (payload cru) ──▶ processador assíncrono cria/atualiza
                        │                                          lead, atendimento e mensagem
                        └─ outbox 'automacao.webhook.repassar' ──▶ PublicadorDeRepasseWebhookOperacoes
                                                                    └─ POST payload CRU ao n8n
n8n ── busca lead/atendimento (endpoint NÃO identificado) ── decide ── envia ao provedor
    └─ POST /internal/v1/atendimentos/{id}/responder          (CRM enfileira e envia)
       ou POST /internal/v1/atendimentos/{id}/mensagens-enviadas (registra o que o n8n já enviou)
```

Os dois ramos do webhook são **independentes**: o repasse sai da outbox sem esperar o
processamento que cria o atendimento e registra a mensagem de entrada.

## Fato 1 — o CRM reentrega o mesmo evento ao n8n quando a resposta demora

- `RepasseWebhookAutomacaoHttpAdapter.repassar` trata **qualquer** `RuntimeException` como
  `TENTAR_NOVAMENTE`, inclusive *read timeout*, que acontece **depois** de o n8n receber o corpo.
- O timeout é `AUTOMACAO_WEBHOOK_TIMEOUT`, padrão **5s** (`application.yml`, `repasse-webhook`).
- `PublicadorDeRepasseWebhookOperacoes` reagenda com backoff até `OUTBOX_MAX_TENTATIVAS`
  (padrão 8), com **o mesmo corpo e a mesma assinatura**.
- `marcarPublicado` apaga `ultimo_erro`, mas `tentativas` permanece: linha com
  `publicado_em IS NOT NULL AND tentativas > 0` é um evento que o n8n **pode** ter recebido mais
  de uma vez. O motivo da falha só fica no log do backend (`WARN ... tentara novamente: <exceção>`).

**Hipótese H1 (envio repetido):** se o nó Webhook do n8n responde só no fim do fluxo (IA +
envio) e isso leva mais de 5s, cada timeout vira uma nova execução, e cada execução envia
de novo ao cliente. Isso explicaria mensagens repetidas "a vários clientes". **Não comprovada:**
exige ver no n8n o mesmo `wamid` de entrada em mais de uma execução (seção C2 do script) e,
no provedor, mais de um `wamid` de saída para a mesma resposta.

A idempotência de `RegistrarMensagemEnviadaDaAutomacaoUseCase` (mesmo `wamid` no mesmo
atendimento) **não protege contra H1**: cada execução gera um envio novo, portanto um `wamid`
novo, e o CRM registra os dois corretamente.

## Fato 2 — o contrato interno não tem busca de atendimento por lead ou telefone para o caso EM_IA

- `GET /internal/v1/atendimentos/em-andamento` devolve `EM_IA` e `EM_ATENDIMENTO`, **sem filtro
  por lead ou telefone**, paginado (padrão 20, teto `SUPORTE_TAMANHO_PAGINA`=20), ordenado pela
  última atividade.
- A única busca por lead (`AtendimentosEmAndamentoRepositorioJdbc.porLeadEmAtendimento`, usada
  pelo EV-05) filtra `status = 'EM_ATENDIMENTO'`: **ignora `EM_IA`**, exatamente o estado em
  que a Automação responde.

**Hipóteses (falta do `atendimentoId`)**, que dependem de qual endpoint o workflow chama:

- **H2 — página:** o workflow lê só a primeira página de `/em-andamento`; com mais de 20
  atendimentos ativos mais recentes, o lead não aparece.
- **H3 — corrida:** o n8n consulta antes de o processador do CRM criar o atendimento (contato
  novo) ou registrar a mensagem de entrada (que o põe no topo da ordenação).
- **H4 — estado:** o workflow usa uma busca que só considera `EM_ATENDIMENTO`.

**Nenhuma comprovada.** O workflow principal do n8n não está versionado no repositório
(`docs/n8n/` só tem o resumo por IA), então não dá para dizer qual endpoint ele chama. Pelo
prompt, o contrato não será alterado com base nessa suposição.

## Como obter os fatos — script somente leitura

```bash
bash docker/operacoes/diagnostico-automacao-duplicidade.sh --stack <nome-da-stack> [--dias 14]
```

A VPS hospeda mais de uma stack, então **nada é escolhido por padrão**:

- `--stack` é obrigatório. Sem ele, o script lista as stacks em execução e para.
- Postgres, backend e n8n são resolvidos pelos labels do Swarm da stack informada
  (`com.docker.stack.namespace` e `com.docker.swarm.service.name=<stack>_<serviço>`). Exige
  **exatamente um** container em execução de cada; zero ou mais de um interrompe.
- Os bancos saem do ambiente dos próprios containers (`POSTGRES_DB`/`POSTGRES_USER` do Postgres,
  `SYNAPSE_DB_URL`/`SYNAPSE_DB_USER` do backend, `DB_POSTGRESDB_HOST`/`DATABASE` do n8n), nunca de
  nome fixo. Interrompe se o backend ou o n8n apontarem para outro host que não o `postgres` da
  própria stack, se o banco do backend divergir do `POSTGRES_DB`, se o usuário divergir ou se um
  banco não existir.
- Exibe os três containers e os dois bancos **antes** da primeira consulta e pede confirmação
  (`--sim` dispensa; sem terminal e sem `--sim`, para sem consultar).

Toda sessão abre com `default_transaction_read_only=on` e o script só executa `SELECT`. Não lê
variáveis de segredo e não imprime token, senha ou telefone: e-mails e números com 8+ dígitos
(mesmo formatados) são mascarados **antes** de o texto ser truncado em 60 caracteres. Seções:

| Seção | Responde | Hipótese |
|---|---|---|
| A / A2 | repasses publicados após falha, por dia, com o `wamid` de entrada | H1 |
| B | motivo das falhas de repasse no log (timeout × conexão × 5xx) | H1 |
| C / C2 | execuções do n8n por workflow e o mesmo `wamid` de entrada em mais de uma execução | H1 |
| D / D2 | mesma mensagem da IA/Sistema repetida no mesmo atendimento em até 10 min, com alcance | H1 × registro duplicado |
| E | atendimentos ativos agora versus página de 20 | H2 |
| E2 | mensagens registradas pela Automação por dia | ausência de registro |

### O que foi validado — e o que não foi

- **Resolução de alvos e modos de falha:** `bash docker/verificacao/testar-diagnostico-automacao.sh`
  roda o script contra um `docker` falso que simula duas stacks na mesma VPS. São 22 verificações:
  - 13 modos de falha, todos sem nenhuma consulta de dados;
  - caminho feliz: só o Postgres, o usuário e os bancos da stack alvo são tocados, toda sessão é
    somente leitura, nenhum token aparece e a outra stack não é tocada.
  
  Removendo de propósito a checagem de host do backend, ou o modo somente leitura, o teste
  correspondente reprova.
- **Consultas do CRM (A, A2, D, D2, E, E2):** executadas num Postgres 15 de desenvolvimento em
  sessão somente leitura. Com dados sintéticos plantados, acusam o repasse publicado após falha e a
  repetição da IA (com o telefone do texto mascarado); sem eles, acusam zero. A máscara também foi
  conferida com telefone formatado, internacional e e-mail na borda do corte.
- **Não validado:** as consultas ao banco do n8n (C, C2) **nunca foram executadas** — não há banco
  do n8n no ambiente de desenvolvimento. Os nomes de tabela e coluna (`execution_entity`,
  `execution_data`, `workflow_entity`, `"startedAt"`, `"retryOf"`) seguem o schema do n8n 1.x e
  podem divergir na versão instalada; se falharem, o script para ali (`ON_ERROR_STOP`) sem alterar
  nada. A seção B (log do backend) também só foi testada com uma linha de exemplo.

## Como ler o resultado

- **C2 com o mesmo `wamid` em 2+ execuções e A2 com o mesmo `wamid`** → H1 comprovada: o CRM
  reentregou e o n8n processou de novo. A correção é no **n8n** (deduplicar pelo `wamid` de entrada
  **antes** do envio, com o nó Webhook respondendo imediatamente) **e** no CRM (read timeout depois
  do envio não pode reenviar às cegas). Nenhuma das duas deve gerar chave nova a cada retry.
- **D com `wamids_saida` distintos para o mesmo texto** → o provedor enviou duas vezes (envio
  duplicado). **Um único `wamid`** repetido → registro/exibição duplicada, não envio.
- **E acima de 20 com relatos de falta de `atendimentoId`** → H2 plausível; confirmar no workflow.

## O que é necessário para seguir

1. Saída do script acima para a stack da instância afetada, conferindo os alvos exibidos antes de
   confirmar (ou acesso SSH de leitura a partir desta máquina).
2. Export JSON do workflow do n8n que recebe o webhook do CRM, sem credenciais. É ele que diz qual
   endpoint busca o lead e como o nó Webhook responde.
3. Pelo menos dois casos relatados pela equipe: lead/telefone, horário aproximado e texto da
   mensagem repetida.
