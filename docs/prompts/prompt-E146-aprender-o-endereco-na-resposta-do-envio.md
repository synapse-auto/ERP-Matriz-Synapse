# Prompt E146 — A Meta devolve o endereço certo e o CRM joga fora

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`fix/aprender-endereco-na-resposta-do-envio`) e PR. **Sem merge, sem deploy.**
> Backend. **Sem migration** — a coluna já existe desde a V58 (E141).
> `cd backend && ./mvnw -pl crm-app -am verify`.

**Continuação direta da E141.** Leia
`docs/prompts/prompt-E141-endereco-de-envio-do-provedor.md` antes de começar: as decisões tomadas
lá continuam valendo integralmente e **não devem ser reabertas** por esta etapa.

---

## O que a E141 resolveu e o que ela não resolve

A E141 separou identidade de endereço: `lead.telefone` continua canônico (13 dígitos, chave de
busca e de casamento), e `lead.telefone_provedor` guarda o endereço que a Meta usa. O envio usa
`COALESCE(telefone_provedor, telefone)`.

O `telefone_provedor` só é preenchido **quando o cliente escreve para nós** — é o `wa_id` do
webhook de entrada. Para quem nunca escreveu, ele é `NULL` e o envio cai no canônico de 13 dígitos.

Medição em produção, 03/09:

```
leads sem wa_id conhecido (telefone_provedor IS NULL)              7.325
leads com wa_id de 12 dig. e telefone de 13 dig.                     170
leads com wa_id identico ao telefone                                   6
```

Entre os leads cujo endereço a Meta já nos entregou, **170 de 176 — 96,6% — usam um endereço
diferente do que guardamos**. Template é, por definição, primeiro contato: o `telefone_provedor`
está sempre `NULL` justamente no caso em que ele faria falta.

Falhas de entrega nos últimos 7 dias, por código:

```
131047  Re-engagement message      32   (Automacao, envio dela direto na Meta - fora do CRM)
131047  Re-engagement message       2   (CRM, texto livre da atendente, janela ABERTA de 16h52m)
131026  Message undeliverable      12   (CRM, 100% template, 11 deles com telefone_provedor NULL)
```

## A causa

O `MetaCloudApiAdapter` faz o `POST /{numero}/messages` e lê **só o wamid** da resposta:

```java
private String idDaMensagem(String resposta) {
    JsonNode mensagens = json.readTree(resposta).path("messages");
    return mensagens.isArray() && !mensagens.isEmpty() ? mensagens.get(0).path("id").asText() : "";
}
```

A resposta da Meta tem outro campo:

```json
{
  "messaging_product": "whatsapp",
  "contacts": [{ "input": "5561999671419", "wa_id": "556199671419" }],
  "messages": [{ "id": "wamid.HBg..." }]
}
```

`contacts[0].wa_id` é **o provedor dizendo qual endereço ele usa para esse destinatário**. Está na
resposta de todo envio aceito, de graça, e é descartado.

## A decisão, e por que ela é a mesma da E141

A E141 registrou, e continua valendo:

> **Quando não houver endereço conhecido, envia no canônico.** O CRM **não deve adivinhar**
> removendo o nono dígito "porque 96% da base é assim" — adivinhar foi o que produziu este bug.

Esta etapa **não viola** essa decisão, e não pode passar a violá-la. Ler `contacts[0].wa_id` não é
o CRM adivinhar: é o provedor informando. A regra continua sendo a mesma — **o endereço quem
decide é quem entrega**. A única mudança é passar a ouvir a resposta que já recebemos.

**Está fora do escopo desta etapa**, e não deve ser implementado nem sugerido no PR: derivar a
forma de 12 dígitos por conta própria, tentar o envio duas vezes com endereços diferentes,
reenfileirar automaticamente após `131026`, ou mexer em qualquer coisa do `TelefoneCanonico`.

---

## Bloco 0 — Medir antes de codar. Este bloco é uma trava, não uma formalidade

**Não escreva nenhuma linha de produção antes de concluir este bloco e escrever o resultado no PR.**

Precisamos saber uma coisa que ninguém verificou ainda: **quando o CRM envia para a forma de 13
dígitos, a Meta devolve `wa_id` igual ao `input` ou resolvido para 12 dígitos?**

- Se a Meta **resolve** (`wa_id` != `input`), esta etapa entrega exatamente o que promete.
- Se a Meta **ecoa** (`wa_id` == `input`), esta etapa não corrige envio frio nenhum — grava um
  endereço idêntico ao que já usaríamos. O código ainda é correto e deve entrar, mas o PR
  **precisa dizer isso com todas as letras**, sem prometer o que não entrega.

Como responder, em ordem de preferência:

1. Procure no repositório e nas tabelas de produção alguma resposta de envio já registrada com o
   corpo completo. Se o corpo da resposta da Meta não é persistido em lugar nenhum hoje, **diga
   isso** — é em si um achado.
2. Escreva o teste de integração do Bloco 3 com os dois cenários (eco e resolução) e deixe o
   comportamento correto nos dois.

O que **não** vale: assumir uma das duas e seguir. Se você não conseguir responder, escreva no PR
"não consegui determinar" e implemente de forma que os dois casos fiquem corretos.

## Bloco 1 — O adaptador passa a devolver o endereço junto com o wamid

`ResultadoDeEnvio.Aceito` hoje é `record Aceito(String idExterno)`. Ele precisa carregar também o
endereço que o provedor informou.

