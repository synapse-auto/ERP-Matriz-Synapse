# Webhooks Z-API persistentes no Dokploy

## Configuracao versionada e alcance

Incidente confirmado na Femina: callbacks reais chegavam com `Origin` externo e o Spring
respondia `403 Invalid CORS request`. Remover esse header no router exclusivo resolveu a
entrada. Nao se deve liberar CORS globalmente, retirar a autenticacao ou copiar essa regra
para o router das APIs.

As labels agora estao em **docker/dokploy-stack.yml**, o Compose Path unico usado pela aplicacao
Stack no Dokploy. Nao dependem de um segundo arquivo ou Command personalizado. O antigo
**docker/dokploy-z-api-transicao.override.yml** foi mantido vazio para que um Command salvo
anteriormente com a referencia a ele continue valido; o override nao e mais fonte funcional.

O router `${TRAEFIK_ROUTER_PREFIX}-transicao` usa o dominio `SYNAPSE_DOMINIO`, entrypoint
`websecure`, TLS/`letsencrypt`, prioridade 150 e o servico `${TRAEFIK_ROUTER_PREFIX}-backend`.
Cobre os prefixos `/webhook/z-api` e `/webhook/canal`, inclusive sufixos que o backend deve
recusar; nao cobre `/api/v1`, `/ws`, `/health` ou `/internal/v1`. Seu middleware exclusivo
`${TRAEFIK_ROUTER_PREFIX}-webhook-sem-origin` remove somente `Origin`. Access logs e tracing
desse router ficam OFF; a observabilidade das APIs permanece como antes.

As validacoes de segredo, HTTPS, instancia, tamanho e persistencia duravel no backend nao
mudam. A query continua intacta ate a autenticacao. Isso nao e assinatura nativa da Z-API.
Nenhum segredo entra em label, nome de router, regra, YAML versionado ou comando do Dokploy.

## Acao unica no Dokploy

O repositório sozinho nao altera uma stack ja implantada. Depois que esta mudanca estiver na
branch de release aprovada, execute **um deploy normal** na aplicacao existente da Femina para
reaplicar a especificacao versionada. Nao e necessario editar Compose Path, Command Stack,
Domains, variaveis ou credenciais. Nenhum deploy e executado por esta tarefa.

1. Manter a aplicacao existente, `Compose Type = Stack` e Compose Path
   `docker/dokploy-stack.yml`; nao criar stack nova nem trocar volumes, rede, dominio ou prefixo.
2. Executar o deploy normal na branch de release que contem esta mudanca. Um Command antigo que
   ainda carregue o override vazio tambem continua valido, mas nao e requisito.
3. Apos o deploy, conferir somente os nomes das labels do servico e o router efetivo no Traefik.
   A especificacao versionada inclui TLS/websecure, prioridade 150, backend, middleware exclusivo
   para remover `Origin` e observability `accessLogs=false`/`tracing=false`.

Nenhuma variavel nova. Preservar valores atuais de `SYNAPSE_DOMINIO`, `TRAEFIK_ROUTER_PREFIX`,
`SERVER_FORWARD_HEADERS_STRATEGY=NATIVE`, flags e segredo exclusivo do webhook. Nao exportar o
Compose interpolado completo: ele contem segredos. O Traefik 3.6.7 foi validado; a sintaxe exige
Traefik 3.3+ com observability por router.

## Conferencias da especificacao implantada

**VPS, somente leitura (operador):**

```bash
docker exec dokploy-traefik traefik version
docker service inspect fmnaprod-uzapi-36acu4_backend --format '{{json .Endpoint.Spec.Ports}}'
docker service inspect fmnaprod-uzapi-36acu4_backend --format '{{range $k,$v := .Spec.Labels}}{{println $k}}{{end}}'
```

O ultimo comando imprime **nomes**, nao valores. Identificar nomes dos routers/middlewares
temporarios de webhook. Nao exportar envs, payloads, URLs com query ou configuracao completa.

Em checkout da release aprovada, com envs carregadas privadamente, validar base e stack sem imprimir valores:

```bash
docker compose -f docker/dokploy-stack.yml config --quiet
docker stack config -c docker/dokploy-stack.yml >/dev/null
docker compose -f docker/dokploy-stack.yml -f docker/dokploy-z-api-transicao.override.yml config --quiet
docker stack config -c docker/dokploy-stack.yml -c docker/dokploy-z-api-transicao.override.yml >/dev/null
docker compose -f docker/dokploy-stack.yml -f docker/dokploy-z-api-homologacao.override.yml config --quiet
docker stack config -c docker/dokploy-stack.yml -c docker/dokploy-z-api-homologacao.override.yml >/dev/null
```

