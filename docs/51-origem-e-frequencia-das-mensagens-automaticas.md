# 51 — Origem e frequência das mensagens automáticas (E219)

Card: "várias mensagens ainda não identificadas sendo enviadas para vários clientes muitas vezes". Não é
duplicidade (isso é o `docs/49`/`docs/50`): é **mensagem automática demais** (follow-up, fidelização,
festiva, aniversário…) e **sem origem**. Na FMNA, as 642 mensagens de IA dos últimos 7 dias tinham
`remetente_id` nulo.

**Não provado:** qual caminho gerou o excesso. Este documento mapeia os caminhos (Bloco 0) e entrega
defesas que valem para qualquer um deles: origem gravada (Bloco 1), reserva com política de frequência
(Bloco 2) e a proposta de contenção (Bloco 3). **O excesso só para de fato quando o n8n adotar a
reserva**: o CRM não envia follow-up, fidelização, festiva nem aniversário, e quem chama o provedor é o
n8n.

## Bloco 0 — caminhos que produzem mensagem automática

Linhas conferidas no código desta entrega. "Sem controle" = sem deduplicação por ocorrência **e** sem
limite de frequência antes desta etapa.

| Caminho | 1. Gatilho/janela | 2. Quem envia e como o CRM sabe | 3. Dedup (lead, regra, ocorrência) | 4. Cooldown/teto | 5. Remetente/origem gravados | 6. Liga/desliga | Controle |
|---|---|---|---|---|---|---|---|
| **Follow-up** — regras em `AutomationConfigInternalController.java:140` (`GET /internal/v1/regras/follow-up`); fila `follow_ups_temporary` no n8n | Executor e janela no **n8n** (fora do repositório) | n8n direto no provedor; o CRM só sabe se o n8n chamar `/mensagens-enviadas` ou `/responder` | Não, no CRM | Não | `IA`, `remetente_id` nulo, sem origem | `regra_follow_up.ativo` (V7) só filtra a lista; a fila do n8n não é vista pelo CRM | **Sem controle** |
| **Fidelização** — `AutomationConfigInternalController.java:149` (`/regras/fidelizacao`) | n8n | n8n direto | Não | Não | `IA`, nulo | `regra_fidelizacao.ativo` (V7) | **Sem controle** |
| **Festivas** — `AutomationConfigInternalController.java:188` (`/mensagens-festivas/hoje`) | n8n consulta; a lista é a mesma **o dia inteiro** | n8n direto | Não: cada consulta no dia devolve os mesmos alvos | Não | `IA`, nulo | `mensagem_festiva.ativo` por data | **Sem controle** |
| **Aniversário** — `AutomationConfigInternalController.java:203` (`/fidelizacao/aniversariantes-hoje`), `AniversariantesDoDiaUseCase.java:32` | n8n consulta; mesma lista o dia inteiro | n8n direto | Não | Não | `IA`, nulo | `fidelizacao.aniversario.habilitado` (V74): desligado, a lista sai vazia | **Sem controle** (com chave) |
| **Avaliação pós-atendimento** — `FinalizarAtendimentoUseCase.java:157` → `PrepararAvaliacaoDeEncerramento` → `PublicadorDeAvaliacao.java:40` | Cada finalização (inclusive a automática por inatividade, que pode finalizar em lote) | CRM manda o evento ao webhook do n8n; o n8n envia os botões | 1 evento por atendimento (outbox) | **Não por lead**: o mesmo lead com vários atendimentos finalizados recebe várias | `IA`, nulo | `avaliacao_atendimento.habilitada` (V55) é lida **pelo n8n**; o CRM só para sem `AUTOMACAO_AVALIACAO_URL` | Parcial |
| **`POST /atendimentos/{id}/responder`** — `TransferenciaAutomacaoInternalController.java:99` → `ResponderAtendimentoDaAutomacaoUseCase.java:92` | Chamado pelo n8n; só em `EM_IA` | CRM grava e envia pela outbox | `Idempotency-Key` (V80), estável só se o n8n usar chave estável | Não | `IA` (`Remetente.ia()`), nulo | Não | Idempotência sim, frequência não |
| **`POST /atendimentos/{id}/mensagens-enviadas`** — `MensagensEnviadasAutomacaoInternalController.java:55` → `RegistrarMensagemEnviadaDaAutomacaoUseCase.java:228/234/243` | n8n registra o que já enviou | n8n direto; o CRM sabe pelo POST | Por `wamid` de saída (V29) e, se o n8n reservar, pela chave V84 (`docs/50`) | Não | `IA`, nulo | Não | Registro, não decisão |
| **Mensagem programada** — `AgendadorDeMensagensProgramadas.java:25` → `ProcessarMensagemProgramadaUseCase.processar` | Job de 1 s; agendada por um atendente | CRM pela outbox | `reservarVencida`: uma saída por agendamento | n/a (humana) | `ATENDENTE` (dono do agendamento) | Cancelar a programada | Controlado |
| **Lembretes (E51)** — `AtendimentosAutomacaoInternalController.java:117` → `CriarLembreteDaAutomacaoUseCase` | n8n cria | Não há mensagem ao cliente: é lembrete interno ao atendente | n/a | n/a | n/a | n/a | Não envia ao cliente |
| **Transferências** — `TransferenciaAutomacaoInternalController.java:120` | n8n | Nenhuma mensagem ao cliente (`TransferirAtendimentoDaAutomacaoUseCase` não grava mensagem) | n/a | n/a | n/a | n/a | Não envia ao cliente |

