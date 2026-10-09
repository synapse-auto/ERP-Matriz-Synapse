# 69. Informações do chatbot no histórico do atendimento

Passagem para o **Dylan** (n8n) e runbook de habilitação por instância. Responde: o que o CRM passa a
oferecer, exatamente o que o n8n precisa chamar e em que ordem, o que acontece quando algo falha e como
ligar o recurso numa instância.

Base: `http://synapse-backend-internal:8080/internal/v1`, header `X-Synapse-Token: <SYNAPSE_TOKEN_INTERNO>`.
**O Swagger é a fonte da verdade** (`/swagger-ui`, tag "Atendimento interno").

---

## 1. O que é

Quando o chatbot transfere o atendimento para um humano, o n8n entrega ao CRM o que coletou. O CRM grava
um **snapshot interno** e a equipe vê, no próprio histórico do chat, um card:

> **Resumo da IA para o atendimento**
> Transferência para atendimento humano
> *(conteúdo recebido da automação, com quebras de linha)*
> 11:05

O que o card **é**: registro interno, imutável, atribuído à automação, entre as mensagens, em ordem
cronológica, que aparece em tempo real para quem tem acesso ao atendimento.

O que o card **não é**:

- **Não é mensagem.** Vive em tabela própria (`atendimento_informacao_chatbot`, V101). Não entra em
  `mensagem`, nunca na outbox de envio ao WhatsApp, não conta como mensagem enviada, custo do provedor,
  resposta humana, não lida, inatividade nem contexto do EV-05.
- **Não é o resumo da ficha.** `POST /internal/v1/atendimentos/{id}/resumo`, o botão Gerar/Regerar e o
  EV-05 continuam exatamente como estavam. Este contrato **não** escreve em `lead.resumo_ia`, e uma
  atualização posterior do resumo da ficha **não** reescreve o card.
- **Não é transferência.** Receber as informações não transfere, não muda responsável, participantes,
  comissão nem visibilidade.
- **Não é geração de IA.** O CRM só recebe, persiste e exibe o que o n8n mandar.

---

## 2. Contrato

```text
POST /internal/v1/atendimentos/{atendimentoId}/informacoes-do-chatbot
X-Synapse-Token: <SYNAPSE_TOKEN_INTERNO>
Idempotency-Key: <chave estável da ocorrência>
Content-Type: application/json
```

```json
{ "conteudo": "Nome: Maria\nInteresse: avaliação\nMelhor horário: manhã" }
```

```json
{
  "id": "5d1c7e0a-3b8e-4f4e-9d51-2a7f0c9a1b11",
  "atendimentoId": "1f2e0c3d-6a41-4b7a-8c10-9e5d2f7a4b66",
  "registradoEm": "2026-10-08T14:05:12.481Z"
}
```

A resposta traz só ids e instante. O texto recebido **não** volta e **não** é escrito em log.

### Regras do corpo

| Campo | Regra |
|---|---|
| `conteudo` | **Texto puro.** Obrigatório. O CRM mostra como texto: HTML e Markdown não são interpretados. |
| Tamanho | Até **4000 caracteres** por padrão, configurável por instância (`AUTOMACAO_INFORMACOES_CHATBOT_TAMANHO_MAXIMO`, máximo 20000). Acima disso: `422`. Não há truncamento silencioso. |
| Normalização | Espaços das pontas são aparados; `\r\n` e `\r` viram `\n`. A normalização vale **antes** do hash de idempotência. |
| Proibido | Vazio, só espaços ou caractere nulo (`\u0000`): `422`. |

### Destino: sempre o atendimento do caminho

O destino é **somente** o `{atendimentoId}` da URL. O CRM nunca procura "o atendimento aberto do lead" e
nunca decide por nome ou telefone. É isso que impede um callback atrasado de cair na conversa errada.

O atendimento precisa estar **`EM_ATENDIMENTO`** (com humano). Por isso a chamada vem **depois** da
transferência.

