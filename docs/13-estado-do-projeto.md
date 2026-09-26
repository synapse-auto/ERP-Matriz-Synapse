# 13. Estado do Projeto — handoff

Documento de continuidade. **Estado reconstruído em 16/09/2026 a partir de
`origin/main` (`e3324f5`), das migrations e do código.** Se este arquivo divergir do
repositório, o repositório vence.

### 26/09/2026 — Investigação do HTTP 410 em documentos da Uzapi/Autotic (FMNA)

Investigação sem acesso a produção, registrada em
[`47-investigacao-midia-documento-410-uzapi.md`](./47-investigacao-midia-documento-410-uzapi.md).
Pelo código, a primeira busca de mídia ocorre ~1 s após `recebido_em`, com o mesmo caminho para
todos os tipos. Qualquer 4xx/5xx é retentado por 10 min, e o `[MIDIA_NAO_RECEBIDA]` marca a última
tentativa (~10,6 min), não a primeira. A mensagem recebida é datada no processamento, então uma bolha
visível 31 min antes do log não pode ser a mesma entrada. Nem o `docs/38` nem o Swagger da Uzapi
documentam retenção ou 410. Decisão: a proposta A (log por etapa: `etapa=resolvedor` ou
`etapa=download` + host) foi implementada, sem mudar classificação, backoff ou prazo. A B (410
terminal) depende de a consulta 4.1 rodar na FMNA; a C (executor próprio) só com a 4.4 mostrando
trava do agendador.

### 20/09/2026 — Convite para atendimento

O header da conversa agora pode convidar um atendente ativo elegível por
`POST /api/v1/atendimentos/{id}/convidar`, com `{ "atendenteId": "uuid" }`. A operação exige
JWT e que o solicitante seja o responsável, participante ativo ou usuário com alçada ampla;
o destino precisa estar ativo e ter papel ATENDENTE ou SUBGESTOR. A restrição única de pedido
pendente torna cliques repetidos idempotentes (`jaExistia=true`) e impede convite para quem já
participa ou para atendimento encerrado.

O convite é persistido como `tipo=CONVITE` em `pedido_entrada_atendimento` (migration V78),
sem alterar o responsável comercial. A RLS concede ao destinatário apenas o alcance necessário
enquanto o convite está pendente, permitindo que o cartão apareça em PENDENTES. O destinatário
aceita ou recusa pelos endpoints existentes de pedidos; a aprovação cria a participação ativa.
Timeline, auditoria e a notificação pessoal `CONVITE_ATENDIMENTO` são publicados somente após
commit. A interface usa a mesma lista estreita de destinos da transferência e abre o atendimento
pela rota canônica quando a notificação é clicada.

### 16/09/2026 — V73 imutável e upgrade controlado para Fêmina

