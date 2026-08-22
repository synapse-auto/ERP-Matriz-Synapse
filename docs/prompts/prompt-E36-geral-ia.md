# Prompt E36 — Automação: completar a aba Geral · IA

> Leia `AGENTS.md`, `frontend/AGENTS.md` e `design/TOKENS.md`. Entrega em 25/08.
> Commite e faça push por bloco.
> Ao encerrar: `cd backend && ./mvnw clean verify` e o **número da run** do CI.

---

## ⚠️ NÃO DESPACHAR AINDA — quatro decisões abertas

Esta etapa toca dado que, uma vez preenchido pelo cliente, fica caro de mudar. As quatro perguntas
abaixo precisam de resposta antes de virar trabalho. A recomendação do arquiteto está em cada uma;
responder "concordo" já libera.

**1. Escala do CSAT — bloqueia o Bloco 2.**
O banco guarda `nota SMALLINT CHECK (nota BETWEEN 1 AND 5)`. O protótipo mostra "9,4 / 10" e a tela
de Equipe diz "escala 0-10". A mensagem de avaliação usa `{nota}` como link.
*Recomendação:* decidir com o cliente qual pergunta a Automação faz ao consumidor final. Sem
resposta dele, **manter 1–5** e corrigir o protótipo — mudar o banco depois, com avaliação real
dentro, é migração de dado; mudar agora, com o sistema vazio, é um `ALTER`.

**2. Rotinas pré-definidas — bloqueia o Bloco 4.**
A tabela que existe é por **papel**, e o protótipo mostra rotina por **atendente**:
```sql
CREATE TABLE horario_trabalho (
    aplicavel_a VARCHAR(20) NOT NULL,  -- 'IA' ou um valor de papel_usuario
    dia_semana  dia_semana NOT NULL,
```
Atender o protótipo exige schema novo (`atendente_id` × `dia_semana`).
*Recomendação:* é o item mais caro da etapa e o menos usado no dia 1. Deixar para a fase 2, por
escrito ao cliente. Se entrar, o Bloco 4 vale; se não, remova-o do prompt antes de despachar.

**3. `mensagem_festiva` — existe no banco, não tem aba no protótipo.**
*Recomendação:* confirmar que está fora da primeira entrega e registrar em `docs/09`, para parar de
contar como dívida.

**4. "Preenchimento automático" — não tem onde morar.**
*Recomendação:* chave em `configuracao_automacao` com `tipo = 'BOOLEAN'` e `descricao` preenchida.
Não criar tabela para um interruptor.

---

## Contexto

A aba **Geral · IA** existe e entrega dois dos cinco blocos do protótipo: os quatro cards de
telemetria e a lista de parâmetros chave/valor
(`frontend/src/components/automacao/pagina-automacao.tsx`). Faltam os outros três.

Vale a mesma regra da E35, e ela está escrita na `V7__automacao_config.sql`:

```sql
-- O CRM configura a automacao; nao a executa (RN-CRM-07).
```

> **Não construa agendador nem disparo.** Esta etapa é cadastro e leitura.

## Bloco 1 — Atendentes disponíveis, com alternador independente da presença

O protótipo mostra um alternador por atendente, com "4 de 6 online" ao lado — **presença e
disponibilidade para a IA são coisas separadas na tela.** No código, não são:

```java
// EquipeRepositorioJdbc.atualizarPresenca
INSERT INTO disponibilidade_atendente_ia(atendente_id, disponivel_para_ia)
SELECT id, ? FROM usuario WHERE id = ? AND papel = 'ATENDENTE'
ON CONFLICT (atendente_id) DO UPDATE SET disponivel_para_ia = EXCLUDED.disponivel_para_ia
```

A flag é escrita junto com a presença, e só para quem é `ATENDENTE`. Hoje **não existe** "atendente
online, fora do rodízio da IA" — quem fica ONLINE entra no rodízio, sem escolha. Está registrado no
`docs/14` e o protótipo confirma que é requisito, não capricho.

- Endpoint para alternar `disponivel_para_ia` de um atendente, **sem** alterar `status_presenca`.
- Ficar OFFLINE continua tirando da lista da IA — presença ausente vence disponibilidade marcada.
  O contrário não vale: ficar ONLINE **não** liga a flag sozinho depois desta etapa.
- Migrar o comportamento sem deixar buraco: atendentes que hoje dependem do gatilho de presença não
  podem sair do rodízio em silêncio na subida. Diga no relatório o que fez com os registros
  existentes.
- A tela mostra presença (o ponto verde e o "4 de 6 online") **e** o alternador, como coisas
  distintas.

