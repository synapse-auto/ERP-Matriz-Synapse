# 21. O que foi entregue para a Automação (E51)

Documento de passagem para o **Dylan**. Responde três coisas: o que existe agora que não existia,
o que muda no que ele já construiu, e o que ainda depende de resposta dele.

Base de tudo aqui: `http://synapse-backend-internal:8080/internal/v1`, header
`X-Synapse-Token: <SYNAPSE_TOKEN_INTERNO>`. **O Swagger é a fonte da verdade** — se este documento
divergir dele, o Swagger está certo. `/swagger-ui` e `/v3/api-docs` no domínio do CRM.

---

## 1. Resumo em cinco linhas

Foram abertos **quatro contratos internos na entrega E51**: listar atendimentos em andamento, criar
lembrete, sobrescrever o resumo da IA, e aplicar tag do catálogo ao lead. Todos exigem
`X-Synapse-Token` e papel de serviço; nenhum deles aparece na API de usuário. A varredura por
`GET /api/v1/atendimentos?visao=TODOS` **sai de cena** e é substituída por
`GET /internal/v1/atendimentos/em-andamento`. Nenhuma ação de infraestrutura é necessária: os
parâmetros novos têm padrão seguro. Ficaram **fora** desta entrega o FAQ institucional e a avaliação
de atendimento.

Esta etapa acrescenta o contrato **EV-05**, descrito na seção 5, para resumo e preenchimento
automático por ciclo do n8n; ele não cria um quinto cron no backend.

---

## 2. Os quatro contratos novos

### 2.1 `GET /internal/v1/atendimentos/em-andamento`

Substitui a varredura. Devolve o **mínimo** para a Automação decidir — nunca histórico, nunca
conteúdo de mensagem.

```text
GET /internal/v1/atendimentos/em-andamento
    ?atividadeDesde=2026-08-25T00:00:00Z
    &atividadeAte=2026-08-25T23:59:59Z
    &pagina=0
    &tamanho=20
X-Synapse-Token: <SYNAPSE_TOKEN_INTERNO>
```

```json
{
  "atendimentos": [
    {
      "atendimentoId": "1f2e...",
      "leadId": "9c7a...",
      "status": "EM_IA",
      "responsavel": null,
      "ultimaMensagemEm": "2026-08-25T14:32:10Z"
    },
    {
      "atendimentoId": "b30d...",
      "leadId": "44f1...",
      "status": "EM_ATENDIMENTO",
      "responsavel": { "id": "7e11...", "nome": "Ana Ribeiro" },
      "ultimaMensagemEm": "2026-08-25T13:05:44Z"
    }
  ],
  "pagina": 0,
  "tamanho": 20,
  "temMais": true
}
```

O que precisa ficar claro antes de montar o nó:

- **Recorte por estado, não por visão.** Devolve `EM_IA` e `EM_ATENDIMENTO`. `FINALIZADO` nunca
  aparece. São os três estados que existem no domínio; não há outros.
- **`responsavel` é `null` quando o atendimento está com a IA.** Não trate isso como erro.
- **`ultimaMensagemEm` é `null` quando ainda não há mensagem nenhuma.** Nesse caso o filtro de data
  usa o início do atendimento — ou seja, um atendimento recém-criado e ainda mudo **entra** no
  recorte de "mudou desde a última passagem", que é o comportamento que você quer.
- **Ordenação:** última atividade decrescente, `id` como desempate. É estável entre páginas.
- **O teto de página é do servidor.** O padrão da instância é **20** (`SUPORTE_TAMANHO_PAGINA`).
  Pedir `tamanho=500` **não dá erro** — devolve 20. Quem decide se acabou é o campo `temMais`, não a
  contagem de itens. Não existe total global aqui, de propósito.
- `atividadeDesde` posterior a `atividadeAte` responde **400**.

**O padrão de uso é incremental:** guarde o instante da última varredura e mande em
`atividadeDesde`. Sem isso você volta a varrer tudo, só que por outra porta.

### 2.2 `POST /internal/v1/atendimentos/{id}/lembretes`

Para o caso de transferência fora do expediente gerar um lembrete de retomada.

