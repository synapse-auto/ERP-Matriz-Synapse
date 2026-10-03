# 53. Onboarding da Disbrel — Fase 1: instância e n8n

Este documento registra o procedimento para provisionar a primeira instância da Disbrel como
filha isolada da Base PAI. A decisão desta etapa é deliberadamente incremental:

1. **Agora:** subir somente a infraestrutura da instância e liberar o n8n para o Dylan trabalhar.
2. **Depois:** implementar, configurar e homologar os requisitos de negócio do documento
   `Requisitos do Projeto — Disbrel.pdf`.

Subir o Stack não significa que a Disbrel está pronta para operar em produção. Nesta fase não
devemos conectar o número oficial à Meta, liberar usuários do cliente ou anunciar o CRM como
canal de atendimento.

## 1. Escopo fechado da Fase 1

### Entra agora

- Um novo Docker Stack no Dokploy, separado das instâncias existentes.
- Banco PostgreSQL, Redis, RabbitMQ, MinIO, backend, frontend e n8n próprios da Disbrel.
- Volumes, bucket, credenciais, prefixo de roteamento e três domínios exclusivos.
- Imagens já publicadas no GHCR, identificadas por SHA do CI.
- Acesso do Dylan ao n8n desta instância para construir e testar os workflows.
- Teste do contrato privado n8n → CRM usando `X-Synapse-Token`, sem expor `/internal/v1` na
  internet.
- Smoke test de saúde, migration, persistência do n8n e isolamento das redes/volumes.

### Não entra agora

- Conexão do número `(61) 99666-6025` à Meta em produção.
- Embedded Signup, coexistência com o WhatsApp Business App ou atuação como Meta Tech Provider.
- Sincronização de seis meses de histórico.
- Eventos `smb_message_echoes`, `smb_app_state_sync` e `history`.
- Detecção de desconexão do número, incluindo `PRIMARY_INACTIVITY`.
- Funil, setores, distribuição por atendente, horários e regras da operação Disbrel.
- Persona, base de conhecimento e respostas da IA da Disbrel.
- Tema branco com `#6b0504` e textos específicos do cliente.
- Carga de usuários finais, campanhas, templates ou tráfego real.

Esses itens ficam para uma segunda etapa porque exigem código, contrato de integração ou decisões
de negócio que não devem ser mascarados por um provisionamento de infraestrutura.

## 2. Pré-condições antes do deploy

1. Confirmar uma tag de imagem do backend/frontend cujo CI esteja verde. Não usar `latest`.
2. Confirmar que a rede externa `dokploy-network` existe no Swarm.
3. Confirmar três hosts DNS exclusivos, por exemplo:

   - `disbrel-hml.crm.seudominio.com`
   - `disbrel-hml.midia.seudominio.com`
   - `disbrel-hml.automacao.seudominio.com`

4. Registrar o GHCR privado no Registry do Dokploy.
5. Gerar segredos novos para a Disbrel. Não copiar segredos da Estrutural, da Femina ou de outra
   filha.
6. Confirmar capacidade da VPS com `docker stats` e `free -h`. A VPS observada anteriormente tem
   15 GiB de RAM; duas instâncias já chegaram a deixar aproximadamente 8,7 GiB disponíveis, mas
   isso não autoriza assumir que uma terceira instância cabe com os limites máximos de todas as
   aplicações. Se o consumo medido não comportar o Stack, usar outra VPS ou reduzir limites com
   evidência de uso — não fazer overcommit silencioso.

## 3. Provisionamento no Dokploy

1. Criar uma nova aplicação no modo **Docker Stack** (Swarm).
2. Apontar para o repositório `synapse-auto/ERP-Matriz-Synapse`, branch `main` e arquivo
   `docker/dokploy-stack.yml`.
3. Informar as variáveis da seção seguinte no ambiente da aplicação. Segredos ficam somente no
   Dokploy; nenhum valor real entra no Git.
