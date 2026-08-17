# Prompt E27b — O telefone canônico passa a garantir o código de país

> Leia `AGENTS.md`. Entrega em 25/08.
> Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## O defeito

A E26 centralizou a normalização de telefone, mas `TelefoneCanonico.normalizar` apenas remove
não-dígitos, e o Javadoc assume a premissa: *"O codigo do pais nao e inferido: ele faz parte do
dado recebido."* A `V24__telefone_canonico.sql` aplica a mesma regra.

| Origem | Entrada | Canônico hoje |
|---|---|---|
| Webhook (Meta) | `5561999999999` | `5561999999999` |
| Tela | `(61) 99999-9999` | `61999999999` |

**Dois leads. Dois donos. A comissão duplicada que o Bloco 1 da E26 existia para impedir.**
O índice único `ux_lead_telefone` não alcança: são strings diferentes.

`TelefoneCanonicoTest` passa porque usa `+55 61 99999-9999`, que já traz o `55`. O formato que
um brasileiro digita sem pensar não está coberto. É o padrão do projeto outra vez — proteção
que existe, passa no teste, e não protege o caminho real.

---

## Bloco 1 — DDI padrão por configuração

**Decisão do arquiteto, já tomada: completar, não recusar.** Um atendente digitando
`(61) 99999-9999` é o caso comum, e transformar isso em erro de formulário troca um defeito de
dado por atrito diário. O DDI ausente passa a ser completado a partir de uma configuração da
instância — nunca de constante no código, porque o próximo filho pode não ser brasileiro.

Regra, por quantidade de dígitos após remover tudo que não é dígito:

| Dígitos | Interpretação | Ação |
|---|---|---|
| ≥ 12 | já tem código de país | mantém |
| 10 ou 11 | fixo ou celular com DDD, sem país | prefixa o DDI padrão |
| < 10 | não é telefone discável | rejeita |

**Limitação conhecida e aceita:** um número internacional de 11 dígitos (EUA, por exemplo)
seria prefixado por engano. O CRM atende uma fábrica em Brasília e a fonte de maior volume é a
Meta, que já envia com país. Registre isso no relatório; não tente resolver com heurística de
prefixo internacional.

**Onde mora o DDI padrão.** Configuração da instância, valor `55` para a Estrutural. Escolha
entre `configuracao_automacao` e propriedade da aplicação, e **justifique no item 3 do
relatório**. Duas restrições: se optar por variável de ambiente, ela entra no
`dokploy-stack.yml` com **default** (`${...:-55}`), nunca `:?obrigatoria` — variável nova
obrigatória já derrubou o deploy inteiro de uma instância; e o `.env.example` e a tabela do
`README.md` são atualizados na mesma etapa.

**Continua existindo um ponto único de normalização.** Hoje `Lead` e
`LeadNoCaminhoDeMensagemJdbc` chamam `TelefoneCanonico`. Se para conhecer o DDI o normalizador
precisar deixar de ser estático, é aceitável — mas o resultado não pode ser "cada caller lembra
de normalizar". Ou o normalizador é injetado e continua único, ou o domínio passa a **rejeitar**
telefone não canônico, transformando a regra em invariante. As duas soluções servem; escolha uma
e explique.

> **Ponto de parada.** Se ao implementar você encontrar caller que não tenha como alcançar a
> configuração sem quebrar a regra de dependência das camadas (`domain` não importa
> `infrastructure`), **pare e me avise** em vez de abrir exceção na arquitetura.

## Bloco 2 — Migration dos registros existentes

A V24 já removeu os não-dígitos. Falta prefixar o DDI nos que ficaram com 10 ou 11 dígitos.

**Esta migration pode criar colisão que a V24 não viu.** Um lead gravado como `61999999999`
vira `5561999999999` e pode colidir com um lead que já existe nesse formato — exatamente os
dois registros do mesmo cliente que motivaram a etapa. O `ux_lead_telefone` reprovaria, mas com
erro de constraint, que não diz quais leads são.

