# Capacidades WhatsApp no CRM: lacunas e plano de implementação

**Estado:** Fase 1 (itens 1 a 4) implementada na branch `feat/whatsapp-contato-compartilhado` (V81), coberta por testes e **não verificada em instância real**; Fase 0 e Fases 2–4 seguem como plano. **Base:** auditoria estática recebida em 23/09/2026, conferida pontualmente com o código de `origin/main`. Não houve consulta às instâncias, aos bancos, aos logs de produção ou aos workflows do n8n. Este documento não certifica comportamento em produção.

## Fase 1 — comportamento implementado

**Contato compartilhado.** Os tradutores Meta e Uzapi/Autotic leem `messages[].contacts[]` e gravam
uma mensagem `CONTATO` (novo valor de `tipo_mensagem`, V81) com `conteudo` nulo e
`midia_metadados = {"contatos":[{"nome":"...","telefones":[{"numero":"...","waId":"...","tipo":"..."}]}]}`.
`nome`, `waId` e `tipo` só aparecem quando o provedor os envia (a Uzapi não envia `waId`). Vários
contatos e vários números no mesmo cartão são preservados na ordem; contato sem telefone fica com
`telefones: []`; cartão sem nome e sem telefone não traz nada utilizável e vira descarte
`CONTEUDO_INVALIDO`. O `value.contacts[]` do envelope segue servindo só para o nome do remetente.
Id externo, remetente, horário, ordem e deduplicação (`mensagem_recebida_idempotencia`) seguem o
mesmo caminho da localização. A citação não mostra nome/telefone do contato: rotula pelo catálogo.

**Bolha.** `BolhaContato` mostra nome e números; `tel:` e "copiar" só aparecem para número com 8 a
15 dígitos (sem letras). As chaves de texto novas (`media.contato`, `contatoSemNome`,
`contatoSemTelefone`, `copiarTelefone`, `ligarPara`, `mensagem.citacao.contato`) são opcionais no
schema, como as de citação: um catálogo de filho publicado antes delas não reprova a tela.

**Descartes observáveis.** O tradutor devolve `Traducao(mensagens, descartes)`. Cada descarte é
`{tipo, motivo}`, com `tipo` normalizado para os tipos documentados pelos dois provedores (o resto
vira `outro`) e `motivo` em `TIPO_NAO_SUPORTADO`, `CONTEUDO_INVALIDO`, `SEM_IDENTIFICADOR`,
`ITEM_MALFORMADO` ou `GRUPO_NAO_SUPORTADO` (E213: grupo ignorado de propósito, mas visível). O processador grava na linha `webhook_entrada.itens_descartados` e
`webhook_entrada.descartes` (JSONB) e escreve um único log
`[DESCARTE_WEBHOOK] entrada=<id_externo> provedor=<p> itens=<n> descartes=[tipo:MOTIVO,...]`.
Nenhum dos dois carrega telefone, nome, conteúdo ou payload. **Não contam como descarte**, por
decisão: `statuses[]` (inclusive `played`/`deleted` da Uzapi), Status/Story da Uzapi e reentrega
de mensagem já registrada. Não há métrica Micrometer: o módulo de atendimento não depende dele e
não há coleta de métricas em produção documentada; o registro durável é a própria linha.

Consulta operacional, limitada por tempo e servida pelo índice parcial `idx_webhook_entrada_com_descarte`:

```sql
SELECT id_externo, provedor, processado_em, itens_descartados, descartes
FROM webhook_entrada
WHERE itens_descartados > 0 AND processado_em >= now() - interval '7 days'
ORDER BY processado_em DESC
LIMIT 100;
```

**Limites conhecidos.** A linha só registra o descarte quando a transação do POST termina bem; se
o POST falha e esgota, vale o alarme `[ALERTA_WEBHOOK_ESGOTADO]` já existente. O SQL de conclusão
sem descarte é idêntico ao anterior à V81, então o caminho comum não depende das colunas novas; já
um contato recebido exige a V81 aplicada (valor `CONTATO` do enum). Onde a V73 ainda estiver
pendente, a V74 em diante — incluindo a V81 — não roda no boot (ver `docs/41`).

## Fase 2, item 1 — clique em botão de template (Meta)