Onde o excesso é possível hoje: **follow-up, fidelização, festivas e aniversário** (nenhum limite em
lugar nenhum do CRM; o executor é o n8n), **avaliação** (sem limite por lead) e qualquer proativa que o
n8n mande por `/responder` ou registre em `/mensagens-enviadas`. Nenhum caminho gravava origem.

## Bloco 1 — origem de toda mensagem automática

**Onde fica:** tabela lateral `mensagem_origem_automacao` (V85), uma linha por mensagem automática,
chave `mensagem_id`. **Não** é coluna em `mensagem`: a tabela é particionada e tem `FORCE ROW LEVEL
SECURITY`; um `ALTER` nela trava todas as partições (lição da V73), e mensagens antigas não precisam de
nada. Mesmo padrão da `mensagem_automacao_idempotencia` (V29): sem FK para a PK composta da partição.

| Campo | Conteúdo |
|---|---|
| `tipo` | `RESPOSTA_IA`, `FOLLOW_UP`, `FIDELIZACAO`, `FESTIVA`, `ANIVERSARIO`, `AVALIACAO`, `LEMBRETE`, `OUTRO`, `PROGRAMADA`, `NAO_INFORMADA` |
| `regra_id` | id da regra/fluxo (texto até 100); na `PROGRAMADA`, o id do agendamento |
| `execucao_id` | id da execução do n8n (até 200) |

Quem grava, na mesma transação da mensagem:

- `POST /mensagens-enviadas` e `POST /responder`: campos opcionais `origemTipo`, `origemRegraId` e
  `origemExecucaoId`. Ausentes ou com tipo desconhecido, a mensagem é registrada normalmente com
  `NAO_INFORMADA` e o log recebe `[ORIGEM_NAO_INFORMADA] <caminho> ... mensagem=<id> atendimento=<id>`.
  O registro nunca é recusado por causa da origem: a mensagem pode já ter saído no provedor.
- Com `chaveDeEnvio` de uma reserva proativa, vale a origem **da reserva** (foi ela que a política
  autorizou).
- Job de mensagens programadas: `PROGRAMADA`.

**Visão por origem/dia:** `GET /internal/v1/envios-automacao/resumo-por-origem?de=AAAA-MM-DD&ate=AAAA-MM-DD`
(somente leitura, `X-Synapse-Token`, até 93 dias, dias no fuso da instância). Devolve
`{dia, origem, mensagens, leads}`. `SEM_ORIGEM_REGISTRADA` conta as mensagens da IA sem linha de origem
(todo o histórico anterior à V85). A consulta equivalente em SQL está no `docs/18` (§4.9).

## Bloco 2 — reserva com política de frequência

