# Evolution API — auditoria e progresso

## Estado: bloqueio contratual, implementação não iniciada

Auditoria de 02/11/2026 sobre `origin/main` em
`989dcc0e199821e05fb1af0946582bd3b3a6ef03` (PR #339 incorporada).
Branch independente: `codex/evolution-api`. Em 03/11/2026, a base foi sincronizada
com `origin/main` em `3a687b3`, que já incorporou a PR Darwin #340. Essas alterações
são herdadas da main; o diff próprio desta PR continua exclusivamente documental.
Esta entrega documental **não habilita nem implementa o canal** e não comprova
homologação. Nenhum cliente existente, workflow, banco ou ambiente foi alterado.

O prompt pede confirmar a release e modalidade antes de implementar. Foi
solicitado ao responsável: **tag/release exata, Baileys ou Cloud API, URL base e
nome/identificador da instância, sem segredos**. Resposta ainda pendente.
URL/nome poderão ser configurados depois; release e modalidade são essenciais
para selecionar o contrato, inclusive janela de envio, citação, mídia e eventos.
Não foi presumido `latest`, Baileys, Evolution Go nem fork compatível.

## 1. Fontes oficiais verificadas

Fontes públicas consultadas em 02/11/2026; não foram chamados endpoints de negócio.
O portal é mutável: sua versão publicada não comprova a versão a implantar.

| Fonte | Evidência e limite |
| --- | --- |
| [Visão geral](https://docs.evolutionfoundation.com.br/evolution-api) | Evolution API Node.js/TypeScript admite Baileys e Meta Cloud API; Evolution Go é outro produto. A modalidade não pode ser inferida do nome Evolution. |
| [Guia de webhooks](https://docs.evolutionfoundation.com.br/evolution-api/configuration/webhooks) | Cita `/webhook/instance`, campos `webhook_by_events`/`webhook_base64` e eventos `MESSAGES_UPSERT`, `MESSAGES_UPDATE`, `SEND_MESSAGE`. Não usar esse guia como schema da release desconhecida. |
| [Set Webhook](https://docs.evolutionfoundation.com.br/evolution-api/set-webhook) | Referência mostra POST `/webhook/set/{instanceName}`, `enabled`, `url`, `events`, `headers`, `base64`; `apikey` autentica essa chamada de configuração. O campo `headers` é evidência de configuração, não prova de assinatura HMAC ou de que toda release o suporta. |
| [Send Text](https://docs.evolutionfoundation.com.br/evolution-api/send-text-message) | Referência mostra POST `/message/sendText/{instanceName}`, `number`, `textMessage.text`, `quoted`; objeto `quoted` sem estrutura suficiente para assumir o formato da citação. |
| [Send Media](https://docs.evolutionfoundation.com.br/evolution-api/send-media-message) | Referência mostra POST multipart `/message/sendMedia/{instanceName}`, `number`, `media`, `caption`, `fileName`. Não substituir por um JSON de outra versão. |
| [Create Instance](https://docs.evolutionfoundation.com.br/evolution-api/create-instance) | POST `/instance/create`, `instanceName`, `integration`, `token`, `number`, `webhook`; não executar provisionamento no fornecedor nesta task. |
| [OpenAPI Message](https://docs.evolutionfoundation.com.br/api-reference/openapi/Evolution-API/message.yaml) | `info.version: 2.3.7`. É a versão declarada **deste arquivo**; não houve confirmação de que será a release implantada. |

**Divergência concreta:** o guia geral e a referência do webhook não descrevem a
mesma rota/estrutura. Isso impede escolher payload por semelhança com exemplos
antigos. Após identificar a release, conferir documentação e código oficial
daquela tag para resolver a divergência e completar callbacks, download e citação.

## 2. Mapa do CRM auditado

Todos os caminhos abaixo são relativos à raiz do repositório.

| Caminho existente | Reuso e adequação necessária |
| --- | --- |
| `crm-atendimento/.../domain/canal/CanalGateway.java` e `TradutorDeCanal.java` | Portas puras já cobrem envio, tradução, status, mídia e referências. ACL Evolution deve ficar em infraestrutura, sem JSON do fornecedor no domínio. |
| `crm-atendimento/.../infrastructure/canal/SeletorDeCanalGateway.java` | Entrada/saída escolhidas pela mesma chave `synapse.canal.whatsapp.provedor`; chave sem adaptador falha no boot. Não existe fallback automático para outro provedor. |
| `crm-atendimento/.../domain/canal/ResultadoDeEnvio.java` | Já distingue `Enfileirado`, `Ambiguo`, `Aceito` e `Recusado`. Reutilizar sem tratar HTTP 2xx como entrega e sem retry cego de POST incerto. |
| `crm-atendimento/.../infrastructure/outbox/PublicadorDaOutboxTransacoes.java` | `Enfileirado` registra identidades WHATSAPP/PROCESSAMENTO; `Ambiguo` suspende tentativa automática. Não duplicar outbox/reconciliação. |
| `crm-atendimento/.../infrastructure/persistencia/referencia/CorrelacaoDeMensagemDoCanalRepositorioJdbc.java` | Identidade composta provedor/instância/namespace/ID, status antecipado e atualização monotônica já existem; conflitos não podem reassociar mensagem. Testar a integração efetiva, não só a porta. |
| `crm-app/.../mensagem/CompositorDeEnvioParaCanalConfigurado.java` | Resolução de citação composta tem condição explícita `z-api`; só criar outro adapter não habilita citações Evolution. Não ampliar silenciosamente o caminho legado dos demais canais. |
| `crm-atendimento/.../infrastructure/webhook/ProcessadorDeWebhookEntradaOperacoes.java` | Processa tradução/status/registro/mídia duráveis. Reserva por provedor e resolução de citação composta também têm ramificações explícitas Z-API; precisam avaliação/testes ao adicionar o canal. |
| `crm-atendimento/.../infrastructure/persistencia/canal/RoteamentoDeSaidaJdbc.java` | Ancoragem de credencial em ciclos novos sem credencial histórica é explícita para `z-api` e `env://Z_API_TOKEN`. Nova instância Evolution precisa cadastro e ancoragem próprios, não reutilização do token Z-API. |
| `crm-atendimento/.../infrastructure/canal/ZApiWebhookProtegidoFilter.java` e `interfaces/WebhookZApiController.java` | Referência para autenticar antes do corpo/persistência, limitar bytes e confirmar instância. Não copiar segredo da API de saída como se autenticasse callback. |
| `crm-atendimento/.../infrastructure/canal/ZApiMidiaEntrada.java` | Referência para streaming limitado, MIME real, breaker/bulkhead, host exato, DNS público, sem redirects/retry transparente. A política recusa rede privada; não serve sem adaptação para Evolution interna. |
| `crm-atendimento/.../interfaces/MidiasDoLeadController.java` | Download privado autenticado existente: `Content-Disposition: attachment`, nome UTF-8 e `nosniff`. Reutilizar autorização e storage; não expor URL do provedor. |
| `crm-shared-kernel/.../midia/DocumentoWord.java` | DOC/DOCX têm MIME, coerência nome/bytes e nome seguro; DOCM não é Word comum. Não ampliar allowlist por extensão. |
| `crm-app/.../mensagemprogramada/ProcessarMensagemProgramadaUseCase.java`, `crm-atendimento/.../application/EncaminharMensagemUseCase.java` e `application/encaminhamentodochat/EncaminharDoChatInternoParaClienteUseCase.java` | Pontos existentes a exercitar nos testes do novo canal. Suporte do adapter isolado não comprova esses fluxos. |

Prefixos da tabela: `crm-*` ficam dentro de `backend/`; `...` representa
`src/main/java/com/synapse/crm/<módulo>/`. São referências de navegação, não
nomes de novos arquivos a criar automaticamente.

## 3. Provisionamento e deploy: lacunas identificadas

- `docker/provisionamento/provisionar-instancia.sql` valida `whatsapp_numero`
  como Phone Number ID numérico Meta e grava `env://WHATSAPP_TOKEN`. Não usar
  esse fluxo inalterado para um nome de instância Evolution nem afrouxar a
  validação Meta globalmente. O cadastro Z-API separado, com SQL testado em
  `CadastroCanalZApiIT`, oferece um padrão de reuso, não um cadastro Evolution pronto.
- `canal`/`canal_credencial` já existem; não há necessidade comprovada de nova
  tabela ou migration. Credencial deve guardar referência, nunca token literal.
- `docker/dokploy-stack.yml` possui router HTTPS dedicado aos caminhos
  `/webhook/z-api` e `/webhook/canal`, remoção de Origin restrita a eles e
  observabilidade reduzida. Uma nova rota não está automaticamente coberta:
  persistir a adequação no stack, preservando as demais rotas.
- `SecurityConfig` permite `/webhook/**`, mas CORS da aplicação continua
  restrito à origem do frontend. `permitAll` não autentica o fornecedor e não
  resolve Origin externo. Testar a rota final protegida e o caminho de proxy.
- Nenhuma variável Evolution foi criada ou habilitada. Defaults atuais e
  configuração dos provedores existentes permanecem intactos. **Dokploy: nenhuma
  ação agora; não há canal Evolution para ativar neste commit documental.**

## 4. Repasse à automação

`AgendarRepasseWebhookAutomacaoUseCase` grava intenção na outbox; o HTTP ocorre
em `RepasseWebhookAutomacaoHttpAdapter`. O contrato vigente preserva o corpo
do provedor e inclui `X-Synapse-Webhook-Token` quando configurado,
`X-Synapse-Evento-Id` e `X-Synapse-Tentativa`. Não normaliza automaticamente um
envelope novo para o parser de Meta/Uzapi/Z-API.

`docs/integracoes/z-api/documento-para-automacao.md` já distingue resposta pelo
CRM (`/internal/v1/atendimentos/{id}/responder`) de envio direto no n8n. Alterar
o provedor do CRM não altera nós HTTP publicados. O repositório não contém um
inventário comprovado dos nós da nova instância.

Evolution requer parser/roteamento explícitos no consumidor, origem do evento e
deduplicação antes dos efeitos. Após confirmar o contrato, redigir credenciais
do payload **antes** da fila, logs e repasse; preservar dados funcionais e não
fabricar assinatura Meta para o corpo transformado. Repasse Evolution não será
ativado por herança de um workflow configurado para outro envelope.

## 5. Matriz de implementação e testes ainda pendentes

| Frente | Evidência exigida para concluir (ainda não executada para Evolution) |
| --- | --- |
| Contrato | Release/modalidade fixadas; endpoints/schema da tag; proteção real de callback; IDs, status, eventos, download e citações completos. |
| Saída real do CRM | HTTP CRM → outbox/publisher → transporte Evolution simulado; texto, quatro mídias, DOC/DOCX, metadados/bytes e capacidades recusadas antes do HTTP. |
| Entrada real do CRM | Webhook autenticado → PostgreSQL → worker → histórico/API → tempo real; interações sem texto; quotes recebidas/enviadas, eco e contato opaco. |
| Integridade | Status antes do retorno de envio, repetidos/fora de ordem, conflito por provedor/instância, timeout/5xx ambíguo sem reenvio automático. |
| Mídia e autorização | Storage real de teste; bytes/nome/MIME, download negado sem acesso ao lead, SSRF/redirect/tamanho/MIME; falha indica etapa real sem URL/token. |
| Integrações existentes | Encaminhamento normal e interno→cliente, programadas, automação, permissões/participação e regressão Meta/Uzapi/Z-API. |
| Configuração | OFF sem rede; segredo/credencial/instância incorretos; cadastro novo idempotente; HTTPS/Origin externo no stack; destino interno permitido só se explicitamente configurado. |

Não há fixture Evolution inventada nem simulador apresentado como homologação.
Não foram criados endpoint, migration, adapter experimental, polling ou controles
de interface sem funcionamento.

## 6. Verificação e continuidade

- Auditoria de código/configuração e fontes públicas: realizada conforme seções acima.
- O CI original falhou em fixtures antigas de encaminhamento, não neste documento:
  a limpeza de `EncaminhamentoDoChatParaClienteIT` fazia DELETE de origem e citação
  no mesmo lote, conflitando com o trigger V65; `RespostaEEncaminhamentoIT` esperava
  a fila inteira após uma única rodada limitada do publicador, com scheduler OFF.
  Evidência: [run PR 41478185581](https://github.com/synapse-auto/ERP-Matriz-Synapse/actions/runs/41478185581)
  e [run push 41478101814](https://github.com/synapse-auto/ERP-Matriz-Synapse/actions/runs/41478101814).
- A atualização da base traz as correções e testes de regressão já integrados pela
  PR #340: desvincular apenas citações do próprio fixture antes da limpeza e
  exercitar novas rodadas do publicador durante a espera por condição. Sem editar
  migration aplicada, desativar trigger, aumentar timeout ou pular teste.
- `clean verify` completo Java 21 e CI são reexecutados sobre a base atualizada.
  Os resultados, SHA e números das runs finais devem constar na PR #341; não
  inferir aprovação de runs anteriores ou de execução local.
- Testes funcionais Evolution e homologação: **não executados**; dependem do contrato.
- CI/PR/SHA de entrega: registrar após commit/push, sem inferir CI de execução local.
- Após resposta do responsável, continuar **esta task/branch**: fixar fontes da
  release, implementar o fluxo completo e seus testes antes de declarar pronto.

Não conectar número, registrar webhook real, enviar mensagem, fazer merge,
deploy ou alterar n8n de produção. A PR documental deve permanecer draft;
nenhum item funcional do prompt está concluído apenas por esta auditoria.
