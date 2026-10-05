# 61 — Encaminhar do Chat Interno para o cliente: análise de viabilidade

Pedido: permitir encaminhar do Chat Interno para o cliente (texto, imagem, vídeo, áudio, documento) pelo canal
de WhatsApp do atendimento. A tarefa exigia começar por esta análise e **interromper** se não houvesse vínculo
seguro entre a mensagem interna e um atendimento externo.

**Resultado: implementação bloqueada na etapa de destino. Nenhum código de envio foi escrito.** O transporte
(texto e as quatro mídias, Meta e UZAPI) já existe e é reaproveitável; o que não existe é um jeito seguro de
saber *para qual cliente* uma mensagem interna deve ir. A decisão de como definir o destino é de produto e está
em "O que desbloqueia".

## Respostas às perguntas da tarefa

**1. Uma mensagem do Chat Interno tem vínculo com lead ou atendimento externo? Não.**
`chat_interno_conversa` (V8, V54, V95) guarda tipo, nome, criador e foto; `chat_interno_participante` liga a
conversa a `usuario`; `chat_interno_mensagem` tem `remetente_id` (usuário), conteúdo e mídia. Nenhuma das três
referencia `lead` ou `atendimento`, nem por coluna nem por tabela auxiliar. No código, `crm-equipe` não
menciona `lead_id`/`atendimento_id` (`EnviarContatoChatUseCase` até declara que "nunca pesquisa leads por
telefone"). No frontend, a conversa interna aparece dentro da página Atendimentos (`lista-conversas.tsx`,
`PainelConversaInterna`), mas sem relação com a conversa externa selecionada: abrir uma substitui a outra.
A documentação (`docs/45`, `46`, `47`) trata o chat como canal entre equipe.

**2. Qual seria o destino?** Das três opções da tarefa, só a terceira é possível hoje:

| Opção | Existe hoje? |
|---|---|
| Cliente associado ao grupo | Não: grupo não tem lead. |
| Cliente associado ao atendimento selecionado | Não: o atendimento selecionado some quando uma conversa interna é aberta. |
| Escolhido manualmente pelo usuário | Possível, mas muda o modelo de segurança (ver item 3). |

**3. Como impedir envio ao cliente errado?** Com destino derivado de vínculo, o backend decidiria sozinho e o
cliente nada escolheria. Com escolha manual, o identificador do atendimento **tem** de vir no payload. A defesa
passa a ser: validar no backend que o usuário alcança o atendimento (RN-CRM-01, RLS), que está aberto e que é o
mesmo que a prévia mostrou; telefone, lead, canal e instância sempre lidos pelo backend; confirmação explícita
com nome, telefone mascarado e atendimento. Isso reduz, mas não elimina, o erro humano de escolher o cliente
errado, e contradiz o requisito "o usuário não consiga alterar o atendimento de destino pelo payload", que só
vale com vínculo. Por isso é decisão do produto, não minha.

**4. O usuário precisa de acesso ao atendimento externo? Sim.** `EnviarMensagemUseCase` exige a capacidade
`atendimentos.responder` e trava o lead por `leads.bloquearParaAtendimento` (RLS = RN-CRM-01). Sem alcance, o
lead responde como inexistente.

**5. Só mensagens próprias ou qualquer mensagem visível?** Não há regra no código (é decisão nova). Risco:
qualquer participante de grupo poderia mandar ao cliente a fala de um colega, com o conteúdo interno de quem não
escreveu para o cliente. Recomendação: começar só com mensagens **do próprio usuário**; ampliar depois, se a
gestão pedir.

**6. Legenda, nome de arquivo, MIME e tipo.** Dá para preservar, mas **não de graça**: os metadados internos usam
`nome_original` e `tamanho_bytes` (`EnviarMidiaChatUseCase`), enquanto os adaptadores externos leem `nome` e
`tamanho` (o `filename` do documento sai de `campoDeMetadados(metadados, "nome")` no `MetaCloudApiAdapter`).
Encaminhar sem remapear mandaria documento sem nome. A legenda mora em `conteudo` e em `legenda` nos metadados.