```
1. POST /internal/v1/leads/{leadId}/envios-proativos/reservas
     {"tipo":"FOLLOW_UP","regraId":"<id da regra>","ocorrencia":"<o que torna o envio único>",
      "chave":"<chave estável>","execucaoId":"<id da execução>"}
     201 podeEnviar=true                 -> pode enviar
     200 podeEnviar=false, motivo=...    -> NÃO enviar
     409                                 -> chave já usada com outro lead/tipo/regra/ocorrência
     400                                 -> campo inválido ou tipo não proativo (RESPOSTA_IA, PROGRAMADA)
2. envia ao provedor (sem retry automático no nó de envio)
3. POST /internal/v1/atendimentos/{id}/mensagens-enviadas  {..., "chaveDeEnvio": "<mesma chave>"}
     -> registra a mensagem, grava a origem e fecha a reserva NA MESMA TRANSAÇÃO
```

| Motivo (`podeEnviar=false`) | Significado |
|---|---|
| `CHAVE_JA_USADA` | reexecução/retry do mesmo envio; `reserva.estado` diz se já saiu |
| `OCORRENCIA_JA_REGISTRADA` | a mesma (lead, tipo, regra, ocorrência) já foi reservada com outra chave |
| `AUTOMACAO_PROATIVA_DESLIGADA` | chave geral `automacao_proativa.habilitada=false` |
| `TIPO_DESLIGADO` | `automacao_proativa.<tipo>.habilitada=false` |
| `COOLDOWN` | outro envio do mesmo tipo ao lead dentro de `cooldown_horas`; `liberadoApos` informa quando |
| `TETO_DIARIO` | o lead já recebeu `teto_diario_por_lead` proativas hoje (fuso da instância); `liberadoApos` = meia-noite |

Regras de implementação:

- **Atômica:** PK na chave, índice único em (lead, tipo, regra, ocorrência) e `pg_advisory_xact_lock` por
  lead. A trava serializa as decisões do mesmo lead: duas ocorrências diferentes chegando juntas não
  furam o teto. Decisões "não envie" não gravam linha.
- **409 (padrão V80):** `requisicao_hash` = SHA-256 de (lead, tipo, regra, ocorrência). `execucaoId` fica
  fora do hash: a reexecução do mesmo envio tem outro id de execução.
- **O que conta para cooldown/teto:** reservas (com ou sem resultado) **mais** mensagens registradas com
  origem proativa sem reserva, sem contar duas vezes. `RESPOSTA_IA`, `PROGRAMADA` e `NAO_INFORMADA`
  nunca contam e nunca são bloqueadas: a resposta ao lead não passa por aqui.
- **Conferência:** `GET /internal/v1/envios-proativos/pendentes?reservadosAntesDe=<instante>` lista as
  reservas `RESERVADO` sem registro (até 100). O envio pode ter saído: confira no provedor/execução e,
  se saiu, registre com a `chaveDeEnvio`. **Nunca reenviar a partir desta lista.**

### Parâmetros (por instância, `configuracao_automacao`, editáveis sem deploy)

| Chave | Semeado | Sugestão conservadora (**confirmar antes do deploy**) |
|---|---|---|
| `automacao_proativa.habilitada` | `true` | `true` |
| `automacao_proativa.<follow_up\|fidelizacao\|festiva\|aniversario\|avaliacao\|lembrete\|outro>.habilitada` | `true` | `true`; desligar o tipo que a consulta de disparos apontar |
| `automacao_proativa.cooldown_horas` | `0` (desligado), faixa 0–720 | `24` |
| `automacao_proativa.teto_diario_por_lead` | `0` (desligado), faixa 0–50 | `2` |

Os padrões semeados **não mudam o comportamento atual**. Valor ausente ou inválido volta ao padrão com
`[ALERTA_CONFIG_AUTOMACAO]` no log. O cache de configuração (`AUTOMACAO_CONFIG_CACHE_TTL`, padrão 5 min)
vale também aqui: a mudança feita pela tela pode levar até o TTL para valer.

## Bloco 3 — contenção imediata (proposta, **não aplicada**)

Nenhuma chave nova funciona antes do deploy desta entrega **e** de o n8n adotar a reserva. Até lá, o
que existe hoje:

