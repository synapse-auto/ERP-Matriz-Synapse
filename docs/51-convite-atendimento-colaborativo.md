# 51. Convite e atendimento colaborativo

Diagnóstico e decisão sobre convidar um colega para o mesmo atendimento sem trocar o
responsável. Reprodução feita com backend e PostgreSQL reais (perfil `dev`, banco descartável),
dois atendentes da seed (A = Ana, B = Bruno) e um terceiro não convidado (C = Caio), com dados
sintéticos. Nenhum lead real foi usado.

## 1. Como reproduzir

```bash
# banco descartável já migrado até a última versão (ver docs/48 §1 para o runner da V73)
psql "$URL" -v ON_ERROR_STOP=1 -f frontend/e2e/fixtures/convite-colaborativo.sql
cd frontend
API_URL=http://localhost:8091 \
PSQL="docker exec -i synapse-postgres psql -U synapse -d <banco> -At" \
node e2e/_reproduzir-convite-colaborativo.mjs
```

O script abre duas sessões STOMP (A e B), executa o fluxo pela API HTTP e, em cada passo, grava
`lead.atendente_responsavel_id`, `atendimento.atendente_id`, pedidos de entrada, participantes
ativos, o status HTTP do histórico para A/B/C e os eventos recebidos por sessão. Sai com código 1
quando algum passo troca o responsável sem a ação explícita "Transferir".

## 2. Resultado antes da correção

Saída completa em `evidencias/convite-colaborativo/antes-reproducao.jsonl`.

| Passo | HTTP | Lead | Atendimento | Participantes | Histórico A/B/C | Eventos |
|---|---|---|---|---|---|---|
| 0. inicial | — | A | A | — | 200/404/404 | — |
| 1. A convida B | 200 | A | A | — (convite PENDENTE) | 200/200/404 | B: `CONVITE_ATENDIMENTO` |
| 2. B vê o convite | 200 | A | A | — | 200/200/404 | cartão em Pendentes |
| 3. B aceita | 200 | A | A | B | 200/200/404 | A: `RESPOSTA_PEDIDO_ENTRADA` |
| 4. A e B abrem | — | A | A | B | 200/200/404 | — |
| **5. B envia** | 200, `transferiuOLead=true` | **B** | **B** | B | **404**/200/404 | A: `MENSAGEM` + **revogação**; B: `TRANSFERENCIA` |
| 6. A envia | **404** | B | B | B | 404/200/404 | — |
| 7. C envia | 404 | B | B | B | 404/200/404 | — |

Conclusão, separando as três hipóteses do prompt:

1. **Clique em "Convidar" ou aceitação** — não transferem. O frontend chama só
   `POST /convidar` e `GerenciarParticipacaoAtendimentoUseCase` não toca no responsável.
2. **Primeira mensagem do convidado** — **é aqui que a propriedade muda.** `EnviarMensagemUseCase`
   aplica a RN-CRM-06 a todo envio manual, inclusive de participante ativo (decisão do commit
   `c7c5a65`, que removeu a exceção criada em `6cabcf2`). B vira dono do lead e do atendimento.
3. **Colaboração real** — consequência direta do item 2: A deixa de ser dono e não é participante,
   então perde a RLS, recebe revogação no WebSocket e não consegue mais ler nem responder (404).
   "Dois atendentes no mesmo lead" durava exatamente até o convidado falar.

### Defeitos adicionais reproduzidos

- **Convite expirado continua concedendo leitura.** A política RLS da V78 libera
  `atendimento`/`lead` para `convite.status = 'PENDENTE'` sem olhar a validade
  (`atendimento.pedido-entrada-expiracao-minutos`, 30 min). Com o convite envelhecido 3 h, B
  continuou lendo o histórico (200) e o cartão continuou em Pendentes.
- **Aceitar convite expirado responde 500.** `responder` lança `IllegalStateException`, que nenhum
  `@ExceptionHandler` traduz.
- **Convite expirado nunca é substituído.** O pedido expirado continua `PENDENTE` no banco; o índice
  `ux_pedido_convite_pendente` impede um novo e `POST /convidar` devolve o pedido velho com
  `jaExistia=true`. O destinatário não consegue mais ser convidado para aquele atendimento.