4. Executar **Preview Compose** antes do deploy e conferir:
   - nenhuma porta de PostgreSQL, Redis, RabbitMQ ou MinIO publicada no host;
   - os três nomes de domínio e o `TRAEFIK_ROUTER_PREFIX` são exclusivos;
   - backend/frontend apontam para `/health/liveness` e o n8n para `/healthz`;
   - não existe referência a volume, bucket ou credencial de outra filha.
5. Apontar os DNS para a VPS e fazer o deploy.
6. Aguardar os sete serviços ficarem `1/1` e saudáveis antes de entregar o endereço do n8n ao
   Dylan.

O Dokploy usa Docker Stack para workloads Swarm e injeta as variáveis somente quando elas são
referenciadas no Compose. Referência operacional: [Docker Compose/Stack no Dokploy](https://docs.dokploy.com/docs/core/docker-compose).

## 4. Variáveis da instância Disbrel

Os valores abaixo são um mapa de preenchimento, não valores para commit. Os nomes marcados como
`<gerar>` devem receber segredos aleatórios e exclusivos.

```dotenv
SYNAPSE_IMAGE_TAG=<SHA curto com CI verde>
N8N_IMAGE_TAG=2.33.4
TRAEFIK_ROUTER_PREFIX=disbrel-hml

SYNAPSE_DOMINIO=disbrel-hml.crm.seudominio.com
MIDIA_DOMINIO=disbrel-hml.midia.seudominio.com
AUTOMACAO_DOMINIO=disbrel-hml.automacao.seudominio.com

SYNAPSE_TENANT_CODIGO=disbrel
SYNAPSE_TENANT_NOME=Disbrel
SYNAPSE_TIMEZONE=America/Sao_Paulo

POSTGRES_DB=disbrel_crm
POSTGRES_USER=disbrel_crm
POSTGRES_PASSWORD=<gerar>

N8N_DB_NAME=disbrel_n8n
N8N_DB_USER=disbrel_n8n
N8N_DB_PASSWORD=<gerar>
N8N_ENCRYPTION_KEY=<gerar e manter estável>

RABBITMQ_USER=disbrel_rabbit
RABBITMQ_PASSWORD=<gerar>
MINIO_ROOT_USER=disbrel_minio
MINIO_ROOT_PASSWORD=<gerar>
MIDIA_S3_BUCKET=disbrel-midia

SYNAPSE_JWT_SEGREDO=<gerar>
SYNAPSE_TOKEN_INTERNO=<gerar e compartilhar somente com o n8n da Disbrel>
AUTOMACAO_TOKEN=<gerar e compartilhar somente com o n8n da Disbrel>

# Fica sem conexão produtiva nesta fase; preencher somente quando a Meta for homologada.
WHATSAPP_PROVEDOR=meta-cloud
WHATSAPP_NUMERO=<Phone Number ID da Meta, não o telefone exibido ao cliente>
WHATSAPP_TOKEN=<somente na Fase 2>
WHATSAPP_WEBHOOK_VERIFY_TOKEN=<gerar na Fase 2>
WHATSAPP_WEBHOOK_SECRET=<App Secret da Meta na Fase 2>

FEATURE_CAMPANHAS=false
FEATURE_CHAT_INTERNO=false
FEATURE_FIDELIZACAO=true
BACKEND_REPLICAS=1
FRONTEND_REPLICAS=1
```

O Stack marca `WHATSAPP_NUMERO`, `WHATSAPP_TOKEN`, `WHATSAPP_WEBHOOK_VERIFY_TOKEN` e
`WHATSAPP_WEBHOOK_SECRET` como obrigatórios. Portanto, se o deploy do ambiente de trabalho for
feito antes da homologação da Meta, devem ser usados valores de ambiente de teste controlados —
nunca o número de produção de outro cliente — ou o Stack precisa receber uma alteração de
configuração que torne o canal opcional. Essa alteração não deve ser improvisada durante o
deploy: registrar como decisão antes de mexer no YAML.

O `N8N_ENCRYPTION_KEY` não pode ser trocado depois que o n8n salvar credenciais. As variáveis
`N8N_DB_*` também são usadas na inicialização do primeiro volume do PostgreSQL; trocar depois
exige rotação/migração operacional.

## 5. Acesso do Dylan ao n8n

- Criar um usuário individual no n8n ou entregar convite individual, conforme a política
  disponível na versão instalada.
- Não compartilhar a conta administrativa do Dokploy.
- O Dylan deve trabalhar somente em `AUTOMACAO_DOMINIO` da Disbrel.
- As credenciais de Meta, CRM e serviços externos devem ser credenciais de homologação ou
  placeholders até a Fase 2.
- O workflow deve chamar o CRM pelo alias interno do Stack:
  `http://synapse-backend-internal:8080/internal/v1`.
- O token deve ser enviado em `X-Synapse-Token`. Não criar um router público para `/internal/v1`.
- Registrar no n8n o workflow de teste e o resultado do primeiro `health/readiness` do backend.

## 6. Checklist de aceite da Fase 1

- [ ] Aplicação Docker Stack criada no Dokploy com branch e caminho corretos.
- [ ] Os sete serviços da Disbrel estão `1/1`; backend, frontend, n8n e dependências estão
      saudáveis.
- [ ] O banco da Disbrel é novo e não recebeu dump da Estrutural ou da Femina.
- [ ] Volumes e bucket têm nomes exclusivos.
- [ ] Nenhuma porta interna está publicada no host.
- [ ] O login do n8n funciona e o Dylan consegue criar/salvar um workflow.
- [ ] O n8n alcança o endpoint interno do CRM com o token correto.
- [ ] Uma migration inicial concluiu e o schema foi validado.
- [ ] Um restart controlado preserva o workflow e as credenciais do n8n.
- [ ] `docker service ps` não mostra loop de restart, OOM ou rollback.
- [ ] O cliente final ainda não recebeu acesso e o número oficial ainda não está conectado.

Com esses itens verdes, a instância está liberada como **ambiente técnico de trabalho do n8n**,
não como operação de atendimento.

## 7. Fase 2 — requisitos a implementar depois

Antes de ligar a operação Disbrel, abrir uma etapa própria para:

1. modelar o adaptador de coexistência e o Embedded Signup;
2. validar a capacidade atual do backend para os eventos de eco, sincronização de estado e
   histórico. A busca no repositório não encontrou handlers para `smb_message_echoes`,
   `smb_app_state_sync` ou `history`, então isso é uma lacuna de implementação, não apenas uma
   configuração;
3. garantir que mensagens enviadas pelo WhatsApp Business App sejam identificadas como
   `Enviada pelo app` e não transfiram o lead;
4. implementar alerta de desconexão, inclusive `PRIMARY_INACTIVITY`;
5. configurar setores Vendas, Suporte e Diretoria, vínculo de atendente e fallback para rodízio;
6. configurar o funil de cinco etapas e o estado externo `Sem Retorno` após sete dias;
7. resolver os valores `[a definir]` da base de conhecimento e validar a persona da IA;
8. definir como `tema.json` e `textos.json` serão injetados por instância sem alterar o core, para
   aplicar o branco + `#6b0504` da Disbrel;
9. homologar horários, permissões, usuários, templates e o webhook real da Meta;
10. executar teste de rollback, backup/restauração e teste controlado de mensagens antes do
    go-live.

## 8. Decisões pendentes antes do primeiro deploy

- Qual domínio real será usado para CRM, mídia e automação?
- Qual tag de imagem com CI verde será promovida?
- A Fase 1 terá um número Meta de homologação ou o canal ficará desligado até a Fase 2? O Stack
  atual exige valores não vazios para as variáveis do canal.
- A VPS atual comporta a terceira instância após medição de `docker stats`, ou será usada uma VPS
  dedicada?
- Quem será o administrador inicial do n8n e qual é o procedimento de revogação do acesso do
  Dylan?
