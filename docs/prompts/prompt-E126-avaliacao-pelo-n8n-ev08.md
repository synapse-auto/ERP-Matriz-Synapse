# Prompt E126 — Religar a avaliação no contrato EV-08 do n8n

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`feat/avaliacao-ev08`) e PR. **Sem merge, sem deploy.**
> Backend apenas. **Uma migration nova (V55).** Nenhuma política RLS muda.
> `./mvnw -pl crm-app -am verify` na raiz de `backend/` — comando unico; todas as ITs moram no `crm-app`.

---

## Contexto: isto é uma religação, não uma construção

O cliente definiu o contrato EV-08 pelo lado do n8n. Ele descreve o que o CRM tem que fazer quando
um atendimento individual é finalizado: um `POST` assíncrono, com retentativa, para o webhook do n8n,
que a partir daí manda os três botões ao cliente e grava a nota.

**Quase tudo disso já existe.** A E83 construiu a máquina inteira: outbox com reserva e lease,
publisher com backoff exponencial e circuit breaker, adaptador HTTP com timeout e classificação de
falha recuperável, e propriedades de configuração com nome de header configurável. A **E124** apenas
**desligou o gatilho** — tirou a chamada `avaliacao.preparar(finalizado)` do
`FinalizarAtendimentoUseCase`, deixando `SolicitacaoDeAvaliacao` e
`PrepararAvaliacaoDeEncerramento` órfãos, mas intactos.

Esta etapa **religa o gatilho e troca o formato do payload**. Não reescreva a máquina.

---

## Bloco 0 — Ordem e pré-requisito

Faça a branch **a partir de `main` com o PR #58 (E124) já mergeado**. Se `FinalizarAtendimentoUseCase`
ainda tiver o campo `SolicitacaoDeAvaliacao` e o `enum Origem`, a E124 não entrou: **pare e avise.**
Não implemente a E124 aqui, e não reverta o commit dela — o caminho é reconstruir por cima do estado
que ela deixou, com as regras novas.

---

## Bloco 1 — O que já está pronto e você NÃO deve tocar

Leia estes arquivos antes de escrever qualquer linha, e confirme no relatório que cada exigência do
EV-08 abaixo já está atendida:

| Exigência do EV-08 | Onde já está resolvido |
| --- | --- |
| §1.3 envio assíncrono; o atendimento não depende do n8n | outbox `outbox_evento` + `PublicadorDeAvaliacao` (`@Scheduled`), fora da transação |
| §1.4 retentativa em rede e 5xx, com o mesmo evento | `AvaliacaoWebhookHttp.chamar`: `recuperavel = 408 \|\| 429 \|\| >= 500`; backoff em `AvaliacaoWebhookProperties.esperaApos` |
| §8 o mesmo evento reenviado não duplica | `OutboxDeAvaliacaoJdbc.enfileirar`: id determinístico `nameUUIDFromBytes(TIPO + ":" + atendimentoId)` + `ON CONFLICT (id) DO NOTHING` |
| §1.2 só com responsável humano | `PrepararAvaliacaoDeEncerramento`: `if (atendimento.atendenteId() == null) return` |
| `wa_id` só dígitos com DDI | mesma classe: `telefone.matches("[1-9][0-9]{9,14}")`, sem normalizar |

Uma coisa que **parece** bug e não é: `PrepararAvaliacaoDeEncerramento` lança `IllegalStateException`
e **reverte a finalização** quando o `INSERT` no outbox falha. Isso é falha de **banco**, não de n8n —
o §1.3 continua respeitado. Não afrouxe.

---

## Bloco 2 — Religar o gatilho, só na finalização individual

O EV-08 §1.1 e §1.5 são explícitos: dispara **somente** na finalização individual; **nunca** em
"Finalizar todos". A E124 apagou o `enum Origem` justamente porque individual e lote passaram a
compartilhar um caminho só. Traga a distinção de volta — na forma que você julgar mais limpa, mas o
comportamento observável tem que ser exatamente este:

- finalização **individual** com responsável humano → enfileira no outbox;
- finalização **em lote** → não enfileira, em nenhuma circunstância;
- finalização individual **sem responsável** → não enfileira, e a finalização segue normal.

Não invente um segundo caminho de finalização. É o mesmo use case, com a origem sabida por quem
chama.

---

## Bloco 3 — O payload novo

O payload de hoje tem 6 campos e é montado em `OutboxDeAvaliacaoJdbc.enfileirar`:

```json
{"modo":"INICIAR_AVALIACAO","status_finalizacao":"FINALIZADO","atendimento_id":"…","lead_id":"…","atendente_id":"…","wa_id":"…"}
```

