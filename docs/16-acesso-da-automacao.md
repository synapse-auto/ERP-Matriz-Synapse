# 16. Como a Automação acessa o CRM

Documento para quem constrói os workflows do n8n. Responde: qual banco usar, como acessar, e o que fazer quando o dado que você precisa não está exposto.

---

## 1. Como o deploy está montado

A instância é uma **aplicação Compose em modo Docker Stack (Swarm)** no Dokploy, descrita por um arquivo único: `docker/dokploy-stack.yml`. Sete serviços sobem juntos: PostgreSQL, Redis, RabbitMQ, MinIO, backend, frontend e n8n.

Três coisas que importam para a Automação:

**Todos os serviços conversam por uma rede interna** chamada `synapse-internal`. Dentro dela, cada serviço tem um nome fixo. O n8n alcança o backend em `http://synapse-backend-internal:8080`.

**Postgres, Redis e RabbitMQ não publicam porta no host.** Não existe `IP:5432` para conectar de fora. É proposital: o banco só é alcançável de dentro da rede da própria instância.

**O Traefik publica só o que tem domínio:** o CRM, o MinIO e o editor do n8n. O namespace `/internal/v1` **não tem rota pública** — ele existe apenas dentro da overlay.

## 2. Existem dois bancos, e eles são separados

No mesmo servidor PostgreSQL, mas isolados por banco lógico e por usuário:

| Banco | Dono | Para quê |
|---|---|---|
| `synapse_crm` | usuário da aplicação | leads, atendimentos, mensagens, equipe — os dados do CRM |
| banco do n8n (`N8N_DB_NAME`) | role própria do n8n | os internos do n8n: workflows, credenciais, execuções |

A role do n8n **não tem permissão** no banco do CRM. Isso é desenho, não descuido: uma credencial de workflow não deve alcançar a tabela de leads.

**O banco que o Dylan usa é o do n8n**, e ele já está configurado — o n8n se conecta sozinho. Não há nada a fazer ali.

## 3. Como a Automação lê e escreve dados do CRM

Pelo contrato interno, nunca por SQL:

```
Base:  http://synapse-backend-internal:8080/internal/v1
Auth:  header  X-Synapse-Token: <SYNAPSE_TOKEN_INTERNO>
Doc:   /v3/api-docs  e  /swagger-ui  no domínio do CRM
```

No n8n, o token vai numa **Credential do tipo Header Auth**, não digitado dentro do nó. Credential é cifrada com a `N8N_ENCRYPTION_KEY`; valor solto no nó vai em texto claro para dentro do JSON do workflow — e workflow a gente exporta e versiona.

A URL é a interna, sem HTTPS e sem domínio. Se apontar para o domínio público, vem 404: aquele namespace não tem rota pública. A resposta certa nesse caso é corrigir a URL, nunca abrir a rota.

### 3.1 O número de WhatsApp pertence à instância

A Meta inscreve o app no nível da **WABA**, não de um número isolado. Uma mesma
conta pode conter o número oficial e um número de homologação; depois da
inscrição, o app recebe eventos de ambos. Isso não autoriza a Automação nem o
CRM a processar ambos.

O backend compara cada
`entry[].changes[].value.metadata.phone_number_id` com o identificador da
credencial ativa persistido em `canal_credencial.identificador_externo`. A
validação acontece depois do HMAC e antes de gravar o payload na entrada ou na
outbox de repasse para o n8n. Evento de outro número recebe `200` e é descartado;
POST misto é descartado inteiro e gera erro operacional. Canal ativo sem o
identificador falha fechado e deixa `/health/critical` em `DOWN`.

Na homologação da Estrutural, o Phone Number ID é `1307417749115229`. No
go-live, o provisionamento precisa ser reexecutado com o ID do número oficial
antes de liberar a inscrição/tráfego desse número. Não deduza o destino pelo
WABA ID, pelo telefone exibido nem por variável lida diretamente no webhook: a
fonte de verdade é a credencial ativa no banco da instância.

### 3.2 Comandos de atendimento

Toda escrita exige `X-Synapse-Token` e um `Idempotency-Key` único. Repetir a
mesma chave e o mesmo comando devolve a resposta original; usar a chave em
outra operação ou atendimento responde `409`. Sem chave, responde `400`.

Para consultar o recorte mínimo dos atendimentos ainda abertos, use:

```text
GET /internal/v1/atendimentos/em-andamento
X-Synapse-Token: <SYNAPSE_TOKEN_INTERNO>
```

