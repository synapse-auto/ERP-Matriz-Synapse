# 13. Estado do Projeto — handoff

Documento de continuidade. **Estado reconstruído em 09/09/2026 a partir de
`origin/main` (`5ac6b9d`), das migrations e do código.** Se este arquivo divergir do
repositório, o repositório vence.

### 30/08/2026 — Nome do cliente na sidebar (PR #30)

O título da ficha (4ª coluna de Atendimentos e overlay da Agenda) passou a ser um editor inline: blur ou Enter grava via o mesmo `PUT /api/v1/leads/{id}`. Nome vazio não chama a API no frontend e o backend devolve 400 (`Nome invalido`) se o campo vier em branco — o schema é `NOT NULL` e card/cabeçalho/busca dependem dele. Depois de salvar, o cache da inbox recebe `leadNome` e a Agenda é invalidada.

### 09/09/2026 — Gravações do composer como nota de voz (E179)

Gravações novas são convertidas para OGG/Opus mono a 48 kHz, com timestamps contínuos e duração
estrutural validada. A Meta recebe `voice: true`; a Uzapi/Autotic recebe somente o `mediaId`
documentado, sem campos não previstos de duração ou PTT. Anexos de áudio escolhidos manualmente
mantêm o fluxo existente.

### 09/09/2026 — Diagnóstico de duração na Uzapi/Autotic

O worker identifica gravações do composer por uma marca interna nos metadados da outbox e valida
novamente o OGG/Opus recuperado do storage antes do upload. O resumo seguro (tamanho, MIME e
SHA-256) é registrado nos limites antes do storage, na leitura e no upload; o conteúdo nunca é
registrado. O caminho normal não transforma bytes entre storage e Uzapi. O Swagger oficial não
documenta `voice`, `ptt`, `duration` ou `seconds`, então o CRM não envia campos inventados: a duração
precisa ser calculada pela Uzapi a partir do OGG válido. Não houve envio real para a conta da
Clínica Fêmina nesta etapa; uma confirmação do relógio no WhatsApp continua sendo evidência
operacional do provedor.

### 09/09/2026 — Envio idempotente e reconciliação de falhas de transporte

Envios iniciados pela interface recebem uma chave `Idempotency-Key` estável por clique. A reserva
da chave, a mensagem e o evento da transactional outbox são persistidos na mesma transação; uma
repetição para o mesmo usuário, lead e atendimento devolve a mensagem já criada sem duplicar
outbox. A chave é devolvida no histórico, na resposta HTTP e no evento `MENSAGEM` do WebSocket,
permitindo reconciliar a bolha otimista por identidade, nunca por texto ou horário.

Uma rejeição de transporte (fetch/XHR sem resposta ou erro 5xx) mantém a bolha pendente enquanto o
frontend consulta o histórico em até três tentativas. Se a mensagem for encontrada, a bolha é
substituída pelo registro real; somente uma resposta 4xx definitiva ou o esgotamento documentado
da reconciliação transforma a bolha em `FALHOU`. O código de UI `-1` usa o texto de “envio não
confirmado”, distinto de uma falha informada pelo provedor. A migration V64 cria o índice durável
`mensagem_envio_idempotencia`; as mensagens continuam na tabela particionada existente.

---

## 1. Onde estamos

O produto está em **produção real**, conforme o estado operacional desta etapa. O git
confirma a promoção do conjunto de homologação para `main` em `89d7dfc`, de 27/08/2026,
mas não registra por si só o instante do deploy nem prova todos os smoke tests do ambiente.
Não tratar esse SHA como imagem necessariamente em execução: o Dokploy deve ser conferido
pelo digest da imagem.

O HEAD de referência é `5ac6b9d` (`origin/main`), após a integração do PR #129. O trabalho normal
é feito em branch própria, publicado no `origin` e entregue por Pull Request para `main`.
O agente não faz merge do próprio PR e não faz deploy; essas ações ficam com o responsável
pela operação.

### 10/09/2026 — Notificações em tempo real

A fila pessoal passou a receber novas mensagens externas para o dono/participantes ativos do
atendimento e mensagens do chat interno para seus destinatários. O frontend mantém uma conexão
STOMP compartilhada, decide deduplicação/autoria/conversa ativa em um serviço comum e exibe aviso
visual com som opcional. O contrato detalhado está em [`24-notificacoes-tempo-real.md`](./24-notificacoes-tempo-real.md);
esta entrega ainda aguarda o PR e o número da execução de CI antes de ser considerada promovida.