O EV-08 §3 pede outro. Passa a ser, em `snake_case` (o contrato aceita camelCase como alias, mas
manda preferir snake_case — prefira):

| Campo | Valor que o CRM envia |
| --- | --- |
| `evento_id` | **o id da linha do outbox**, como string. Já é determinístico por atendimento — é exatamente a chave de idempotência que o §8 pede, e hoje ela não sai no corpo |
| `atendimento_id` | UUID do atendimento finalizado |
| `lead_id` | UUID do lead |
| `atendente_id` | UUID do responsável humano |
| `wa_id` | telefone do lead, só dígitos com DDI, sem normalizar |
| `status_finalizacao` | **sempre `"FINALIZADO"`** — ver Bloco 6 |
| `operacao` | **sempre `"FINALIZAR_INDIVIDUAL"`** |
| `finalizacao_em_massa` | **sempre `false`** |

O campo `modo` **sai**. Os dois últimos são constantes porque o Bloco 2 garante que o CRM só dispara
no caso individual — o contrato pede os campos como redundância defensiva do lado do n8n, e é assim
que devem ser preenchidos. **Não** passe a disparar em lote só porque existe um campo para marcá-lo.

`AvaliacaoWebhookHttp.payloadValido` valida o formato antigo com `no.size() != 6`, `"INICIAR_AVALIACAO"`
e `"FINALIZADO"` cravados. Atualize para o formato novo — inclusive a contagem de campos. Essa guarda
existe para uma linha antiga do outbox não ser publicada com o formato errado depois de um deploy:
mantenha a intenção, e diga no relatório o que uma linha no formato velho passa a sofrer (deve ser
recusa **permanente**, não retentativa eterna).

---

## Bloco 4 — Migration V55: a chave do toggle

O EV-08 §5 diz que o n8n consulta `GET /internal/v1/automation-config` e procura
`avaliacao_atendimento.habilitada`. **Essa chave não existe** em `configuracao_automacao` hoje —
conferi a tabela inteira. Sem ela o n8n lê a lista e não acha nada.

`V55__toggle_avaliacao_atendimento.sql`, no padrão das V23/V27/V33/V36:

```sql
INSERT INTO configuracao_automacao (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES ('avaliacao_atendimento.habilitada', 'false', NULL, 'BOOLEAN', NULL, NULL,
        'Liga a pesquisa de satisfacao pos-atendimento executada pelo n8n (contrato EV-08).')
ON CONFLICT (chave) DO NOTHING;
```

Nasce **`false`**: quem liga é o gestor, na tela de Automação, quando o Dylan disser que o workflow
está pronto. Não ligue por padrão em produção.

**O CRM não lê esse toggle.** Quem decide se manda os botões é o n8n (§4 e §6 do contrato), e
RN-CRM-07 é literal: *"O CRM configura a automação; não a executa."* O CRM continua com o gate que já
tem — `config.configurada()`, que exige URL, token e nome de header válidos. Escreva isso no javadoc
de quem for mexer, para ninguém "consertar" isso depois achando que faltou.

Confirme no relatório que a chave aparece em `GET /internal/v1/automation-config` com
`{"chave": "...", "valor": "false"}` dentro de `parametros`, no formato que o §5 espera.

---

## Bloco 5 — Autenticação: é configuração, não código

O documento do EV-08 mostra `X-Avaliacao-Token: <TOKEN_COMBINADO>`. **Esse não é o header.** O header
real, confirmado pelo dono do projeto, é:

```
crm-synapse-marc-auth: <segredo>
```

E o código **já suporta isso sem alteração**: `AvaliacaoWebhookHttp.chamar` faz
`.header(config.authHeader(), config.token())`, e `AvaliacaoWebhookProperties.configurada()` aceita
`crm-synapse-marc-auth` (bate com o regex de nome de header permitido — só letras, dígitos e os
separadores de token do RFC 7230, entre eles o hífen — e não está na lista de headers proibidos:
`host`, `content-length`, `content-type`, `connection`, `expect`, `upgrade`). É só ambiente:

- `AUTOMACAO_AVALIACAO_AUTH_HEADER=crm-synapse-marc-auth`
- `AUTOMACAO_AVALIACAO_TOKEN=<segredo, só no ambiente>`
- `AUTOMACAO_AVALIACAO_URL=<URL definitiva do webhook>`

**Não escreva o segredo em lugar nenhum do repositório** — nem em `.env.example`, nem em teste, nem
em doc, nem em comentário. As três variáveis já estão declaradas em `application.yml`,
`.env.example`, `README.md` e `docker/dokploy-stack.yml`: confirme que estão, com default vazio, e
**não mexa nelas**. Se algo estiver faltando, diga no relatório em vez de inventar valor.

