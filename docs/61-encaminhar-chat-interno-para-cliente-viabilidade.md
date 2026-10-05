# 61 — Encaminhar do Chat Interno para o cliente

Permite encaminhar uma mensagem do Chat Interno (texto, imagem, vídeo, áudio, documento) para o cliente de um
atendimento, pelo canal de WhatsApp daquele atendimento. A tarefa começou por uma análise de viabilidade, que
parou num bloqueio de produto (abaixo); a decisão de negócio de 05/10/2026 o resolveu, e a feature foi implementada.

## Viabilidade e o bloqueio que houve

**O Chat Interno não tem vínculo com lead ou atendimento.** `chat_interno_conversa` (V8, V54, V95) guarda tipo, nome,
criador e foto; `chat_interno_participante` liga a conversa a `usuario`; `chat_interno_mensagem` tem remetente
(usuário), conteúdo e mídia. Nenhuma referencia `lead` ou `atendimento`, e `crm-equipe` não menciona nenhum dos dois.
Na tela, a conversa interna e a externa são alternativas exclusivas na página Atendimentos.

Sem vínculo, o destino não pode ser derivado pelo backend; só pode ser **escolhido por quem encaminha**. Isso
contradiz "o usuário não altera o destino pelo payload", e por isso a análise parou e perguntou. A resposta do
negócio foi aceitar o destino escolhido, desde que o backend o valide por completo (abaixo).

O transporte já existia: o envio externo cobre texto e as quatro mídias na Meta e na UZAPI, com outbox,
idempotência, janela de 24h e RN-CRM-01/06 (`EnviarMensagemUseCase`).

| Tipo | Meta Cloud API | UZAPI | Legenda | Teto de fallback (Meta) |
|---|---|---|---|---|
| Texto | só dentro de 24h da última mensagem do cliente; fora, só template | sempre | n/a | n/a |
| Imagem | sim | sim | sim | 5 MB |
| Vídeo | `video/mp4`, `video/3gpp` | mesmo recorte | sim | 16 MB |
| Áudio | sim (`voice=true` só OGG/Opus) | sim | **não** (a API recusa) | 16 MB |
| Documento | sim, com `filename` | sim, com `filename` | sim | 100 MB |

## Decisões de negócio (05/10/2026)

1. **Quem pode**: quem participa da conversa interna (vê a mensagem) **e** pode responder no atendimento de
   destino (`atendimentos.responder` + alcance pela RN-CRM-01). Qualquer mensagem visível pode ser encaminhada,
   não só as próprias.
2. **Atendimento sem responsável**: quem encaminha assume o lead (RN-CRM-06), com a transferência auditada.
3. **Atendimento com responsável**: **nunca transfere**, nem para gestor, subgestor ou participante por entrada
   direta. O responsável continua e quem encaminhou **recebe um convite** para participar (docs/51), que ele
   aceita ou recusa. Quem já participa, ou já tem convite pendente, não recebe outro.
4. Quem encaminha **já sendo o responsável**: só envia.

Isso é mais estrito que a RN-CRM-06 do envio comum, por isso há um método próprio:
`EnviarMensagemUseCase#executarEncaminhamentoDoChatInterno`, que reaproveita todo o caminho de envio mas nunca
chama `transferirPara`; usa `assumirSeSemDono`, que só atribui quando não há dono.

## Fluxo

1. Menu da mensagem → **Encaminhar para o cliente** (só aparece para quem tem `atendimentos.responder` e para
   texto/mídia não apagados; evento de sistema, contato interno e mensagem apagada ficam de fora).
2. O diálogo busca no **servidor** os atendimentos **abertos que o usuário alcança** (RLS/RN-CRM-01), por nome ou
   dígitos do telefone, com pausa na digitação, no máximo 20 por busca e telefone mascarado. A listagem completa
   da aba não serve aqui: não é paginada e não escala para quem enxerga todos os atendimentos.
3. **Prévia** (`GET .../previa`): cliente, telefone mascarado, responsável, conteúdo e o que o envio fará com a
   responsabilidade. Não grava nada. Bloqueios vêm do backend: atendimento finalizado, fora da janela de 24h,
   formato não aceito, arquivo acima do limite.