### Etapas reconstruídas

| Etapa | Entrega confirmada | Evidência no git |
|---|---|---|
| E59 | ligação/paridade do chat interno | `e9cddf6`, promovido em `89d7dfc` |
| E60–E61 | correções de mensagens programadas e MIME de áudio | `7399b72` |
| E62–E63b | inbox unificada e correções de produção/login/cache | `80ee893`, `3f2c841`, `e1f5971` |
| E64 | isolamento dos schedulers na suíte de integração | `e0420e4` |
| E65 | aviso da sidebar e mensagens programadas | `a65db62`, promovido em `89d7dfc` |
| E66 | nova conversa permanece aberta após o clique | `43bf65e` |
| E67–E67b | ficha do lead, novidades e correções de entrega visual | `56ede13`, `74e528d` |
| E68–E69 | menu de finalização/chat interno e isolamento do ajuste da E65 | `e9cddf6`, `79b7b71` |
| E70 | correção da auditoria da E67b | `74e528d` |
| E71–E72 | feedbacks e Administração, incluindo autorização backend | `ca41ea5`, `c5892a5`, `db29534` |
| E73–E77 | validação integrada, identidade visual e promoção de `hmlgc` | `b35f1f8`, `79b7b71`, `89d7dfc` |
| E78 | remoção dos scripts auxiliares locais | `7d729f8` |
| E79 | datas determinísticas das Novidades no CI | `9f491de` |
| E80 | mídia no chat interno | `4d03812` |
| E81 | refino do menu Administração e cobertura OpenAPI | PRs #1 e #2: `180072a`, `72d35e9` |
| E83/E83b/E83c/E83d + E85 | avaliação automática pós-finalização, outbox, lease e concorrência | PR #14: `b7a7ab8` |
| E84/E84b | sidebar dinâmica e reações persistidas/tempo real | PR #15: `99048f6` |
| E84c | sidebar sem cobrir o chat e hover suave | PR #21: `be0bc48` |
| E86 | iniciar chat interno pela equipe | PR #16: `be5b1b8` |
| E87 | responder e encaminhar mensagens | PR #19: `d9b249a` |
| E88 | mídias/documentos na ficha do lead | PR #17: `9dbe439` |
| E88b | correção visual dos balões do chat | PR #18: `3b01818` |
| E92 | identificação da WABA para templates da Meta | PR #20: `91ea622` |
| E92b | respostas da Meta por texto/content-type e rótulos acessíveis | PR #24: `2f7f2b4` |
| Correção de templates | RFC 7807 para falhas de templates | PR #22: `bc89ba6` |
| E89–E91 | prompts preservados, mas sem merge identificado com esse rótulo no histórico de `main` | não confirmado como etapas independentes; verificar os commits/PRs que absorveram cada ajuste |
| E93 | documentação e regras de migration | PR #29: `ed02ac3` |
| E124 | pausa do gatilho de avaliação no caminho do atendente | PR #58: `0eeed43` |
| E126 | religação do gatilho no contrato EV-08, payload de 8 campos e toggle V55 | branch `feat/avaliacao-ev08` |
| E133 | sonda de saúde isolada do tráfego de mídia/envio; disjuntor aberto não gasta tentativa da fila | branch `fix/sonda-de-saude-derruba-midia` |
| Notificações em tempo real | nova mensagem externa, chat interno, transferências, deduplicação e som opcional | branch `codex/notificacoes-tempo-real` (em revisão) |

Não foi encontrado um merge independente identificado como E82, E87b ou E89–E91. Isso não
prova que nenhum ajuste correspondente entrou como parte de outro PR; por isso esses itens
ficam explicitamente marcados como não isolados, e não como feitos apenas porque o prompt
existe.

## 2. O que está implementado e antes não aparecia na documentação

Confirmado pela árvore de `origin/main`:

- **Templates da Meta:** administração em `/api/v1/whatsapp/templates`, listagem/criação
  pelo WABA ID configurado, tratamento de indisponibilidade em RFC 7807 e catálogo de
  variáveis posicionais. Isso não é uma rota do contrato interno do n8n.