- **Convidado com convite apenas pendente consegue enviar e herdar o lead.** A V78 pôs o convite
  pendente dentro das políticas `FOR ALL`, então a RLS liberava também o `SELECT … FOR UPDATE` e o
  `UPDATE` do envio. Reproduzido: B, sem aceitar, enviou (200, `transferiuOLead=true`) e virou dono
  do lead e do atendimento de A.

### Defeito visual

Capturas em `evidencias/convite-colaborativo/antes-modal-{1366,1024,768,390}.png` (Ana,
convite aberto pelo cabeçalho, lista com três candidatos, um com nome de 62 caracteres):

- O nome longo é **cortado no meio da palavra, sem reticências e sem `title`**: o conteúdo mede
  452 px dentro de um botão de 350 px (324 px em 390 px de largura) em todas as larguras.
- Um clique no nome já envia o convite: não há seleção, confirmação nem indicação do que acontece.
- O modal não mostra quem é o responsável nem quem já participa, e reaproveita sem distinção a lista
  da transferência — o que alimenta a leitura de que "Convidar" transfere.

Com nomes curtos o layout não quebra; nenhum outro defeito de geometria foi observado.
## 3. Decisão sobre a RN-CRM-06

Requisito desta etapa: convidar e aceitar não transferem; responsável e participante atuam no
mesmo atendimento. "Atuar" inclui responder, então a RN-CRM-06 ganha **uma exceção explícita**:

| Quem envia | Efeito do envio manual |
|---|---|
| Responsável | Nenhuma troca (já é dono) |
| Participante por **convite aceito** (`origem = CONVITE`) | **Não transfere.** Lead, atendimento e comissão continuam do responsável |
| Participante por **pedido aprovado pelo responsável** (`origem = PEDIDO_APROVADO`) | **Não transfere** |
| Participante por **entrada direta** de gestor/subgestor ou **abertura pela Agenda** (`origem = ENTRADA_DIRETA`) | Transfere, como antes |
| Gestor/subgestor que não participa | Transfere, como antes |
| Convidado com convite **pendente** | Não envia (404): o convite só dá leitura |
| Quem saiu, foi recusado ou tem convite expirado | Sem acesso (404) |
| Lead sem dono (IA) | Quem fala assume, inclusive participante — não há responsável a preservar |

Por que a exceção é pela **origem** e não por "qualquer participante ativo": o fluxo da Agenda
(`POST /novo-contato` e `/leads/{id}/novo`) registra participação automaticamente para quem abre o
contato de um colega, sem consentimento do responsável. Estender a exceção a essa participação
permitiria a qualquer atendente entrar e responder em conversa alheia sem assumir — a "colaboração
silenciosa" que o requisito proíbe. Consentimento existe quando o convidado aceita um convite
(emitido por responsável, participante ou gestor) ou quando o responsável aprova um pedido.

Mudar o responsável de um atendimento colaborativo é só pela ação explícita **Transferir**.

## 4. O que mudou

| Camada | Mudança |
|---|---|
| `V87` | Convite pendente sai das políticas `FOR ALL` de `lead`/`atendimento` e volta como política `FOR SELECT` própria, limitada à validade (`app_validade_pedido_entrada()`) |
| `V86` | `atendimento_participante.origem` (`ENTRADA_DIRETA`, `CONVITE`, `PEDIDO_APROVADO`), com backfill das participações ativas a partir dos pedidos aprovados |
| `EnviarMensagemUseCase` | Participante consentido segue o caminho que preserva o responsável (só retira da IA); evento `MensagemEnviada` com `participante=true`, `transferiu=false` |
| Participação | Convite vencido é marcado `EXPIRADO` antes de criar outro; aceitar/recusar expirado ou já respondido responde **409** (antes 500); pedido inexistente 404 |
| Pendentes | Convite vencido sai da visão Pendentes |
| Modal | Seleção + "Enviar convite", responsável e participantes separados, nomes longos com reticências e `title`, erro de carga distinto de lista vazia |
| Cabeçalho | Participante vê "suas mensagens não transferem o atendimento" |