**Evidência antes de implementar.** A Meta Cloud API documenta o clique em resposta rápida de
template como `type: button`, com `button.text`, `button.payload` e `context.id` (o wamid do template
enviado) — formato distinto da resposta interativa (`type: interactive` + `button_reply`), que já
era traduzida. O CRM lista e envia qualquer template aprovado da conta
(`MetaCloudApiAdapter.listarTemplates` não filtra componentes), inclusive os criados no Business
Manager com botões de resposta rápida; só a criação/edição pelo CRM é limitada a corpo textual.
Logo, o formato pode ocorrer na versão usada, e até aqui virava descarte `TIPO_NAO_SUPORTADO`.
**Não houve amostra real** — não há acesso às instâncias nesta etapa. Para confirmar uso real, basta a
consulta operacional acima filtrando `descartes @> '[{"tipo":"button"}]'`.

Na **Uzapi** o Swagger salvo (07/09) não tem template: o clique documentado é `type: button_reply`
com `interactive.button_reply.{id,title}`, que o tradutor já cobria.

**Comportamento.** `type: button` vira mensagem `TEXTO` do lead com `button.text`; `context.id`
vincula a resposta ao template (citação `RESPOSTA`) quando o template é do mesmo lead. O
`button.payload` é identificador de controle de quem montou o template: não vai para o histórico
(o n8n continua recebendo o payload cru). Clique sem `button.text`, sem objeto `button` ou com
`button` malformado vira descarte `CONTEUDO_INVALIDO` — o payload **não** substitui o texto — e não
derruba os demais itens do POST. Remetente, deduplicação e ordem seguem o caminho do texto; como em
toda mensagem recebida, `enviado_em` é a hora do processamento, não o `timestamp` do provedor.

**Testes.** `MetaCloudWebhookTradutorTest` (clique com contexto; clique vazio/sem objeto/malformado
no meio de um lote) e `RespostaInterativaWebhookIT` (POST assinado → fila → persistência → leitura
pela API com citação do template; assinatura inválida não grava; reentrega não duplica; descarte
registrado em `webhook_entrada.descartes` sem o payload do botão).

## Fase 2, item 2 — reação recebida do cliente (E214)

**Contrato do provedor.** Meta e Uzapi (schema `ReactionMessage` do Swagger) usam o mesmo formato:
`type: reaction`, `reaction.message_id` = id externo da mensagem reagida, `reaction.emoji` = emoji
atual; emoji vazio ou ausente = o cliente removeu a reação. O evento tem `id` próprio.

**Semântica (definida antes do banco).**

| Caso | Comportamento |
|---|---|
| Vínculo | Pelo id externo (`mensagem_id_externo`), nunca por "última mensagem". |
| Autoria | O cliente **não** é `usuario_id`: tabela própria `mensagem_reacao_cliente` (V82), uma linha por mensagem. `mensagem_reacao` (equipe) não é tocada. |
| Substituição | Reagir de novo troca o emoji. |
| Remoção | Grava `emoji = NULL` (não apaga a linha), para um evento atrasado não ressuscitar a reação. |
| Ordem | Só evento com `reagido_em` (horário do provedor) igual ou mais novo altera a linha. |
| Repetição | Mesmo id de evento é deduplicado em `mensagem_recebida_idempotencia`; evento que não muda o emoji não grava nem publica. |
| Mensagem desconhecida | Descarte `ALVO_DESCONHECIDO` na linha de `webhook_entrada`; não cria lead nem atendimento. |
| Outra conversa | O alvo precisa ser da conversa do lead que reagiu; senão, o mesmo descarte. Conhecer o id não dá acesso. |
| Remetente sem lead | Mesmo descarte; reação nunca cria lead. |
| Efeitos colaterais | Reação não é mensagem: não entra no histórico, não conta interação, não passa pelo reset nem abre a janela de 24h. |
| Emoji inválido / sem alvo / malformado | `CONTEUDO_INVALIDO` / `SEM_IDENTIFICADOR`, sem derrubar o lote. |

**Leitura e tela.** `GET /api/v1/atendimentos/{id}/mensagens` ganha o campo aditivo `reacaoDoCliente`
(emoji ou `null`), carregado em lote pela PK junto das reações da equipe — uma consulta a mais por
página, sem varrer partição. A autorização continua a do histórico (RN-CRM-01 pela Specification).
Depois do commit, o CRM publica no tópico do atendimento o evento WebSocket
`REACAO_CLIENTE {atendimentoId, mensagemId, enviadoEm, emoji|null}` — sem telefone nem nome —, e a
tela troca só esse campo no cache, sem F5 e sem mexer nas reações da equipe. Na bolha a reação do
cliente é um chip informativo (não é botão), com rótulo do catálogo `acoes.reacaoDoCliente`
(opcional; catálogo antigo mostra o próprio emoji).