- **Avaliação de atendimento:** registro de CSAT com intenção durável/outbox, reserva e
  idempotência; o contrato de gravação é `POST /internal/v1/atendimentos/{id}/avaliacao`.
  A E124 pausou o gatilho; a **E126** o religou no contrato EV-08: finalização **individual**
  enfileira, "Finalizar todos" **nunca** enfileira, e o corpo passou a ter 8 campos com
  `evento_id`. O toggle `avaliacao_atendimento.habilitada` (V55) existe para o n8n ler em
  `GET /internal/v1/automation-config` e nasce `false`; o CRM não o consulta.
- **Reações:** reações de mensagens do atendimento e do chat interno, com persistência,
  autorização por participação/visibilidade e publicação em tempo real.
- **Responder e encaminhar:** citação persistida, `wamid` para `context.message_id` da
  Meta e encaminhamento como novo envio com referência denormalizada.
- **Mídia e anexos:** painel de mídias do lead, download autorizado, menu de anexos e envio
  de vários arquivos/arrastar para o composer.
- **Áudio gravado no composer para Meta Cloud e Uzapi/Autotic:** antes de persistir, FFmpeg
  normaliza a gravação para OGG/Opus mono a 48 kHz (perfil `voip`, timestamps contínuos). A
  validação exige páginas OGG completas, cabeçalho Opus e uma página EOS com `granule position`
  positivo, garantindo duração estrutural diferente de zero. A Meta recebe `audio.id` com
  `voice: true`; a Uzapi recebe apenas o `audio.id` documentado e calcula a duração a partir do
  OGG válido — não há campo documentado de `voice`, `ptt` ou duração para enviar. Áudio anexado
  como arquivo continua sem transcodificação forçada; o fallback AAC/ADTS no worker só protege
  registros antigos ISO-BMFF fragmentados.
- **Emoji:** catálogo amplo categorizado no composer; o backend valida uma sequência Unicode
  válida para reações. A aparência final depende da plataforma/fonte emoji do navegador.