Aceita `atividadeDesde`, `atividadeAte`, `pagina` e `tamanho`. A resposta não contém
histórico nem conteúdo de mensagens; o limite de página é aplicado pelo CRM.

Responder pela IA grava a mensagem como `Remetente.IA` e a intenção na outbox;
o n8n não chama a Meta diretamente e não precisa enviar `wamid`:

```text
POST /internal/v1/atendimentos/{atendimentoId}/responder
X-Synapse-Token: <SYNAPSE_TOKEN_INTERNO>
Idempotency-Key: workflow-123-resposta-1
Content-Type: application/json
```

```json
{ "conteudo": "Mensagem da automação" }
```

Para entregar a conversa a uma pessoa, informe somente um usuário ativo com
papel `ATENDENTE`; gestor, subgestor, IA e UUID inexistente são recusados:

```text
POST /internal/v1/atendimentos/{atendimentoId}/transferir
X-Synapse-Token: <SYNAPSE_TOKEN_INTERNO>
Idempotency-Key: workflow-123-transferencia-1
```

```json
{ "atendenteId": "00000000-0000-0000-0000-000000000000" }
```

Para devolver ao robô, use `PATCH /internal/v1/atendimentos/{id}/modo-ia` com
corpo `{}`. Para distribuição automática, use
`POST /internal/v1/atendimentos/{id}/transferir-proximo-humano` sem corpo: o
CRM escolhe o primeiro disponível na ordem recomendada: menor quantidade de
atendimentos abertos (`status = EM_ATENDIMENTO`), depois quem está há mais tempo
sem receber e, por fim, o `id` para desempate determinístico. Atendentes que
nunca receberam vêm antes de quem já recebeu. Ao consultar
`GET /internal/v1/atendentes/disponiveis`, o primeiro item é portanto o destino
recomendado; não reordene a lista no workflow. Ambas as ações registram
`AUTOMACAO` na timeline e auditoria, sem usuário técnico ou UUID fictício.

Depois que o atendimento estiver `FINALIZADO`, a Automação pode gravar o CSAT
na escala 1–5 (a mesma do `CHECK` de `avaliacao.nota`). Uma nota por conversa;
segunda tentativa responde `409`. Conversa ainda aberta ou sem atendente
humano responde `422`.

```text
POST /internal/v1/atendimentos/{atendimentoId}/avaliacao
X-Synapse-Token: <SYNAPSE_TOKEN_INTERNO>
Content-Type: application/json
```

```json
{ "nota": 5, "comentario": "Atendimento rápido" }
```

Quando a Automação já enviou uma mensagem diretamente à Meta, registre o resultado no
histórico do CRM. Esta rota **não chama a Meta**; ela persiste a saída, publica no WebSocket
e mantém o `wamid` necessário para responder mensagens futuras:

```text
POST /internal/v1/atendimentos/{atendimentoId}/mensagens-enviadas
X-Synapse-Token: <SYNAPSE_TOKEN_INTERNO>
Content-Type: application/json
```

O corpo inclui `wamid`, `tipo`, e os campos normalizados de texto/mídia. A chamada é
idempotente pelo `wamid`; um `wamid` já ligado a outro atendimento responde `409`.

### 3.3 Regras de follow-up e fidelização

O gestor configura as regras na API administrativa, protegida por JWT e pelos
papéis `GESTOR`, `SUBGESTOR` ou `ADMINISTRADOR`:

```text
GET|POST       /api/v1/automacao/follow-ups
PUT|DELETE     /api/v1/automacao/follow-ups/{id}
PATCH          /api/v1/automacao/follow-ups/{id}/ativo
GET|POST       /api/v1/automacao/fidelizacao
PUT|DELETE     /api/v1/automacao/fidelizacao/{id}
PATCH          /api/v1/automacao/fidelizacao/{id}/ativo
```

As rotas internas já existentes para o n8n continuam sendo somente de leitura
e somente de regras ativas: `GET /internal/v1/regras/follow-up` e
`GET /internal/v1/regras/fidelizacao`. O armazenamento usa minutos para
follow-up; a interface converte para horas quando o valor não é múltiplo de
1440 e para dias quando é. Fidelização usa dias sem contato.

