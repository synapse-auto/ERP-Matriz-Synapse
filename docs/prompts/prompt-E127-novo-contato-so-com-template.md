# Prompt E127 — Novo contato: a primeira mensagem é template, não texto livre

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`fix/novo-contato-so-com-template`) e PR. **Sem merge, sem deploy.**
> Frontend, mais **um endpoint novo** de leitura no backend. **Sem migration.** Nenhuma regra de
> negócio, nenhuma política RLS, nenhum contrato de envio muda.
> `./mvnw -pl crm-app -am verify` na raiz de `backend/` e a suíte do `frontend/`.

---

## O bug, na tela

O diálogo **"Novo atendimento"** tem um campo livre **"Primeira mensagem (opcional)"**. O atendente
digita ali, clica em "Iniciar atendimento", e leva o erro:

> *a janela de 24h esta fechada: fora dela o provedor oficial so aceita template pre-aprovado, nao
> texto livre*

E isso acontece **sempre**, não às vezes. Contato novo, por definição, nunca mandou mensagem para a
empresa; sem mensagem do cliente não existe janela de 24h; sem janela, a Meta recusa texto livre.
O campo oferece uma coisa que nunca funciona no caso para o qual a tela foi feita.

O aviso azul logo abaixo do campo já diz isso em voz baixa — *"Dependendo do canal, pode ser
necessário utilizar um template aprovado"* — e ainda assim a tela deixa o atendente digitar e só
reclama depois de enviar. **O conserto é oferecer o que funciona, em vez de avisar sobre o que não
funciona.**

---

## Bloco 1 — O contrato do backend já aceita template. Confirme antes de escrever código

Isto não é uma etapa de backend de envio. **Nada no caminho de envio muda.** Verifique você mesmo e
confirme no relatório:

- `POST /api/v1/atendimentos/novo-contato` já recebe
  `template: { nome, idioma, parametros }` — `NovoContatoRequisicao` tem quatro campos, e
  `TemplateNovoContatoRequisicao` já existe em `AtendimentoAcoesController`.
- `IniciarNovoContatoUseCase.Pedido` já tem o campo `template`, já recusa "texto livre **e** template
  juntos", e já exige idioma quando vem template.
- `EnviarMensagemUseCase` só checa a janela para texto livre:
  `if (conteudo instanceof ConteudoDeEnvio.MensagemLivre && !canal.aceitaTextoLivre(...))`.
  **Template passa com a janela fechada** — é exatamente por isso que o conserto é este.
- `ListaTemplatesWhatsApp` já existe (`frontend/src/components/atendimentos/lista-templates-whatsapp.tsx`),
  já filtra `status === "APROVADO"`, já agrupa por categoria, já tem busca e já coleta os parâmetros
  do corpo. O composer a usa em dois lugares. **Reaproveite; não escreva uma segunda lista.**
- A query dos templates é `useQuery({ queryKey: ["whatsapp-templates"] })` sobre
  `listarTemplatesWhatsApp()` → `GET /api/v1/whatsapp/templates`. **Use a mesma `queryKey`**, para o
  diálogo aproveitar o cache do composer em vez de refazer a chamada.

Se qualquer um desses cinco pontos não bater com o que você encontrar, **pare e avise** — a etapa
inteira foi desenhada em cima deles.

---

## Bloco 2 — O backend: expor a capacidade do canal, não o nome do provedor

O pedido do cliente é *"se o provider for META, sempre exibir a lista de templates"*. A tradução
correta disso para este projeto **não** é um `if` sobre o nome do provedor.

`application.yml` linha 130 é explícito sobre a regra da casa:

> *"A UNICA chave que decide qual adaptador de canal roda. Trocar de provedor para um filho e mudar
> esta variavel — nao ha `if` em lugar nenhum do codigo."*

A pergunta "este canal exige template fora da janela?" **já é uma capacidade do domínio**:
`CanalGateway.exigeTemplateForaDaJanela()`. Hoje ela só sai em
`GET /internal/v1/automation-config`, que é do n8n e exige `X-Synapse-Token` — o navegador não
alcança. Por isso o frontend não tem como saber, e por isso esta etapa toca o backend.

Acrescente em `ConfigInstanciaController` (`crm-automacao-config`, o controller que já existe para
"configuração da instância para o frontend"):

```
GET /api/v1/config/canal   →   { "exigeTemplateForaDaJanela": true }
```

- **Autenticado** (não entra na lista pública do `SecurityConfig`, ao lado de `/tema`, `/textos` e
  `/logo` — esses são de tela de login; este não é).
- O valor vem de `CanalGateway.exigeTemplateForaDaJanela()`, a mesma fonte que o
  `AutomationConfigInternalController` já injeta nesse módulo. **Não** duplique a decisão, **não**
  leia `synapse.canal.whatsapp.provedor` em lugar nenhum, e **não** compare strings de provedor.
- Nenhum segredo, nenhuma URL, nenhum token pode aparecer nessa resposta. Só o booleano.
- O teste de contagem de endpoints do OpenAPI vai acusar um endpoint a mais: atualize o número e
  diga no relatório qual era e qual ficou.

