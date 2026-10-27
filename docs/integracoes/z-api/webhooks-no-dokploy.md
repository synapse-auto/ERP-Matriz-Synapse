# Webhooks Z-API persistentes no Dokploy

## Configuracao versionada e alcance

Incidente confirmado na Femina: callbacks reais chegavam com `Origin` externo e o Spring
respondia `403 Invalid CORS request`. Remover esse header no router exclusivo resolveu a
entrada. Nao se deve liberar CORS globalmente, retirar a autenticacao ou copiar essa regra
para o router das APIs.

Reutilizamos **docker/dokploy-z-api-transicao.override.yml** sobre
**docker/dokploy-stack.yml**. Nao existe um novo compose de producao nem uma copia da stack.
O base continua igual para Meta, Uzapi e instancias que nao usam esse override.

O router `${TRAEFIK_ROUTER_PREFIX}-transicao` usa o dominio `SYNAPSE_DOMINIO`, entrypoint
`websecure`, TLS/`letsencrypt`, prioridade 150 e o servico `${TRAEFIK_ROUTER_PREFIX}-backend`.
Cobre os prefixos `/webhook/z-api` e `/webhook/canal`, inclusive sufixos que o backend deve
recusar; nao cobre `/api/v1`, `/ws`, `/health` ou `/internal/v1`. Seu middleware exclusivo
`${TRAEFIK_ROUTER_PREFIX}-webhook-sem-origin` remove somente `Origin`. Access logs e tracing
desse router ficam OFF; a observabilidade das APIs permanece como antes.

As validacoes de segredo, HTTPS, instancia, tamanho e persistencia duravel no backend nao
mudam. A query continua intacta ate a autenticacao. Isso nao e assinatura nativa da Z-API.
Nenhum segredo entra em label, nome de router, regra, YAML versionado ou comando do Dokploy.

## Antes do deploy: salvar no Dokploy, nao na VPS

**Esta mudanca exige salvar o Command da aplicacao existente.** Um arquivo override no Git
nao e carregado automaticamente. `COMPOSE_FILE` nao substitui `-c` no Docker Stack.

1. Na aplicacao **existente da Femina**, confirmar Source Git/GitHub, branch de release
   aprovada e **Compose Type = Stack**. Nao criar outra aplicacao/stack nem trocar banco,
   volumes, rede, dominio ou prefixo. Esta entrega nao faz merge ou deploy automaticamente.
2. **Compose Path permanece `docker/dokploy-stack.yml`.** Nao colocar dois arquivos nesse
   campo. Preservar Registry, Domains (sem routers duplicados), mounts e todas as envs.
3. Em **Advanced / Command**, salvar o comando completo abaixo **sem o prefixo `docker`**.
   Ele substitui o comando padrao, nao acrescenta flags. A ordem base -> override importa.
   Conferir o App Name exibido no comando padrao antes de salvar; o nome informado no
   diagnostico desta instancia foi `fmnaprod-uzapi-36acu4`:

   ```text
   stack deploy -c docker/dokploy-stack.yml -c docker/dokploy-z-api-transicao.override.yml fmnaprod-uzapi-36acu4 --prune --with-registry-auth
   ```

   Se o App Name atual for diferente, usar **exatamente o atual**, nunca renomear a stack.
   Preservar flags operacionais adicionais do comando existente. Nao usar `$APP_NAME`,
   substituicao de shell, pipes ou comandos de preparacao nesse campo. O Dokploy v0.29.13
   recusa esses caracteres; executa `docker <Command>` na raiz do checkout e exporta as envs
   da stack. Por isso os caminhos relativos acima e as envs existentes funcionam.
4. Nao carregar tambem `dokploy-z-api-homologacao.override.yml`: sao alternativas, nao
   camadas cumulativas. Nao deixar router temporario com a mesma regra e prioridade.
5. **Nenhuma env nova.** Preservar os valores existentes de `SYNAPSE_DOMINIO`,
   `TRAEFIK_ROUTER_PREFIX`, `SERVER_FORWARD_HEADERS_STRATEGY=NATIVE`, flags e segredo exclusivo
   do webhook. Backend privado, sem porta 8080 publicada; proxy deve ser confiavel.
6. Confirmar Traefik serie 3, >=3.3, com observability por router. A versao informada e testada
   aqui e **3.6.7**. Em versao incompativel, parar; nao remover as labels de protecao para
   forcar deploy. Nao reiniciar ou atualizar Traefik nesta etapa.

O Preview Compose pode mostrar somente o base: a prova de carregamento e o **Command salvo**,
o comando no log de deployment e, apos implantacao manual autorizada, o router efetivo. Nao
exportar o compose interpolado completo: ele contem segredos do ambiente.

## Conferencias e labels temporarias

**VPS, somente leitura (operador):**

```bash
docker exec dokploy-traefik traefik version
docker service inspect fmnaprod-uzapi-36acu4_backend --format '{{json .Endpoint.Spec.Ports}}'
docker service inspect fmnaprod-uzapi-36acu4_backend --format '{{range $k,$v := .Spec.Labels}}{{println $k}}{{end}}'
```

O ultimo comando imprime **nomes**, nao valores. Identificar nomes dos routers/middlewares
temporarios de webhook. Nao exportar envs, payloads, URLs com query ou configuracao completa.

Em checkout da release aprovada, com envs carregadas privadamente, validar sem imprimir valores:

```bash
docker compose -f docker/dokploy-stack.yml -f docker/dokploy-z-api-transicao.override.yml config --quiet
docker stack config -c docker/dokploy-stack.yml -c docker/dokploy-z-api-transicao.override.yml >/dev/null
```

Nao basta `config`: ele nao prova que o Traefik aceitou o schema nem que o Dokploy carregou o
override. No deploy manual, conferir no log apenas o comando completo (dois `-c`) e no
Traefik que o router esta aceito, ligado ao backend e sem erro de middleware/observability.

**Quando remover as labels temporarias:** primeiro salvar o Command acima. Remover do
compose Raw/customizacao persistente ou Domains somente as definicoes temporarias deste
webhook que duplicam o router; preservar todas as outras configuracoes. Na mesma implantacao
manual, o Stack aplica as labels versionadas. Labels adicionadas apenas por `service update`
sao substituidas pela especificacao versionada; nao fazer um `label-rm` antecipado que abra
uma janela sem protecao. Se reaparecerem, ainda existe outra fonte persistente: corrigir essa
fonte e nao aumentar prioridade para disputar com ela. Depois conferir o router efetivo e os
testes abaixo. Nenhuma remocao massiva de labels e autorizada por este documento.

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

CI valida tambem a composicao base+override. Isso nao comprova configuracao salva/ativa no
Dokploy nem callbacks reais da conta: essas conferencias sao operacionais e nao executadas
pelo agente.

- [Dokploy Docker Compose e Command](https://docs.dokploy.com/docs/core/docker-compose).
- [Builder Dokploy v0.29.13](https://github.com/Dokploy/dokploy/blob/v0.29.13/packages/server/src/utils/builders/compose.ts).
- [Traefik 3.6: remover header com valor vazio](https://doc.traefik.io/traefik/v3.6/reference/routing-configuration/http/middlewares/headers/#adding-and-removing-headers).
- [Traefik 3.6: observability por router](https://doc.traefik.io/traefik/v3.6/reference/routing-configuration/http/routing/observability/).
