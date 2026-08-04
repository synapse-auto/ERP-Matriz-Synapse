# Prompt E02b — RLS: encanamento agora, políticas junto

> Pré-requisito: E02 commitada (`84f9bd8`). Etapa curta: ~meio dia.
> Decisão tomada a partir do seu relatório da E02.

---

## A decisão e o porquê

Você perguntou se RLS entra antes da E03. **Sim** — mas por um motivo mais estreito do que "defesa em profundidade genérica", e vale ser preciso sobre isso.

Sua defesa de aplicação é forte. As camadas 1–3 são de compile-time e cobrem o caminho JPA inteiro. RLS **não** adiciona muito contra o cenário "um caso de uso esqueceu a Specification" — isso você já tornou impossível.

O que RLS cobre e suas camadas não:

- **SQL cru dos read models.** O `docs/01` §2.2 define que Dashboard e Relatórios usam SQL direto/jOOQ, justamente para não carregar entidade em tela de listagem. Esse caminho não passa pelo `LeadRepositorio` e não vê nenhuma das quatro camadas.
- **Acesso manual ao banco.** `psql` numa madrugada de investigação.

O motivo de fazer **agora** não é o valor imediato — é que o encanamento (`SET LOCAL` a cada transação, nos dois DataSources) é cross-cutting. Introduzir isso com 2 casos de uso custa horas; com 60, custa dias e uma revisão de cada um. As políticas em si são baratas depois que o encanamento existe, então não há razão para separar.

**Escopo deliberadamente estreito:** 4 tabelas, segunda camada, sem substituir nada do que você construiu.

---

## O que construir

### 1. Encanamento — a parte que fica cara depois

Um interceptor que executa, no início de **toda** transação:

```sql
SET LOCAL app.usuario_id = '<uuid>';
SET LOCAL app.papel     = '<PAPEL>';
```

Requisitos:

- **`SET LOCAL`, nunca `SET`.** Com pool em modo transaction (PgBouncer, previsto para produção), `SET` de sessão vaza entre transações de usuários diferentes — que seria o pior bug possível neste sistema.
- Aplicar nos **dois** DataSources (`generalDataSource` e `chatDataSource`).
- Deve funcionar via `UsuarioContext`, que já existe.

### 2. Três contextos, não um

Este é o ponto onde implementações de RLS costumam quebrar em produção. Existem três tipos de acesso, e só um tem usuário:

| Contexto | Quem | Comportamento |
|---|---|---|
| **Requisição autenticada** | Atendente/gestor via HTTP | `app.usuario_id` e `app.papel` preenchidos; política aplica |
| **Serviço** | Consumidor de fila, jobs `@Scheduled`, publisher da outbox, migrations | Role de banco com `BYPASSRLS`, ou política que libera quando `app.papel = 'SERVICO'` |
| **Sem contexto** | Bug — alguém abriu transação fora dos dois acima | **Zero linhas.** Falha fechado. |

Falhar fechado é a escolha certa: um bug faz a tela aparecer vazia (visível, diagnosticável em segundos) em vez de mostrar leads de outro atendente (invisível, e comercialmente grave).

Documente os três contextos no `README.md` — quem chegar depois precisa saber por que uma query no `psql` não retorna nada.

### 3. Políticas nas 4 tabelas

`lead`, `atendimento`, `lembrete`, `mensagem_programada`.

Regra, espelhando `VisibilidadeLead`:

- `ATENDENTE` → linhas onde é o responsável, mais leads em `status_basico = 'IA'`
- `SUBGESTOR`, `GESTOR`, `ADMINISTRADOR` → todas
- `SERVICO` → todas
- Sem contexto → nenhuma

**Não duplique a regra em dois lugares sem amarrar os dois.** Escreva um teste que percorra os papéis e verifique que a política SQL e a `VisibilidadeLeadSpecification` retornam **o mesmo conjunto** para os mesmos dados. No dia em que a variante "subgestor vê só a própria equipe" existir, seu `switch` exaustivo quebra o build — e esse teste é o que garante que a política do banco foi junto.

### 4. Testes

- Cada papel enxerga exatamente o esperado, **via SQL cru** (fora do `LeadRepositorio`)
- Transação sem contexto retorna zero linhas
- Contexto de serviço enxerga tudo
- Paridade política ↔ Specification (item 3)
- Uma transação não vaza contexto para a seguinte na mesma conexão do pool

### 5. Dois itens soltos da E02

- **Teste de mutação do ArchUnit:** introduza uma violação proposital (classe fora de `...persistencia.lead` dependendo de `LeadJpaRepository`), confirme que a regra reprova, remova. Uma regra que nunca reprovou nada não é uma regra — o `DoNotIncludeJars` da E00 é a prova viva disso.
- **Registre o padrão dos 4 repositórios.** Anote no `README.md` ou num `package-info.java` a estrutura obrigatória: porta sem `findAll`/`findById` cru, implementação JPA pacote-privada, regra ArchUnit correspondente. A E03 vai criar `AtendimentoRepositorio` e precisa repetir isso — hoje nada obriga.

## Restrições

- RLS é **segunda** camada. Nada do que você construiu na E02 é removido ou afrouxado.
- Nenhuma alteração no `LeadRepositorio` ou nas quatro camadas.
- Se a política e a Specification divergirem, a Specification é a fonte da verdade — a política a persegue.

## Definição de pronto
 
- [ ] `SET LOCAL` em toda transação, nos dois DataSources
- [ ] Os três contextos funcionando, com o "sem contexto" retornando zero
- [ ] Políticas nas 4 tabelas
- [ ] Teste de paridade política ↔ Specification passando
- [ ] Teste de não-vazamento entre transações do pool
- [ ] Regra ArchUnit validada por mutação
- [ ] Padrão dos repositórios documentado
- [ ] CI verde

Commit: `feat: RLS como segunda camada de isolamento de lead`.