**Testes.** `ReacaoRecebidaTradutoresTest` (Meta e Uzapi: reação, remoção, alvo ausente, emoji
inválido, malformada, sem remetente), `WebhookReacaoDoClienteMetaIT` (assinatura inválida; alvo de
outra conversa e desconhecido viram descarte; reação da equipe preservada; atendente de outro lead
recebe 404; reentrega sem republicar; substituição; evento atrasado ignorado; remoção; eventos de
tempo real), `WebhookReacaoDoClienteUzapiIT` (segredo, reentrega, alvo desconhecido, remoção),
`SchemaMigracoesIT`, testes de frontend do chip e do cache. Com a guarda "mesma conversa" desligada
de propósito, `WebhookReacaoDoClienteMetaIT` reprova.

## Fase 2, item 3 — isolamento de grupo (filtrar com observabilidade)

**Decisão aplicada:** filtrar. Conversa de grupo **não** foi implementada (sem UI, schema ou fluxo).

**Falha encontrada.** O Swagger da Uzapi declara `isGroup` obrigatório em toda mensagem recebida, e
o registro de callback recomendado em `docs/38` §8.0 liga `group_messages: true`. O tradutor ignorava
a flag: a mensagem de grupo chegava com `from` = participante e era gravada no atendimento individual
dele — e passava pelo comando de reset por texto. O controller repassava o POST à Automação antes de
qualquer tradução, então o n8n recebia o texto do grupo como se fosse do privado. Provado por
`WebhookGrupoUzapiIT`: com o filtro desligado, "no grupo" cai na conversa do participante e o repasse
é enfileirado. As 10 amostras reais de Status registradas na E163 já traziam `isGroup: true`, o que
confirma a presença do campo no payload real.

**Comportamento.**

- **Uzapi:** item com `isGroup: true` (booleano ou texto) ou com JID de chat terminando em `@g.us`
  (`from`, `chatid`, `remoteJid`, `groupId`…) vira descarte `GRUPO_NAO_SUPORTADO`, com o tipo
  normalizado e sem telefone/conteúdo. Status/Story continua identificado só por `status@broadcast`,
  avaliado **antes**, e ignorado em silêncio (decisão da E163 preservada: `isGroup` nunca aciona o
  filtro de Status).
- **Meta:** defensivo. A conta Cloud API usada é individual; item com `group_id` recebe o mesmo
  descarte. Formato de grupo da Meta não verificado nesta etapa.
- **Repasse à Automação (decisão de 25/09: misto não é limite aceitável):**
  - POST sem grupo: corpo e assinatura **originais, byte a byte**.
  - POST só de grupo: não é repassado.
  - POST misto: repassado **no mesmo envelope, sem os itens de grupo**. Na Meta a
    `X-Hub-Signature-256` é recalculada com o mesmo App Secret, então continua válida para quem a
    confere; a Uzapi não assina o corpo (segredo na query).
  - Reentrega do provedor (mesmo POST, mesma linha de `webhook_entrada`) **não é repassada de novo**:
    a mensagem privada é processada uma vez no CRM e uma vez no n8n. POST só de status continua sendo
    repassado a cada chegada, como antes.
- O POST original (com grupo) ainda entra em `webhook_entrada`, para o descarte ficar na consulta
  operacional. Nenhum contrato `/internal/v1` mudou.
- **Segunda camada no n8n:** o workflow também deve ignorar grupo — ver
  [`docs/n8n/filtro-de-grupo.md`](./n8n/filtro-de-grupo.md). Não aplicado nesta etapa (sem acesso ao
  workflow); precisa ser feito e testado antes do deploy.

**Testes.** `UzapiAutoticWebhookTradutorTest` (flag booleana e textual, JID `@g.us`, grupo ≠
Status, `repasseSemGrupos`), `MetaCloudWebhookTradutorTest` (`group_id`; assinatura refeita e válida
no misto, original intacta sem grupo), `WebhookCanalControllerTest` (repasse agendado com o corpo e a
assinatura devolvidos), `WebhookGrupoUzapiIT` (segredo inválido; grupo com `#reset` não toca o
atendimento, não cria lead nem repassa; lote misto realista com reentrega: privado gravado uma vez,
`#reset` do grupo não devolve a conversa da Ana à IA, repasse único e sem o item de grupo). Com o
filtro do repasse desligado de propósito, os dois ITs reprovam.

