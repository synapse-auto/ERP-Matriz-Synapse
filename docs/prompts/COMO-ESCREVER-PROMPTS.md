# Como escrever um prompt de etapa

Formato usado da E19 em diante. Nasceu de tentativa e erro — cada seção existe porque a
ausência dela custou uma etapa.

---

## 1. O ciclo

```
arquiteto escreve o prompt
      ↓
agente executa e reporta nos sete itens do AGENTS.md
      ↓
arquiteto CONFERE o relatório contra o repositório
      ↓
decide o que ficou aberto e calibra o próximo
```

O passo que mais se pula é o terceiro. **Relatório não é evidência.** Neste projeto já houve
relatório correto descrevendo código que não fazia o que dizia, e relatório que omitiu o achado
mais importante porque não era escopo. Abra o arquivo.

## 2. Antes de escrever: leia o código

Um prompt escrito a partir do sintoma produz correção de sintoma. Um prompt escrito a partir do
código produz correção de causa — e quase sempre descobre que o problema é maior.

Três exemplos desta base:

- **E27.** O sintoma era "chegou mensagem de outro número". Lendo o `WebhookCanalController`,
  o ponto decisivo não era *filtrar*, era **onde** filtrar: `agendarRepasse` e `registrarSeNovo`
  gravam o payload cru **antes** do processamento, então um filtro no processador já teria
  gravado a conversa de terceiros em dois lugares.
- **E30.** O relatório da E28 dizia "áudio não envia". A causa era `caption` num payload de
  áudio, que a Meta não aceita — três linhas, invisíveis pelo sintoma.
- **E32.** O sintoma era `get(0)`. Lendo o pipeline, `webhook_entrada.id_externo` é PRIMARY KEY
  e chaveia o **POST**, não a mensagem. Consertar a perda sem tratar isso **criaria**
  duplicação.

Se você não sabe dizer qual arquivo e qual linha, o prompt ainda não está pronto.

## 3. Anatomia

### Cabeçalho — sempre igual

```
> Leia `AGENTS.md`. Entrega em 25/08.
> Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.
```

O número da run não é burocracia: "CI verde" sem número já significou "verde na minha máquina".

### Contexto — com o código dentro

Cole o trecho real, com o caminho do arquivo. Não descreva o defeito: **mostre**.

```java
private Optional<JsonNode> primeiraMensagem(String payloadCru) {
    ...
            ? Optional.of(mensagens.get(0))     // <- só a primeira
```

Um comentário de uma linha apontando o ponto vale mais que três parágrafos de explicação.

Diga também a **consequência de negócio**, não a técnica. "O cliente manda três mensagens, o CRM
grava uma e responde 200" move mais que "iteração incompleta do array".

### Blocos numerados

Um bloco = um commit = uma coisa que dá para verificar sozinha. Se um bloco não cabe num
commit, são dois blocos.

Cada bloco diz **o que**, **onde** e **por que aqui e não ali**. O "por que" é o que impede o
agente de resolver no lugar errado.

### Proibições explícitas

O agente vai encontrar caminhos mais curtos. Feche os errados **antes**:

> **Não explode o payload em uma linha por mensagem.** A `V17` guarda o payload cru byte a byte
> justamente para permitir reconferir o HMAC.

> **Não altere o comportamento de payload MISTO.** Não é otimização pendente: é sinal de
> configuração errada na Meta.

Sem isso, alguém "melhora" a decisão anterior sem saber que era decisão.

### Ponto de parada

Quando a premissa pode não se sustentar, mande parar — não escolher:

> **Ponto de parada.** Se ao implementar você concluir que o fail-closed conflita com a regra de
> precedência, **pare e me avise antes de escolher outro caminho.** Não troque para fail-open por
> conta própria.

Já evitou duas entregas erradas.

### Testes — "a proteção nasce com um teste que a viola"

Liste os testes, não a cobertura. E liste **o negativo**:

- O que tem que acontecer
- O que **não** pode acontecer, item por item ("não cria lead, **não grava `webhook_entrada`**,
  **não enfileira na outbox**")
- Teste de **ponto de entrada**, chamando o controller como o runtime chama — não o método
  interno

O caso mais caro do projeto: a RLS estava escrita, o teste passava, e não protegia nada. Só o
teste negativo expôs.

### Definição de pronto

Checklist curto, verificável, sem adjetivo. Cada item tem que ser respondível com sim ou não
olhando o repositório.

### No relatório

Peça explicitamente o que costuma sumir:

1. **Variável nova no Dokploy** — e diga qual é a expectativa ("nenhuma"). Se precisou de uma,
   item próprio, com nome e valor de exemplo
2. As decisões que ele teve que tomar sozinho, com o porquê
3. O SHA final — `SYNAPSE_IMAGE_TAG` é fixado por commit
4. Números, quando houver ("quantas mensagens já foram perdidas, entre que datas")

### Fora desta etapa

Nomeie o que você **sabe** que está aberto e não quer agora. Sem isso o agente ou faz, ou
descobre e não conta.

## 4. Erros do arquiteto que custaram etapa

Registrados porque se repetem:

- **Restrição impossível.** Escrevi "trocar de cliente não pode exigir rebuildar a imagem" sem
  ter lido `ConfiguracaoDeInstanciaResources`, que lê do classpath na subida. Confira a premissa
  no código antes de transformá-la em requisito.
- **Aceitar plano de agente sem verificar.** Um plano propôs `--border: transparent`; a variável
  é usada em mais de vinte arquivos. Outro inventou uma tabela `configuracao_tema` que não
  existe. `git grep` antes de aprovar.
- **Escopo pela lista de sintomas.** A E28 virou três etapas porque o prompt tratou "não envia
  áudio" como um problema, e eram três.

## 5. Modelo em branco

```markdown
# Prompt EXX — <título curto e concreto>

> Leia `AGENTS.md`. Entrega em 25/08.
> Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## Contexto — <o defeito em uma frase de negócio>

<trecho de código real, com caminho do arquivo>

<por que ninguém percebeu até agora>

## Bloco 1 — <o que fica verdadeiro no fim>

- requisito
- requisito
- **por que aqui e não ali**

> **Não faça <caminho curto errado>.** <motivo, ligado a uma decisão anterior.>

> **Ponto de parada.** <quando parar e avisar em vez de decidir.>

## Bloco 2 — ...

## Testes — a proteção nasce com um teste que a viola

- caso positivo
- caso negativo, item por item
- teste de ponto de entrada

## Definição de pronto

- [ ] ...
- [ ] CI verde com **número da run**

## No relatório

1. Variável nova no Dokploy — expectativa: nenhuma
2. Decisões tomadas sozinho, com o porquê
3. SHA final

---

## Fora desta etapa

<o que está aberto e não é agora>
```

---

Prompts anteriores nesta pasta servem de exemplo. Os mais completos: **E27** (isolamento do
canal), **E30** (áudio e registro da Automação) e **E32** (payload com várias mensagens).
