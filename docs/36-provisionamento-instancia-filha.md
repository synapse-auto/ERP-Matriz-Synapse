# 36. Provisionamento de uma instância filha

Roteiro operacional para criar um novo cliente a partir da Base PAI sem
reutilizar dados, segredos, webhook, número de WhatsApp ou identidade visual
de outra instância. Foi revisado em 05/09/2026 contra `origin/main` em
`ec86220`.

Este documento não autoriza deploy nem chamadas reais à Meta. Ele separa o que
é necessário configurar do que ainda precisa de uma decisão ou de um artefato
novo.

## 1. Decisão de go/no-go

O isolamento funcional já existe: cada filho sobe com banco, volumes, segredos,
domínios, stack e credenciais próprios. Não há `tenant_id` nem compartilhamento
de dados no schema.

Há, porém, um bloqueio para expor uma **marca nova** usando a imagem atual:

- `backend/crm-app/src/main/resources/textos.json` contém a marca da instância-base;
- `tema.json` e `logo.png` são recursos lidos do classpath no boot;
- `ConfiguracaoDeInstanciaResources` não lê um diretório, URL ou variável externa;
- o `docker/dokploy-stack.yml` recebe apenas tags de imagens já construídas.

Assim, definir somente `SYNAPSE_TENANT_NOME` no Dokploy não troca tudo que o
usuário vê. Para o filho de hoje, gere uma **tag própria de imagem** a partir
de um artefato de configuração do filho que substitua estes três arquivos:

```text
backend/crm-app/src/main/resources/textos.json
backend/crm-app/src/main/resources/tema.json
backend/crm-app/src/main/resources/logo.png
```

O frontend já busca esses recursos no backend em runtime; ainda assim, o CI
publica backend e frontend com a mesma tag. Portanto use a tag do build desse
artefato nos dois serviços, em `SYNAPSE_IMAGE_TAG`. Não reutilize a tag da
instância-base se o filho não pode mostrar a marca dela.

Uma solução estrutural futura é permitir que esses recursos sejam lidos de uma
Docker Config/volume externo. Ela não existe hoje e não deve ser improvisada no
dia do deploy.

## 2. Valores exclusivos que devem ser definidos

Comece por uma cópia nova de `.env.example` e de
`docker/provisionamento/instancias/instancia.exemplo.env.example`, sempre fora
do Git. Os exemplos versionados são genéricos desde esta revisão.

| Grupo | Variáveis / dados | Regra |
|---|---|---|
| Identidade | `SYNAPSE_TENANT_CODIGO`, `SYNAPSE_TENANT_NOME`, `SYNAPSE_TIMEZONE` | Código único e estável; não reaproveitar o de outro filho. |
| Domínios | `SYNAPSE_DOMINIO`, `MIDIA_DOMINIO`, `AUTOMACAO_DOMINIO`, `TRAEFIK_ROUTER_PREFIX` | Cada host e prefixo precisa ser exclusivo no servidor. |
| Bancos | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `N8N_DB_*` | Banco e roles exclusivos; n8n não recebe acesso ao banco do CRM. |
| Segredos | `SYNAPSE_JWT_SEGREDO`, `SYNAPSE_TOKEN_INTERNO`, `N8N_ENCRYPTION_KEY`, senhas de Redis/RabbitMQ/MinIO/Postgres | Gerar novos valores; nunca copiar nem registrar em workflow/export. |
| Meta | `WHATSAPP_NUMERO`, `WHATSAPP_TOKEN`, `WHATSAPP_WEBHOOK_VERIFY_TOKEN`, `WHATSAPP_WEBHOOK_SECRET`, `WHATSAPP_CONTA_NEGOCIO` | `WHATSAPP_NUMERO` é o **Phone Number ID**; WABA ID vai somente em `WHATSAPP_CONTA_NEGOCIO`. |
| Automação | `AUTOMACAO_WEBHOOK_EVENTOS_URL`, `AUTOMACAO_AVALIACAO_*`, `AUTOMACAO_TOKEN` | URLs e tokens pertencem ao workflow deste filho. Vazio desliga a integração opcional; nunca aponte para n8n de outro cliente. |
| Mídia | `MIDIA_S3_BUCKET`, `MIDIA_DOMINIO`, chaves MinIO | Com MinIO dentro da stack o volume é isolado pela stack; em S3 compartilhado o bucket precisa ser único por filho. |
| Produto | etapas, tags, administrador inicial, flags e parâmetros | Carregar pelo provisionamento; não usar seed de demonstração em produção. |

O arquivo `docker/dokploy-stack.yml` usa `:?obrigatoria` somente para dados
sem os quais a instância não pode iniciar. Variáveis opcionais de automação e
templates usam default vazio e não devem derrubar Atendimentos se ainda não
estiverem configuradas.

## 3. Sequência segura de criação

1. Criar a aplicação/stack própria no Dokploy e os três registros DNS. Confirmar
   que `TRAEFIK_ROUTER_PREFIX` não coincide com nenhuma outra aplicação no host.
2. Preparar o artefato de marca do filho e aguardar o CI publicar a tag. Conferir
   o conteúdo de `textos.json`, `tema.json` e `logo.png` antes de usar a tag.
3. Preencher as variáveis obrigatórias no Dokploy, sem usar arquivo de outra
   instância como base de valores. Usar o mesmo `SYNAPSE_IMAGE_TAG` para backend
   e frontend.
