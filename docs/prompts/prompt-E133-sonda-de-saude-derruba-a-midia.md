# Prompt E133 — A sonda de saúde derruba a entrada de mídia

> Leia `AGENTS.md`, `CLAUDE.md` e `docs/13-estado-do-projeto.md`.
> Branch própria (`fix/sonda-de-saude-derruba-midia`) e PR. **Sem merge, sem deploy.**
> Backend + configuração. **Sem migration, sem frontend, sem contrato novo.**
> `./mvnw -pl crm-app -am verify` na raiz de `backend/`.

**Incidente aberto em produção.** 50 mídias de cliente — fotos, áudios e documentos — perdidas em
três dias (31/08: 23, 01/09: 18, 02/09: 9). Não aparecem na conversa, não viram erro na tela, não
voltam.

---

## A causa, provada em produção — não reinvestigue

`GET /health/critical` em produção, agora:

```json
{"nome":"canal","status":"DOWN","severidade":"CRITICO",
 "detalhe":"provedor indisponivel: UnknownContentTypeException"}
```

E o mesmo endpoint da Meta, chamado à mão com o token da instância, responde **200 com
`content-type: application/json; charset=UTF-8`**. A Meta está saudável. O defeito é nosso.

### A linha

`MetaCloudApiAdapter.consultarIdentidadeDoCanal`, linha 151:

```java
.retrieve().body(JsonNode.class);
```

É a **única** chamada do arquivo que pede `JsonNode`. Todas as outras leem `String` e convertem à mão
com o `ObjectMapper` injetado — `buscarTemplates` e `buscarMidiaRecebida` fazem
`.body(String.class)` seguido de `json.readTree(...)`.

O `RestClient` é montado a partir do `RestClient.Builder` injetado (linha 93) e não tem converter que
desserialize direto em `JsonNode`. `UnknownContentTypeException` é exatamente isso. **Essa linha
nunca funcionou.**

### A cadeia

```
sonda de saúde a cada 30s (AgendadorDaSaudeCritica, SAUDE_INTERVALO_MONITORAMENTO=30s)
  → verificarAutenticacao → consultarIdentidadeDoCanal → UnknownContentTypeException
  → executeSupplier registra falha no breaker canal-meta-cloud
  → breaker abre (minimum-number-of-calls 10, failure-rate 50%)
  → wait-duration-in-open-state = 30s = MESMO intervalo da sonda
  → half-open recebe a sonda, que falha de novo e reabre. Nunca fecha.
  → enviar() e baixarMidiaRecebida() usam O MESMO breaker
  → mídia que chega na janela aberta gasta as 5 tentativas contra o disjuntor e esgota
```

O envio sobrevive porque a outbox retenta sem limite. A entrada morre porque tem orçamento fixo.

### Por que ninguém viu

Três coisas esconderam, e as três entram no conserto:

1. `verificarAutenticacao` tem `catch (RuntimeException e)` que devolve
   `recusada("provedor indisponivel: " + e.getClass().getSimpleName())` e **não loga nada**. A
   exceção real morreu ali.
2. O breaker compartilhado espalhou o dano para uma operação que não tinha nada a ver.
3. O `ultimo_erro` das mídias diz `circuit breaker aberto` — aponta o sintoma, nunca a causa.

Confirme os quatro pontos no código antes de mexer e diga no relatório.

---

## Bloco 1 — A causa

Em `consultarIdentidadeDoCanal`, leia `String.class` e converta com o `ObjectMapper` já injetado,
**exatamente como `buscarTemplates` e `buscarMidiaRecebida` fazem**. Não registre converter novo, não
mexa no `RestClient.Builder`: o padrão do arquivo já existe e é o certo — foi essa linha que saiu
dele.

Resposta ilegível continua virando `recusada(...)`, não exceção que suba.

---

## Bloco 2 — Sonda de saúde não abre o disjuntor do tráfego

Observar o canal é diagnóstico, não operação de negócio. Uma sonda quebrada **nunca** pode impedir
mensagem de cliente de entrar.

O precedente já está no arquivo, linha 69: *"Listar/criar template nao pode abrir o breaker do envio
— a aba Atendimentos continua."* Foi por isso que existe o `canal-meta-cloud-templates`. O mesmo
argumento vale aqui, com consequência pior, e nunca foi aplicado.

`verificarAutenticacao` passa a ter disjuntor próprio. Configure a instância em `application.yml`
ao lado das duas que já existem.

E o `catch (RuntimeException)` **para de engolir**: logue a exceção real, com stack, em nível de
`WARN`. O texto devolvido ao `/health/critical` pode continuar curto; o log é que precisa dizer o que
houve. Foi a ausência dele que custou dias.

---

## Bloco 3 — Enviar e baixar mídia são disjuntores diferentes

Hoje `enviar()` (linha 161) e `baixarMidiaRecebida()` (linha 651) compartilham `canal-meta-cloud`.
São operações com falhas, volumes e consequências diferentes: uma rajada de recusa no envio não pode
apagar a foto que o cliente acabou de mandar, e vice-versa.

