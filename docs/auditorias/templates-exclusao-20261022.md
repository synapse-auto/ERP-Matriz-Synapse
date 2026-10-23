# Exclusao de templates Meta — auditoria de 22/10/2026

## Origem e limite da conclusao

Relato da Estrutural, por uma usuaria gestora. Nao foram fornecidos horario, ID do
template, HTTP ou codigo da tentativa afetada. O papel nao e uma causa demonstrada.
Nenhuma credencial, VPS ou template real foi acessado/alterado nesta auditoria.

**Defeitos locais comprovados, nao causa presumida do incidente:**

1. O DELETE considerava qualquer JSON em HTTP 200 como sucesso, inclusive
   `success:false`, erro no corpo, array e objeto sem confirmacao. Seis casos
   de regressao falharam antes da correcao e passaram depois.
2. O cache de templates de Campanhas tinha `descartarCache()`, mas nenhum
   chamador em producao apos excluir. A UI invalidava apenas
   `["whatsapp-templates"]`, nao `["campanhas","templates"]`.
3. A confirmacao mostrava nome/impacto na conta compartilhada, mas nao o ID e
   idioma selecionados.

## Contrato e fontes primarias

Versao padrao do repositorio: Graph v21.0. A versao/credencial efetivamente
implantada na Estrutural nao foi inspecionada.

