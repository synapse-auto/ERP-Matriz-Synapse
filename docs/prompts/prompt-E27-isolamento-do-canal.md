# Prompt E27 — Isolamento do canal por número de destino

> Leia `AGENTS.md`. Entrega em 25/08.
> Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## Contexto — incidente de 16/08

A conta do WhatsApp (WABA `1574679126928884`) da Estrutural Vidros contém **dois números**:
o oficial, atendido pelo app `f-bot` do cliente, e o de teste da Synapse
(`+55 61 3199 1947`, phone number id `1307417749115229`).

`subscribed_apps` na Meta é **por WABA, não por número**. Ao inscrever o app
`Estrutural-Synapse` nessa conta, o CRM de homologação passou a receber **as conversas reais
dos clientes da Estrutural**, gravando-as em `webhook_entrada`, na outbox de repasse e como
lead. A contenção foi desinscrever o app — o que deixa a homologação sem WhatsApp até esta
etapa existir.

A coluna `phone_number_id` existe em `canal_credencial` desde a `V3__configuracao_base.sql`.
Busca por `phoneNumberId` / `phone_number_id` em todo o `backend/`: **nenhuma linha de Java
lê essa coluna**. O dado para se proteger estava no banco desde a V3 e nunca foi usado.

Diferente dos quinze casos anteriores do projeto, esta proteção não falhou em silêncio: ela
nunca existiu. O efeito é o oposto do habitual — o sistema aceita demais.

---

## Bloco 1 — O webhook só aceita o que é do canal desta instância

**Onde o filtro entra é o ponto central desta etapa.** Hoje o `WebhookCanalController.receber`
faz, nesta ordem:

```java
if (!tradutor.assinaturaValida(payloadCru, assinatura)) { ...403... }
agendarRepasse.executar(payloadCru, assinatura, recebidoEm);      // outbox -> n8n
...
entrada.registrarSeNovo(idExterno, tradutor.provedor(), payloadCru, recebidoEm);
```

As duas chamadas persistem o **payload cru**, com o conteúdo da mensagem. Um filtro colocado
depois — no `ProcessadorDeWebhookEntrada`, por exemplo — descartaria o lead e ainda assim
teria gravado a conversa de terceiros em dois lugares. **O filtro vai logo após a validação
da assinatura, antes de `agendarRepasse` e de `registrarSeNovo`.**

Requisitos:

- `TradutorDeCanal` ganha a extração de `entry[].changes[].value.metadata.phone_number_id`.
  É o tradutor que conhece o formato do provedor; o controller não parseia JSON.
- O canal ativo desta instância vem de `canal_credencial.phone_number_id`. **Lido do banco,
  nunca de constante nem de variável de ambiente** — no go-live esse valor troca pelo do
  número oficial, e o mesmo código serve ao próximo filho.
- Evento que não é do canal: **responder `200` sem gravar nada**. Não é erro. Status de erro
  faz a Meta reentregar e, na insistência, desativar o webhook — o que derrubaria a entrada
  de mensagem legítima.
- Log em `WARN` com o `phone_number_id` recusado e a contagem. **Sem o corpo**: a rota é
  pública e o corpo é de terceiros. Siga o que o `receber` já faz no caminho de assinatura
  inválida ("nem uma linha gravada, nem um log com o corpo").

**Payload misto.** Um POST da Meta pode trazer várias `entry`/`changes`, e nada garante que
todas sejam do mesmo número. Decida por evento, não pelo POST inteiro. Como
`registrarSeNovo` grava o payload byte a byte para permitir reconferir o HMAC (ver
`V17__webhook_payload_texto.sql`), **reescrever o payload para remover o que não é nosso não
é opção** — quebraria a auditoria. Então: se todas as changes forem do canal, siga o fluxo
normal; se nenhuma for, descarte; se vier **misto**, descarte o POST inteiro e logue em
`ERROR` — é raro, é sinal de configuração errada, e não pode passar em silêncio. Relate se
ocorreu e quantas vezes.

**Canal sem `phone_number_id` cadastrado: fail-closed.** Recusa tudo. O raciocínio é que
fail-open reproduz exatamente o estado em que o incidente aconteceu. Para que isso não vire
uma queda silenciosa da entrada de mensagem, o Bloco 2 exige que a falta apareça no
`/health/critical` e no startup.

> **Ponto de parada.** Se ao implementar você concluir que o fail-closed conflita com a regra
> de precedência absoluta (a aba Atendimentos não pode cair 08:00–18:30) de um jeito que o
> Bloco 2 não cobre, **pare e me avise antes de escolher outro caminho.** Não troque para
> fail-open por conta própria.

## Bloco 2 — A falta de configuração aparece antes de custar mensagem

- `/health/critical` reprova quando existe canal ativo sem `phone_number_id`. Mensagem que
  diga o que fazer, não só "unhealthy".
- Erro no log no startup, no mesmo caso.
- O provisionamento (`docker/provisionamento/`) e o seed passam a exigir o `phone_number_id`
  ao cadastrar canal. Cadastrar canal sem ele deve falhar ali, não em produção.
- `docs/16-acesso-da-automacao.md` e `docs/18-runbook-pendencias-operacionais.md` atualizados:
  a inscrição na Meta é por WABA, e o CRM só processa o número cadastrado.

## Testes — a proteção nasce com um teste que a viola

- Evento com `phone_number_id` **de outro número**: não cria lead, não cria atendimento,
  **não grava `webhook_entrada`**, **não enfileira repasse na outbox**, e a resposta é `200`.
  Verifique as quatro coisas; verificar só o lead deixaria passar exatamente o defeito que
  motivou esta etapa.
- Evento do número cadastrado: fluxo íntegro, sem regressão do caminho de entrada.
- Payload misto: descartado, com o log de `ERROR`.
- Canal ativo sem `phone_number_id`: entrada recusada **e** `/health/critical` reprovando.
- Teste de ponto de entrada, chamando o controller como o runtime chama — não o método
  interno de filtragem.

## Definição de pronto

- [ ] Filtro por `phone_number_id` entre a validação do HMAC e as duas persistências
- [ ] Valor lido de `canal_credencial`, sem constante nem variável de ambiente
- [ ] `200` no descarte, log `WARN` sem corpo, contador
- [ ] Payload misto descartado com log `ERROR`
- [ ] Fail-closed quando não há `phone_number_id`, coberto por `/health/critical` e startup
- [ ] Provisionamento e seed exigindo `phone_number_id`
- [ ] Os cinco testes acima, incluindo os quatro efeitos do descarte
- [ ] `docs/16` e `docs/18` atualizados
- [ ] CI verde com **número da run**

## No relatório

Diga explicitamente se alguma variável nova precisa entrar no Dokploy antes do próximo deploy
— a expectativa desta etapa é que **nenhuma** precise, porque o dado é de banco. Se você
precisou de uma, isso vai em item próprio, com nome e valor de exemplo.

Diga também qual o `phone_number_id` que o provisionamento passa a exigir em homologação
(`1307417749115229`) e o que muda no go-live.

---

## Fora desta etapa

O telefone canônico não infere código de país (`TelefoneCanonico.normalizar` só remove
não-dígitos), então `(61) 99999-9999` e `5561999999999` continuam sendo leads diferentes.
Está aberto e depende de decisão do arquiteto entre recusar telefone sem DDI ou completar com
um DDI padrão configurável. **Não resolva por conta própria nesta etapa.**
