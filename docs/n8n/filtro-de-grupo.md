# Filtro de grupo no workflow de atendimento (n8n)

**Status:** especificação para aplicar **antes do deploy** da E213. Não aplicado nem testado no n8n
nesta etapa: o workflow principal não está versionado neste repositório e não houve acesso à
instância. O CRM já filtra grupo no próprio repasse (ver `docs/44`, Fase 2 item 3); este filtro é a
segunda camada, para o caso de o workflow receber webhook por outro caminho ou de uma versão antiga do
CRM ainda estar no ar.

## Onde colocar

Primeiro nó depois do Webhook Trigger que recebe o repasse do CRM, **antes** de qualquer nó que:
responda ao cliente, chame IA, reconheça `#reset`/`#resetgeral` ou grave contexto/memória.

## Regra

Uma mensagem é de grupo quando qualquer um vale:

- `isGroup === true` ou `isGroup === "true"` (Uzapi; obrigatório no Swagger);
- algum identificador de chat termina em `@g.us`: `from`, `chatid`, `chatId`, `remoteJid`,
  `remotejid`, `groupId`, `group_id`, `jid`;
- `group_id` preenchido e diferente de `status@broadcast` (Meta, defensivo).

`status@broadcast` é Status/Story, **não** grupo: siga a regra que o workflow já tem para Status.

O nó remove só os itens de grupo de `entry[].changes[].value.messages[]` e mantém o resto do envelope.
Se não sobrar nenhuma mensagem nem `statuses[]`, encerra a execução sem resposta. Mensagem privada no
mesmo lote segue normalmente, **uma vez**.

## Nó Code (JavaScript, "Run Once for Each Item")

```javascript
const CHAVES = ['from', 'chatid', 'chatId', 'remoteJid', 'remotejid', 'groupId', 'group_id', 'jid'];

function ehGrupo(m) {
  if (m.isGroup === true || String(m.isGroup).toLowerCase() === 'true') return true;
  if (typeof m.group_id === 'string' && m.group_id && m.group_id !== 'status@broadcast') return true;
  return CHAVES.some((c) => typeof m[c] === 'string' && m[c].toLowerCase().endsWith('@g.us'));
}

const corpo = $json.body ?? $json; // conforme o Webhook Trigger expõe o corpo
let sobrou = false;
for (const entrada of corpo.entry ?? []) {
  for (const mudanca of entrada.changes ?? []) {
    const valor = mudanca.value ?? {};
    if (Array.isArray(valor.messages)) {
      valor.messages = valor.messages.filter((m) => !ehGrupo(m));
      sobrou ||= valor.messages.length > 0;
    }
    sobrou ||= Array.isArray(valor.statuses) && valor.statuses.length > 0;
  }
}
if (!sobrou) return []; // só grupo: nenhum item segue, nada é respondido
return [{ json: { ...$json, body: corpo } }];
```

Não registre o corpo em log nem em nota de execução: ele traz telefone e conteúdo.

## Teste obrigatório (lote misto realista)

Execute o workflow **de teste** (URL de teste, não a de produção) com o corpo abaixo, que usa o
formato do Swagger da Uzapi e dados sintéticos. Use um número de teste da equipe, nunca de cliente.

```json
{"object":"whatsapp_business_account","entry":[{"id":"","changes":[{"value":{
  "messaging_product":"whatsapp",
  "metadata":{"display_phone_number":"5500000000000","phone_number_id":"<phone_number_id do ambiente>"},
  "contacts":[{"profile":{"name":"Teste"},"wa_id":"<numero de teste>"}],
  "messages":[
    {"from":"<numero de teste>","id":"TESTE-GRUPO-1","isGroup":true,"timestamp":"1768843300",
     "type":"text","text":{"body":"#reset"}},
    {"from":"<numero de teste>","id":"TESTE-PRIVADO-1","isGroup":false,"timestamp":"1768843400",
     "type":"text","text":{"body":"mensagem de teste no privado"}}
  ]},"field":"messages"}]}]}
```

Resultado esperado:

1. A mensagem `TESTE-PRIVADO-1` é processada **uma vez** (uma resposta, se o fluxo responde).
2. A mensagem `TESTE-GRUPO-1` não gera resposta, não chama IA e **não** aciona o reset.
3. Reenviando o mesmo corpo, nada é respondido de novo, se o workflow já deduplica por `id`. Se não
   deduplica, registrar: o CRM não repassa mais reentregas, mas o n8n pode receber o mesmo evento por
   outro caminho.
4. Um corpo só com o item de grupo termina sem nenhuma resposta.

## Antes de desligar `group_messages`

Decisão de 25/09: **não desligar ainda**. Antes, procure em todos os workflows ativos referências a
`isGroup`, `@g.us`, `group`, `groupId` e `group_id`, e confirme que nenhum depende de evento de grupo.
Desligar é redução de ruído, não substitui este filtro nem o do CRM.