---

## Bloco 6 — O que o contrato pede e esta etapa NÃO faz

Três pontos do EV-08 estão errados ou fora do alcance do CRM. **Não os implemente, não os
"resolva", não invente adaptação.** Registre no relatório e siga.

1. **`VENDA_CONCLUIDA` não existe no CRM.** `StatusAtendimento` tem exatamente `EM_IA`,
   `EM_ATENDIMENTO` e `FINALIZADO`; `StatusBasicoLead` tem `IA`, `EM_ATENDIMENTO` e `FINALIZADO`.
   Não há origem para esse valor. Existe um `resultado_etapa` (`EM_ANDAMENTO`, `GANHO`, `PERDIDO`,
   V21) que **talvez** seja o que o contrato chama de venda concluída, mas isso é decisão de negócio
   e ainda não foi tomada. Envie sempre `"FINALIZADO"`. **Não** derive nada de `resultado_etapa`.
2. **A escala das notas.** O §6 do contrato manda o n8n gravar `Ruim = 2, Bom = 7, Otimo = 10`
   direto em `public.avaliacao`. A tabela tem `nota SMALLINT NOT NULL CHECK (nota BETWEEN 1 AND 5)`
   (V2) e o domínio `Avaliacao` valida a mesma faixa. Não altere a constraint, não altere o domínio,
   não crie conversão. É pendência do outro lado.
3. **O endpoint de gravação da nota já existe.** O §6 afirma que o CRM não precisa fornecer endpoint
   porque o n8n grava direto no Postgres. Está desatualizado:
   `POST /internal/v1/atendimentos/{id}/avaliacao` existe, autentica por `X-Synapse-Token`, devolve
   409 em duplicata e 422 em atendimento aberto ou sem responsável. **Não crie endpoint novo** e não
   mexa nesse. Só confirme no relatório que ele está lá, com essa assinatura.

---

## Bloco 7 — Testes

O `WebhookAvaliacaoIT` é o teste central desta integração e a E124 acabou de **invertê-lo** para
provar que a finalização não solicita mais avaliação. Agora ele volta a provar o contrário, com as
regras novas — inverta de novo, com honestidade, e diga no relatório o que cada caso passou a afirmar.

- Finalização **individual** com responsável: enfileira **uma** linha no outbox, e o payload
  publicado tem exatamente os 8 campos do Bloco 3, com `operacao = FINALIZAR_INDIVIDUAL` e
  `finalizacao_em_massa = false`.
- `evento_id` no corpo é **igual** ao id da linha do outbox, e **estável** entre tentativas.
- Finalizar o **mesmo** atendimento duas vezes não cria segunda linha (`ON CONFLICT DO NOTHING`).
- Finalização **em lote**: nenhuma linha enfileirada, para nenhum dos atendimentos. Este é o teste
  que o cliente vai cobrar — é o bug que originou a E124.
- Finalização individual **sem responsável**: nenhuma linha, e a finalização conclui.
- `status_finalizacao` publicado é sempre `FINALIZADO`.
- Resposta **5xx** do webhook → retentativa com o mesmo `evento_id`; resposta **4xx** que não seja
  408/429 → falha permanente, sem retentativa.
- Payload no **formato antigo** (6 campos, com `modo`) parado no outbox: recusado permanentemente
  pela guarda, sem loop.
- `GET /internal/v1/automation-config` traz `avaliacao_atendimento.habilitada` com valor `false`.
- Com a integração **não configurada** (URL ou token vazios), nada é publicado e a finalização
  funciona igual — comportamento que já existe, garanta que não regrediu.

Nenhum teste pode conter o segredo real. Use fixture.

## Verificação

```
./mvnw -pl crm-app -am verify
```
Spotless, ArchUnit e a contagem de endpoints do OpenAPI verdes.

## Relatório

1. Confirmação, item por item, da tabela do Bloco 1 — o que já estava pronto e você não tocou.
2. Como a distinção individual × lote voltou, e por que nessa forma.
3. O payload final, literal, de um evento real de teste.
4. O que acontece com uma linha do outbox no formato antigo.
5. A saída de `GET /internal/v1/automation-config` mostrando a chave nova.
6. Confirmação de que nenhum segredo entrou no repositório.
7. Os três pontos do Bloco 6, confirmados no código: os enums de status, o
   `CHECK (nota BETWEEN 1 AND 5)` e a assinatura do endpoint interno que já existe.
8. O que cada teste invertido do `WebhookAvaliacaoIT` passou a afirmar.