### Idempotência (obrigatória)

`Idempotency-Key` ausente ou em branco: `400`, antes de qualquer outra validação.

**Ordem das decisões:** chave → **replay de operação já concluída** → só para operação **nova**: flag
(`409`), conteúdo (`422`) e estado do atendimento (`409`). A configuração de hoje valida o que ainda não
aconteceu e **nunca invalida o que já foi concluído**: o replay devolve a resposta original mesmo que a
flag tenha sido desligada, o limite de caracteres reduzido ou o atendimento finalizado depois. O hash do
pedido usa só o texto normalizado, não o limite.

| Situação | Resposta |
|---|---|
| Mesma chave, mesmo atendimento, mesmo conteúdo (depois de normalizar) | `200` com a **resposta original**. Nada é gravado de novo, nenhum aviso em tempo real é reenviado. |
| Mesma chave, **conteúdo diferente** | `409` |
| Mesma chave, **outro atendimento** | `409` |

**Atenção, a tabela de chaves é compartilhada por todos os comandos internos** (transferir, responder,
finalizar, lembrete, negociação…). Reusar a chave da transferência aqui dá `409` (outra operação). Derive
uma chave própria e **estável**, por exemplo `<chave-da-transferência>:informacoes`. Nunca use
timestamp de execução: o retry viraria card novo.

### Respostas

| Status | Quando | Corpo |
|---|---|---|
| `200` | Registrado, ou retry da mesma ocorrência | `{id, atendimentoId, registradoEm}` |
| `400` | Corpo sem `conteudo` ou `Idempotency-Key` ausente | Problem Details |
| `401` | `X-Synapse-Token` ausente ou inválido (token de usuário não serve) | — |
| `404` | Atendimento inexistente | Problem Details |
| `409` | Chave reutilizada com outro conteúdo/atendimento; ou um dos `motivo` abaixo | Problem Details + `motivo` |
| `422` | Conteúdo vazio, com caractere nulo ou acima do limite | Problem Details |

Valores de `motivo` (campo aditivo do `409`):

| `motivo` | Significa | O que o n8n faz |
|---|---|---|
| `FUNCIONALIDADE_DESABILITADA` | A instância não habilitou o recurso. Nada foi gravado e a chave **não** foi reservada. | **Tratar como "não se aplica"**: seguir sem erro, sem retry. |
| `ATENDIMENTO_NAO_TRANSFERIDO` | Atendimento ainda `EM_IA`: a transferência não aconteceu. | Não enviar; a transferência é que precisa ser resolvida. |
| `ATENDIMENTO_FINALIZADO` | O callback chegou depois do fim do atendimento. | Descartar. Não tentar em outro atendimento do mesmo lead. |

`409` por chave reutilizada **não** traz `motivo`.

---

## 3. Ordem das chamadas e falhas

```text
1. n8n coleta as informações com o cliente (no próprio fluxo)
2. POST /atendimentos/{id}/transferir-proximo-humano   (ou /transferir)
        Idempotency-Key: T
      ├─ 2xx  → segue para o passo 3
      └─ 409 sem destino / qualquer erro → NÃO envia as informações; trata a transferência como hoje
3. POST /atendimentos/{id}/informacoes-do-chatbot
        Idempotency-Key: T:informacoes
```

- **A transferência nunca espera as informações.** Se o passo 3 falhar, a transferência já aconteceu e
  fica de pé. O CRM não depende de IA nem do n8n para transferir.
- **Retry só de rede e `5xx`**, com a mesma chave e backoff limitado. **Não** repetir `400`, `401`, `404`,
  `409` nem `422`.
- Falha definitiva no passo 3: a equipe atende normalmente, sem o card. Registrar apenas ids técnicos e
  status HTTP na execução do n8n. **Não registrar o conteúdo coletado em log nem em canal público.**
- Não há operação de edição nem de exclusão do card: um card errado não é corrigido, só se envia outro
  (chave nova) se fizer sentido.
