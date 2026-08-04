# Prompt E04 — Atendimento (domínio e casos de uso)

> Pré-requisito: E03b commitada. **Comece com sessão limpa.**
> Esta é a etapa do caminho crítico. `RNF-CRM-01` se aplica a tudo aqui.

---

**Etapa E04 — Agregados Atendimento e Mensagem, casos de uso e eventos de domínio.**

Nada de integração com WhatsApp nesta etapa — isso é a E05. Aqui é domínio, casos de uso e persistência.

## Dívida herdada da E03b — resolva primeiro

`lead.ultima_interacao_em` (V14) existe e **ninguém escreve nela**. Enquanto isso, o filtro `semRetornoDias` cai no `COALESCE` com `criado_em`.

Isso não é uma pendência cosmética. Se ficar assim, o filtro **não quebra — mente**: "sem retorno há 30 dias" passa a significar "criado há 30 dias", e leads ativos entram em campanha de reativação. Falha silenciosa, plausível e visível para o cliente final.

**Obrigação:** toda mensagem registrada atualiza `ultima_interacao_em` na **mesma transação**. Junto com os contadores `num_atendimentos` e `num_mensagens`, que seguem a mesma regra.

Escreva um teste que registre mensagem e verifique as três colunas na mesma transação. Se algum caminho de escrita de mensagem existir sem passar por lá, ele vai aparecer.

## O que construir

### 1. Agregados

**Atendimento** — `lead`, `canal`, `canal_credencial`, `atendente`, `status`, timestamps. Comportamento no agregado, não no serviço: `transferirPara(atendente)`, `finalizar()`.

**Mensagem** — remetente (tipo + id), tipo de mídia, conteúdo, `midia_metadados`, status de entrega, `enviado_em`.

Repositórios seguindo o padrão obrigatório — a regra ArchUnit genérica da E03a já cobre, mas o desenho (porta sem `findAll`/`findById` cru, implementação pacote-privada) é seu.

### 2. Casos de uso

| Caso de uso | Observação |
|---|---|
| `RegistrarMensagemRecebidaUseCase` | Cria atendimento se não houver aberto; atualiza contadores e `ultima_interacao_em` |
| `EnviarMensagemUseCase` | **Transfere o lead para quem enviou** (`RN-CRM-06`) |
| `TransferirAtendimentoUseCase` | Reatribui para atendente ou IA; gera evento |
| `FinalizarAtendimentoUseCase` | Encerra; atualiza `status_basico` do lead |

**Sobre `RN-CRM-06`:** enviar mensagem manual transferir o lead é a regra que dá o lead a quem trabalhou. Ela é a contrapartida do isolamento de agenda — e, como envolve comissão, merece teste explícito: atendente B envia mensagem em lead de A, e o lead passa a ser de B, com evento na timeline registrando quem transferiu.

### 3. Eventos de domínio

`MensagemRecebida`, `MensagemEnviada`, `AtendimentoTransferido`, `AtendimentoFinalizado`.

Consumidos por `@TransactionalEventListener(phase = AFTER_COMMIT)`. **Nada de listener síncrono dentro da transação** — a timeline, o audit log e (na E06) a notificação WebSocket são reações, não parte do ato de registrar a mensagem. Um listener lento vira latência no caminho crítico.

Nesta etapa, os listeners gravam em `evento_timeline` e `audit_log`.

### 4. Caminho crítico — três cuidados

Esta é a primeira etapa onde `RNF-CRM-01` deixa de ser teoria:

- **Use o `chatDataSource`** nos casos de uso de mensagem. Ele existe desde a E00 e nunca foi usado. É o Bulkhead: relatório pesado não pode roubar conexão do chat.
- **A tabela `mensagem` é particionada.** A partição `DEFAULT` e a verificação de boot já protegem, mas o caminho de escrita não deve assumir nada sobre partição.
- **Nada bloqueante no envio.** Se uma operação pode falhar ou demorar, ela é evento `AFTER_COMMIT` ou vai para fila (E07). Não entra na transação de registrar mensagem.

### 5. Testes

- Mensagem recebida cria atendimento quando não há aberto; reusa quando há
- Contadores e `ultima_interacao_em` atualizados na mesma transação — **teste explícito**
- `RN-CRM-06`: envio manual transfere o lead e registra na timeline
- Transferência e finalização geram eventos, e os listeners só rodam após commit
- Rollback da transação não deixa evento publicado nem contador incrementado
- RLS: atendente não lê atendimento de lead alheio (a política já existe; confirme que o novo caminho a respeita)

## Restrições

- Nada de Spring/JPA em `domain`
- Nenhuma integração externa nesta etapa
- Nenhum listener síncrono no caminho de mensagem

## Definição de pronto

- [ ] Os quatro casos de uso funcionando com teste
- [ ] `ultima_interacao_em` e contadores escritos na transação correta
- [ ] `RN-CRM-06` provado por teste
- [ ] Eventos disparando apenas após commit
- [ ] `chatDataSource` em uso no caminho de mensagem
- [ ] CI verde

Commit: `feat: agregados de atendimento e mensagem`.

Ao terminar, me diga se algum caso de uso precisou de operação que não coube em `AFTER_COMMIT` — é onde a fila da E07 vai ser necessária mais cedo do que o planejado.