> **Ponto de parada.** `GET /internal/v1/atendentes/disponiveis` filtra por
> `disponivel_para_ia = TRUE AND ativo AND papel = 'ATENDENTE' AND status_presenca = 'ONLINE'`. Se
> separar os dois conceitos mudar quem esse endpoint devolve de um jeito que você não consiga
> preservar, **pare e avise.** É a lista que decide para quem a IA entrega lead, e os atendentes
> trabalham por comissão.

## Bloco 2 — Avaliação por atendimento

*Depende da decisão 1.*

Configurável: ligado/desligado, quanto tempo após finalizar (número + Minutos/Horas), e a mensagem
com `{nome}` e `{nota}`.

- Sem tabela própria hoje. Use `configuracao_automacao` (chave/valor tipado, com `valor_min` e
  `valor_max`) em vez de criar tabela para três campos — a coluna existe justamente porque
  *"parametro de tempo com valor absurdo derruba a operacao em silencio"*.
- Se concluir que não cabe em chave/valor, **relate a razão** antes de criar migration.
- `{nota}` é link de avaliação: o formato depende da decisão 1. Não invente escala.
- Placeholders validados na gravação, no mesmo mecanismo da E35 — não crie um segundo.

## Bloco 3 — Recursos de IA

Dois interruptores: **resumo automático por IA** e **preenchimento automático**.

- Resumo já tem casa: `configuracao_resumo_ia`, singleton com `ativo`, `gatilho` e
  `quantidade_mensagens`. Exponha o que existe; não duplique em `configuracao_automacao`.
- Preenchimento automático: conforme a decisão 4.
- Os dois precisam ser legíveis pelo n8n em `/internal/v1` — interruptor que o painel liga e a
  Automação não lê é interruptor decorativo.

## Bloco 4 — Rotinas pré-definidas

*Só se a decisão 2 for "entra". Caso contrário, remova este bloco antes de despachar.*

Rotina nomeada ("Sábado — Plantão"), dias da semana ativos, e quais atendentes valem nela.

- Schema novo: rotina × dia da semana × atendente. `horario_trabalho` é por papel e não serve.
- Defina explicitamente a precedência entre rotina e o alternador do Bloco 1. Duas fontes de verdade
  para "quem está disponível" sem regra de precedência é incidente comercial esperando data.

## Bloco 5 — Quem escreve a telemetria

Os quatro cards leem `status_automacao_telemetria`, um singleton. Descubra e relate **quem atualiza
essa linha hoje**. Se ninguém atualiza, a tela mostra zero — e zero parece dado, não parece ausência.

Se for o n8n, documente o contrato em `docs/16`. Se for o CRM, diga onde. Se for ninguém, é achado
de etapa e vai no relatório como item próprio — **não invente um job para preencher.**

---

## Testes — a proteção nasce com um teste que a viola

- Alternar `disponivel_para_ia` não altera `status_presenca`, e vice-versa.
- Atendente ONLINE com a flag desligada **não** aparece em `/internal/v1/atendentes/disponiveis`.
- Atendente OFFLINE com a flag ligada **não** aparece.
- Ficar ONLINE depois desta etapa **não** liga a flag sozinho.
- Autorização em cada rota nova: papel sem permissão → `403`, sem escrita.
- Tempo de avaliação fora da faixa → `422`, sem escrita.
- Placeholder desconhecido na mensagem de avaliação → `422`, pelo mesmo mecanismo da E35.
- Interruptores de IA: o que o painel grava é o que `/internal/v1` devolve.
- Testes pelo controller real, não pelo caso de uso.

## Definição de pronto

- [ ] As quatro decisões respondidas **antes** do primeiro commit
- [ ] Disponibilidade para a IA independente da presença, com a migração dos registros explicada
- [ ] Avaliação por atendimento configurável, na escala decidida
- [ ] Resumo e preenchimento automático legíveis pelo n8n
- [ ] Rotinas — ou entregues, ou removidas do escopo por decisão registrada
- [ ] Quem escreve `status_automacao_telemetria` respondido no relatório
- [ ] Nenhum literal de UI, nenhuma cor hardcoded
- [ ] Os testes acima
- [ ] CI verde com **número da run**

## No relatório

1. As quatro decisões, como foram respondidas e por quem.
2. O que aconteceu com os registros de `disponibilidade_atendente_ia` já existentes.
3. Quem escreve a telemetria.
4. Variável nova no Dokploy: expectativa **nenhuma**.
5. SHA final — `SYNAPSE_IMAGE_TAG` é fixado por commit, nunca `latest`.

---

## Fora desta etapa

Follow-up e fidelização (E35). `mensagem_festiva`. Execução, varredura ou disparo de qualquer
regra — isso é do n8n, por RN-CRM-07.