- Nunca escrever direto no banco: a escrita direta não dispara tempo real nem respeita a idempotência.

### Callback atrasado e concorrência

Se a execução do n8n demorar e o atendimento for finalizado (ou o lead abrir outro atendimento) antes da
chamada, o CRM responde `409 ATENDIMENTO_FINALIZADO` e **não** grava em nenhum outro atendimento.

**Validar o estado e gravar são uma decisão só.** O CRM toma o lock do lead e do atendimento, na mesma
ordem de finalizar e transferir, e só então confere o estado e grava. Uma finalização ou devolução para a
IA em paralelo termina antes (o card é recusado: `ATENDIMENTO_FINALIZADO` ou
`ATENDIMENTO_NAO_TRANSFERIDO`) ou depois (o card já está gravado). Nunca no meio, num destino obsoleto.

---

## 4. Quem vê o card

- Leitura pela API autenticada: `GET /api/v1/atendimentos/{id}/informacoes-do-chatbot[?desde=&cursor=]` →
  `{"itens":[{"id","atendimentoId","conteudo","origem":"AUTOMACAO","registradoEm"}],"proximoCursor":"…"|null}`.
  **Nenhum card some:** a leitura é paginada por cursor opaco (do mais recente para o mais antigo; cada
  página sai em ordem cronológica; tamanho da página 50 por padrão), no mesmo espírito do histórico de
  mensagens. A tela acompanha a janela de mensagens que já carregou: `desde` é o instante da mensagem
  mais antiga carregada e os cards desse trecho vêm em páginas, uma de cada vez. Ao carregar mensagens
  mais antigas a janela cresce e os cards daquele trecho entram. Nenhuma consulta traz a conversa inteira.
  Cursor ou `desde` malformado: `400`.
- **Mesma visibilidade do atendimento, exatamente:** quem não alcança o atendimento recebe `404` (nunca
  `403`) e não lê o conteúdo. A regra mora numa única política (RLS de `atendimento`) e a tabela do card
  herda dela; o card **não amplia** o acesso a atendimentos de colegas. Um IT compara, usuário a usuário e
  cenário a cenário (dono, gestor, subgestor, atendente sem relação, participante ativo, participante que
  saiu, convite pendente, convite vencido, solicitação de entrada, atendimento em IA e finalizado), a
  resposta dos cards com a das **mensagens**. O que a regra existente concede, e que o card apenas herda,
  está em [`70-visibilidade-de-atendimentos-em-ia-e-finalizados.md`](./70-visibilidade-de-atendimentos-em-ia-e-finalizados.md).
- Tempo real: o evento `INFORMACOES_CHATBOT` chega pelo canal do atendimento **sem o texto** (só ids); a
  tela revalida pela API autorizada. A tela revalida uma vez a cada conexão do WebSocket (a primeira e as reconexões), para recuperar um card criado antes de o canal estar assinado.
  Não existe polling.
- Instância com a flag desligada: a leitura devolve lista vazia, a tela não faz a requisição e o
  contrato interno responde `409 FUNCIONALIDADE_DESABILITADA`.

---

## 5. Como habilitar na Femina

O recurso nasce **desligado em toda instância**. A migration V101 só cria a linha da flag com
`habilitado = FALSE`; ligar é operação pontual por instância, fora do Flyway:

```bash
# No banco da Femina (e somente nele):
psql "$DATABASE_URL_FEMINA" -v ON_ERROR_STOP=1 -f docker/provisionamento/habilitar-informacoes-do-chatbot.sql
```

Pré-requisito: a versão com a V101 já implantada na Femina (o script recusa se a flag não existir).

- Quem já está com a aba aberta precisa **recarregar a página uma vez** (a lista de flags fica no cache
  do navegador).