Capturas depois da correção: `evidencias/convite-colaborativo/depois-modal-*.png`.

## 5. Fora do escopo, registrado

- **Não existe revogação de convite** pelo convidador: o convite pendente só deixa de valer por
  recusa ou expiração. O responsável também não remove um participante; o participante sai sozinho.
- **Pedido de entrada (`SOLICITACAO`) expirado também trava um pedido novo**: `app_registrar_pedido_entrada`
  devolve o pedido vencido, como acontecia com o convite. Não corrigido aqui.
- O convidado com convite pendente lê o histórico completo antes de aceitar (decisão da V78, mantida).

## 6. Defeitos de tempo real e de tela achados no E2E de duas sessões

O E2E (`frontend/e2e/convite-colaborativo.spec.ts`, navegador real, backend e PostgreSQL reais)
expôs quatro defeitos pré-existentes no frontend que impediam a colaboração mesmo com o backend certo:

| Defeito | Causa | Correção |
|---|---|---|
| A recebia o frame `MENSAGEM` de B pelo WebSocket, mas a bolha só aparecia ao recarregar | `processarEventoCanonico` religava os incrementais com `ciclo: cicloJaSincronizado ?? -1`; com dois eventos canônicos em sequência (convite, aceite, ou o mesmo evento por `/notificacoes` e pela conversa) o segundo via a liberação nula e gravava ciclo `-1`, que nunca casa — todo frame seguinte era descartado | O snapshot que responde ao evento é liberado com o ciclo de conexão vigente (`cicloAtualRef`) |
| Aceitar o convite gravava a participação, mas a tela mostrava "Não foi possível atualizar sua participação" | `POST /aprovar` responde 200 sem corpo e `apiFetch` só tolerava corpo vazio em 204; `response.json()` lançava | `apiFetch` devolve `null` para 200 sem corpo (também corrige `Optional` vazio de `pedido-entrada/meu` e `resumo-ia`, que viravam erro) |
| `GET /pedido-entrada/meu` disparado a cada render do cabeçalho (centenas por minuto com a conversa aberta) e `GET /pedidos-entrada` em 404 repetido para quem não é responsável | `useRemoteParticipation` chamava `carregar` em todo render | Uma busca por chave; nova busca só via `invalidarParticipacao`; pendentes só para o responsável |
| A resposta do convidado chegava sem autor na tela de A (parecia da responsável) | O frame de tempo real não traz `remetenteNome` e o fallback só conhecia o responsável | `nomeDaAutoria` também procura nos participantes ativos do snapshot |

Medido no navegador após as correções: aceite mostra sucesso; `pedido-entrada/meu` caiu de centenas
de chamadas para 4 em 8 s (carga inicial e invalidações por evento).

Capturas: `evidencias/convite-colaborativo/depois-duas-sessoes-{ana,bruno}.png` e
`depois-e2e-modal-390.png`.

### Ainda aberto

- **O convidado não carrega a ficha do lead** (`GET /api/v1/leads/{id}` e `/tags` respondem 404): a
  `VisibilidadeLeadSpecification` não considera participação, só a RLS do caminho de mensagem
  considera. B lê e responde a conversa, mas o painel de detalhes do lead fica indisponível.
  Mudar isso altera a RN-CRM-01 da Specification; não foi feito.
- O cartão da conversa não aparece nas visões Ativos/Pendentes de quem é só participante.

## 7. Operador e matriz de recebimento (04/10/2026)

`OPERADOR` tem o mesmo recorte operacional do ATENDENTE, não a entrada direta da gestão.
Convites para esse destino exigem `atendimentos.receber_de_<papel da origem>` concedida no
perfil/exceção efetiva do convidado, ativo e com permissão para responder. A lista e o POST
direto verificam a mesma política (docs/47 §15). O convite duplicado mantém a idempotência
existente. Aceitar dá participação `CONVITE` sem alterar responsável/lead; não dá visão global.
O rodízio e os endpoints internos da automação não incluem o Operador.