---

## Bloco 3 — O diálogo

`frontend/src/components/atendimentos/dialogo-novo-contato.tsx`.

Quando `exigeTemplateForaDaJanela` for **true**:

- O `Textarea` de texto livre **sai da tela**. Não fica desabilitado, não fica escondido atrás de um
  "avançado": sai. Ele nunca funciona neste diálogo.
- No lugar dele entra a `ListaTemplatesWhatsApp`, sob o mesmo rótulo de primeira mensagem, com os
  campos de parâmetro que ela já sabe renderizar.
- O rótulo continua dizendo **opcional**, e continua sendo verdade: abrir a conversa sem nenhuma
  mensagem já funciona hoje (`IniciarNovoContatoUseCase` abre em modo humano sem mensagem) e a
  conversa cai no composer, que oferece os templates. Não passe a exigir template.
- O aviso azul de hoje (`textos.avisoTemplate`) e a linha vermelha do erro de janela **saem**: com
  template escolhido, esse erro não pode mais acontecer. Deixar um aviso sobre um problema que a tela
  acabou de eliminar é ruído.
- "Iniciar atendimento" manda `template: { nome, idioma, parametros }` em vez de `primeiraMensagem`.
  **Nunca os dois** — o backend recusa com 422 e a mensagem *"informe texto livre ou template, nao os
  dois"*.

Quando `exigeTemplateForaDaJanela` for **false**: a tela continua **exatamente como está hoje**, com o
textarea livre. É esse ramo que faz a etapa respeitar "se o provider for META" em vez de cravar o
comportamento da Meta numa segunda parte do código.

Enquanto a chamada de `/api/v1/config/canal` estiver carregando, **não** mostre o textarea "por
enquanto" e troque depois — isso pisca e convida o atendente a digitar algo que vai sumir. Mostre o
campo de mensagem em estado de carregamento até saber qual dos dois é.

`PedidoDeNovoContato` (`frontend/src/lib/atendimento/types.ts`) ganha `template?: { nome: string;
idioma: string; parametros: string[] }`, espelhando o que o backend já aceita.

---

## Bloco 4 — O que NÃO muda

- O composer. Ele já trata janela aberta, fechada e inexistente, e já mostra os templates no caso
  fechado. Não encoste.
- `IniciarNovoContatoUseCase`, `EnviarMensagemUseCase`, `AtendimentoAcoesController` e os DTOs de
  requisição. O contrato já está pronto — **acrescentar campo lá é sinal de que você leu errado.**
- A checagem de janela do backend continua sendo a autoridade. `janela-24h.ts` continua sendo
  estimativa de tela. Não mexa em nenhuma das duas.
- O caminho "abrir para lead existente" (`POST /api/v1/atendimentos/leads/{id}/novo`), que já não
  manda mensagem nenhuma.
- Nenhum texto de `textos.json` some. As chaves que a tela deixar de usar (`avisoTemplate`) ficam no
  arquivo — outra tela pode usá-las, e remover chave de catálogo é etapa própria.

---

## Bloco 5 — Testes

Frontend:

- Com `exigeTemplateForaDaJanela = true`: o diálogo **não** renderiza o textarea de primeira
  mensagem, e renderiza a lista de templates aprovados.
- Escolher um template e confirmar dispara `onConfirmar` com `template` preenchido e **sem**
  `primeiraMensagem`.
- Confirmar sem escolher template nenhum dispara `onConfirmar` só com nome e telefone — a conversa
  abre sem mensagem, que é comportamento válido.
- Template com parâmetros não preenchidos não pode ser enviado (a `ListaTemplatesWhatsApp` já trata
  isso — garanta que o diálogo não contorna).
- Com `exigeTemplateForaDaJanela = false`: a tela é a de hoje, com textarea, e o pedido sai com
  `primeiraMensagem`.
- Nome e telefone continuam obrigatórios, com as mesmas mensagens de erro. A máscara
  `mascararTelefoneBr` continua idêntica — os testes dela ficam verdes **sem edição**.
- Base UI: nada de `data-[state=active]:`; use `data-active:` se precisar de estado em gatilho.

Backend:

- `GET /api/v1/config/canal` autenticado devolve `{"exigeTemplateForaDaJanela": true}` com o
  adaptador Meta ativo.
- Sem JWT devolve 401 — não é rota pública.
- A resposta tem **um** campo. Nenhum token, URL ou número de telefone vaza nela.

## Verificação

```
./mvnw -pl crm-app -am verify
```
e a suíte do frontend. Spotless, ArchUnit e a contagem de endpoints do OpenAPI verdes.

## Relatório

1. Os cinco pontos do Bloco 1, confirmados no código, com arquivo e linha.
2. Onde ficou o endpoint novo e por que ele devolve capacidade em vez de nome de provedor.
3. A contagem de endpoints do OpenAPI: de quanto para quanto.
4. Como o diálogo se comporta enquanto a capacidade ainda está carregando.
5. Confirmação de que nenhum arquivo do caminho de envio foi tocado.
6. O payload literal que a tela manda ao confirmar com um template escolhido.