```text
POST /internal/v1/atendimentos/{id}/lembretes
X-Synapse-Token: <SYNAPSE_TOKEN_INTERNO>
Idempotency-Key: workflow-123-lembrete-1
Content-Type: application/json
```

```json
{ "texto": "Retornar com o orçamento", "dataHora": "2026-08-26T13:00:00Z" }
```

```json
{
  "id": "e5b2...",
  "atendimentoId": "1f2e...",
  "leadId": "9c7a...",
  "atendenteId": "7e11...",
  "texto": "Retornar com o orçamento",
  "dataHora": "2026-08-26T13:00:00Z",
  "origemAutomatica": true,
  "status": "PENDENTE"
}
```

- **O lembrete é do atendente responsável pelo atendimento no instante da chamada.** Não existe
  destinatário no corpo, e não há identidade técnica dona de lembrete.
- **Atendimento sem responsável humano é recusado com `409`.** Lembrete sem dono não aparece para
  ninguém e vira lixo silencioso — é recusa deliberada, não bug. Se você precisa lembrar alguém de
  um atendimento que está com a IA, transfira primeiro.
- **Idempotência:** o `Idempotency-Key` é obrigatório (sem ele, `400`). Repetir a **mesma chave com
  o mesmo texto e a mesma data** devolve a resposta original, sem criar um segundo lembrete. Repetir
  a mesma chave com conteúdo diferente, ou em outro atendimento, responde **409**. Gere a chave a
  partir de algo estável do workflow — não de um timestamp de execução, ou o retry vira lembrete
  novo.

### 2.3 `POST /internal/v1/atendimentos/{id}/resumo`

```text
POST /internal/v1/atendimentos/{id}/resumo
X-Synapse-Token: <SYNAPSE_TOKEN_INTERNO>
```

```json
{ "resumo": "Cliente solicitou orçamento de box e aguarda medidas." }
```

```json
{ "atendimentoId": "1f2e...", "leadId": "9c7a...", "resumo": "Cliente solicitou..." }
```

- **Sobrescreve.** É um retrato do estado atual, não um histórico. O resumo anterior não fica em
  lugar nenhum. Se a IA precisar de histórico de resumos, isso é outra conversa.
- Grava no lead do atendimento e **aparece na ficha do lead imediatamente**, com o selo de IA.
- **Limite de 8000 caracteres**, configurável por instância
  (`AUTOMACAO_RESUMO_IA_TAMANHO_MAXIMO`). Acima disso, **422**. Não há truncamento silencioso: ou
  cabe, ou é recusado. Resumo de IA cresce sozinho se ninguém o impedir.
- Não pede `Idempotency-Key` — sobrescrever duas vezes com o mesmo texto dá no mesmo.

### 2.4 Tags: `GET /internal/v1/tags` e `POST /internal/v1/leads/{id}/tags`

```text
GET /internal/v1/tags
```

```json
[ { "id": "3a90...", "nome": "Orçamento", "cor": "#1F74E0", "icone": "tag" } ]
```

```text
POST /internal/v1/leads/{leadId}/tags
```

```json
{ "tagId": "3a90..." }
```

```json
{ "leadId": "9c7a...", "tag": { "id": "3a90...", "nome": "Orçamento", "cor": "#1F74E0", "icone": "tag" } }
```

**A trava mais importante desta entrega: a Automação nunca cria tag.** Ela aplica UUID que veio do
catálogo. Tag que não existe é recusada com **422 nomeando o UUID recusado** — não é criada, não é
aproximada por nome, não é ignorada.

O motivo é prático: sem essa regra, em duas semanas o catálogo tem "orçamento", "Orçamento",
"orcamento" e "ORÇAMENTO", e os filtros da tela param de significar alguma coisa. Quem cria tag é
gente, na tela de Tags.

- **O corpo aceita `tagId`, não nome.** Leia o catálogo, case pelo nome no seu lado, mande o UUID.
- **Reaplicar tag que o lead já tem é sucesso**, não erro. Pode repetir à vontade em retry.
- Lead inexistente responde **404**.
- Toda aplicação por IA fica **auditada como `AUTOMACAO`** — dá para saber depois que não foi uma
  pessoa que colocou aquela tag.