| Ação (responsável decide) | O que interrompe | O que **não** interrompe |
|---|---|---|
| `fidelizacao.aniversario.habilitado=false` (tela de Automação) | `/fidelizacao/aniversariantes-hoje` passa a devolver lista vazia | Aniversário que o n8n calcule por conta própria ou já tenha enfileirado |
| Desativar a data em `mensagem_festiva` (tela) | `/mensagens-festivas/hoje` deixa de devolvê-la | Envios já disparados no dia; festivas fora dessa tabela |
| Desativar regras em `regra_follow_up` / `regra_fidelizacao` (tela) | Somem de `/regras/*` | **A fila `follow_ups_temporary` do n8n:** follow-ups já agendados continuam. Só param pelo n8n |
| `avaliacao_atendimento.habilitada=false` | Se o workflow respeitar a chave (EV-08), o n8n deixa de mandar os botões | O CRM continua enfileirando o evento; parar no CRM exige remover `AUTOMACAO_AVALIACAO_URL` (Dokploy, não proposto aqui) |
| Depois do deploy + n8n com reserva: `automacao_proativa.<tipo>.habilitada=false` ou a geral | Toda nova reserva do tipo (ou de todas) responde "não envie" | Fluxos do n8n que enviem **sem** reservar |

**Os follow-ups do n8n só param pelo n8n.** O CRM não vê nem controla `follow_ups_temporary`.

## Especificação para o n8n (Dylan)

Sem acesso ao workflow (não versionado), os nós são descritos pela função:

1. **Todo nó que manda mensagem proativa** (follow-up, fidelização, festiva, aniversário, avaliação,
   lembrete ao cliente, outras), em todos os sub-workflows:
   - antes do envio: `POST /internal/v1/leads/{leadId}/envios-proativos/reservas`;
   - IF `podeEnviar`; se `false`, **não enviar** e registrar o `motivo` na execução;
   - envio ao provedor **sem retry automático** no nó;
   - `POST /internal/v1/atendimentos/{id}/mensagens-enviadas` com `chaveDeEnvio` = a mesma chave.
2. **Chave e ocorrência estáveis** (a mesma em toda reexecução):

   | Tipo | `regraId` | `ocorrencia` | `chave` sugerida |
   |---|---|---|---|
   | FOLLOW_UP | id da `regra_follow_up` (ou da linha em `follow_ups_temporary`) | data/hora de referência do follow-up | `fu:<leadId>:<regraId>:<ocorrencia>` |
   | FIDELIZACAO | id da `regra_fidelizacao` | data da última interação que disparou | `fid:<leadId>:<regraId>:<ocorrencia>` |
   | FESTIVA | id da `mensagem_festiva` | `AAAA-MM-DD` da data | `fest:<leadId>:<regraId>:<data>` |
   | ANIVERSARIO | vazio | ano (`AAAA`) | `aniv:<leadId>:<ano>` |
   | AVALIACAO | vazio | id do atendimento finalizado | `aval:<atendimentoId>` |
   | LEMBRETE | id do compromisso | data/hora do compromisso | `lemb:<leadId>:<regraId>:<ocorrencia>` |

   Nunca usar UUID aleatório nem id de execução na chave: isso anula a proteção.
3. **Origem em toda mensagem**, inclusive respostas: em `/mensagens-enviadas` e `/responder`, enviar
   `origemTipo` (`RESPOSTA_IA` para resposta ao lead), `origemRegraId` e `origemExecucaoId`
   (`{{$execution.id}}`). Sem isso, a mensagem entra como `NAO_INFORMADA` e o CRM avisa no log.
4. **Conferência periódica:** `GET /internal/v1/envios-proativos/pendentes?reservadosAntesDe=<agora−5min>`;
   para cada item, conferir no provedor e registrar se saiu. Não saiu ⇒ decisão humana.
5. **`follow_ups_temporary`:** o CRM não muda essa tabela. Para o follow-up obedecer à política, o nó que
   consome a fila precisa chamar a reserva antes de cada envio (item 1).

## O que ficou sem prova

- A causa do excesso. Falta a consulta de disparos por texto (IA sem mensagem do lead nos 30 min
  anteriores, por dia e texto — `docs/18` §4.9) e confirmar a instância do card.
- O comportamento do workflow do n8n (consome `/regras/*`? respeita `ativo`? usa `/responder` ou envia
  direto?). Nada aqui foi executado contra o n8n.
- Os valores de cooldown e teto: sugeridos acima, não confirmados.

## Variáveis

Nenhuma variável de ambiente nova. Os parâmetros ficam no banco (`configuracao_automacao`).