## Objetivo e limites

Garantir que uma mensagem válida e relevante ao atendimento não desapareça silenciosamente entre o provedor WhatsApp e o histórico do CRM. A comparação é por **provedor × direção × capacidade**: recurso do aplicativo WhatsApp não implica suporte da API, da versão instalada ou do CRM. A Base PAI deve funcionar por capacidade, sem condicional pelo nome do cliente.

Antes de qualquer mudança, preservar a disponibilidade da aba Atendimentos: parsing, persistência e observabilidade novos não podem bloquear sincronicamente o caminho de envio/recebimento. Não fazer testes que enviem mensagens a clientes reais sem autorização específica. Payloads, telefones, tokens e nomes não entram em logs, métricas ou fixtures não sanitizados.

## O que a auditoria encontrou

| Capacidade | Meta | UZAPI/Uzapi Autotic | Situação no CRM e evidência | Prioridade proposta |
|---|---|---|---|---|
| Contato compartilhado recebido | Documentado pelo provedor | Documentado pelo provedor | `type: contacts` não consta do mapeamento de nenhum dos dois tradutores; tipo desconhecido gera `WARN` e o item é descartado. `value.contacts[]` é usado para o perfil do remetente e **não** é o contato compartilhado de `messages[].contacts[]`. A linha de entrada pode terminar processada sem mensagem no histórico. **Fase 1: traduzido, persistido e exibido nos dois provedores; coberto por testes, não verificado em instância real.** | P1; elevar a P0 se o incidente real for correlacionado |
| Reação recebida | Documentada | Documentada | **Fase 2, item 2 (E214): traduzida nos dois provedores e gravada em `mensagem_reacao_cliente` (V82), separada da reação por usuário do CRM; aparece na bolha e chega por evento `REACAO_CLIENTE`. Coberto por teste local e CI; não verificado em instância real.** | P2 |
| Resposta rápida de template recebida | `type: button` documentado | `button_reply` (sem template no Swagger) | **Fase 2, item 1: Meta `type: button` traduzido (ver seção abaixo); Uzapi já traduzia `button_reply`. Coberto por teste local e CI; não verificado com payload real.** | P1 suspeito; confirmar uso antes da correção |
| Tipo `unsupported` ou novo | Pode ocorrer | Pode ocorrer | Item descartado com `WARN`, sem indicação ao atendente ou contador operacional por tipo. Não transformar todo evento desconhecido em mensagem visível: status e ruído devem continuar filtrados. **Fase 1: o descarte agora fica na linha da fila e no log `[DESCARTE_WEBHOOK]`; aviso ao atendente continua pendente.** | P2 |
| Grupo recebido | Fora do fluxo individual deste plano | Possível na configuração da instância | **Confirmado no código (E213): a mensagem de grupo entrava na conversa individual do participante e ia para a Automação. Corrigido: filtrada com descarte `GRUPO_NAO_SUPORTADO` e sem repasse (ver seção abaixo). Conversa de grupo continua não implementada. Coberto por teste local e CI; não verificado em instância real.** | P2, decisão de produto antes de alterar |
| Vídeo enviado pelo atendente | Adaptador tem caminho de envio | Adaptador tem caminho de envio | Tipos permitidos no domínio e seletor do composer não incluem vídeo. | P2 |
| Figurinha recebida | Aceita | Aceita | Renderizada como imagem, sem identificação de figurinha. | P3 |
| Status `played`/`deleted` | Contrato próprio | Eventos possíveis | Não mapeados pela integração UZAPI. Sem decisão de produto registrada sobre exibição/semântica. | P3 |
| Teste ponta a ponta da entrada UZAPI | — | — | Há testes do tradutor, mas não ficou demonstrado teste controller → job → mensagem comparável ao da Meta. | P2 |

Referências de código inicial: [tradutor Meta](../backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/canal/MetaCloudWebhookTradutor.java), [tradutor UZAPI](../backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/canal/UzapiAutoticWebhookTradutor.java), [processador de entrada](../backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/infrastructure/webhook/ProcessadorDeWebhookEntradaOperacoes.java), [tipos de mídia](../backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/domain/midia/TiposDeMidiaPermitidos.java), [seletor de arquivos](../frontend/src/lib/atendimento/arquivos-do-composer.ts). Contratos existentes: [Meta/UAZAPI](./37-contrato-uazapi.md) e [UZAPI Autotic](./38-contrato-uzapi-autotic.md). Essas referências são pontos de partida; o implementador deve redescobrir o caminho completo no commit em que trabalhar.

