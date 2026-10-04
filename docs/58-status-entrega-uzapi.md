# Status de entrega UZAPI — diagnóstico e correlação

## Evidência na Femina (04/10/2026, leitura pelo Dokploy)

Stack `fmnaprod-uzapi-36acu4`, backend criado em `2026-10-01T19:09:31.820748446Z`,
digest `sha256:496de8f730eaf92743768379aabbfa0853fd520a070e2c7184dd0cae1fb18c1c`.
Última migration bem-sucedida: V84. Não foi possível associar esse digest a um SHA
de código com a evidência disponível; o checkout corrigido parte de `origin/main`.

Consulta limitada aos últimos 1.000 repasses da outbox dos três dias anteriores:

| Status do provedor | Eventos | `statuses[].id` cadastrado | `conversation.id` cadastrado |
|---|---:|---:|---:|
| delivered | 12 | 0 | 7 |
| read | 1 | 0 | 0 |

Os sete matches de entrega são sete mensagens diferentes e todas permaneciam em
`ENVIADO`. Os 1.012 envios não-LEAD dos últimos três dias estavam em `ENVIADO`.
Log do backend em 03/10: status com identificador desconhecido ignorado.
Esses números são amostra diagnóstica limitada, não taxa global de entrega/leitura.
Não foi lido conteúdo de pacientes, não foram impressos segredos e não houve
escrita, replay de webhook, envio a cliente, redeploy ou alteração no n8n.

## Causa e contrato preservado

O aceite UZAPI já grava `messages[0].id` (wamid). No webhook observado, `id` é o
identificador nativo, enquanto `conversation.id` pode conter aquele wamid.
O tradutor só repassava `id`, que não alcançava o mapa persistido.
O Swagger oficial (`https://api.uzapi.com.br/docs/swagger.json`, status delivered/read)
mostra essa diferença de formatos, mas chama `conversation.id` de ID de conversa.
Por isso **não** se trata todo ID de conversa como mensagem nem se presume
correspondência para os seis eventos sem match comprovado.

Somente o ACL UZAPI oferece candidato alternativo quando o ID principal não tem
prefixo `wamid.` e o alternativo tem. O caso de uso dá prioridade absoluta a um
ID principal já cadastrado (inclusive status duplicado/avançado), e só usa o
alternativo quando o principal é desconhecido. O UPDATE existente exige match
exato em `mensagem_id_externo`; sem match, não altera nada. Nenhuma dedução por
telefone, horário ou conteúdo. Meta continua usando exclusivamente `statuses[].id`.

Não há migration, endpoint, enum ou configuração nova. Assinatura/segredo, destino,
RLS, transação nova de serviço, UPDATE monotônico e publicação AFTER_COMMIT
continuam iguais. O caminho de envio, outbox e chamadas externas não foi alterado.

## Estados e interface

- PENDENTE: ainda enviando; não confirma aceite, entrega ou leitura.
- ENVIADO: aceite de envio comprovado; não significa recebido pelo paciente.
- ENTREGUE: evento `delivered` correlacionado.
- LIDO: evento `read` correlacionado; não deduzido de entrega.
- FALHOU: evento `failed` ou falha de envio; motivo conhecido vem do histórico.

`played`, `deleted` e status desconhecidos não são convertidos em confirmação.
Duplicados não republicam evento; entrega/leitura não regridem. Falha persistida
é terminal; somente falha otimista local (`temp-`) pode ser reconciliada por aceite
posterior. O frontend antes descartava FALHOU depois de ENVIADO e aceitava um
ENVIADO tardio sobre FALHOU. Foi alinhado à progressão do backend.

Os checks existentes mantêm seus ícones/tokens, agora acompanhados do rótulo do
catálogo e nome acessível. Isso funciona em touch sem depender de tooltip nativo.
Não há confirmação simulada. A melhoria de apresentação/reconciliação é comum;
a diferença de identificadores é exclusiva do provedor UZAPI, nunca do cliente.

## Validação e operação

`StatusDeEntregaUzapiWebhookIT` chama `/webhook/canal?secret=...` com dados
sintéticos e prova persistência, HTTP de histórico, isolamento, segredo inválido,
destino alheio, status desconhecido, prioridade do ID principal e STOMP real.
`StatusDeEntregaWebhookIT` protege Meta. Testes do tradutor, caso de uso, hook e
bolha protegem duplicidade, ordenação e apresentação.

Playwright CLI headed, aplicação real local com backend, Postgres e STOMP:
em 1440×1000, o histórico recarregado exibiu Enviado/Entregue/Lido/Falha com
motivo; um novo webhook HTTP local mudou Enviado para Entregue sem refresh.
Em 390×844, os estados e ações permaneceram visíveis sem overflow horizontal.
Screenshots: `output/playwright/status-desktop.png`,
`status-desktop-ao-vivo.png` e `status-mobile.png`. Dados exclusivamente
sintéticos em banco local separado; nenhuma mensagem foi enviada à UZAPI.
Isso valida a correção local, não substitui a observação pós-deploy na Femina.

Nenhuma variável nova no Dokploy. Após aprovação e deploy autorizado, observar
um envio de teste autorizado e correlacionar o aceite com o webhook de entrega
e, se o destinatário efetivamente ler, leitura; comparar WebSocket e recarga.
Não declarar o hotfix validado em produção antes disso. O acesso de leitura
confirmou que UZAPI fornece eventos, mas não prova entrega de todos os envios
nem configuração correta de todos os tipos de webhook.

Não reprocessar automaticamente o histórico de produção: as mensagens antigas
não ganham confirmação retroativa neste PR. Uma recuperação histórica requer
escopo/autorização próprios e correlação exata, não atualização em massa para LIDO.