Repita o padrão da V24: **detecte antes de alterar**, e se houver colisão levante exceção
listando `id`, `nome` e `telefone` de cada par. **Não mescle, não apague, não escolha um
sobrevivente** — reconciliar conversa e comissão é decisão comercial.

O seed e o provisionamento passam a gravar já no formato completo.

## Bloco 3 — Rejeitar o que não é telefone

Menos de 10 dígitos deixa de ser aceito na entrada, com Problem Details (RFC 7807) e mensagem
que diga o formato esperado. Hoje `NULLIF(...)` transforma entrada sem dígito em `NULL`
silenciosamente; um `1234` viraria chave de lead.

Telefone ausente (`NULL`) continua válido — nem todo lead tem telefone.

## Testes — a proteção nasce com um teste que a viola

- **O teste que faltava na E26:** lead criado pela tela com `(61) 99999-9999` e mensagem
  recebida por webhook de `5561999999999` resolvem para **o mesmo lead**. Sem `+`, sem `55` na
  entrada da tela — é esse o caso que hoje falha.
- Entrada já com país (`+55 61 99999-9999` e `5561999999999`) continua estável, sem `55`
  duplicado.
- Número com 12 e com 13 dígitos passa intacto.
- Entrada com menos de 10 dígitos é recusada com Problem Details.
- Migration: cenário com colisão real interrompe e lista os pares; cenário sem colisão
  completa e o índice único continua válido.
- DDI padrão diferente de `55` na configuração produz outro prefixo — prova que não há valor
  fixo no código.

## Definição de pronto

- [ ] DDI completado a partir de configuração da instância, sem valor fixo no código
- [ ] Ponto único de normalização preservado, ou invariante no domínio — com justificativa
- [ ] Limitação do número internacional de 11 dígitos registrada no relatório
- [ ] Migration prefixando os existentes, com detecção de colisão que interrompe e lista os pares
- [ ] Seed e provisionamento gravando no formato completo
- [ ] Entrada com menos de 10 dígitos recusada com Problem Details
- [ ] Os seis testes acima
- [ ] `.env.example` e tabela do `README.md` atualizados, se houver variável nova
- [ ] CI verde com **número da run**

## No relatório

Item próprio para **ação necessária no Dokploy antes do próximo deploy**, com nome e valor de
exemplo — ou a afirmação explícita de que nenhuma variável nova é necessária.

Diga onde o DDI padrão ficou e por quê.

Diga se a migration encontrou colisão e **quantas**. Se encontrou, liste os pares e devolva
para decisão.

---

## Antes de deployar — verificação no ambiente

A V24 nunca rodou em homologação, então ela e a migration desta etapa entram no mesmo deploy.
Duas colisões possíveis, uma por migration. Meça antes, no banco da instância:

```sql
-- colisões da V24: só remoção de não-dígitos
SELECT regexp_replace(telefone,'[^0-9]','','g') AS canonico, count(*),
       string_agg(format('%s | %s | %s', id, nome, telefone), '; ' ORDER BY id)
  FROM lead WHERE telefone IS NOT NULL
 GROUP BY 1 HAVING count(*) > 1;

-- colisões desta etapa: após completar o DDI
WITH c AS (
  SELECT id, nome, telefone,
         CASE WHEN length(regexp_replace(telefone,'[^0-9]','','g')) BETWEEN 10 AND 11
              THEN '55' || regexp_replace(telefone,'[^0-9]','','g')
              ELSE regexp_replace(telefone,'[^0-9]','','g') END AS canonico
    FROM lead WHERE telefone IS NOT NULL)
SELECT canonico, count(*), string_agg(format('%s | %s | %s', id, nome, telefone), '; ' ORDER BY id)
  FROM c GROUP BY 1 HAVING count(*) > 1;
```

Qualquer uma retornando linhas: **não deploye**. Migration que aborta faz o Flyway falhar no
boot, o backend não sobe, e a aba Atendimentos cai — a regra de precedência absoluta.