- **Desligar** (rollback do recurso, sem apagar nada):

  ```sql
  UPDATE feature_flag SET habilitado = FALSE WHERE chave = 'informacoes_chatbot_historico';
  ```

  Os cards já gravados ficam no banco, mas deixam de ser exibidos e de ser aceitos.
- Estrutural e demais instâncias: **não** executar o script. Nada muda para elas.
- Texto do card: vem do catálogo (`textos.json`, `atendimentos.informacoesChatbot`). A terminologia da
  instância (por exemplo "Paciente") entra pelo `textos.json` do filho, nunca no componente.

Variável opcional (default seguro, **sem ação obrigatória no Dokploy**):

| Variável | Padrão | Função |
|---|---|---|
| `AUTOMACAO_INFORMACOES_CHATBOT_TAMANHO_MAXIMO` | `4000` | Limite de caracteres do `conteudo`. Faixa 1–20000; fora dela o backend não sobe. |
| `AUTOMACAO_INFORMACOES_CHATBOT_TAMANHO_PAGINA` | `50` | Tamanho da página de cards da leitura (cursor). Não limita quantos cards existem nem quantos a tela alcança: define só quantos vêm por requisição. |

---

## 6. Checklist do n8n

- [ ] Chamar o passo 3 **somente** depois de um 2xx da transferência.
- [ ] `Idempotency-Key` própria e estável (`<chave-da-transferência>:informacoes`), nunca timestamp.
- [ ] Enviar texto puro, com `\n` entre as linhas, dentro do limite da instância.
- [ ] Tratar `409` com `motivo` `FUNCIONALIDADE_DESABILITADA` como "não se aplica" (o mesmo workflow
      serve instâncias com e sem o recurso). No nó HTTP do n8n isso exige **"continue on fail"** (ou
      `neverError`) para o status `409`; sem isso, toda transferência numa instância com a flag
      desligada vira execução com erro e pode interromper os nós seguintes.
- [ ] Retry só de rede/`5xx`, mesma chave, backoff limitado.
- [ ] Falha do passo 3 não desfaz nem bloqueia a transferência.
- [ ] Nada de conteúdo coletado em log ou em canal público.
- [ ] Credencial `SYNAPSE_TOKEN_INTERNO` só em credential do n8n, nunca no JSON do workflow.

Os workflows de produção **não** foram alterados por esta entrega.

---

## 7. Evidência

- `InformacoesDoChatbotIT` (HTTP + Postgres real): registro sem efeito colateral (sem `mensagem`, sem
  outbox, responsável/participantes/resumo da ficha intactos), retry sem duplicar, **replay depois de
  desligar a flag e depois de finalizar o atendimento**, chave com conteúdo ou destino diferente,
  atendimento em IA, callback atrasado sem contaminar o atendimento atual, 404, 422, 400 (inclusive chave
  em branco), 401, flag desligada (sem reserva de chave), **leitura paginada sem perder card, janela
  `desde` e cursor malformado**, e o contrato publicado no OpenAPI (parâmetros, segurança e códigos).
- `InformacoesDoChatbotConcorrenciaIT`: finalização e devolução à IA em curso (o card espera o lock do
  lead, vê o novo estado e é recusado sem gravar) e 12 corridas reais contra `/finalizar` e `/modo-ia`.
  Sem o lock, estes testes reprovam (verificado removendo-o).
- `InformacoesDoChatbotVisibilidadeIT`: paridade de status entre cards e mensagens para cada usuário em
  cada cenário de acesso.
- `RlsInformacoesDoChatbotIT`: política de leitura herdada de `atendimento`, só o contexto de serviço
  insere, UPDATE/DELETE negados (privilégio revogado e sem política), chave única.
- `RegistrarInformacoesDoChatbotIdempotenciaTest` e demais unitários: ordem das decisões, replay imune à
  configuração atual, lock na ordem de finalizar, páginas e cursor, relay de tempo real. No front, o card,
  a intercalação com as mensagens e o hook (janela, páginas em sequência, flag desligada = nenhuma
  requisição).