- **Código numérico do lead:** `lead.codigo`, somente dígitos, editável e visível na ficha/
  card sem colocar dados extensos em listagem (PR #28).
- **Nome do cliente na sidebar:** o título da ficha é editor inline; vazio é recusado (PR #30).
- **Chat interno:** conversa iniciada pela lista de atendimentos e suporte a mídia/reação,
  além do chat direto já existente.

## 3. Estado técnico e banco

- Migrations presentes: **V1 a V47 e V65**, última `V65__acoes_mensagens_chat_interno.sql`.
- V41 adiciona leitura de atendimento por usuário; V42 feedbacks; V43 unicidade/índice de
  avaliação; V44 reserva da avaliação na outbox; V45 reações; V46 `wamid` e referência de
  mensagem; V47 código numérico do lead.
- O caminho de mensagem mantém WebSocket, outbox, retry e circuit breaker separados de
  chamadas externas. A aba Atendimentos não pode depender de Meta, n8n ou outro provedor.
- O isolamento da Meta continua sendo pelo `phone_number_id` da credencial ativa; a
  inscrição do app é por WABA, mas o WABA ID usado para administrar templates é uma
  configuração distinta.

## 4. Pendências reais

O arquivo `docs/prompts/pendencias-clickup-para-cursor.md` é o inventário inicial, mas
estava desatualizado. Após confrontá-lo com os merges, estes itens estão feitos e não devem
ser reabertos como se fossem pendências: E86, E87, E88/E88b, reações/sidebar de E84/E84c,
templates Meta de E92/E92b e código numérico do lead do PR #28.

Ainda exigem confirmação ou implementação:

| Item | Estado verificável |
|---|---|
| E32 — payload da Meta com várias mensagens agrupadas | não há merge de E32 identificado; deve continuar pendente até prova de teste/código |
| Regras de follow-up, fidelização, festiva e executor de automação | configuração/contratos existem; executor continua sendo responsabilidade do n8n |
| Horários de trabalho e disponibilidade da IA independente da presença | não confirmados como entregues |
| Kanban, CSV e troca de credencial de canal | não confirmados como entregues |
| Impersonação, participação em atendimento e módulos de fase 2 | fora do escopo ou aguardando decisão de produto/segurança |
| Download de mídia retornando 401 | prompt separado preservado em `docs/prompts/pendencia-E88-download-midia-401.md`; não há evidência de correção nesta `main` |
| Operação | validar no Dokploy a imagem em execução, smoke RLS, backup/restauração, watchdog, domínio real, rotação de segredos e PITR |

Nada deve ser marcado como “feito” só por existir um prompt: o item precisa de merge,
teste ou evidência operacional correspondente.

## 5. Como o trabalho acontece agora

1. O responsável cria um prompt versionado para uma etapa e define o critério de pronto.
2. O agente atualiza uma branch própria `codex/...` ou a branch explicitamente pedida.
3. O agente executa os testes proporcionais, registra decisões/gaps e faz commit convencional.
4. O agente publica a branch e abre/atualiza o PR para `main`.
5. O responsável revisa, aguarda CI e decide o merge; deploy e validação de produção são
   ações operacionais separadas. O agente não faz merge nem deploy sozinho.

## 6. Evidências que ainda precisam ser mantidas

“CI verde” só vale com número da run; execução local é evidência local. Para cada promoção,
confira o SHA/digest realmente rodando no Dokploy, os smoke tests de RLS e o caminho real
Meta → CRM → tela. O `docs/22-bugs-abertos-26-08.md` continua como registro histórico,
não como painel vivo.

## 7. Próximos passos recomendados

1. Revisar/mergir o PR desta E93 sem alterar `main` diretamente.
2. Confirmar o estado do download de mídia 401 e do payload multi-mensagem.
3. Validar operação real: imagem/digest, WABA/Phone Number ID, RLS, backup, watchdog,
   domínios e rotação de credenciais.
4. Só então transformar a próxima pendência confirmada em prompt isolado.

## 8. E176 — paridade de ações do chat interno

O chat interno reutiliza `InteracaoMensagem`, `CitacaoMensagemVisual`, o composer de anexos e o
mesmo catálogo de ações do chat de atendimentos. A autorização continua sendo por participação na
conversa, inclusive para gestores; nenhuma ação consulta ou publica dados de um lead externo.

| Ação no chat de atendimento | Aplicável ao chat interno | Implementação/paridade | Motivo quando não aplicável |
|---|---|---|---|
| Reações | ✅ | `InteracaoMensagem`, `PUT/DELETE /chat-interno/.../reacao`, evento `CHAT_INTERNO_REACAO` | — |
| Copiar texto | ✅ | `InteracaoMensagem`/`copiarTexto` | — |
| Responder/citar | ✅ | `ResponderMensagemChatUseCase`, `.../{mensagemId}/responder`, `CitacaoMensagemVisual` | — |
| Encaminhar | ✅ | `EncaminharMensagemChatUseCase`, destino limitado a conversa interna participante | — |
| Excluir | ✅ | `ExcluirMensagemChatUseCase`, tombstone e evento `CHAT_INTERNO_MENSAGEM_REMOVIDA` | — |
| Mídia, áudio e documento | ✅ | `ComposerChatInterno`, `ZonaSoltarArquivos`, URL assinada autorizada | — |
| Colar imagem/anexo | ✅ | `ComposerChatInterno` usa o mesmo `onPaste`/validação do caminho de anexos | — |
| Status de entrega / retry de provedor | ⚠️ | Não há provedor nem outbox de canal no chat interno; erros HTTP permanecem no composer | Não existe entrega externa para confirmar ou repetir. |
| Template WhatsApp | ❌ | Não exposto | Template é contrato exclusivo do canal WhatsApp, sem semântica interna. |
| Finalizar/transferir atendimento | ❌ | Não exposto | Conversa interna não possui lead, responsável ou ciclo de atendimento. |

Exclusões são lógicas: conteúdo e referência de mídia ficam nulos, o registro permanece para
auditoria e referências posteriores recebem apenas o estado seguro “mensagem removida”. O trigger da
V65 atualiza citações mesmo quando a conversa de origem não está no escopo RLS do autor. Os eventos
de mensagem, reação e remoção são publicados pelo relay somente `AFTER_COMMIT`; reconexão e
paginação continuam recarregando o histórico por HTTP.
