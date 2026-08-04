# Prompt — Consertar a flakiness e fechar o CI

> Tarefa curta. O build local está em 137/139; as 2 falhas são a mesma causa.

---

## O diagnóstico

`mvn -B clean verify` com Docker no ar e JDK 21 falha em exatamente dois testes:

```
CanalWhatsAppIT$Envio.envio_pontaAPonta_terminaEnviado:135
  Expected size: 1 but was: 0

CanalWhatsAppIT$Infra.trocaDeNumero_preservaHistoricoEMensagemEmTransito:376
  expected: "ENVIADO" but was: "PENDENTE"
```

Os dois são o mesmo defeito: **a asserção acontece antes de o `PublicadorDaOutbox` ter drenado.** O publisher roda por `fixedDelay`; o teste verifica imediatamente. Corrida clássica — às vezes o publisher chega primeiro e passa, às vezes não.

Era essa a flakiness intermitente que apareceu três vezes e foi descartada como "pré-existente, passa se rodar de novo".

## 1. Corrigir com espera por condição

Substitua a asserção imediata por espera até a condição, com timeout generoso:

```java
Awaitility.await()
    .atMost(Duration.ofSeconds(10))
    .pollInterval(Duration.ofMillis(100))
    .untilAsserted(() -> assertThat(...).isEqualTo("ENVIADO"));
```

Se o Awaitility ainda não for dependência, acrescente ao escopo de teste.

**Não** use `Thread.sleep` fixo — troca uma corrida por outra, mais lenta.
**Não** use `rerunFailingTestsCount` do Surefire — isso é desligar o alarme.

Varra os outros testes que dependem do publisher ou de qualquer job agendado e aplique o mesmo tratamento. Se um teste espera efeito assíncrono, ele espera por **condição**, nunca por tempo.

## 2. Trocar `mvn` por `./mvnw` no workflow

O `ci.yml` chama `mvn`, que usa o Maven pré-instalado do runner em vez do wrapper. O wrapper existe justamente para o build ser idêntico em qualquer máquina — usar outro Maven desperdiça a garantia e introduz uma variável desnecessária.

## 3. Push e verificar o CI

Depois de verde local:

```
git push origin main
```

Abra a aba Actions. Dois cenários:

- **Verde** — a causa era ambiente local (JDK 17 e Docker parado) mais a flakiness. Encerrado.
- **Vermelho** — agora sim há diferença real entre sua máquina e o runner, e ela está isolada. Me traga o erro.

## 4. Provar que o CI reprova

Com o pipeline verde, quebre de propósito e confirme que ele pega:

- Um teste falhando ⇒ job vermelho
- Formatação errada ⇒ Spotless reprova
- Violação de arquitetura ⇒ ArchUnit reprova

Reverta em seguida.

Este projeto tem sete casos documentados de proteção que existia e não protegia nada. Um CI que nunca reprovou nada seria o oitavo — e o mais caro, por ser a última linha antes de produção.

## Definição de pronto

- [ ] `mvn -B clean verify` em 139/139
- [ ] Nenhuma espera por tempo fixo em teste de efeito assíncrono
- [ ] Workflow usando `./mvnw`
- [ ] Os dois jobs verdes no GitHub Actions
- [ ] CI provado por falha proposital

Commit: `fix: espera por condicao no lugar de corrida com o publisher`.