Requisitos:

- O campo é **opcional na prática**: um provedor não oficial não devolve nada disso, e o `default`
  do `CanalGateway` não pode passar a exigir. Ausente vira `null`/vazio, nunca exceção.
- `idDaMensagem` continua se comportando como hoje quando a resposta é ilegível: **2xx com corpo
  ruim não desfaz o envio**. A leitura do novo campo segue a mesma regra — falhar em lê-lo não
  pode transformar um envio aceito em falha. O `log.warn` que já existe cobre o caso.
- O nome do campo deve dizer o que ele é. `enderecoDoProvedor` serve; `waId` não, porque o domínio
  não conhece Meta.
- Nada abaixo do adaptador pode aprender o formato do JSON da Meta. A tradução mora onde já mora.

## Bloco 2 — O publicador da outbox grava o endereço aprendido

Em `PublicadorDaOutboxTransacoes`, no `case ResultadoDeEnvio.Aceito`, depois do que já é feito
hoje (`marcarPublicado`, `atualizarStatusEntrega`, `idsExternos.gravar`, evento):

- Se o endereço informado pelo provedor **existe e é diferente** de `pendente.telefoneDestino()`,
  gravar em `lead.telefone_provedor` usando o método que a E141 já criou:
  `LeadNoCaminhoDeMensagem.registrarTelefoneProvedor(leadId, endereco)`. O `leadId` está em
  `pendente.leadId()`.
- Se for igual, ou vazio, **não escreva nada**. Um `UPDATE` por mensagem enviada em uma tabela
  quente, para gravar o valor que já está lá, é desperdício puro.
- `lead.telefone` **não é tocado**. Nem aqui nem em lugar nenhum desta etapa.

**Gravação silenciosa, sem evento de timeline.** Decisão tomada: `telefone_provedor` é endereço de
entrega, não fato de negócio. O `COMMENT` da coluna na V58 já diz que não é para exibir nem para
buscar. Emitir evento de timeline em 7.325 leads produziria milhares de linhas sobre as quais o
atendente não tem nada a fazer. Um `log.debug` no publicador é suficiente para conferência.

Cuidado com transação: o `case Aceito` já roda dentro da transação do publicador. A escrita entra
nela — não abra transação nova, não use `REQUIRES_NEW`, e não deixe uma falha ao gravar o endereço
derrubar um envio que a Meta já aceitou. Se essa escrita puder falhar por motivo alheio (lead
apagado, por exemplo), ela não pode desfazer o `marcarPublicado`. Decida e **justifique no PR** se
isso exige isolar a escrita ou se o cenário não é alcançável.

## Bloco 3 — Testes

Em `crm-app`, junto dos que a E141 já deixou em `CanalWhatsAppIT` (ali já existem asserções sobre
`telefone_provedor` e uma constante `TELEFONE_PROVEDOR_SEM_NONO` — reúse, não duplique):

1. **A Meta resolve.** Envio para lead com `telefone_provedor` NULL; a resposta simulada traz
   `contacts[0].wa_id` diferente do `input`. Ao fim, `lead.telefone_provedor` contém o `wa_id` e
   `lead.telefone` está **intacto**.
2. **A Meta ecoa.** Mesma situação, mas `wa_id` == `input`. Ao fim, `telefone_provedor` continua
   `NULL` e **nenhum `UPDATE` foi emitido** na `lead`.
3. **Resposta sem `contacts`.** Só `messages[0].id`. O envio é aceito, o wamid é gravado como
   hoje, `telefone_provedor` continua `NULL`, e nada estoura.
4. **Endereço já conhecido e igual.** Lead com `telefone_provedor` preenchido; a resposta traz o
   mesmo valor. Nenhuma escrita.
5. **Segundo envio usa o endereço aprendido.** Envia uma vez (aprende), envia de novo, e o
   `"to"` do segundo `POST` é o endereço aprendido, não o canônico. **Este é o teste que prova a
   etapa inteira** — sem ele o resto é decoração.
6. **Template.** O mesmo aprendizado vale para `MensagemTemplate`, que é o caso que motivou a
   etapa. Cubra pelo menos um cenário com template, não só com texto livre.

## O que esta etapa não conserta — e o PR precisa dizer

Seja explícito na descrição do PR. Não deixe o leitor concluir mais do que foi feito:

- **A primeira mensagem para um lead desconhecido continua podendo falhar.** O endereço só é
  aprendido depois que a Meta responde. O ganho é da segunda tentativa em diante.
- **Se a Meta apenas ecoar o `input`** (Bloco 0), esta etapa não muda o resultado de envio frio
  nenhum. Nesse caso o próximo passo é uma **decisão de negócio do Marcondes**, não do agente:
  reabrir ou não a proibição de derivar o nono dígito que a E141 registrou. **Não a reabra por
  conta própria e não implemente a derivação "já que estamos aqui".**
- **As 32 falhas `131047` de `remetente_tipo = 'IA'` não são deste código.** Vêm do
  `RegistrarMensagemEnviadaDaAutomacaoUseCase`: a Automação envia direto na Meta e só registra a
  cópia no CRM. Não há checagem de janela ali, e **está certo assim** — o CRM não enviou nada. Não
  adicione checagem de janela nesse caso de uso.
- **O botão "Reenviar" naquelas bolhas continua quebrado.** É outra etapa.
