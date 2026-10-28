# Diagnóstico de um clique Z-API — botões e listas

## Conclusão da revisão do código

Na `main` após o merge da PR #334 (merge commit `a5be00f39a3663d8cf0642e707b9693de0bbaa1a`), `buttonsResponseMessage` e `listResponseMessage` já percorrem o caminho de entrada. A PR #334 acrescentou `pollVote`, que é uma expansão independente; enquete não é o formato de clique de botão/lista e não é evidência da causa do incidente relatado.

O contrato oficial vigente exemplifica no `ReceivedCallback`:

- botão: `buttonsResponseMessage.buttonId` e `buttonsResponseMessage.message`;
- lista: `listResponseMessage.selectedRowId`, `listResponseMessage.message` e `listResponseMessage.title`. No exemplo oficial, `message` contém a descrição e `title` o título da opção escolhida;
- identidade do evento: `messageId`; referência à mensagem original, quando fornecida: `referenceMessageId`.

Ambos os objetos de escolha aparecem no nível superior do callback e os exemplos não dependem de `text.message`. O tradutor testa as interações antes do ramo de texto e não exige `text.message`. Uma referência ausente não impede a persistência nem o repasse; se presente, é preservada separadamente e não é usada como ID do clique.

Fonte primária consultada em 27/10/2026: [Exemplos oficiais de ReceivedCallback](https://developer.z-api.io/webhooks/on-message-received-examples), seções “Resposta de botão de ação” e “Resposta de lista”. A mesma página documenta `pollVote` em seção própria.

## Condições que impedem persistência ou repasse

| Etapa | Condição observada no código | Efeito |
|---|---|---|
| Proxy/CORS | As labels no Compose Path unico criam uma rota exclusiva que remove `Origin` somente dos webhooks e desliga access logs/tracing nela. Se o servico implantado nao contem essas labels, o CORS pode recusar antes do controller; conferir a especificacao apos o deploy. | Pode haver resposta HTTP sem linha em `webhook_entrada`; consultar a tentativa no painel da Z-API. |
| Filtro de proteção | Rota diferente da rota exata, HTTPS não reconhecido, método diferente de POST, segredo ausente/inválido, corpo acima do limite ou `Content-Type` diferente de JSON. | Recusa antes de ler/persistir o corpo; códigos registrados pelo filtro quando a requisição chega ao backend: 403, 413 ou 415. |
| Controller | JSON inválido, chaves duplicadas/trailing tokens, objeto sem `instanceId`, instância divergente ou destino não configurado/ativo. | 400, 403 ou 503 antes da inserção. Os logs usam códigos fixos sanitizados. O guard de IDs vazios também responde 400, mas, com o tradutor atual e a instância já validada, `idsExternos` gera uma chave de deduplicação inclusive quando falta `messageId` (usa o JSON do evento); nesse caso o corpo é persistido e o worker depois registra `SEM_IDENTIFICADOR`, sem mensagem nem repasse. |
| Idempotência de entrada | A chave do evento já existe em `webhook_entrada`. | Responde normalmente, mas não agenda novamente o repasse; retry da outbox existente é separado. Na operação normal, a inserção e o agendamento da outbox estão na mesma transação. |
| Configuração do webhook | `somente-coleta=true`, tradutor Z-API/despacho desabilitado, ou configuração de automação ausente no momento do aceite. | O evento pode ficar persistido sem mensagem/outbox de repasse. Uma reentrega deduplicada depois que a configuração muda não agenda um novo repasse automaticamente. |
| Worker/tradutor | `type` diferente de `ReceivedCallback`; `fromMe` ausente/malformado ou true; grupo; flags de status/newsletter/edição/espera incompatíveis; ausência de telefone numérico suportado; `messageId` ausente/inválido; `momment` ausente, inválido ou não positivo; mais de um objeto interativo; resposta não objeto; `buttonId`/`selectedRowId` ausente, vazio, não textual ou com controles; botão sem `message` textual não vazio; lista sem `message` nem `title` textual não vazio; `title` presente em tipo não textual. | O evento pode estar persistido, mas fica registrado em `webhook_entrada.descartes`; não gera mensagem nem repasse interativo. Para lista, quando `message` está vazio, o CRM usa o `title` não vazio da opção como texto visível e ainda mantém título e `selectedRowId` em metadados. LID-only sem telefone continua fora da associação automática a lead. |
| Repasse e destino | `repasseSemGrupos` somente encaminha uma tradução reconhecida; o caso de uso grava outbox apenas se a integração estiver configurada. O publisher respeita provedor de origem, circuito, tentativas e esgotamento. | Sem outbox: configuração/eligibilidade a investigar. Com outbox pendente: tentativas/erro sanitizado. `publicado_em` significa que o destino HTTP respondeu 2xx, não que o workflow executou até o fim. |

Os nomes e códigos acima vêm dos guards do adaptador. O campo `message` dentro do objeto interativo não é o campo superior `text.message`; nas listas, ele pode trazer a descrição e o `title` o rótulo da opção.

## Causa comprovada na ocorrência controlada da Fêmina

A consulta somente leitura localizou a identidade técnica do clique em `webhook_entrada`, com tipo `LISTA`, processamento concluído, zero tentativas e um item descartado como `ITEM_MALFORMADO`. A inspeção sanitizada dos tipos confirmou `listResponseMessage` como objeto, `selectedRowId` textual válido e `title` textual, mas `message` textual vazio. Isso prova que a requisição chegou ao controller e foi persistida como entrada; não prova, por si só, o status HTTP observado pelo emissor. O descarte aconteceu no guard do tradutor, antes de criar a mensagem do atendimento e o repasse correspondente.

Correção nesta PR: lista com `message` vazio usa o `title` não vazio como texto exibido; o ID próprio do clique, `selectedRowId`, `title`, referência e envelope original continuam preservados. Botões continuam exigindo seu `message`; listas sem texto visível continuam sendo descartadas, sem inventar conteúdo. O JSON enviado ao n8n permanece o evento bruto original, inclusive `message` vazio. A PR não prova execução do workflow nem entrega/visualização pela Z-API em homologação.

## Prova automatizada do fluxo local

`ZApiInteracoesIT.escolhaSemTextoPersisteRecarregaPublicaUmaVezERespondeComIdDoClique` passa pela rota HTTP real, fila durável, PostgreSQL, persistência/histórico, evento de tempo real e publisher de outbox para HTTP de destino simulado. A asserção agora exige explicitamente `messageId`, ausência de `text`, e os pares `buttonId`/`message` ou `selectedRowId`/`message` no corpo HTTP de saída.

`escolhaSemTextoComOriginAtravessaProxyEEntregaCamposOriginaisAoDestino` percorre também o proxy Traefik de teste com `Origin` externo e segredo válido, depois o mesmo processamento e destino HTTP. O teste parametrizado cobre botão e lista. O transporte do n8n é simulado; isso prova o que o CRM envia ao HTTP, não execução real do workflow.

## Diagnóstico produtivo somente leitura

Faça um único clique de teste controlado, sem mensagens de pacientes, e anote uma janela UTC de até um minuto. Não dispare cliques adicionais. Executar o SQL da raiz do repositório a partir de uma sessão `psql` já autorizada:

```text
psql -X -v ON_ERROR_STOP=1 \
  -v inicio='AAAA-MM-DDTHH:MM:SSZ' \
  -v fim='AAAA-MM-DDTHH:MM:SSZ' \
  -v instancia='INSTANCE_ID' \
  -f docs/diagnosticos/coletar-clique-z-api.sql
```

O fim é exclusivo. O script abre transação `READ ONLY`, aplica timeout de 5s e imprime somente horários, IDs técnicos, estados, nomes de campos e motivos fixos sanitizados. Não imprime payload, escolha/texto, telefone, URL, token, segredo ou erro bruto. `pollVote` é rotulado como `ENQUETE_SEPARADA` para não confundi-lo com clique.

Os nomes das chaves são limitados a um catálogo conhecido; chaves não padronizadas aparecem apenas como `CAMPO_NAO_PADRONIZADO`. O teste de integração executa este mesmo SQL contra PostgreSQL real com uma chave sintética contendo um número e confirma que nem a chave nem seu valor aparecem no resultado.

Para recusas que não chegaram à persistência, no host Docker e somente para a mesma janela UTC, extraia exclusivamente os marcadores fixos do backend (não compartilhe a saída bruta de `docker service logs`):

```bash
docker service logs --timestamps --since 'AAAA-MM-DDTHH:MM:SSZ' fmnaprod-uzapi-36acu4_backend 2>&1 \
  | awk -v inicio='AAAA-MM-DDTHH:MM:SSZ' -v fim='AAAA-MM-DDTHH:MM:SSZ' '$1 >= inicio && $1 < fim' \
  | sed -nE 's/^([^ ]+).*Z_API_WEBHOOK_REJEITADO etapa=([A-Z]+) http=([0-9]+) codigo=([A-Z_]+).*$/\1 etapa=\2 http=\3 codigo=\4/p'
```

O filtro de segredo intencionalmente não lê o corpo antes de autenticar. Portanto, para 403 de autenticação, é correto que não haja `messageId` nem nomes de campos nos logs do CRM; correlacione horário e status com a tentativa de callback da Z-API. A rota do Traefik não registra access log/tracing, então um bloqueio CORS/proxy anterior ao backend também exige a tela/log sanitizado de tentativa HTTP do fornecedor para comprovar o status. Não habilite access logs temporariamente e não copie URL de callback.

### Interpretar as evidências sem misturar eventos

1. Uma linha `webhook_entrada` para o `messageId` técnico do clique prova que o controller aceitou/persistiu aquela identidade; compare `tipo_recebido`, nomes de campos, processamento e descartes.
2. `mensagem_id` preenchido na correlação `z-api` + instância + `WHATSAPP` prova que aquela mensagem do clique foi persistida. O texto foi deliberadamente excluído da saída.
3. Só o objeto em `repasses_correlacionados` com o mesmo `messageId`, instância e objeto `buttonsResponseMessage`/`listResponseMessage` é evidência do repasse daquele clique. Não conte outbox de outros IDs.
4. `HTTP_2XX_ACEITO` significa apenas resposta HTTP 2xx e `publicado_em`; o CRM não persiste o código HTTP exato nem a execução final do n8n. Use o `evento_id` estável para localizar a execução correspondente no n8n. Um evento publicado de outro `messageId` não prova o clique.
5. Sem linha de entrada, a ausência sozinha não distingue “callback não enviado” de recusa pré-persistência. Confirme o status HTTP da tentativa no painel Z-API e o marcador sanitizado do backend, se houver.

## Conferência da versão implantada

No manager Swarm, a leitura da especificação e das tasks é somente leitura:

```bash
docker service inspect fmnaprod-uzapi-36acu4_backend --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}'
docker service ps --no-trunc fmnaprod-uzapi-36acu4_backend --format '{{.Image}} {{.CurrentState}}'
```

O workflow de publicação usa tag curta do SHA e labels OCI. Se a imagem estiver disponível no nó atual, leia apenas a revisão:

```bash
docker image inspect 'ghcr.io/synapse-auto/erp-matriz-synapse-backend:TAG_DA_TASK' \
  --format '{{index .Config.Labels "org.opencontainers.image.revision"}}'
```

Compare o SHA/manifest efetivamente rodando com a história que contém o merge `a5be00f39a3663d8cf0642e707b9693de0bbaa1a`. Se o service/task mostrar somente tag sem digest, label indisponível ou tasks divergentes, a versão implantada não está comprovada. A revisão local em `main` e o CI da PR #334 não comprovam o que está rodando na Fêmina.

## Estado do incidente e limite operacional

A causa do não aparecimento desta resposta de lista foi comprovada no CRM: `message` veio vazio, `title` veio como texto e o guard rejeitou a entrada antes da mensagem/outbox. Isto é distinto de um callback ausente ou recusado antes da persistência. A falta de outbox é consequência direta do descarte do tradutor. Não há evidência de que o n8n tenha recebido este clique; também não se deve inferir execução ou falha do workflow a partir de eventos de outros IDs. Não atribuir este incidente a `pollVote`.

A correção de código é localmente testável e requer CI para a integração PostgreSQL real. Após a release, validar com um contato controlado que uma seleção de lista com `message` vazio e `title` preenchido aparece após recarga e chega ao endpoint de repasse sob o mesmo `messageId`. Não fazer replay da linha produtiva descartada; a deduplicação existente impede que reenviar o mesmo evento reabra automaticamente o processamento.

Nenhum deploy, replay, ajuste no painel Z-API ou alteração em workflow é necessário para executar a coleta.