**7. O fluxo externo suporta os cinco tipos? Sim.** `ConteudoDeEnvio` (selado: `MensagemLivre`,
`MensagemTemplate`, `MensagemMidia`) cobre texto e as quatro mídias; `EnviarMensagemUseCase` grava mensagem e
outbox na mesma transação.

**8. Meta e UZAPI? As duas suportam, com as mesmas regras de legenda.**

| Tipo | Meta Cloud API | UZAPI | Legenda | Teto de fallback (Meta) |
|---|---|---|---|---|
| Texto | Só dentro de 24h da última mensagem do cliente; fora, só template (`ForaDaJanelaException`) | Sempre | n/a | n/a |
| Imagem | sim (upload + `id`) | sim | sim | 5 MB |
| Vídeo | sim (`video/mp4`, `video/3gpp`) | sim (CRM aplica o mesmo recorte) | sim | 16 MB |
| Áudio | sim; `voice=true` só para OGG/Opus | sim (`LinkMessage`) | **não** (a API rejeita) | 16 MB |
| Documento | sim, com `filename` | sim, com `filename` | sim | 100 MB |

A UZAPI não publica MIME nem teto de vídeo; o CRM usa o mesmo recorte da Meta (`docs/38`).

## O que já pode ser reaproveitado

- **Envio**: `EnviarMensagemUseCase.executarComReferencia(leadId, conteudo, referencia, chaveIdempotencia)`, já
  usado por `EncaminharMensagemUseCase` (externo → externo). Cobre outbox transacional, idempotência persistente
  por chave, janela de 24h, RN-CRM-01/06, âncora `atendimentoEsperadoId` (recusa atendimento finalizado ou
  trocado) e evento em tempo real. A resposta do worker de envio e o status de entrega
  (`AplicarStatusDeEntregaDoCanalUseCase`) atualizam a mensagem externa sem mudança.
- **Provedores**: `MetaCloudApiAdapter` e `UzapiAutoticAdapter` já sobem a mídia lendo `armazenamento.baixar`,
  com circuit breaker e timeout.
- **Storage**: a porta `ArmazenamentoDeMidia` é a mesma do chat interno e do atendimento. `MinioArmazenamentoDeMidia`
  ignora o nome recebido e grava `midia/<uuid>.<ext>`, então uma referência interna é legível pelo fluxo externo.
- **Classificação e limites**: `RegrasDeAnexoBase`, `LimiteDeAnexoRepositorio` e `TiposDeMidiaPermitidos`.
- **Auditoria**: `@Auditable` (aspecto em `crm-app`).

## Lacunas e armadilhas (valem para qualquer opção de destino)