Marcondes confirmou `flyway_schema_history` da Estrutural com V73 bem-sucedida. Não editar o SQL,
não rodar `flyway repair` e não alterar o histórico. A Fêmina teve tentativas de V73 no boot
abortadas após mais de 30 minutos: healthcheck reiniciava o backend, novas sessões JDBC concorriam
por locks e a API respondia 503; a versão continuava em 72. `7462937` e `e3324f5` são tags de
imagens que contêm V73; `78c4e53` também já contém a V73 (foi o merge que a introduziu, PR #155) — a
última imagem anterior a ela é a de `40fdc54` (PR #154). Esses dados não confirmam
qual imagem está atualmente implantada em cada serviço, que deve ser obtida do Dokploy/runtime antes
de agir.

O boot normal valida checksums e, enquanto V73 estiver pendente, nunca a executa: banco vazio/antigo
pode avançar somente até V72 e schema72 permanece intacto. O bridge one-shot usa contexto mínimo,
lock de advisory sem espera, timeouts finitos e alvo estrito 73; aceita somente 72→73 com exatamente
uma migration ou 73 já aplicada. No caminho 72→73, a implementação Java processa fusões e
normalizações em lotes curtos, com checkpoint, lease e limite de tentativas; o SQL V73 permanece
imutável e fornece o checksum. Depois da V73, migrations posteriores voltam ao fluxo normal. Antes da Fêmina, exige backup, simulação
restrita, janela fora de 08:00–18:30 e homologação com cópia estruturalmente equivalente da
Estrutural. Nenhum deploy/rollback/SQL de produção foi executado nesta etapa. A Estrutural continua
protegida: se uma checagem futura achar V73 pendente nela, bloquear o deploy.

SHA de runtime, tags atuais de backend/frontend e última versão aplicada da Fêmina e da Estrutural
precisam ser levantados por acesso operacional read-only; não há credencial/runtime de produção
conectado a este workspace. A execução controlada agora é paginada e retomável, mas qualquer
operação histórica diferente da V73 continua exigindo desenho separado, dry-run, checkpoint e
aprovação explícita.

### 17/09/2026 — Ficha da Agenda em contexto colaborativo

A listagem da Agenda pode exibir contatos fora da carteira individual do atendente, conforme a
autorização colaborativa da própria Agenda. A ficha aberta a partir dela usa
`GET /api/v1/leads/{id}/agenda`, que aplica `ContextoDeAgenda` somente durante a leitura e mantém a
rota comum `GET /api/v1/leads/{id}` sob a `VisibilidadeLeadSpecification` normal. O botão de abertura
continua chamando `POST /api/v1/atendimentos/leads/{leadId}/novo`; a resposta desse comando é a fonte
canônica de `atendimentoId` usada na navegação, inclusive quando o item sai do filtro após um refetch.

### 30/08/2026 — Nome do cliente na sidebar (PR #30)

O título da ficha (4ª coluna de Atendimentos e overlay da Agenda) passou a ser um editor inline: blur ou Enter grava via o mesmo `PUT /api/v1/leads/{id}`. Nome vazio não chama a API no frontend e o backend devolve 400 (`Nome invalido`) se o campo vier em branco — o schema é `NOT NULL` e card/cabeçalho/busca dependem dele. Depois de salvar, o cache da inbox recebe `leadNome` e a Agenda é invalidada.

### Ficha lateral de Atendimento — resumo persistido e notas internas

`GET /api/v1/leads/{id}` é a fonte autorizada da ficha completa. Além dos campos gerais, ele devolve
`notas`, `resumoIa` e `resumoIaAtualizadoEm`; a listagem de leads continua sem esses campos longos.
`PUT /api/v1/leads/{id}` aceita a atualização parcial de `notas`, preservando RLS e a regra de
visibilidade do lead. A ficha exibe o resumo recolhido por padrão, a última geração quando o marco
está preenchido e mantém Notas internas editáveis para usuários já autorizados.

O botão “Gerar/Regerar” chama `POST /api/v1/atendimentos/{atendimentoId}/resumo-ia` com um
`Idempotency-Key` UUID estável. O navegador não chama n8n: o CRM grava `PENDENTE` e entrega somente
somente `atendimentoId` e `leadId` à URL configurada em `AUTOMACAO_RESUMO_IA_URL` pela Transactional Outbox,
com `CRM-Synapse-RES: AUTOMACAO_RESUMO_IA_TOKEN` e a chave no header `Idempotency-Key`. O n8n
mantém a chave em Data Table própria, marca `PROCESSANDO`, consulta o contexto limitado e grava o
texto em `POST /internal/v1/ev05/leads/{leadId}/resumo` usando a mesma chave e `contextoAte`. Em
seguida marca `CONCLUIDO` ou `FALHOU` em `/resumo-status`; o CRM publica `RESUMO_IA_STATUS` após o
commit. Atendimento finalizado, transferido ou substituído torna o resultado tardio `409`, sem apagar
o resumo anterior. O template do workflow sem credenciais está em `docs/n8n/resumo-ia-sob-demanda.json`.

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

### 16/09/2026 — Captura assíncrona de foto de perfil pela UZAPI/Autotic

O adaptador `uzapi-autotic` agora oferece a capacidade opcional de consultar a foto do contato pela
ação documentada `POST /{version}/{phone_number_id}/contacts` com `type=contacts`,
`action=getPicture` e `contacts.to` em dígitos. A consulta dispara somente após o commit de uma
mensagem recebida, em bulkhead/circuit breaker separado do webhook e do envio. O resultado é
convertido no ACL para bytes de imagem, validado e reencodado pelo mesmo caso de uso do contrato
interno do n8n; o navegador recebe apenas a rota autenticada já existente e URL temporária nunca é
persistida ou exposta.

O Swagger não define o corpo de resposta (somente HTTP 201), então o adaptador aceita imagem binária
ou campos de foto allowlisted e recusa estruturas desconhecidas. 404, ausência de foto, URL expirada,
imagem inválida e indisponibilidade mantêm as iniciais. O cache por lead é configurável por
`CANAL_FOTO_PERFIL_CACHE_TTL` (6h), com executor próprio (`CANAL_FOTO_PERFIL_CONCORRENCIA`/`FILA`);
desligar `CANAL_FOTO_PERFIL_HABILITADO` não altera o caminho normal de mensagens. A capacidade não
é ativada por nome de cliente: Meta permanece no fallback padrão. A validação real na Fêmina ainda
depende de enviar uma mensagem nova e observar a foto; este workspace não possui credenciais de
produção.

### 14/09/2026 — Retrieve Media URL oficial da Uzapi/Autotic

Após a confirmação do suporte Uzapi/Autotic na sexta-feira (11/09/2026), o adaptador usa o
resolvedor oficial `GET /{version}/{mediaId}` para áudio, imagem, vídeo e documento. As rotas de
instância, upload e envio continuam `/{version}/{phone_number_id}/...`; o número não é enviado ao
resolvedor. Nenhuma rota usa o segmento `{username}`. `WHATSAPP_USUARIO_API` é mantida apenas como
variável legada e não é lida para montar URLs nem para validar credenciais; `WHATSAPP_VERSAO_API`
continua fornecendo `{version}`.

O workaround anterior `GET /{version}/{phone_number_id}/{mediaId}` fica somente como registro do
incidente de 11/09 e não deve voltar ao código. Falhas 400/404/5xx do resolvedor continuam sendo
indisponibilidades retentáveis: o webhook permanece durável e recebe backoff em
`proxima_tentativa_em`, sem guardar corpo de resposta, token ou URL temporária.

### 14/09/2026 — E180: prefixo de discagem na importação de leads

`TelefoneCanonico` e `app_telefone_canonico` removem o trunk nacional `0` ou a operadora `0XX`
somente quando, por comprimento, restam exatamente 10 ou 11 dígitos. Prefixos de serviço (`0300`,
`0400`, `0500`, `0800`, `0900`), números já em E.164 e entradas ambíguas ficam intactos para
revisão; a regra nunca adivinha um contato.

`PrepararImportacaoLeadsCsv` continua usando o normalizador de domínio, portanto uma reimportação
do mesmo número casa a chave canônica e não cria lead duplicado. A V73 atualiza as funções SQL e
limpa os dados existentes: faz `UPDATE` apenas sem gêmeo e funde apenas quando o importado tem
`telefone_provedor` vazio, zero mensagens e o gêmeo possui conversa. O gêmeo sobrevive; as FKs de
linhas dependentes são movidas e referências escalares do lead só preenchem campos vazios do
sobrevivente (campos preenchidos por ele prevalecem). O nome do sobrevivente é preservado, seguindo
o precedente da V50. Casos ambíguos,
especiais ou sem evidência de importação são listados por `RAISE NOTICE` e permanecem intactos.
Antes da execução explícita e controlada da V73 (nunca no boot normal), executar e guardar a saída de
`docker/provisionamento/simular-limpeza-prefixo-discagem.sql`; as contagens reais dependem do banco
de cada instância e não foram inventadas neste handoff.


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

### 11/09/2026 — Diagnóstico de 400 transitório após finalização

Uma resposta HTTP de negócio (`4xx`) não representa recusa do provedor. A chave idempotente é
consultada antes de qualquer alteração no lead ou no atendimento: se a primeira requisição já
persistiu a mensagem, um replay (inclusive depois de a conversa ter sido finalizada) devolve a
mesma `EnvioResposta` e não cria atendimento/outbox novos. Isso fecha a janela em que a resposta
do navegador podia ser perdida e o retry receber um 409 enquanto a primeira outbox ainda entregava.

As transições da outbox agora são compare-and-set (`publicado_em`/`esgotado_em` ainda nulos).
Resultado tardio de outro worker é ignorado e não pode regravar `status_entrega` nem publicar um
evento residual. O E130 de reconciliação de transporte permanece inalterado; falhas ambíguas
continuam pendentes até a reconciliação por chave.

### 12/09/2026 — Contrato EV-05 para resumo e preenchimento automático

O ciclo de cinco horas é responsabilidade exclusiva do n8n. O CRM agora expõe o contrato
interno `/internal/v1/ev05` para listar, de forma paginada, somente atendimentos
`EM_ATENDIMENTO`, consultar contexto limitado e ler/gravar resumo e preenchimento automático de
`email`, `cpf`, `empresa` e `localizacao`. Todas as escritas exigem `Idempotency-Key`, validam
entrada e preservam campos já preenchidos; a mesma chave devolve a resposta concluída. Os
intervalos independentes de resumo e preenchimento vivem em `configuracao_automacao` (V68), e os
marcos de última escrita/avaliação ficam no lead. O n8n não recebe acesso ao PostgreSQL, e nenhum
cron ou chamada de IA foi adicionado ao backend.

### 17/09/2026 — Geração sob demanda do resumo pelo n8n

O CRM passou a persistir os ciclos `PENDENTE`, `PROCESSANDO`, `CONCLUIDO` e `FALHOU` em `V75`,
publicar a solicitação pela outbox dedicada e expor a rota autenticada de status para o n8n. A
idempotência é por UUID de solicitação, o contexto é amarrado ao `atendimentoId` e o resultado tardio
é rejeitado pelo CRM. A UI atualiza a ficha por WebSocket sem chamada direta ao n8n. O fluxo não fica
ativo quando `AUTOMACAO_RESUMO_IA_URL` está vazio.

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
esta entrega está no PR #135, com CI verde no run `34553824975`; permanece aguardando revisão e merge para ser considerada promovida.

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
| Notificações em tempo real | nova mensagem externa, chat interno, transferências, deduplicação e som opcional | PR #135, branch `codex/notificacoes-tempo-real` (CI verde, aguardando revisão) |

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
  Meta e encaminhamento como novo envio com referência denormalizada. A origem de uma citação
  pode ser carregada pontualmente por ID, sempre ancorada no atendimento visível (ou na conversa
  interna participante); a resposta devolve somente metadados e URL assinada de curta duração.
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
  além do chat direto já existente. O envio de imagem aceita legenda na mesma mensagem, mostra
  preview antes do envio e usa `Idempotency-Key` para reconciliar retries sem duplicar arquivo,
  mensagem ou evento (V76).

## 3. Estado técnico e banco

- Migrations presentes: **V1 a V77**, última `V77__remover_checkpoint_runner_v73.sql` (limpeza da
  tabela operacional criada pela execução controlada da V73).
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
| Regras de follow-up, fidelização e datas festivas | CRUD administrativo, configuração de aniversário e datas festivas dinâmicas entregues; executor e envio continuam sendo responsabilidade do n8n |
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
| Editar texto próprio | ✅ | `EditarMensagemChatUseCase`, `PATCH .../mensagens/{mensagemId}`, `editadoEm` e evento `CHAT_INTERNO_MENSAGEM_EDITADA` | Áudio, imagem, vídeo, documento, figurinha, tombstone e mensagens de outro autor preservam o conteúdo original. |
| Mídia, áudio e documento | ✅ | `ComposerChatInterno`, `ZonaSoltarArquivos`, URL assinada autorizada | — |
| Colar imagem/anexo | ✅ | `ComposerChatInterno` usa o mesmo `onPaste`/validação do caminho de anexos | — |
| Status de entrega / retry de provedor | ⚠️ | Não há provedor nem outbox de canal no chat interno; erros HTTP permanecem no composer | Não existe entrega externa para confirmar ou repetir. |
| Template WhatsApp | ❌ | Não exposto | Template é contrato exclusivo do canal WhatsApp, sem semântica interna. |
| Finalizar/transferir atendimento | ❌ | Não exposto | Conversa interna não possui lead, responsável ou ciclo de atendimento. |

### E177 — edição e painel lateral do chat interno

Mensagens textuais só podem ser editadas pelo próprio autor, sem limite de tempo. A alteração mantém
o identificador, remetente, data original e reações; `editadoEm` marca a última alteração. O trigger
`app_atualizar_citacoes_chat_editadas` recalcula a prévia sanitizada de respostas/encaminhamentos,
inclusive entre conversas. O evento `CHAT_INTERNO_MENSAGEM_EDITADA` é publicado somente após commit e
é consumido como atualização de cache, sem criar aviso sonoro.

O painel lateral reutiliza `PainelLateralGrupo` e `ListaDeMidiasDoGrupo`: em conversa direta mostra o
outro participante e suas mídias autorizadas; em grupo mantém participantes, ações de gestão e mídias.
Não há campos de lead, telefone, atendimento, tags ou IA. Em telas estreitas o painel vira drawer
sem desmontar o histórico.

Exclusões são lógicas: conteúdo e referência de mídia ficam nulos, o registro permanece para
auditoria e referências posteriores recebem apenas o estado seguro “mensagem removida”. O trigger da
V65 atualiza citações mesmo quando a conversa de origem não está no escopo RLS do autor. Os eventos
de mensagem, reação e remoção são publicados pelo relay somente `AFTER_COMMIT`; reconexão e
paginação continuam recarregando o histórico por HTTP.

### E178 — distribuição sequencial da IA por configuração

`AtendenteDisponivelRepositorioJdbc` mantém a consulta de elegibilidade única para a Automação e
seleciona uma de duas ordenações constantes. Por padrão, `ia.distribuicao.sequencial = false`
preserva o critério de menor carga, seguido de recência e `id`. Quando a gestão altera a chave para
`true` no CRUD de `configuracao_automacao`, a ordem passa a ser `ultimo_recebido_em NULLS FIRST,
id`, fazendo o próximo atendimento girar por quem recebeu há mais tempo, sem redeploy. A chave é
BOOLEAN para fechar os dois estados e aproveitar a validação já existente; se a linha estiver
ausente durante uma atualização, o código retorna ao comportamento de menor carga.

A ordenação sequencial afeta a escolha do primeiro destino (`findFirst`) e não a semântica da tela:
`GET /internal/v1/atendentes/disponiveis` continua devolvendo a mesma lista elegível, apenas na
ordem que a Automação deve consumir. A recência combina `atendimento.iniciado_em` com os eventos
`ATENDIMENTO_TRANSFERIDO` (`paraAtendenteId`) e `LEAD_TRANSFERIDO_POR_ENVIO`, portanto uma
transferência automática atualiza a posição do atendente. A Estrutural permanece no padrão
`false`; a FMNA deve habilitar `true` somente pela configuração da própria instância.

### E179 — reatribuição explícita pela Automação

`POST /internal/v1/atendimentos/{id}/transferir` continua aceitando apenas um destino ativo com
papel `ATENDENTE` ou `SUBGESTOR`, mas agora pode reatribuir um atendimento aberto em `EM_IA` ou
`EM_ATENDIMENTO` quando o n8n recebeu um pedido explícito do cliente. O responsável anterior é
substituído pelo destino e a comissão acompanha o novo dono; a timeline, a auditoria e o evento em
tempo real identificam a origem como `AUTOMACAO`, sem fabricar usuário. O caminho
`transferir-proximo-humano` permanece separado e estrito a `EM_IA`, para que o rodízio não
reembaralhe conversas já assumidas por humanos. Atendimento `FINALIZADO` é estado terminal e
responde `409` sem alterar responsável, lead ou eventos.

### Citações — prévia e navegação de mensagens citadas

`CitacaoMensagemVisual` é o componente compartilhado por atendimentos Meta/Uzapi e pelo chat interno.
Quando a origem está na janela carregada, a imagem usa a URL assinada já presente no histórico; quando
está fora da página, o frontend chama `GET /api/v1/atendimentos/{atendimentoId}/mensagens/{mensagemId}`
ou `GET /api/v1/chat-interno/conversas/{conversaId}/mensagens/{mensagemId}`. O backend valida a
visibilidade/participação antes de consultar e nunca faz busca global. A referência inteira é um
controle de teclado/mouse que centraliza e destaca a mensagem por tempo curto. Tombstones, falhas de
autorização e origens ausentes permanecem como “mensagem removida”/indisponível, sem conteúdo, URL ou
metadados sensíveis.

### 14/09/2026 — E177: finalização automática por inatividade

Foi criado o parâmetro de produção `atendimento.finalizar_apos_horas` na V70, com valor inicial de
24 horas e faixa de 1 a 720, e a V72 adicionou a trava `atendimento.finalizar_inativos.habilitado`,
BOOLEAN e `false` por padrão em todas as instâncias. O scheduler
`AgendadorDeFinalizacaoDeAtendimentosInativos` lê a trava a cada rodada: ausente ou `false` é um
estado normal (sem seleção, alteração ou alerta); somente `true` habilita a finalização e então lê
o limiar. A troca do valor no CRUD da instância passa a valer no próximo tick, sem redeploy.

Quando ligado, o scheduler executa em contexto `SERVICO`, a cada intervalo operacional configurável,
e processa no máximo o lote definido por `ATENDIMENTOS_FINALIZAR_INATIVOS_LOTE`. A recência é a
última mensagem de qualquer lado no atendimento, com `iniciado_em` como fallback. Cada candidato é
relido sob lock e passa pela `FinalizarAtendimentoUseCase`, então lead, avaliação, timeline e eventos
mantêm o mesmo contrato da finalização manual/automação. Apenas `EM_ATENDIMENTO` é elegível;
`EM_IA` permanece em Potenciais.

O primeiro ciclo após ligar o toggle pode finalizar um backlog real de conversas humanas paradas e,
quando a configuração de avaliação estiver ativa, preparar as solicitações correspondentes pela
mesma transição de finalização. O log registra apenas contagens agregadas e o corte (`candidatos`,
`finalizados`, `ignorados`, `falhas`), sem conteúdo ou dados de contato. O job não reabre conversas:
uma nova mensagem do cliente seguirá o fluxo existente e abrirá atendimento em IA.
