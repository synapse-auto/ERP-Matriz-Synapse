# Prompt — Consertar o CI

> Tarefa de manutenção. O pipeline nunca passou em nenhuma das 12 etapas.
> Prioridade: **antes do deploy de homologação.**

---

O CI do GitHub Actions está vermelho nos dois jobs (0/2) desde o primeiro push. O workflow está em `.github/workflows/ci.yml` e nunca teve uma execução verde. Hoje há ~150 testes e todo o backend apoiados nele.

Preciso que você diagnostique e conserte.

## Ordem de investigação

### 1. Reproduza os comandos exatos do CI, não os do dia a dia

```
cd backend && mvn -B --no-transfer-progress clean verify
cd frontend && npm ci && npm run lint && npm run build
```

Três diferenças em relação ao uso normal, e cada uma já causou falha de CI em algum projeto:

- **`verify`, não `install`** — o `verify` roda `spotless:check`, que reprova formatação; o fluxo local costuma passar por cima disso
- **`mvn`, não `./mvnw`** — o workflow usa o Maven pré-instalado do runner, que pode ser outra versão que a do wrapper
- **`npm ci`, não `npm install`** — apaga `node_modules` e instala estritamente pelo lock

Se falhar aqui, você achou. Corrija e siga para o item 5.

### 2. Se passar local, procure diferença de ambiente

Suspeitos, em ordem de probabilidade para este projeto:

**Quebra de linha e Spotless.** O `.gitattributes` com `eol=lf` entrou na E00, mas arquivos criados antes dele podem ter entrado com CRLF. O Spotless do runner Linux reprova. Verifique com `git ls-files --eol` e normalize se necessário.

**Ordem de execução dos testes.** Este projeto já foi mordido **duas vezes** por isso: o `BootSemParticaoIT` passava por sorte de ordenação, e o `ignore-migration-patterns` dependia de qual suíte rodava primeiro no container compartilhado. A ordem no runner raramente é a mesma da máquina local. Rode com ordem aleatória local (`-Djunit.jupiter.testclass.order.default=...` ou equivalente) e veja se quebra.

**Docker/Testcontainers no runner.** O `ubuntu-latest` tem Docker, mas confirme que o Testcontainers está achando o socket e que não há limite de memória estourando com Postgres + a aplicação.

**Locale e timezone.** Testes que comparam data formatada quebram quando o runner está em UTC e sua máquina em `America/Sao_Paulo`.

### 3. Conserte a flakiness do `CanalWhatsAppIT`

Ela já apareceu **três vezes**, sempre descartada como "pré-existente, passa se rodar de novo". Isso agora é bloqueante, porque um CI que fica verde e vermelho aleatoriamente treina todo mundo a apertar "re-run" sem ler — e o dia em que o vermelho for real, ninguém nota.

É o mesmo mecanismo do `exige transacao ativa` que virou ruído de fundo por duas etapas e escondeu o bug de auto-invocação.

Flakiness de timing em teste de integração quase sempre é `Thread.sleep` ou espera fixa onde deveria haver espera por condição. Troque por `Awaitility` com timeout generoso e condição explícita.

**Não** use `rerunFailingTestsCount` do Surefire para mascarar. Isso é desligar o alarme.

### 4. Troque `mvn` por `./mvnw` no workflow

O wrapper existe justamente para o build usar a mesma versão do Maven em qualquer máquina. Usar o Maven do runner desperdiça essa garantia e introduz uma variável desnecessária.

### 5. Prove que o CI funciona

Depois de verde, **quebre de propósito** e confirme que ele reprova:

- Um teste falhando de propósito ⇒ job vermelho
- Formatação errada ⇒ Spotless reprova
- Violação de arquitetura ⇒ ArchUnit reprova

Reverta em seguida.

Este projeto tem sete casos documentados de proteção que existia e não protegia nada. Um CI que nunca reprovou nada é o oitavo candidato — e agora seria o mais caro, porque é a última linha antes de produção.

## Definição de pronto

- [ ] Os dois jobs verdes no GitHub Actions
- [ ] Causa raiz identificada e explicada, não contornada
- [ ] Flakiness do `CanalWhatsAppIT` corrigida na causa, sem retry
- [ ] Workflow usando `./mvnw`
- [ ] CI provado por falha proposital nos três mecanismos

Commit: `ci: corrige pipeline e flakiness de timing`.

Ao terminar, me diga qual era a causa e se ela existiria também no deploy — algumas diferenças de ambiente que quebram CI quebram produção pelo mesmo motivo.