---

## 3. O que muda no que já está construído

### 3.1 Pare de usar `GET /api/v1/atendimentos?visao=TODOS`

Essa rota é da **API autenticada de usuário**: exige JWT de uma pessoa. E o conceito de `visao` é da
tela — ele recorta por papel e por dono, porque os atendentes trabalham por comissão e cada um vê o
que é seu (RN-CRM-01). **A Automação não tem papel nem dono**, então não existe valor de `visao`
que signifique o que ela precisa; `TODOS` só parecia funcionar.

O substituto é `GET /internal/v1/atendimentos/em-andamento`, que recorta por **estado**, que é o
conceito certo.

> **Pergunta em aberto, e ela é a primeira da lista:** com que credencial os workflows chamam essa
> rota hoje? Os workflows não estão versionados no repositório, então não deu para confirmar do
> nosso lado. Se houver JWT de uma pessoa real guardado numa credential do n8n, isso precisa ser
> dito agora — é rotação de credencial, não ajuste de workflow.

### 3.2 Duas transferências, e elas não são a mesma coisa

Esse ponto estava descrito errado no documento anterior e foi corrigido:

- `POST /internal/v1/atendimentos/{id}/transferir` — **exige `atendenteId` no corpo** e honra a
  escolha. Só aceita usuário **ativo** com papel `ATENDENTE` ou `SUBGESTOR`; gestor,
  administrador, IA e UUID inexistente respondem **422**. Quando o n8n recebeu um pedido explícito
  do cliente, pode reatribuir atendimento aberto (`EM_IA` ou `EM_ATENDIMENTO`), inclusive trocando o
  responsável humano atual; `FINALIZADO` responde **409**.
- `POST /internal/v1/atendimentos/{id}/transferir-proximo-humano` — **não aceita destinatário**. O
  CRM escolhe: menor quantidade de atendimentos abertos, depois quem está há mais tempo sem receber,
  depois o `id` para desempate determinístico. Quem nunca recebeu vem antes de quem já recebeu. Esse
  rodízio é estrito a atendimentos `EM_IA` e nunca reatribui uma conversa já assumida por humano.

Se o workflow não tem um motivo de negócio para apontar a pessoa, use a segunda e deixe o rodízio
decidir. `GET /internal/v1/atendentes/disponiveis` já devolve a lista **na ordem recomendada** — o
primeiro item é o destino sugerido. Não reordene no workflow.

Quando o cliente cita uma pessoa pelo nome, a Automação pode resolver o UUID sem manter uma tabela
própria de usuários:

```text
GET /internal/v1/atendimentos/atendentes?nome=Daiane
X-Synapse-Token: <SYNAPSE_TOKEN_INTERNO>
```

O CRM faz busca case-insensitive por substring e devolve `200` com todos os candidatos, cada um
contendo apenas `id` e `nome`. O filtro é o mesmo da transferência explícita (`ativo = TRUE` e papel
`ATENDENTE` ou `SUBGESTOR`), sem filtrar `disponivel_para_ia`; portanto a Automação deve tratar
ambiguidade e então chamar `/transferir` com o UUID escolhido. Sem correspondência, a lista é `[]`;
nome ausente ou em branco é `400`, e token ausente ou inválido é `401`.

Para finalizar um único atendimento, use `POST /internal/v1/atendimentos/{id}/finalizar` sem corpo.
Exija `Idempotency-Key`; a transição marca o atendimento e o lead como `FINALIZADO` e registra
`AUTOMACAO` na timeline, auditoria e evento pós-commit, sem usuário técnico ou UUID fictício. A
resposta contém apenas os IDs, status, instante de finalização e origem; não expõe telefone,
histórico ou mensagens. A mesma chave e atendimento devolvem a resposta original, enquanto uma chave
reutilizada em outro atendimento/operação ou uma segunda finalização com chave nova responde `409`.

---

## 4. O que continua exatamente igual