Separe. Instância nova em `application.yml`, no padrão das existentes.

**Não** afrouxe os limiares para "resolver" — o problema nunca foi o disjuntor estar sensível demais,
foi ele proteger três coisas ao mesmo tempo.

---

## Bloco 4 — Disjuntor aberto não consome tentativa

`ProcessadorDeWebhookEntradaOperacoes`, linha 254: `int tentativasFeitas = pendente.tentativas() + 1`
e, na 256, esgota ao chegar em `maximoDeTentativas`. Hoje uma tentativa que **nem falou com a Meta**
gasta orçamento igual a uma que falou e falhou.

Disjuntor aberto é "tente de novo mais tarde", não "falhou". A linha volta para a fila com
`proxima_tentativa_em` adiada e **sem** incrementar `tentativas`.

`baixarMidiaRecebida` hoje sinaliza isso com `IllegalStateException` e uma mensagem de texto.
**Não faça o processador comparar string** — crie um tipo de exceção próprio para "provedor
temporariamente indisponível" e trate por tipo. Comparação de mensagem quebra no dia em que alguém
reescrever o texto.

**Guarda obrigatória contra fila eterna:** sem contar tentativa, uma linha poderia ficar para sempre
se o disjuntor nunca fechasse. Estabeleça um **prazo absoluto** a partir de `recebido_em` — depois
dele a linha esgota, mesmo por disjuntor. A URL de mídia da Meta expira de qualquer forma; retentar
um dia depois não recupera nada. Escolha o prazo, justifique no relatório, e deixe configurável.

---

## Bloco 5 — As variáveis que não existem na stack

`WEBHOOK_MAX_TENTATIVAS` é lida em `application.yml` (`synapse.canal.webhook.maximo-de-tentativas`)
mas **não está declarada em `docker/dokploy-stack.yml`** — conferido em produção: o container não a
recebe, e preencher no painel do Dokploy não tem efeito nenhum. Provavelmente
`SAUDE_INTERVALO_MONITORAMENTO` está no mesmo caso; **verifique**.

Declare as duas na stack com **default seguro** (`${VAR:-valor}`, nunca `:?obrigatoria` — a regra do
`AGENTS.md` para recurso que pode ficar no default), e acrescente ao `.env.example` e à tabela do
`README`. Faça o mesmo para as instâncias novas de disjuntor, se elas ganharem variável.

No relatório, item próprio: **"ação necessária no Dokploy antes do próximo deploy"**, com nome e
valor de exemplo de cada variável nova.

---

## Bloco 6 — O que NÃO fazer

- Não mexa no `RestClient.Builder` nem registre converter novo. O arquivo já tem o padrão certo.
- Não afrouxe `failure-rate-threshold`, `minimum-number-of-calls` nem `wait-duration` para esconder
  o sintoma.
- Não mexa no download de mídia em si, no MinIO, na URL assinada nem no tradutor de webhook. A
  **E132** está em curso e toca o tradutor e o `switch` de envio do mesmo arquivo — mantenha o diff
  longe disso.
- Não crie rotina que limpe ou reprocesse `webhook_entrada`. A recuperação das 50 linhas é operação
  manual, com o disjuntor fechado, e é decisão de quem opera.
- Nenhum arquivo de frontend.

---

## Bloco 7 — Testes

- `consultarIdentidadeDoCanal` com resposta `application/json` válida → `aceita()`. **É o teste que
  falharia hoje**, e prova a causa.
- Resposta com corpo ilegível → `recusada(...)`, sem exceção subindo, **e com log da exceção real**.
- Sonda de saúde falhando **não** abre o disjuntor de envio nem o de mídia: com N falhas seguidas em
  `verificarAutenticacao`, `enviar()` e `baixarMidiaRecebida()` continuam passando. Este é o teste
  central da etapa.
- Falha de envio não abre o disjuntor de mídia, e vice-versa.
- `webhook_entrada`: com o disjuntor de mídia aberto, a linha **não** incrementa `tentativas` e
  continua elegível; passado o prazo absoluto, esgota.
- Falha real da Meta (4xx/5xx no download) **continua** consumindo tentativa — o Bloco 4 vale só para
  disjuntor aberto.
- `AnexoMidiaIT` e `CanalWhatsAppIT` continuam verdes sem edição.

## Verificação

```
./mvnw -pl crm-app -am verify
```
Spotless, ArchUnit e a contagem de endpoints do OpenAPI verdes.

## Relatório

1. Os quatro pontos do diagnóstico, confirmados no código com arquivo e linha.
2. Como `consultarIdentidadeDoCanal` ficou, e a confirmação de que segue o padrão dos vizinhos.
3. Os nomes das instâncias de disjuntor e qual operação cada uma protege.
4. O tipo de exceção novo, e por que tratar por tipo e não por mensagem.
5. O prazo absoluto escolhido no Bloco 4 e a justificativa.
6. As variáveis declaradas na stack, no `.env.example` e no `README`, em item próprio de ação no
   Dokploy.
7. Confirmação de que o tradutor de webhook e o `switch` de envio não foram tocados (E132).
