# Prompt E33 — comandos internos para o n8n

> Leia `AGENTS.md`. Entrega em 25/08.
> Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e informe o **número da run** do CI.

---

## Contexto — a Automação precisa operar o atendimento da Estrutural

O n8n do CRM clínico antigo usava rotas `/api/n8n/...`. Este CRM não é clínico: não há paciente,
profissional, consulta, encaixe ou agenda de consultas no domínio. Não copie esses endpoints nem
crie nomes clínicos para uma fábrica de vidros.

O contrato deste repositório para a Automação é `/internal/v1`, autenticado por `X-Synapse-Token`:

```java
// backend/crm-equipe/src/main/java/com/synapse/crm/equipe/infrastructure/seguranca/
// SynapseTokenAuthenticationFilter.java
static final String PREFIXO_PROTEGIDO = "/internal/v1/";
// autentica como ROLE_SERVICO
```

Já existe:

```java
// backend/crm-equipe/src/main/java/com/synapse/crm/equipe/interfaces/internal/
// AtendentesDisponiveisInternalController.java
@RequestMapping("/internal/v1/atendentes")
@GetMapping("/disponiveis")
```

Também existem as regras de transferência e envio humano em:

```java
// backend/crm-atendimento/src/main/java/com/synapse/crm/atendimento/interfaces/
// AtendimentoAcoesController.java
POST /api/v1/atendimentos/{id}/transferir
POST /api/v1/atendimentos/mensagens
```

Mas esses caminhos usam JWT e `UsuarioContext`. O `UsuarioContextSpring` atual só aceita um
principal `Jwt`; o principal do token interno é `automacao`. Portanto, apenas duplicar o controller
para `/internal/v1` quebraria ou atribuiria a mensagem ao ator errado.

O domínio já possui `Remetente.ia()`. Uma resposta do n8n deve ser mensagem da IA, passar pela mesma
outbox e pelo mesmo gateway do canal, mas **não** pode ser registrada como `ATENDENTE` nem transferir
o lead pela RN-CRM-06.

## Bloco 1 — contrato interno e ator da Automação

Crie o contrato canônico abaixo. Não crie `/api/n8n` como contrato novo:

```text
GET   /internal/v1/atendentes/disponiveis                         (já existe)
POST  /internal/v1/atendimentos/{atendimentoId}/responder
POST  /internal/v1/atendimentos/{atendimentoId}/transferir
PATCH /internal/v1/atendimentos/{atendimentoId}/modo-ia
POST  /internal/v1/atendimentos/{atendimentoId}/transferir-proximo-humano
```

Requisitos:

- Todos os quatro comandos novos exigem `ROLE_SERVICO` e só podem entrar por `X-Synapse-Token`.
- O caminho `/api/v1` continua protegido por JWT e não pode aceitar o token interno por acidente.
- `responder` recebe `{ "conteudo": "..." }` e cria `Remetente.ia()`.
- `responder` deve gravar mensagem e outbox na mesma transação, sem chamada HTTP ao provedor no
  request. Reutilize a porta `Outbox` e o `CanalGateway`; não duplique o publisher nem crie um
  caminho síncrono para a Meta.
- `responder` não pode alterar o dono do lead por causa da RN-CRM-06, que é exclusiva do humano.
- `transferir` recebe `{ "atendenteId": "uuid" }` e valida que o destino existe, está ativo e é
  `ATENDENTE`. Não aceite um UUID arbitrário nem permita transferir para gestor, subgestor ou IA por
  essa rota.
- `modo-ia` devolve o atendimento para a IA sem inserir UUID falso ou `null` como ator humano.
- `transferir-proximo-humano` escolhe um atendente disponível pelo mesmo critério já usado em
  `AtendenteDisponivelRepositorioJdbc` (`nome`, com `id` como desempate), e então executa a
  transferência na mesma transação. Se não houver atendente elegível, responda `409` com Problem
  Details; não retorne sucesso sem transferência.
- A timeline e a auditoria precisam identificar a ação como Automação/IA/Sistema de forma explícita.
  **Não use UUID fictício, não faça `toString()` de `null` e não classifique a IA como `USUARIO`.**
  Se o evento atual só aceita ator humano, estenda o evento/modelo e todos os listeners com `switch`
  exaustivo. Não contorne o problema gravando diretamente nas tabelas de timeline ou auditoria.

> **Não faça:** chamar `EnviarMensagemUseCase` diretamente a partir do controller interno. Ele lê o
> usuário JWT e registra `Remetente.ATENDENTE`; isso falsificaria comissão, timeline e visibilidade.

> **Ponto de parada:** se a modelagem atual não permitir representar o ator IA/Sistema na timeline,
> auditoria ou RLS sem quebrar o contrato existente, pare e relate a decisão necessária. Não use um
> usuário atendente real nem um UUID mágico para fazer o teste passar.

## Bloco 2 — idempotência e respostas HTTP

Comandos do n8n podem ser repetidos após timeout. Cada comando de escrita deve aceitar o header
`Idempotency-Key` e ser seguro para retry:

