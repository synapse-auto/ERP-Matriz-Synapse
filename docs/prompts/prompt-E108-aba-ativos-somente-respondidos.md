# Prompt E108 — Aba "Ativos": só o que já foi respondido

> Leia `AGENTS.md` e `CLAUDE.md`. Branch própria (`fix/aba-ativos-respondidos`) e PR.
> **Sem merge, sem deploy.** `./mvnw -pl crm-atendimento -am verify`. Sem migration, sem frontend.
>
> **Faça a branch a partir de `main` com o PR #37 já mergeado.** Esta etapa mexe nas mesmas
> constantes de `WHERE` que ele acabou de tocar. Se o #37 não estiver em `main`, **pare e avise.**

---

## O pedido

O cliente definiu as quatro abas. Três já estão certas no código; uma não:

| Aba | Definição | Situação |
| --- | --- | --- |
| Potenciais | clientes conversando com a IA — visão global | já correta |
| Pendentes | transferidos ao atendente e **ainda não respondidos** | já correta |
| Todos | os atendimentos do próprio atendente, sem os potenciais | correta desde o #37 |
| **Ativos** | transferidos ao atendente **e já respondidos** | **é o que esta etapa corrige** |

Todas as definições são **do ponto de vista do atendente**. Para gestor, subgestor e administrador
nada muda — as visões amplas continuam como estão.

## Bloco 1 — O que está errado hoje

`WHERE_ATIVOS`, em `PainelDeAtendimentosRepositorioJdbc`, é só:

```sql
EXISTS (SELECT 1 FROM atendimento visivel
         WHERE visivel.lead_id = a.lead_id
           AND visivel.status = 'EM_ATENDIMENTO'
           AND visivel.atendente_id = ?)
```

Não há nenhuma condição sobre quem falou por último. Resultado: **Ativos e Pendentes se sobrepõem** —
uma conversa em que o cliente escreveu por último aparece nas duas, e o mesmo lead é contado duas
vezes nos contadores do topo.

`WHERE_PENDENTES_PROPRIOS` já tem exatamente a condição que falta, com sinal invertido: ele exige que
a última mensagem seja do lead, via o `LEFT JOIN LATERAL` em `mensagem`. **Reaproveite essa mesma
construção** — não escreva uma segunda forma de descobrir quem falou por último, senão as duas abas
divergem no dia em que alguém mexer numa só.

Depois da correção, Ativos e Pendentes têm que ser **disjuntas**: nenhum lead pode aparecer nas duas.

## Bloco 2 — A armadilha: conversa sem mensagem nenhuma

Não escreva `remetente_tipo <> 'LEAD'`. Existe conversa **sem mensagem alguma**, e nela o
`remetente_tipo` é `NULL` — `NULL <> 'LEAD'` não é verdadeiro, então essa conversa sumiria das duas
abas.

E isso não é hipotético: `IniciarNovoContatoUseCase` abre atendimento em modo humano **sem primeira
mensagem** quando o atendente inicia um contato e a janela de 24h está fechada (ele fica esperando o
atendente escolher um template). Esse atendimento é do atendente, está `EM_ATENDIMENTO`, e não tem
mensagem nenhuma. Ele **tem** que aparecer em Ativos — é justamente uma conversa que precisa de ação.

Use `IS DISTINCT FROM`, que trata `NULL` como "diferente":

```sql
AND ultima_visivel.remetente_tipo IS DISTINCT FROM 'LEAD'
```

Confirme lendo o `IniciarNovoContatoUseCase` que esse caso existe mesmo, e diga no relatório.

## Bloco 3 — O que não muda

- **Pendentes** fica como está. Ela já estava certa; não mexa.
- **Potenciais** fica como está: global, sem filtro de usuário.
- **Todos** fica como o #37 deixou: leads do próprio atendente em qualquer estado — **inclusive os
  finalizados**, que continuam abaixo da divisória da E99 — mais as conversas em que ele é
  participante ativo. Ambos confirmados pelo cliente. Não estreite.
- **Gestão** não muda em nenhuma aba, inclusive no `WHERE_PENDENTES_TODOS`.
- `listar`, `listarPaginado` e `contar` continuam compartilhando a mesma constante. O contador tem
  que bater com a lista.
- Nenhuma política RLS, nenhuma migration, nenhum contrato, nenhum arquivo de frontend.

## Bloco 4 — Testes

- Lead do atendente com a **última mensagem do cliente**: aparece em Pendentes, **não** aparece em
  Ativos.
- Lead do atendente com a **última mensagem do atendente**: aparece em Ativos, **não** aparece em
  Pendentes.
- **Disjunção:** para o mesmo atendente, nenhum lead aparece em Ativos e Pendentes ao mesmo tempo.
  Este é o teste central da etapa.
- **Conversa sem mensagem nenhuma** (atendimento aberto pelo "novo contato" sem primeira mensagem):
  aparece em **Ativos**. É o caso do Bloco 2, e é o que quebra se alguém usar `<>` em vez de
  `IS DISTINCT FROM`.
- Contadores batem com as listas nas quatro abas, para atendente e para gestão.
- Os testes que o #37 trouxe (`RecorteDaAbaTodosIT`) continuam verdes **sem serem editados**.

## Verificação

```
./mvnw -pl crm-atendimento -am verify      # na raiz de backend/
```

## Relatório

1. A constante `WHERE_ATIVOS` nova, e como você reaproveitou a construção de Pendentes.
2. Confirmação de que usou `IS DISTINCT FROM` e por quê, com o caso do Bloco 2 verificado no código.
3. O resultado do teste de disjunção.
4. Confirmação de que Pendentes, Potenciais, Todos e as visões de gestão ficaram intocados.
