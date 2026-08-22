# Prompt E36b — o backfill que falta antes do deploy da E36

> Leia `AGENTS.md`. Entrega em 25/08.
> Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## Contexto — a E36 está correta e, do jeito que está, derruba a operação de manhã

A E36 (`d4dfb63`) separou presença de disponibilidade para a IA. O comportamento novo está certo e
testado. O problema é o **estado que fica no banco quando a imagem subir**.

O código antigo escrevia a flag junto com a presença:

```java
// EquipeRepositorioJdbc.atualizarPresenca — comportamento ANTERIOR à E36
INSERT INTO disponibilidade_atendente_ia(atendente_id, disponivel_para_ia)
SELECT id, ? FROM usuario WHERE id = ? AND papel = 'ATENDENTE'
ON CONFLICT (atendente_id) DO UPDATE SET disponivel_para_ia = EXCLUDED.disponivel_para_ia
//        ^ s == StatusPresenca.ONLINE  →  grava FALSE quando o atendente sai
```

Todo atendente que **encerrou o expediente** teve `disponivel_para_ia = FALSE` gravado. Deploy é
feito fora do horário comercial: a base inteira está em `FALSE` no momento em que a imagem sobe.

O relatório da E36 diz:

> ✅ Tabela `disponibilidade_atendente_ia` já existia; nenhuma linha foi reescrita ou perdida.

**Preservar é exatamente o que causa a falha.** Amanhã o atendente entra, fica ONLINE, e a flag não
liga mais sozinha — porque esse era o ponto da E36. `GET /internal/v1/atendentes/disponiveis`
devolve lista vazia, a IA não tem para quem transferir, e ninguém entende o motivo. Dentro da janela
08:00–18:30, que é a regra de precedência absoluta do projeto.

---

## Bloco 1 — Backfill, como migration

Migration nova (`V34`), não script avulso: precisa valer para qualquer filho provisionado depois,
e precisa rodar no deploy sem alguém lembrar.

```sql
INSERT INTO disponibilidade_atendente_ia (atendente_id, disponivel_para_ia)
SELECT id, TRUE FROM usuario WHERE ativo AND papel = 'ATENDENTE'
ON CONFLICT (atendente_id) DO UPDATE SET disponivel_para_ia = TRUE, atualizado_em = now();
```

- Escreva no comentário da migration **por que** ela existe: o valor preservado é `FALSE` para quem
  estava fora do expediente, e sem isto ninguém recebe lead da IA no primeiro dia.
- Atendente **inativo** fica de fora: usuário desativado não entra em rodízio.
- Ligar todos é decisão consciente — é o estado que o protótipo mostra e o que a operação tinha
  antes, na prática, durante o expediente. **Não** tente adivinhar quem "deveria" estar ligado a
  partir de `status_presenca` ou de `atualizado_em`: o dado não distingue "desligado de propósito"
  de "desligado por sair do sistema".

## Bloco 2 — O atendente novo precisa de um default

Se a presença não escreve mais nessa tabela, **quem cria a linha de um atendente cadastrado
amanhã?** Se ninguém, ele nasce sem linha, não aparece em `/internal/v1/atendentes/disponiveis`, e
nunca recebe lead da IA — até um gestor descobrir o alternador.

É o mesmo defeito do Bloco 1 com data futura, e igualmente silencioso.

- A criação de usuário com papel `ATENDENTE` passa a criar a linha com **`TRUE`**.
- Mudança de papel **para** `ATENDENTE` também cria a linha, se não existir.
- Desativar usuário continua desligando a flag — esse caminho já existia em
  `EquipeRepositorioJdbc.desativar` e não pode ter sido perdido na E36. Confirme.
- Mudança de papel **saindo** de `ATENDENTE`: decida o que acontece com a linha e **relate**.
  Manter a linha órfã é aceitável (o filtro por papel já protege a leitura); apagar também. O que
  não vale é ninguém ter pensado.

> **Ponto de parada.** Se `disponivel_para_ia = TRUE` como default de atendente novo conflitar com
> alguma regra que você encontre no caminho, pare e avise. Não escolha `FALSE` por ser "mais
> seguro": mais seguro aqui é o atendente **não** receber lead, o que é perda comercial silenciosa.

## Bloco 3 — A confirmação que a E36 não trouxe

O prompt da E36 pedia, e o relatório não respondeu:

> *"Confirme que o teste de separação **falha** quando você reintroduz a escrita da flag junto com a
> presença."*

`DisponibilidadeParaIaIT.presencaOnlineNaoLigaFlag` é **o** teste daquela etapa. Sem essa
confirmação não se sabe se ele reprova a regressão ou se passa por acidente.

- Reintroduza temporariamente a escrita da flag no caminho de presença.
- Rode o teste. Ele **tem** que falhar.
- Reverta e relate o que apareceu — status, valor, mensagem.

Faça o mesmo com os testes novos deste prompt.

---

## Testes

- Base com atendentes ativos e `disponivel_para_ia = FALSE` → depois da migration, todos `TRUE`.
- Atendente **inativo** com `FALSE` → continua `FALSE`.
- Atendente que já estava `TRUE` → continua `TRUE`, sem linha duplicada.
- Migration rodando duas vezes não quebra e não muda o resultado.
- Criar usuário `ATENDENTE` → linha criada com `TRUE`, e ele aparece em
  `/internal/v1/atendentes/disponiveis` quando ficar ONLINE.
- Criar usuário `SUBGESTOR` → **nenhuma** linha criada.
- Promover ATENDENTE a GESTOR e voltar: o comportamento decidido no Bloco 2, coberto por teste.
- Desativar atendente → sai da lista interna. Regressão do caminho que já existia.
- Pelo controller real, como a E35b e a E36 fizeram.

## Definição de pronto

- [ ] `V34` com o backfill e o comentário explicando o motivo
- [ ] Atendente inativo fora do backfill
- [ ] Default na criação de usuário e na mudança de papel para ATENDENTE
- [ ] Saída de papel decidida e relatada
- [ ] `desativar` continua desligando a flag
- [ ] Confirmação por mutação do `presencaOnlineNaoLigaFlag`
- [ ] Os testes acima, pelo controller
- [ ] CI verde com **número da run**

## No relatório

1. **Quantas linhas o backfill alterou** em homologação, e quantos atendentes ativos existem. Se os
   números não baterem, alguém está fora do rodízio e vale saber quem.
2. O resultado da mutação do Bloco 3 — o que falhou e como.
3. A decisão sobre a saída de papel.
4. **Os nomes dos testes novos, um por linha.** Não informe o total da suíte.
5. Variável nova no Dokploy: expectativa **nenhuma**.
6. O SHA final **e o SHA curto** — `SYNAPSE_IMAGE_TAG` usa a tag curta, nunca o hash de 40
   caracteres e nunca `latest`.

---

## Fora desta etapa

Avaliação por atendimento (escala do CSAT em aberto). Rotinas pré-definidas por atendente.
`mensagem_festiva`. Renomear `/internal/v1/automation-config/recursos-ia` para o padrão em
português — está anotado, mas contrato publicado não se mexe junto com correção urgente.