### O que **não** está comprovado

- Não foi correlacionado o contato compartilhado relatado pelo usuário a um webhook, provedor, horário, mensagem externa ou registro do banco. A causa do incidente específico permanece hipótese, embora o descarte de `contacts` esteja demonstrado no código.
- O código prevê repasse de payload bruto ao n8n quando configurado; **não** está comprovado que o evento foi entregue nem como o workflow o tratou. A automação deve ser auditada separadamente, com export/execução sanitizados e autorização.
- “Implementado e coberto por teste” não equivale a “confirmado na instância”. O suporte operacional dos outros tipos depende de payloads e versões efetivos.
- A classificação de grupo UZAPI, `button` Meta em uso e status `played`/`deleted` carece de amostra real ou decisão funcional.

## Plano de implementação, em ordem de dependência

As etapas são propostas para PRs pequenos e independentes, com deploy gradual por instância. Não há prazo ou percentual de ganho inventado. Cada etapa exige testes negativos que provem que a proteção realmente atua.

### Fase 0 — Confirmar o incidente e fixar a linha de base (investigação, sem deploy)

1. Obter instância/provedor, janela de horário e identificador externo, quando disponível. Consultar `webhook_entrada` por janela **indexada/limitada** e correlacionar com log, persistência e repasse. Evitar varredura por regex no payload inteiro em produção durante pico; primeiro restringir pelo tempo e inspecionar amostra pequena e sanitizada.
2. Confirmar em qual ponto o contato parou: provedor → controller → fila → tradutor → mensagem → UI → n8n. Distinguir “não chegou” de “chegou e foi descartado”.
3. Registrar contagem de descartes por provedor/tipo e taxa de falhas atuais como linha de base; se os dados não existirem, registrar explicitamente a limitação.

**Saída:** incidente confirmado, refutado ou não verificável, com evidência e sem PII. Não elevar para P0 por hipótese.

### Fase 1 — Tornar perdas observáveis e receber contatos compartilhados (P0/P1)

1. Fazer descarte de tipo válido não suportado deixar evidência operacional estruturada por provedor, tipo e motivo, sem payload/telefone. Definir retenção e cardinalidade segura. A linha processada precisa permitir identificar descarte parcial sem transformar eventos de status em falso incidente. Alarmes devem distinguir perda de mensagem de evento intencionalmente ignorado.
2. Adicionar contato compartilhado ao domínio e aos tradutores Meta e UZAPI, com representação estruturada de nome e telefones, preservando deduplicação, lote misto, id externo, remetente e ordem. Escolher migration **nova** se tipo persistido exigir enum; verificar o próximo número em `origin/main` antes de criá-la. Não usar o array de perfil `value.contacts[]` como mensagem.
3. Persistir e exibir bolha acessível de contato no histórico, inclusive contato sem telefone e múltiplos contatos/números. Ação de chamar/copiar só aparece se houver número válido. Textos via catálogo e cores via tokens.
4. Cobrir os dois provedores em teste de entrada real (controller → job → persistência → leitura), além de casos de uso e UI. Testes negativos obrigatórios: envelope com apenas perfil não cria mensagem de contato; item malformado não elimina os demais do POST; duplicata não duplica mensagem; remetente fora da visibilidade não vaza lead.

**Pronto:** um contato de cada provedor aparece no histórico e é recuperável após recarregar; falha/descartes são rastreáveis; nenhuma regressão no recebimento de texto, mídia e status. Validar primeiro em homologação com payload sanitizado e depois em janela controlada por instância.

### Fase 2 — Interações recebidas e segurança do fluxo (P1/P2)

1. Confirmar se templates ativos geram `type: button` na Meta. Se sim, traduzir texto/identificador e contexto de forma que o atendente veja a resposta; não tratar clique vazio como resposta válida. Testar contra payload oficial/observado e webhook completo.
2. Definir semântica e modelo de reação do cliente: associação ao `wamid`, atualização/remoção, reação repetida e origem externa. Não improvisar `usuario_id` do CRM para representar cliente. Provar que reação a mensagem desconhecida não altera outra conversa nem derruba o lote.
3. Resolver grupos UZAPI por decisão explícita: **filtrar com observabilidade** ou implementar conversa de grupo de ponta a ponta. Até essa decisão, não transformar grupo em conversa individual nem responder automaticamente ao privado. Exigir teste de isolamento com `isGroup` e identificador de grupo.
4. Implementar teste de integração de entrada UZAPI incluindo segredo inválido, POST com itens mistos, retries e deduplicação. Validar que evento ignorado não bloqueia mensagem válida do mesmo lote.

