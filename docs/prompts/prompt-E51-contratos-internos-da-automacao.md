# Prompt E51 — os quatro contratos internos que a Automação precisa

> Leia `AGENTS.md`, `CLAUDE.md`, `docs/01-arquitetura-geral.md` e `docs/04-adrs-e-api.md`.
> **Pode commitar localmente a qualquer momento.** Não execute `git push` sem autorização explícita.

---

## Contexto

O Dylan (n8n) entregou a lista do que falta no CRM para a Automação. Esta etapa entrega os quatro
itens que já têm tudo no lugar e só precisam da porta. Fora do escopo, e ditos aqui para você não
inventá-los: **FAQ institucional** (é etapa própria, precisa de tela para o cliente editar) e
**avaliação de atendimento** (não entrou no escopo).

**Tudo aqui é `/internal/v1`, com `X-Synapse-Token` e `ROLE_SERVICO`.** Nada desta etapa aparece na
API autenticada de usuário.

## Bloco 0 — Uma investigação que vem primeiro

O documento do Dylan diz que a Automação consome hoje:

```
GET /api/v1/atendimentos?visao=TODOS
```

Isso é a **API autenticada de usuário**, não a `/internal/v1`. Descubra e relate, com evidência,
**antes de escrever o endpoint novo**:

- Essa rota exige autenticação de usuário? Se exige, **com que credencial o n8n está chamando**?
- Existe alguma rota de atendimento sem autenticação, ou com token de serviço aceito onde não
  deveria?

Se aparecer credencial de pessoa sendo usada por máquina, ou rota aberta, **pare e relate** — isso
passa na frente de tudo nesta etapa. Não conserte por conta própria; é decisão do Marcondes.

O conceito de **visão** é da tela: recorta por papel e por dono (RN-CRM-01). A Automação não tem
papel nem dono. Por isso o endpoint novo **não** recebe `visao`.

## Bloco 1 — Atendimentos em andamento

`GET /internal/v1/atendimentos/em-andamento`

Hoje a Automação varre todos os atendimentos para achar os que interessam. O recorte certo é por
**estado**, não por visão.

- Devolve os atendimentos **não encerrados** — os que estão com a IA e os que estão com humano.
  Confirme os estados reais no domínio antes de escolher; não deduza pelo nome.
- Aceita filtro por **data de última atividade**, para o n8n pedir só o que mudou desde a última
  passagem. Sem isso, ele volta a varrer tudo, só que por outra porta.
- Paginado, com limite máximo imposto pelo servidor. Um endpoint interno sem teto é um incidente
  esperando acontecer.
- Devolve o **mínimo** para a Automação decidir: id do atendimento, id do lead, estado, quem é o
  responsável, e quando foi a última mensagem. **Não** devolva histórico de conversa aqui.

## Bloco 2 — Lembrete criado pela Automação

`POST /internal/v1/atendimentos/{id}/lembretes`

A tabela e a tela de Lembretes já existem. Falta a porta de serviço, para o caso de transferência
fora do expediente gerar um lembrete de retomada.

**Uma decisão que você não toma sozinho:** de quem é o lembrete? A resposta natural é **do atendente
que recebeu o atendimento** — é ele que precisa retomar. Implemente assim e **registre no relatório**
que foi decisão sua, para o Marcondes confirmar.

- Se o atendimento não tem responsável humano, **recuse com erro claro**. Lembrete sem dono não
  aparece para ninguém e vira lixo silencioso.
- Idempotência: a Automação **vai** repetir a chamada em retry. Duas chamadas iguais não podem gerar
  dois lembretes. O projeto já tem tabelas estreitas de idempotência para comandos da Automação
  (`V32__idempotencia_comandos_automacao`) — **use o mecanismo que já existe**, não invente outro.

## Bloco 3 — Resumo da IA

**Confirme antes de construir:** o painel do lead **já exibe** "Resumo do atendimento" com selo IA.
Então provavelmente a coluna existe e falta só a rota de escrita. Diga no relatório o que encontrou.

Se faltar só a escrita: `POST /internal/v1/atendimentos/{id}/resumo`.

- Sobrescreve o resumo anterior — é um retrato do estado atual, não um histórico.
- Limite de tamanho, recusado com erro claro. Resumo de IA cresce sozinho se ninguém o impedir.
- O resumo aparece na tela do lead imediatamente; confirme que o caminho de leitura já existe.

## Bloco 4 — Tags aplicadas pela IA

Dois endpoints:

```
GET  /internal/v1/tags                          catálogo do que existe
POST /internal/v1/leads/{id}/tags               aplica tags ao lead
```

**A trava mais importante desta etapa:** a Automação só aplica tag **que já existe no catálogo**.
Ela **nunca cria tag**. Tag inexistente é recusada com erro claro, nomeando a tag recusada.

Sem essa regra, em duas semanas o catálogo tem "orçamento", "Orçamento", "orcamento" e "ORÇAMENTO",
e os filtros da tela param de significar alguma coisa. Quem cria tag é gente, na tela de Tags.

- Aplicar tag que o lead já tem é sucesso, não erro — a Automação repete chamada.
- A aplicação por IA é **auditável**: precisa dar para saber depois que foi a Automação, e não uma
  pessoa, que colocou aquela tag.

## Bloco 5 — O que vale para os quatro

- **Autorização no caso de uso**, `hasRole('SERVICO')`, como o resto do `/internal/v1`.
- **Contexto de serviço:** use a ponte criada na correção do `#reset` (`ContextoDeServico`), que agora
  abre RLS e autoridade Spring juntos. Não replique nem contorne.
- **OpenAPI:** o `OpenApiIT` conta operações. Ele vai quebrar — atualize a contagem junto, no mesmo
  commit, e não num commit de conserto depois.
- Nenhum endpoint desta etapa devolve dado de outro cliente, de outro canal, ou histórico completo de
  conversa. Se a Automação precisar disso, é outra conversa e outra etapa.

---

## Verificação

- Backend: `./mvnw clean verify` **com testes**, reator inteiro.
- Teste de que cada endpoint recusa quem não tem `ROLE_SERVICO`.
- Teste de que `em-andamento` não devolve atendimento encerrado, e de que o filtro por data corta.
- Teste de que o limite de paginação é imposto pelo servidor mesmo com pedido maior.
- Teste de que a mesma chamada de lembrete, repetida, gera **um** lembrete.
- Teste de que lembrete em atendimento sem responsável é recusado.
- Teste de que **tag fora do catálogo é recusada**, e de que reaplicar tag existente é sucesso.
- Teste de que o resumo sobrescreve e respeita o limite de tamanho.
- Documente os quatro contratos onde o Dylan lê — o Swagger é a fonte da verdade dele.
