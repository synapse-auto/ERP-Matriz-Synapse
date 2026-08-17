# Prompt E21b — Transferência pela Automação, e o card de Resolução por IA

> Leia `AGENTS.md`. Desbloqueia os Blocos 2 e 3 da E21.
> Commite e faça push por bloco. Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## O que a E21 encontrou

`docs/01-arquitetura-geral.md` e `docs/04-adrs-e-api.md` descrevem `automation.events.transferir-lead`. **Não existe consumidor, endpoint nem chamada.** É a quinta vez que um documento deste projeto descreve algo que nunca foi construído, e esta é a mais séria: não é uma métrica, é **o caminho pelo qual a IA entrega a conversa a um humano**.

Hoje só há transferência com JWT humano. A IA não transfere; alguém precisa assumir da fila.

Duas decisões já tomadas, para você não reabrir:

**1. Vai por `/internal/v1`, não pela fila.** A documentação diz RabbitMQ, mas o contrato que de fato existe é o síncrono — o n8n já fala com `http://synapse-backend-internal:8080/internal/v1` pela overlay, com `X-Synapse-Token`. Consumidor de fila é máquina nova para o mesmo resultado, e numa transferência o cliente está esperando do outro lado: confirmação imediata vale mais que desacoplamento. Se o CRM estiver fora, a transferência não faz sentido de qualquer forma.

**2. Ator técnico é `ator_tipo = AUTOMACAO`, com `ator_id` nulo.** O enum `origem_evento` já existe e o `audit_log` já o usa. **Não crie um usuário "Automação" na tabela `usuario`** — ele apareceria na tela de Equipe, no ranking de vendas e na contagem de gente da operação.

Atualize `docs/01` e `docs/04` para descrever o que passou a existir. A fila sai da documentação.

## Bloco 1 — O endpoint de transferência

`POST` em `/internal/v1`, autenticado por `X-Synapse-Token`, transferindo um atendimento da IA para um atendente.

Reaproveite o caso de uso de transferência que já existe — **não escreva um segundo**. O que muda é a identidade de quem executa e a autorização de entrada, não a regra.

**As regras comerciais continuam valendo integralmente.** `RN-CRM-01`, `RN-CRM-02` e `RN-CRM-06` existem porque atendente trabalha por comissão. Um endpoint que a Automação chama **não** pode ser um caminho lateral para entregar lead Potencial a um atendente escolhido a dedo — a E13 já pegou exatamente esse defeito no `/transferir` humano. A distribuição segue a regra de roteamento, não o corpo da requisição.

O evento `ATENDIMENTO_TRANSFERIDO` é gravado com `ator_tipo = AUTOMACAO`.

Testes obrigatórios:

- sem token, ou com token errado: 401/403, e **nada transferido**
- JWT humano **não** abre este endpoint
- transferência pela Automação respeita a regra de distribuição; não aceita destinatário arbitrário
- o evento é gravado com `AUTOMACAO`, não com um usuário

## Bloco 2 — Card "Resolução por IA"

Agora que os três caminhos de transferência registram evento, a premissa se sustenta:

> resolução por IA no período = atendimentos **finalizados** no período **sem nenhum evento de transferência** ÷ atendimentos finalizados no período

É o que o card diz embaixo do número: "Sem transferência humana".

Confirme que os três caminhos ficam cobertos:

| Caminho | Evento |
|---|---|
| Envio/assunção manual | `LEAD_TRANSFERIDO_POR_ENVIO` |
| Transferência manual e reatribuição por gestor | `ATENDIMENTO_TRANSFERIDO` |
| Transferência pela Automação | `ATENDIMENTO_TRANSFERIDO` com `ator_tipo = AUTOMACAO` |

**Teste que prova o número:** um atendimento finalizado com transferência **não** entra no numerador. É a única forma de garantir que a taxa não fique otimista em silêncio.

## Bloco 3 — Sobras da E21

- `feature_flag.dashboard` no script de provisionamento, e o `README.md` do provisionamento documentando que instâncias já existentes precisam do `UPDATE` manual
- `docs/14-pendencias-de-funcionalidade.md`: remova o que E21 e E21b entregaram
- Confirme o resultado da run `#31563033266`, que ficou em execução, e informe se ficou verde

## Definição de pronto

- [ ] `POST` de transferência em `/internal/v1`, reaproveitando o caso de uso existente
- [ ] `ator_tipo = AUTOMACAO`, sem usuário fantasma
- [ ] **Regra de distribuição respeitada** — não aceita destinatário escolhido no corpo
- [ ] Testes negativos: sem token, token errado, e JWT humano
- [ ] `docs/01` e `docs/04` corrigidos; a fila sai da documentação
- [ ] Card de Resolução por IA, com teste provando que transferência exclui do numerador
- [ ] Flag da Dashboard no provisionamento e documentada
- [ ] `docs/14` atualizado
- [ ] CI verde com **número da run**

Commit por bloco: `feat: transferência pela automação`, `feat: card de resolução por IA`.

No relatório: diga se encontrou **outros** contratos documentados sem implementação. Cinco já apareceram; se houver um sexto, é hora de varrer `docs/01` e `docs/04` inteiros contra o código, não esperar tropeçar no próximo.