- [Colecao oficial Meta — Delete template by ID](https://www.postman.com/meta/whatsapp-business-platform/request/yf21ouy/delete-template-by-id):
  `DELETE /{WABA-ID}/message_templates?hsm_id={ID}&name={NAME}`.
- [SDK oficial Meta, tag 21.0.0](https://github.com/facebook/facebook-python-business-sdk/blob/21.0.0/facebook_business/adobjects/whatsappbusinessaccount.py):
  `delete_message_templates` usa DELETE na edge `/message_templates` do objeto
  WABA, com parametros `hsm_id` e `name`. Conferido pela API do GitHub.
- [Documentacao oficial de gestao](https://developers.facebook.com/documentation/business-messaging/whatsapp/templates/template-management/#delete-templates):
  consulta retornou HTTP 429. Nao foi contornada.

O CRM ja enviava WABA, ID e nome corretamente; Phone Number ID continua exclusivo
do envio de mensagens. O token e da configuracao do canal ativo, nunca escolhido
pelo navegador. Nao foi criado fallback por nome nem exclusao em lote.
O ID seleciona o recurso, inclusive entre idiomas de mesmo nome; a referencia
oficial diferencia a operacao por ID daquela somente por nome. O teste local
com duas variantes comprova que o CRM envia somente o ID escolhido, nao a
semantica efetiva de uma conta real.

Nao foi possivel verificar nesta sessao todas as restricoes atuais de negocio,
permissoes efetivas do token nem o estado do template real. A advertencia
preexistente sobre nome/30 dias foi preservada, nao revalidada como garantia.
Nenhum codigo/subcodigo de negocio foi inventado ou convertido em nova regra.

## Tratamento entregue

O endpoint existente continua `DELETE /api/v1/whatsapp/templates/{id}?nome=...`.
A autorizacao continua exigindo papel de gestao **e** `templates.excluir`.
Nao existe excecao por nome de pessoa ou cliente.

| Resultado Meta | CRM | Codigo adicional |
|---|---|---|
| 2xx, booleano `success:true`, sem erro no corpo | 204 | — |
| 401 | 422 | `TEMPLATE_EXCLUSAO_CREDENCIAL_RECUSADA` |
| 403 | 422 | `TEMPLATE_EXCLUSAO_PERMISSAO_RECUSADA` |
| 404 | 422 | `TEMPLATE_EXCLUSAO_RECURSO_INDISPONIVEL` |
| Outro 4xx/erro no corpo HTTP 200 | 422 | `TEMPLATE_EXCLUSAO_RECUSADO` |
| 429/5xx/breaker aberto | 503 | `TEMPLATE_EXCLUSAO_PROVEDOR_INDISPONIVEL` |
| Timeout/rede/JSON invalido/`success:false` ou ausente | 503 | `TEMPLATE_EXCLUSAO_RESULTADO_INCERTO` |

Uma recusa 401/403 do **provedor** nao e 401/403 da sessao CRM: isso evita
renovacao de login/repeticao do DELETE por confusao com autenticacao local.
404 nao prova que foi excluido; pode ser recurso inacessivel. Nao retorna falso
sucesso nem tenta excluir todas as variantes pelo nome. Resultado incerto pede
conferencia da lista/estado antes de nova tentativa manual.

Problem Details e log guardam somente etapa `excluir_template_meta`, motivo
normalizado, HTTP, codigo/subcodigo inteiros e `fbtrace_id` com caracteres e
tamanho limitados (descartado se contiver o token configurado).
Nao guardam corpo, URL, mensagem livre, conteudo do template ou excecao original.
O frontend traduz os codigos pelo catalogo, com defaults para catalogos antigos.

Recusas deterministicas nao abrem o breaker. Timeout/429/5xx continuam contando
no breaker separado de administracao de templates. Nao foi adicionado retry.
Envio/recebimento/outbox e outros provedores nao foram modificados.

## Cache, referencias e concorrencia

Somente apos confirmacao valida, o caso de uso publica `TemplateExcluido`,
evento local para invalidar a projecao de Campanhas. O listener usa AFTER_COMMIT,
com fallback quando nao existe transacao (a exclusao externa nao tem transacao
local). Nao publica fila, nao representa entrega duravel e nao altera campanhas.
A direcao de dependencia continua Campanhas → Atendimento.

A UI revalida os dois catalogos e preserva a lista em falha. O bloqueio sincrono
de clique duplicado e a protecao do dialogo em voo ja existiam e foram mantidos.
Nao ha atualizacao em tempo real entre abas/sessoes para o catalogo de templates.
O cache do backend e local ao processo; outras replicas, se houver, continuam
sujeitas ao TTL existente (60s, tolerancia de falha de 5min).

Campanhas armazenam snapshot (`template_id/nome/idioma/corpo`), nao FK para um
catalogo local deletavel. Nao foi encontrado bloqueio de referencia no caso de
uso de exclusao. Nao foi criada cascata nem regra nova de cancelamento automatico.
Mensagens programadas/automacao e historico nao sao apagados. A advertencia de
impacto continua na confirmacao.

## Validacao e reproducao

- Regressoes dirigidas iniciais: adaptador 57/57 e casos de uso 2/2.
- Endpoint real + Postgres: primeira rodada 29/29 em
  `TemplatesWhatsAppMetaIT`, `TemplatesWhatsAppIT`, `TemplatesWhatsAppSemWabaIT`.
- Suite final completa, frontend e E2E: registrar os resultados finais no PR.
- Frontend: primeira suite completa 1380/1380, typecheck e lint aprovados
  (4 avisos preexistentes); build em verificacao no momento deste registro.
- Capturas da aplicacao real em 1920x900 e 390x900:
  [confirmacao desktop](../evidencias/templates-exclusao-20261022/meta-confirmacao-1920.png),
  [resultado incerto desktop](../evidencias/templates-exclusao-20261022/meta-resultado-incerto-1920.png),
  [confirmacao celular](../evidencias/templates-exclusao-20261022/meta-confirmacao-390.png),
  [resultado incerto celular](../evidencias/templates-exclusao-20261022/meta-resultado-incerto-390.png).
  As duas verificacoes visuais dirigidas passaram na repeticao com rotas aquecidas.
  A primeira tentativa desktop excedeu os 30s totais do teste durante compilacao
  local; nao foi considerada aprovada. A suite final usa 90s por teste, um worker.
- O helper E2E antigo pressupunha login sempre em Atendimentos. Foi atualizado
  para aguardar a saida do login e navegar explicitamente a Atendimentos.
- Revisao adicional: JSON com tokens extras depois de success=true tambem e
  recusado. O clean verify foi reiniciado para cobrir essa ultima protecao.
- Novos casos exercitam timeout HTTP real, erros 401/403/404/400, erro dentro de
  200, sanitizacao, cache em sucesso/falha e snapshot de campanha preservado.

Para validar visualmente sem credenciais ou banco de cliente:

1. Executar o stub existente `node frontend/e2e/fixtures/graph-meta-stub.mjs`.
2. Compilar test-classes e executar `com.synapse.crm.app.TemplatesMetaLocal`
  com `TEMPLATES_VISUAL_LOCAL=true` e classpath de testes/dependencias.
   O fixture prepara banco/Redis descartaveis e fixa a Graph em 127.0.0.1:8089.
   Apenas a outbox sintetica executa seu ponto de entrada real em executor local,
   no intervalo existente da configuracao; outros jobs continuam desligados.
3. Frontend em localhost:3013, `NEXT_PUBLIC_API_URL=http://localhost:8093`,
   `SYNAPSE_BACKEND_URL` apontando ao mesmo backend (host.docker.internal em Docker).
4. `PLAYWRIGHT_BASE_URL=http://localhost:3013 PLAYWRIGHT_API_URL=http://localhost:8093
   npx playwright test e2e/templates-gestao.spec.ts --headed --workers=1`.

Esses testes usam componentes, estilos, API e adaptador reais, mas provedor
simulado. Nao equivalem a homologacao destrutiva na Meta. As capturas usam apenas
dados sinteticos; as fontes devem estar carregadas.

## Diagnostico pendente da ocorrencia real

Pedir ao operador, por canal privado, **somente**:

- janela de horario com fuso e ID tecnico do template/idioma;
- imagem/tag/digest do backend efetivamente implantado, versao Graph e WABA ID;
- HTTP do CRM e, se houver, etapa, HTTP/codigo/subcodigo/trace do provedor.

Nao pedir token, variaveis de ambiente, payload integral, corpo ou conteudo do
template. A leitura da imagem pode usar `docker service inspect SERVICO --format
'{{.Spec.TaskTemplate.ContainerSpec.Image}}'`, apos identificar o servico correto.
Na versao anterior, trechos brutos de erro podem conter dados sensiveis: nao
compartilhar logs integrais; extrair somente os campos tecnicos acima.
Sem essa correlacao, nao afirmar que a causa da tentativa da Michele foi resolvida.
Qualquer homologacao destrutiva depende de autorizacao expressa e template
descartavel. Nenhum reprocessamento/exclusao real foi autorizado aqui.

## Branch, operacao e divergencias

A branch partiu da PR #318 para reutilizar Next 16.4.2 validado. Durante esta
task, #318 foi mergeada; a nova PR pode usar `main` como base, sem repetir seu diff.
Alteracoes posteriores de Cancelamentos em main nao fazem parte desta entrega.

Esta auditoria complementa docs/48: seu antigo diagnostico de backend correto
nao cobria JSON de falso sucesso nem invalidacao do cache de Campanhas.
O procedimento antigo de bootstrap V73 nao e usado pelo fixture atual.

**Acao necessaria no Dokploy: nenhuma variavel nova. Sem deploy nesta task.**