**Pronto:** ações do cliente não somem silenciosamente e grupo não gera resposta no destinatário errado. Preservar contratos `/internal/v1` e comportamento existente do n8n salvo mudança contratual aprovada.

### Fase 3 — Saída e acabamento de mídia (P2/P3)

1. Habilitar vídeo no domínio e no composer apenas após conferir limites/tipos reais dos dois provedores, upload, armazenamento, feedback de erro, status e custo operacional. Testar vídeo aceito, acima do limite, MIME incorreto e falha de envio sem travar chat.
2. Diferenciar figurinha de imagem no histórico se o provedor expuser metadado confiável; manter compatibilidade com mensagens antigas.
3. Decidir e documentar se `played` e `deleted` da UZAPI devem atualizar estado, aparecer como evento ou ser ignorados intencionalmente; só então implementar e testar transição sem regressão de status.
4. Avaliar envio de contato, localização, reação e figurinha **por valor de produto e suporte efetivo do provedor**. Hoje não estão disponíveis no composer; não assumir que todos são obrigatórios para a primeira entrega.

### Fase 4 — Recursos avançados (backlog condicionado, não compromisso de paridade)

Avaliar individualmente pedido, Flows, edição/revogação em Coexistência, grupos Meta, troca de número, localização ao vivo, enquetes, catálogo e metadados de anúncio (`referral`). Para cada um: provar suporte na versão/API usada, demanda real, regra de segurança, contrato do n8n, testes e custo. Se não houver suporte ou demanda, registrar “ignorado intencionalmente”, não “bug pendente”. Templates UZAPI e composição manual de botões/listas seguem decisões/fluxos próprios até revisão explícita.

## Critérios transversais de aceite e rollout

- **Segurança:** nenhum webhook de outro número/instância entra no histórico; RLS e `VisibilidadeLeadSpecification` continuam valendo; resposta manual não permite roubar lead fora do recorte. Testar casos negativos por papel e por origem.
- **Resiliência:** mensagem inválida não derruba as válidas do mesmo POST; retries são idempotentes; falhas de mídia e repasse externo não bloqueiam a aba Atendimentos. Observar filas, tempo de processamento e conexões de banco antes/depois.
- **Contrato:** mudanças em `/internal/v1` exigem teste de contrato e coordenação com n8n; não inferir que workflow foi atualizado pelo deploy do CRM.
- **Compatibilidade:** mensagens antigas continuam legíveis; migration nunca altera arquivo Flyway já aplicado; rollback da aplicação não pode tornar o histórico ilegível sem plano de reversão.
- **Verificação:** `backend/./mvnw clean verify -Dmaven.compiler.release=21`, testes frontend, cenários reais sanitizados por provedor e CI com número da run. Deploy e medição por instância, fora do pico, com critério de rollback se aumentar falhas, latência ou uso de recursos.

## Decisões pendentes do produto/operador

1. Qual instância/provedor e horário do contato compartilhado relatado? Isso é necessário para fechar o incidente real.
2. Aprovar contato como tipo estruturado (recomendado) ou texto formatado; definir exposição de múltiplos números e retenção de dados pessoais.
3. Grupos UZAPI: filtrar ou suportar conversa de grupo completa? O comportamento atual não deve ser assumido como correto.
4. Reação recebida: representação e semântica de remoção/atualização; definir se é prioridade antes de vídeo de saída.
5. Quais recursos de saída e recursos avançados têm demanda real nos clientes? Não abrir implementação de paridade total sem essa decisão.

## Divergências documentais a corrigir durante as respectivas fases

- ~~Comentários do processador de entrada citam figurinha como não suportada~~ — corrigido na Fase 1.
- ~~Comentário da bolha de mensagem omite capacidades já presentes~~ — corrigido na Fase 1.
- ~~O contrato UZAPI/Autotic não explicita o descarte atual de contato e reação~~ — `docs/38` §8
  atualizado na Fase 1 (contato traduzido; reação registrada como descarte).