> **Atualização E213 (25/09/2026).** O repasse deixou de levar mensagens de grupo e reentregas:
> POST sem grupo segue cru e com a assinatura original; POST misto segue no mesmo envelope sem os
> itens de grupo e, na Meta, com `X-Hub-Signature-256` recalculada com o mesmo App Secret; POST só de
> grupo e reentrega do mesmo POST não são repassados. Detalhe em `docs/44`, Fase 2 item 3.

- O repasse do webhook da Meta: payload cru, `X-Hub-Signature-256` repassado, retentativa com recuo
  exponencial até 8 tentativas, workflow precisa estar **ativado** (a URL de produção, não a de
  teste).
- `POST /internal/v1/eventos` continua sendo a única coisa que atualiza a telemetria da Automação.
  Não existe scheduler no CRM: a frequência do snapshot é a frequência dos eventos que você manda.
- `GET /internal/v1/regras/follow-up` e `/regras/fidelizacao` continuam somente leitura, somente
  regras ativas. Quem executa follow-up e fidelização é o n8n (RN-CRM-07).
- Nada de acesso direto ao banco do CRM. A role do n8n não tem permissão lá, e escrita direta não
  dispara WebSocket, outbox, timeline nem métrica — a linha existe no banco e é como se não
  existisse no produto.

---

## 5. EV-05 — contrato de ciclo do n8n

O EV-05 não cria job no CRM. O n8n executa o cron (cinco horas) e usa
`GET /internal/v1/automation-config/ev05` para obter, separadamente, `resumo.habilitado` e
`resumo.intervaloHoras`, e `preenchimentoAutomatico.habilitado` e
`preenchimentoAutomatico.intervaloHoras`. Os intervalos vêm de `configuracao_automacao` e têm
faixa persistida (1–720 horas).

`GET /internal/v1/ev05/candidatos` pagina somente `EM_ATENDIMENTO` (`atendimentoId`, `leadId`,
`situacao`, `atualizadoEm`). Para cada candidato, o workflow pode consultar `.../leads/{leadId}/resumo`,
`.../leads/{leadId}/preenchimento` e `.../atendimentos/{atendimentoId}/contexto`. O contexto é
limitado pelo teto de histórico da instância e sinaliza `conteudoSuficiente`; não expõe telefone,
notas, URL assinada, token ou payload do provedor.

As escritas são idempotentes e exigem `Idempotency-Key`:

```text
POST /internal/v1/ev05/leads/{leadId}/resumo
{ "leadId": "...", "resumo": "...", "contextoGeradoEm": "..." }

POST /internal/v1/ev05/leads/{leadId}/preenchimento
{ "leadId": "...", "email": "...", "cpf": "...", "empresa": "...", "localizacao": "..." }
```

O resumo substitui o retrato atual e recusa contexto obsoleto. O preenchimento normaliza e valida
e-mail/CPF, nunca limpa nem sobrescreve campo manual e informa por campo `APLICADO`,
`IGNORADO_JA_PREENCHIDO`, `IGNORADO_INVALIDO` ou `AUSENTE`; a avaliação é marcada mesmo sem
aplicação. Replay da mesma chave devolve a mesma resposta; chave incompatível responde `409`,
chave ausente `400`, recurso inelegível `404` e dados inválidos `422` (RFC 7807).

### 5.1 Resumo sob demanda iniciado pelo CRM

O navegador chama apenas `POST /api/v1/atendimentos/{atendimentoId}/resumo-ia` com uma chave UUID
estável no header `Idempotency-Key`. O CRM grava o ciclo `PENDENTE` e publica o pedido leve na
Transactional Outbox; a resposta `202` não significa que a IA terminou. Se a mesma chave for
repetida para o mesmo atendimento, o CRM devolve o mesmo ciclo; outra combinação de atendimento/lead
responde `409`.

O publisher assíncrono do CRM chama o webhook configurado em `AUTOMACAO_RESUMO_IA_URL`:

```text
POST {AUTOMACAO_RESUMO_IA_URL}
CRM-Synapse-RES: <AUTOMACAO_RESUMO_IA_TOKEN>
Idempotency-Key: <solicitacaoId>
```

```json
{
  "atendimentoId": "uuid",
  "leadId": "uuid"
}
```