- repetir a mesma chave e operação não cria segunda mensagem, segunda transferência ou segundo evento;
- reutilizar a mesma chave para outra operação ou atendimento responde `409`;
- sem chave, responda `400` e não altere o banco;
- a resposta repetida deve ser compatível com a resposta original;
- crie migration/tabela mínima somente se não houver mecanismo existente para persistir essa reserva;
  não use cache em memória como idempotência.

Defina DTOs estáveis e documente no OpenAPI:

```json
POST /internal/v1/atendimentos/{id}/responder
{ "conteudo": "Mensagem da automação" }

POST /internal/v1/atendimentos/{id}/transferir
{ "atendenteId": "00000000-0000-0000-0000-000000000000" }

PATCH /internal/v1/atendimentos/{id}/modo-ia
{}
```

Use Problem Details para `400`, `401`, `404`, `409` e `422`. Não exponha stack trace, credencial,
senha ou detalhes de RLS.

> **Não faça:** criar endpoints clínicos como `/agenda/pacientes`, `/agenda/profissionais`,
> `/agenda/encaixe` ou `/cancelamentos`. Se a Estrutural precisar futuramente de visitas técnicas
> ou compromissos de instalação, isso será uma etapa própria, com domínio e vocabulário próprios.

## Bloco 3 — testes de contrato e integração

Teste pelo controller real, com `X-Synapse-Token` e PostgreSQL/Testcontainers. Não teste somente o
caso de uso por chamada direta.

Inclua, no mínimo:

- token ausente e token inválido em cada comando → `401`, sem escrita;
- token válido → comando autorizado como `ROLE_SERVICO`;
- `responder` cria exatamente uma mensagem `IA`, uma outbox e nenhum evento de transferência de lead;
- `responder` não altera `atendente_responsavel_id` nem transforma o lead em propriedade de um humano;
- conteúdo vazio, atendimento inexistente e atendimento finalizado → erro correto e nenhuma escrita;
- `transferir` para atendente ativo → atendimento e lead passam para o destino;
- destino inexistente, inativo, não-ATENDENTE ou UUID inválido → erro e rollback;
- `modo-ia` deixa atendimento sem atendente e lead em `IA`, com timeline/auditoria identificando a
  Automação;
- `transferir-proximo-humano` escolhe o primeiro elegível pelo critério documentado;
- nenhum atendente disponível → `409`, sem alteração;
- repetição do mesmo `Idempotency-Key` → mesma resposta e nenhuma segunda mensagem/transferência/evento;
- chave reutilizada em operação ou atendimento diferente → `409`;
- reentrega depois de uma falha no meio do comando → não duplica efeitos;
- o endpoint já existente `GET /internal/v1/atendentes/disponiveis` continua no contrato e continua
  recusando chamadas sem token.

Atualize o snapshot de `/internal/v1` somente com a alteração intencional e deixe o diff legível.
Não substitua o teste de contrato por comparação contra o OpenAPI gerado na mesma execução.

## Definição de pronto

- [ ] Não existe endpoint clínico ou `/api/n8n` novo neste escopo.
- [ ] Os quatro comandos internos funcionam com `X-Synapse-Token` e `ROLE_SERVICO`.
- [ ] Resposta da Automação é `Remetente.IA`, usa outbox e não transfere lead por RN-CRM-06.
- [ ] Transferência humana valida destino e registra ator Automação sem UUID falso.
- [ ] Devolução para IA e transferência para próximo humano funcionam com os erros definidos.
- [ ] Idempotência por `Idempotency-Key` está persistida e testada pelo controller.
- [ ] Testes negativos comprovam ausência de escrita, duplicação e vazamento de autorização.
- [ ] OpenAPI e snapshot de `/internal/v1` foram atualizados conscientemente.
- [ ] `cd backend && ./mvnw clean verify` passou, ou a falha local foi explicada.
- [ ] CI verde com o número da run informado.

## No relatório

1. Variável nova no Dokploy: expectativa **nenhuma**; reutilize `SYNAPSE_TOKEN_INTERNO`/configuração
   existente. Se criar alguma, informe nome, motivo e valor de exemplo sem segredo.
2. Endpoints finais, método, corpo, autenticação, códigos de erro e exemplo de resposta.
3. Como a IA/Sistema ficou representada em timeline, auditoria e eventos.
4. Estratégia de idempotência, migration criada e comportamento de retry.
5. Testes executados, incluindo quantidade e os negativos.
6. Decisões tomadas sozinho e qualquer ponto de parada encontrado.
7. SHA final, confirmação de push para `origin/main`, número de arquivos e número da run do CI.
8. Confirme explicitamente que `SYNAPSE_IMAGE_TAG` deve apontar para o SHA do deploy; nunca use
   `latest` no Dokploy.

---

## Fora desta etapa

- Agenda de consultas, pacientes, profissionais clínicos, encaixe e cancelamento de consultas.
- Visitas técnicas, instalação, orçamento ou calendário específico da Estrutural.
- Novo fluxo de RabbitMQ além do que já existe para outbox/publicação.
- Alteração do webhook Meta, tradução de payload ou idempotência de `wamid` da E32.
- Frontend e telas administrativas para configurar os endpoints.

Commit sugerido do primeiro bloco: `feat: expor comandos internos para automacao n8n`.