4. **Confirmar** (`POST`): obrigatório e explícito; duplo clique é travado de forma síncrona; a chave de
   idempotência é gerada por tentativa e reaproveitada se o usuário repetir depois de uma falha de rede.
5. **Acompanhamento** sem F5: o diálogo consulta o estado da mensagem externa (`PENDENTE`, `ENVIADO`, `ENTREGUE`,
   `LIDO`, `FALHOU`) até um estado final.

## API

| Rota | Efeito |
|---|---|
| `GET /api/v1/atendimentos/encaminhamento-do-chat-interno/destinos?busca=` | Atendimentos abertos que o usuário alcança, mais recentes primeiro, filtrados por nome (qualquer parte, sem caixa) ou dígitos do telefone; curingas digitados valem como texto. `403` sem `atendimentos.responder`. |
| `GET /api/v1/atendimentos/{atendimentoId}/encaminhamento-do-chat-interno/previa?conversaId=&mensagemId=` | Prévia. `403` não participa da conversa, `404` atendimento fora do alcance, `422` conteúdo não encaminhável. |
| `POST /api/v1/atendimentos/{atendimentoId}/encaminhamento-do-chat-interno` (corpo `{conversaId, mensagemId}`, header `Idempotency-Key` **obrigatório**) | `202` aceito para entrega. `400` sem chave, `403`, `404`, `409` atendimento finalizado ou chave usada em outra operação, `422` conteúdo/arquivo/janela. |
| `GET /api/v1/chat-interno/conversas/{conversaId}/mensagens/{mensagemId}/encaminhamentos-ao-cliente` | O que o próprio usuário encaminhou desta mensagem, com o estado atual da entrega. |

Erros em RFC 7807; recusas de negócio trazem `motivo` (`FORA_DA_JANELA`, `ATENDIMENTO_FINALIZADO`,
`TIPO_NAO_SUPORTADO`, `ARQUIVO_ACIMA_DO_LIMITE`, `ARQUIVO_SEM_TAMANHO`), que a tela traduz pelo catálogo de textos.

## Segurança

- O corpo só diz **qual mensagem** e **qual atendimento** (na URL). Cliente, telefone, lead, canal, instância e
  conteúdo saem do backend. Campos extras no corpo (`leadId`, `telefone`, `conteudo`) são ignorados (testado).
- O atendimento é lido sob a RLS de quem pede: fora do alcance responde `404`, sem revelar que existe, e nunca
  chega a gravar mensagem, outbox, elo ou convite.
- A mensagem é lida da linha persistida, só se o usuário participa da conversa **e** o par conversa/mensagem bate.
- O atendimento do clique é a âncora (`atendimentoEsperadoId`): finalizado devolve `409` e **não abre outro**.
- Tokens dos provedores e o bucket não passam pelo navegador; a mídia vai do storage ao provedor dentro do worker
  da outbox. O telefone na tela é sempre mascarado (`5561*****1234`).
- Mensagens de sistema, apagadas e contatos internos não são encaminháveis.

## Mídia: sem cópia

A mensagem externa **aponta para o mesmo objeto** do storage da mensagem interna. O objeto já foi validado pelos
bytes ao entrar no chat, então o encaminhamento revalida pelas regras do **envio ao cliente** usando o tipo real
gravado nos metadados: o chat aceita `.xlsm` e, sem configuração, até 100 MB; o envio ao cliente segue
`TiposDeMidiaPermitidos` e o limite configurado da categoria (ou o teto da Meta). Nenhum byte passa pelo caminho
de envio, que continua assíncrono: o upload ao provedor acontece no worker da outbox.

Os metadados são remontados na forma que os adaptadores leem (`nome`, `mimetype`, `tamanho`, `legenda`); o chat
grava `nome_original` e `tamanho_bytes`, e sem esse remapeamento o documento sairia sem nome. O nome é sanitizado
como no envio de anexo (só `[A-Za-z0-9._-]`; acentos viram `_`).

Risco conhecido: dois registros apontam para o mesmo objeto. Hoje nada apaga objetos de mídia (a exclusão de mensagem
interna só limpa a referência no banco), mas **uma limpeza futura de storage precisa contar as referências**.

## Persistência, idempotência e auditoria