O teste `proxyProdutivoRemoveOriginSomenteDosWebhooksSemAfrouxarSegredoOuCors` compoe o Compose Path
sozinho e o arquivo de compatibilidade, exige labels identicas e executa o router derivado do
base no Traefik de teste. Isso evita que a CI passe apenas porque um override manual estava
presente. O teste nao prova configuracao efetiva no Dokploy: depois do deploy normal, conferir o
servico e o dashboard/config dinamica do Traefik. Nao remover labels temporarias antes de a
especificacao versionada estar ativa; se houver uma fonte externa ao Compose, identificar essa
fonte separadamente e remover somente a duplicata comprovada.

## Provas sem mensagens ou pacientes

**Operador na VPS**, depois de implantar e conferir a protecao de logs. Desativar xtrace;
nao colar segredo em comando, argumento de curl, historico, print ou arquivo compartilhado:

```bash
set +x
read -rp 'Dominio CRM, sem protocolo: ' CRM_DOMINIO
read -rsp 'Segredo exclusivo do webhook: ' WEBHOOK_SECRET; printf '\n'
case "$CRM_DOMINIO" in *[!a-zA-Z0-9.-]*|'') echo 'Dominio invalido'; exit 1;; esac
case "$WEBHOOK_SECRET" in *[!a-zA-Z0-9_-]*|'') echo 'Esperado segredo URL-safe'; unset WEBHOOK_SECRET; exit 1;; esac
printf 'url = "https://%s/webhook/z-api?secret=%s"\n' "$CRM_DOMINIO" "$WEBHOOK_SECRET" |
  curl --config - --silent --show-error --max-time 10 -o /dev/null -w 'HTTP %{http_code}\n' \
    -H 'Origin: https://callback-externo.example.test' -H 'Content-Type: application/json' --data '{}'
unset WEBHOOK_SECRET
```

Esperado **400**, nao `403 Invalid CORS request`: autenticacao/TLS passam, corpo vazio e
recusado antes de persistir evento. Nao usar payload de paciente nem seguir redirecionamento.

Ainda com `CRM_DOMINIO`, sem segredo real:

```bash
for SUFIXO in '' '?secret=canario-invalido'; do
  curl --silent --show-error --max-time 10 -o /dev/null -w 'HTTP %{http_code}\n' \
    -H 'Origin: https://callback-externo.example.test' -H 'Content-Type: application/json' \
    --data '{}' "https://$CRM_DOMINIO/webhook/z-api$SUFIXO"
done
curl --silent --show-error --max-time 10 -o /dev/null -w 'HTTP %{http_code}\n' \
  -H 'Origin: https://callback-externo.example.test' "https://$CRM_DOMINIO/api/v1/config/features"
```

Esperado **403 / 403 / 403**, APIs sem `Access-Control-Allow-Origin` para essa origem.
Segredo invalido/ausente nao persiste evento. Usar canario aleatorio **falso** para verificar
ausencia no destino de access logs/tracing do proxy; confirmar tambem WAF/LB/APM e logs de
aplicacao antes do segredo real. Router sem access log nao protege outra camada de log.
Nenhuma chamada acima envia WhatsApp, dispara provedor ou reprocessa outbox.

## Validacao automatizada e fontes

`ZApiWebhookProtegidoIT.proxyProdutivoRemoveOriginSomenteDosWebhooksSemAfrouxarSegredoOuCors`
compoe os arquivos reais com envs sinteticas, compara o base com o resultado (somente labels
aditivas), executa as regras/middleware resultantes em Traefik 3.6.7 com TLS local e chama
o backend Spring/PostgreSQL real. Prova recusa CORS direta, webhook autenticado pelo proxy,
negativos de segredo, protecao de `/webhook/canal`, CORS da API e ausencia de access log nos
webhooks com controle positivo na API. A configuracao de tracing OFF e verificada e aceita
pelo schema real; nao se afirma inspecao do coletor produtivo. A fixture usa certificado local
sem validacao apenas no cliente de teste; a aplicacao/producao nao muda TLS.

CI valida o Compose Path isolado, o `docker stack config` isolado e a composicao com ambos os
overrides legados. Isso nao comprova configuracao ativa no Dokploy nem callbacks reais da conta; essas
conferencias sao operacionais e nao executadas pelo agente.

- [Dokploy Docker Compose e Command](https://docs.dokploy.com/docs/core/docker-compose).
- [Builder Dokploy v0.29.13](https://github.com/Dokploy/dokploy/blob/v0.29.13/packages/server/src/utils/builders/compose.ts).
- [Traefik 3.6: remover header com valor vazio](https://doc.traefik.io/traefik/v3.6/reference/routing-configuration/http/middlewares/headers/#adding-and-removing-headers).
- [Traefik 3.6: observability por router](https://doc.traefik.io/traefik/v3.6/reference/routing-configuration/http/routing/observability/).