1. **Vínculo mensagem interna → mensagem externa**: não existe tabela. Seria nova (V96, próxima livre depois da
   V95 do PR #266): `chat_interno_encaminhamento_externo` com mensagem interna, usuário, atendimento, lead,
   mensagem externa (id + `enviada_em`, pela FK composta da tabela particionada), chave de idempotência e
   criação. `ReferenciaDeMensagem.ENCAMINHAMENTO` não serve: aponta para linha de `mensagem`, e a origem aqui não
   é uma.
2. **Fronteira de módulo**: `crm-atendimento` depende de `crm-equipe`, não o contrário. A orquestração tem de
   morar em `crm-app` (ou numa porta de `crm-equipe` implementada lá).
3. **Duas transações**: casos de uso do chat interno usam o gerente padrão; o envio externo exige
   `Pools.CHAT_TRANSACTION_MANAGER`. Gravar o vínculo e a mensagem externa atomicamente exige decidir em qual
   pool o vínculo mora e testar a atomicidade.
4. **Cópia da mídia**: apontar a mensagem externa para o mesmo objeto da interna cria dono duplo (hoje a
   exclusão de mensagem interna não apaga o objeto, mas qualquer limpeza futura quebraria o histórico do
   cliente). Copiar (`baixar` + `salvar`) resolve, mas é trabalho síncrono proporcional ao arquivo (até 100 MB
   num documento) no caminho de envio, o que contraria a regra de precedência. Teria de ser assíncrono (job com
   reserva, status e retry), como a tarefa pede.
5. **Revalidar pelas regras externas**: o chat interno aceita o que o externo não aceita. O limite interno cai
   em 100 MB quando não há configuração e a lista interna admite `.xlsm` (macro) que a classificação externa
   não prevê. Uma imagem de 20 MB passa no chat e a Meta recusa acima de 5 MB. O encaminhamento precisa
   reclassificar (`TiposDeMidiaPermitidos.classificar`) e conferir o limite externo, com erro claro.
6. **Janela de 24h**: com Meta, texto fora da janela é recusado. Deve virar erro explicado na prévia, não 500
   nem falha silenciosa na outbox.
7. **RN-CRM-06**: todo envio manual **transfere o lead** para quem enviou (só gestor/subgestor alcançam lead de
   colega; atendente alcança lead próprio ou sem dono). Encaminhar do chat é um envio manual e vai transferir.
   A prévia precisa avisar isso, e o produto precisa confirmar que é o desejado.
8. **Conteúdo que não pode sair**: mensagem de sistema (`tipo = SISTEMA`), mensagem excluída, contato
   compartilhado interno (`CONTATO`, dados de colega) e citações. Filtrar no backend.
9. **Mensagem do atendimento aberta sem ciclo**: se não há atendimento aberto, `EnviarMensagemUseCase` **abre um
   novo** (`Atendimento.abrirComIa`). Para "atendimento finalizado" recusar, é preciso passar a âncora
   `atendimentoEsperadoId`.

## O que desbloqueia

Decidir como o destino é definido:

- **A. Vínculo explícito** (nova coluna `lead_id`/`atendimento_id` em conversa, ou conversa criada a partir de um
  atendimento): destino derivado pelo backend, nada no payload. É o modelo que a tarefa assume. Custo: modelo de
  dados novo, UX para criar/ligar, regras de quem vê (um grupo com colegas vê o telefone do cliente?) e backfill
  inexistente (nenhuma conversa atual tem vínculo).
- **B. Escolha manual** (seletor de atendimento no encaminhamento): sem mudança de modelo de conversa, mas com o
  atendimento no payload e a defesa do item 3. Recomendação, **se** o produto aceitar o risco: busca só entre
  atendimentos abertos que o usuário alcança, prévia com nome + telefone mascarado + atendimento, aviso de
  RN-CRM-06 e de janela, só mensagens próprias, confirmação explícita, âncora do atendimento e chave de
  idempotência por clique.
- **C. Manter bloqueado**: o chat interno continua canal só da equipe (o que o código e a documentação assumem hoje).

## Esboço do que seria implementado (opção B)

Backend: porta em `crm-equipe` (`EncaminharParaClientePorta`) e caso de uso/adaptador em `crm-app`;
`POST /api/v1/chat-interno/conversas/{id}/mensagens/{mensagemId}/encaminhar-cliente` com `atendimentoId` e
`Idempotency-Key`; `GET .../pre-visualizacao-cliente` (nome, telefone mascarado, tipo, avisos); tabela V96;
cópia assíncrona de mídia; `@Auditable`; status do vínculo exposto à tela, com evento STOMP.
Frontend: ação no menu da mensagem só quando `podeEncaminharParaCliente` (vindo do backend), diálogo de seletor +
prévia + confirmação, trava de duplo clique, estado de envio/entregue/falhou sem F5, textos no catálogo.
Testes: os dezenove da tarefa, mais negativos de RN-CRM-01, mutação do destino pelo payload, adaptadores
Meta e UZAPI com mídia real e o fluxo pelo endpoint (sem `Thread.sleep`).

## Estado

Nada foi implementado nem alterado em código de produção por esta análise; apenas este documento.