- **V96** `chat_interno_encaminhamento_cliente`: elo mensagem interna → mensagem externa (FK composta para a tabela
  particionada), usuário, atendimento, lead, tipo, `transferiu_o_lead`, `convite_criado` e a chave (`UNIQUE`).
- Tudo no pool do chat, **numa transação só**: envio (mensagem + outbox), elo e convite. Ou saem os três, ou nenhum.
- Idempotência: a repetição da mesma chave responde antes de qualquer outra leitura e devolve o mesmo encaminhamento
  (`reutilizado=true`), sem nova mensagem, outbox, convite ou linha de auditoria; mesma chave para outro atendimento
  ou outra mensagem responde `409`.
- Auditoria (`@Auditable`, `ENCAMINHAR_CHAT_INTERNO_PARA_CLIENTE`): usuário, conversa, mensagem interna, atendimento,
  lead, mensagem externa, tipo e os dois efeitos; nunca o texto nem o telefone (allowlist em `SerializadorAuditavel`).
  A transferência (`ENVIO_COM_TRANSFERENCIA_DE_LEAD`) e o convite (`CONVITE_ATENDIMENTO_CRIADO`) saem dos eventos
  já existentes do envio e da participação. Tentativa recusada não deixa registro.
- Retry: o publicador da outbox já faz o retry controlado e o circuit breaker/timeout dos adaptadores vale como
  para qualquer envio; provedor fora do ar mantém a mensagem `PENDENTE`, e recusa definitiva a marca `FALHOU`.

## Limitações conhecidas

- O texto que o cliente recebe leva a **assinatura do atendente** (`*Nome:*`), como em todo envio manual.
- Texto livre em provedor oficial exige a janela de 24h; fora dela a prévia bloqueia (não há envio de template aqui).
- O destino é um atendimento **aberto e alcançável** pelo usuário; para um atendente isso significa os próprios e os
  potenciais, para gestão e subgestão, os de todos. A busca devolve no máximo 20: o usuário refina digitando.
- Não há vínculo permanente entre conversa interna e cliente: cada encaminhamento escolhe o destino de novo.
- O acompanhamento do diálogo é por consulta periódica (2 s) até o estado final, não por WebSocket.
- Mensagem interna encaminhada duas vezes ao mesmo cliente é permitida (chaves diferentes).

## Testes

- `EncaminhamentoDoChatParaClienteIT` (34 casos, HTTP + Postgres + RLS + outbox, canal fake): texto e as quatro mídias
  com tipo/MIME/nome/legenda e mesmo objeto de storage; sem responsável (assume), com responsável (mantém e convida),
  convite aceito (não convida de novo), participante por entrada direta (não transfere), o próprio responsável;
  prévia sem escrita e com telefone mascarado; não participante da conversa, atendente fora do alcance,
  sem `atendimentos.responder`, par conversa/mensagem trocado, mesma chave para outro cliente, atendimento
  finalizado, fora da janela, mensagem de sistema/apagada/contato, `.xlsm`/QuickTime/tipo trocado, acima do limite e
  sem tamanho, sem chave; idempotência (1 mensagem, 1 elo, 1 evento de envio, 1 auditoria); status
  `PENDENTE → ENVIADO`; `FALHOU`; provedor fora do ar com retry e envio único; auditoria; e a busca de destinos
  (atendente × gestão, finalizados e de colegas fora, busca por nome/telefone, curingas como texto, limite de 20,
  401/403).
- `EncaminhamentoDoChatNosProvedoresTest`: o conteúdo do chat passando pelos adaptadores Meta e UZAPI (payload,
  upload, `filename`, legenda, áudio sem legenda) e a tradução de falha 4xx/5xx.
- `ConsultarMensagemDoChatParaEncaminhamentoUseCaseTest`, `MontadorDeConteudoDoChatParaClienteTest`,
  `TelefoneMascaradoTest`, `AuditoriaDeAcoesSensiveisTest`.
- Frontend: diálogo (busca no servidor com pausa, prévia, bloqueio, confirmação, duplo clique, chave reaproveitada, status,
  erros), menu e elegibilidade da mensagem, e a página (a ação só chega a quem tem a permissão).
- Cada proteção foi violada de propósito (ver o relatório da tarefa) e o teste correspondente reprovou.