Mensagens aceitam somente o placeholder `{nome}`. Placeholder desconhecido ou
mensagem vazia é recusado no cadastro com `422` (RFC 7807), antes de chegar ao
banco. O CRM apenas configura e expõe as regras; não há executor ou scheduler
no backend. A execução continua sendo responsabilidade do n8n, conforme
`RN-CRM-07`.

### 3.4 Recursos do assistente de IA

O painel administrativo expõe a configuração singleton do resumo por IA em
`GET|PUT /api/v1/automacao/config/resumo-ia`, protegido por JWT de gestão. O
contrato interno de leitura para o n8n é
`GET /internal/v1/automation-config/recursos-ia` e também informa se o recurso
`ia.preenchimento_automatico` está ligado. A escrita desse parâmetro continua
no CRUD administrativo de `configuracao_automacao`.

Não existe scheduler, rotina de varredura ou disparo no backend. O snapshot
`status_automacao_telemetria` é atualizado exclusivamente pelo caso de uso
`RegistrarEventoDeAutomacaoUseCase`, chamado pelo n8n através de
`POST /internal/v1/eventos`; portanto a frequência é a frequência dos eventos
que o workflow envia, e não um job periódico do CRM.

## 4. Por que não acessar o banco direto

Três motivos, em ordem de gravidade:

**1. O CRM tem Row-Level Security.** Quem vê qual lead é decidido por política no Postgres, e os atendentes trabalham por comissão. Uma conexão direta com o usuário errado enxerga tudo — e vazamento de lead entre atendentes é incidente comercial, não bug técnico.

**2. Escrever direto não dispara nada.** O CRM reage a mudanças por eventos de domínio. Um `INSERT` manual numa tabela de mensagem:

- não avisa a tela do atendente pelo WebSocket — ninguém vê a mensagem chegar
- não entra na outbox — ela nunca é enviada ao WhatsApp
- não gera evento de timeline — some do histórico do lead
- não conta nas métricas do Dashboard

A linha existe no banco e é como se não existisse no produto.

**3. O schema pertence ao Flyway.** São 47 migrations versionadas, até `V47__lead_codigo.sql`. Uma coluna criada na mão fica fora desse controle: no próximo deploy o `validate` pode recusar subir, ou o filho seguinte nasce sem ela. Este é um produto multi-instância — o schema tem que ser idêntico em todos.

### 4.1 Templates da Meta não são contrato interno

O CRM agora possui administração de templates pela rota pública autenticada
`/api/v1/whatsapp/templates`, usada pela tela de Administração. Ela consulta e submete
templates de texto no Graph da Meta fora do caminho de envio/recebimento. Para esse fluxo,
o backend usa o **WABA ID** configurado em `WHATSAPP_CONTA_NEGOCIO`; ele é diferente do
Phone Number ID usado para filtrar eventos. A ausência do WABA ID degrada somente a
administração de templates para `503` em RFC 7807; não bloqueia o atendimento.

O n8n não deve chamar essa rota nem falar com a Meta diretamente. O contrato do n8n
continua sendo o namespace `/internal/v1`.

## 5. Quando você precisa guardar um dado que não existe

**Campo customizado, não coluna nova — com uma exceção já existente.** O CRM tem `campo_customizado` + `lead.dados_customizados` para dado que só um filho precisa. Identificador interno numérico do cliente **já é coluna**: `lead.codigo` (V47), editável pelo PUT da ficha, visível no card. Não crie um campo customizado chamado `codigo` para o mesmo fim.

Nenhuma migration, nenhum deploy para um campo customizado novo, e funciona diferente em cada cliente sem tocar em código.

**Se o dado não couber num campo customizado**, ou se o `/internal/v1` não expuser a operação que você precisa — não contorne pelo banco. Peça a extensão do contrato. É uma etapa curta de backend, e ela vem com autorização, evento de domínio e teste, que é o que faz o dado valer para o produto inteiro e não só para o seu workflow.

## 6. Resumo 

| Pergunta | Resposta |
|---|---|
| Qual banco eu uso? | O do n8n. Já está conectado, nada a configurar |
| Como acesso os dados do CRM? | `http://synapse-backend-internal:8080/internal/v1`, com `X-Synapse-Token` |
| Onde vejo o que existe? | Swagger, no domínio do CRM |
| Posso criar coluna no banco do CRM? | Não. Use campo customizado, ou peça um endpoint |
| Preciso de acesso SSH ou psql? | Não |
| E se eu precisar de algo que a API não tem? | Pede. É etapa curta, e o dado passa a valer para o sistema inteiro |