O workflow n8n usa o valor de `Idempotency-Key` como solicitação e mantém a chave e o estado em Data Table persistida no banco próprio do n8n. Ele
responde imediatamente `202` (nova) ou `200` (replay), atualiza `PROCESSANDO`, consulta o contexto
limitado, gera o texto no provedor de IA e grava pela rota EV-05 abaixo:

```text
GET  /internal/v1/ev05/atendimentos/{atendimentoId}/contexto
POST /internal/v1/ev05/leads/{leadId}/resumo
POST /internal/v1/ev05/leads/{leadId}/resumo-status
```

Na escrita, `Idempotency-Key` é exatamente `solicitacaoId` e `contextoGeradoEm` é exatamente o
`contextoAte` retornado. O status recebe `PROCESSANDO`, `CONCLUIDO` ou `FALHOU`; falhas de rede/5xx
são retentadas com backoff limitado, enquanto 4xx (inclusive `409` de ciclo obsoleto) não entram em
retry automático. O CRM publica `RESUMO_IA_STATUS` depois do commit e nunca apaga o resumo anterior
quando uma nova geração falha. O endpoint de status recusa atendimento finalizado, transferido ou
substituído e não aceita mensagem de erro com token, telefone, URL ou payload.

O arquivo `docs/n8n/resumo-ia-sob-demanda.json` é o template versionado sem credenciais. Após importar,
configure a Header Auth `SYNAPSE_TOKEN_INTERNO` para o CRM e a validação do webhook com
`AUTOMACAO_RESUMO_IA_TOKEN`; não coloque segredos no JSON exportado.

## 6. O que ficou de fora, e por quê

- **FAQ institucional** — é etapa própria: precisa de tela para o cliente editar o conteúdo. Não
  adianta abrir endpoint para um dado que ninguém consegue cadastrar.
- **Avaliação de atendimento** — não entrou no escopo desta entrega.
- **Histórico completo de conversa por contrato interno** — o `em-andamento` deliberadamente não
  devolve conteúdo. Se a Automação precisa mesmo de histórico, é outra etapa; veja a pergunta 3
  abaixo.

---

## 7. Perguntas que dependem do Dylan

1. **Credencial.** Com que credencial os workflows chamam `/api/v1/atendimentos` hoje? (não deu para
   confirmar: workflows não versionados)
2. **Telemetria de envio.** O relatório mostra `Clientes Transferidos: 9` e `Mensagens Enviadas: 0`.
   O evento de transferência está chegando em `POST /internal/v1/eventos`; o de mensagem enviada,
   não. É emissão que falta no workflow, ou é outra coisa?
3. **Histórico.** "A API de mensagens atual não serve para recuperar todo o histórico útil" — qual é
   o caso concreto? Quantas mensagens, para decidir o quê? Sem o caso, não dá para desenhar o
   contrato certo, e histórico completo por rota interna é exatamente o tipo de coisa que não se
   abre por precaução.
4. **Isolamento por canal.** O `X-Synapse-Token` hoje é **por instância**, não por canal. Se os
   workflows vierem a atender mais de um canal na mesma instância, isso precisa mudar antes — não
   depois.
5. **Versionar os workflows.** Exportar o JSON e versionar em `automacao/workflows/`, sem
   credenciais. O que fica só no volume some junto com o volume — e foi exatamente o que impediu de
   responder a pergunta 1 sem perguntar.

---

## 8. Checklist de migração

- [ ] Trocar a varredura por `GET /internal/v1/atendimentos/em-andamento`, guardando o instante da
      última passagem em `atividadeDesde`.
- [ ] Paginar por `temMais`, não por contagem de itens; não assumir que `tamanho` pedido é o
      recebido.
- [ ] Tratar `responsavel: null` e `ultimaMensagemEm: null` como normais.
- [ ] Gerar `Idempotency-Key` estável (não derivada de timestamp de execução) para o lembrete.
- [ ] Ler `GET /internal/v1/tags` e aplicar por UUID; tratar 422 como "a tag não existe, avise
      alguém", nunca como "crie a tag".
- [ ] Conferir se alguma chamada ainda usa `/api/v1/...` com credencial de pessoa.
- [ ] Exportar e versionar os workflows.