4. Fazer o primeiro deploy e confirmar liveness/readiness. Não ligar o webhook
   da Meta nem a Automação antes desse ponto.
5. Carregar somente o arquivo local do filho e executar
   `docker/provisionamento/executar-provisionamento.sh`. O script cria/atualiza
   o administrador, canal, credencial ativa, funil, tags e configuração inicial.
6. Validar o canal: a credencial ativa deve conter o Phone Number ID daquele
   filho. Configurar no Meta o webhook público do CRM; a inscrição é por WABA,
   mas o backend filtra pelo Phone Number ID persistido.
7. Configurar no n8n a URL interna
   `http://synapse-backend-internal:8080/internal/v1` e uma credencial Header
   Auth com `X-Synapse-Token`. Nunca publicar `/internal/v1` no Traefik.
8. Executar os smoke tests de RLS, login, envio/recebimento com contato de teste
   autorizado e URL assinada de mídia. Só depois ativar o tráfego real.
9. Se a pesquisa de satisfação for contratada, seguir o
   [runbook de avaliação](./35-runbook-webhook-avaliacao.md) e testar uma
   finalização individual controlada. Ela não pode ser ligada por cópia de
   segredos ou URLs de outro filho.

## 4. Avaliação de atendimento: contrato atual

A avaliação não é uma anotação do lead. A resposta da pesquisa é persistida em
`avaliacao`, uma linha por `atendimento_id`, na escala 0–10.

```http
POST /internal/v1/atendimentos/{atendimentoId}/avaliacao
X-Synapse-Token: <segredo exclusivo do filho>
Content-Type: application/json

{"nota": 10, "comentario": "opcional"}
```

O primeiro callback válido devolve `201`; repetição idêntica devolve `200` sem
duplicar; nota ou comentário diferente devolve `409`; atendimento aberto, sem
responsável humano ou nota fora da faixa devolve `422`. A finalização só inicia
o fluxo assíncrono; o n8n precisa fazer esse POST depois que o cliente responde.

## 5. Auditoria de acoplamentos e hardcodes

O levantamento abaixo cobre arquivos de produção e templates versionados;
testes, protótipos em `design/` e documentos históricos não são runtime.

| Severidade | Local | Situação | Impacto no filho | Ação |
|---|---|---|---|---|
| **P0** | `backend/crm-app/src/main/resources/textos.json`, `tema.json`, `logo.png` | Recursos de marca embutidos na imagem-base | O filho pode exibir identidade visual da instância-base mesmo com env correto | Criar imagem de configuração do filho antes de expor o domínio. |
| **P0 — corrigido nesta revisão** | `.env.example` | Tinha identidade e URL de webhook de outra instância | Copiar o exemplo poderia enviar eventos brutos para o n8n errado | Agora usa placeholders e URL vazia. |
| **P0 — corrigido nesta revisão** | `docker/provisionamento/instancias/instancia.exemplo.env.example` | Tinha um Phone Number ID real | Provisionamento poderia registrar o número de outro cliente | Agora o campo é vazio e o executor exige o valor do filho. |
| **P1** | `application.yml` | Defaults de `SYNAPSE_TENANT_CODIGO` e `SYNAPSE_TENANT_NOME` apontam para a instância-base | Um boot fora do Dokploy, sem env, herda a identidade errada | No stack de produção as duas variáveis são obrigatórias; manter essa exigência. Remover defaults é melhoria futura. |
| **P1** | `MIDIA_S3_BUCKET` default `synapse-crm-midia` | Nome genérico, não único globalmente | Colide apenas se filhos compartilharem um S3 externo | Definir bucket exclusivo no Dokploy quando o storage for compartilhado. |
| **P2** | `R__seed_dev.sql` | Saudação e dados de desenvolvimento da instância-base | Só afeta perfil `dev`; não entra em produção sem esse perfil | Nunca ativar perfil `dev` no filho; revisar o seed antes de uso local com marca nova. |
| **P2** | Exemplos OpenAPI e testes | Há exemplos nominais da instância-base | Não mudam comportamento de produção | Trocar apenas se a documentação pública do filho for exposta. |

Não foi encontrado código de produção que escolha comportamento por
`if (cliente == ...)`; os resultados encontrados no core são defaults, recursos
de marca ou exemplos. Os valores de produto que já possuem variável de ambiente
(DDI 55, Graph API, tempos, limites de filas e circuit breakers) são defaults
operacionais, não identidade de cliente — mas devem ser revisados no checklist
do filho, principalmente se ele for de outro país ou tiver outro volume.

## 6. O que não fazer

- Não compartilhar banco, volume, domínio, `TRAEFIK_ROUTER_PREFIX`, segredo ou
  Phone Number ID entre filhos.
- Não apontar `AUTOMACAO_WEBHOOK_EVENTOS_URL` nem `AUTOMACAO_AVALIACAO_URL` para
  um workflow de outro cliente.
- Não usar `R__seed_dev.sql` ou `seed-demonstracao.sql` em produção.
- Não expor `/internal/v1` no domínio público para "facilitar" o n8n; ele já
  possui DNS interno na rede da stack.
- Não adicionar `tenant_id` nem condicional por cliente para corrigir uma
  diferença de configuração.
