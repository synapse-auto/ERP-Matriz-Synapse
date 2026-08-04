# Prompt E09a — Auditoria via AOP

> Etapa curta: ~meio dia. Rodar **logo após a E07**, antes do frontend.

---

**Etapa E09a — `@Auditable` e consulta de log de auditoria.**

## Por que agora e não depois

Mesma lógica que trouxe a outbox para a E05 e o `SET LOCAL` para a E02b: **o encanamento transversal fica caro proporcionalmente ao número de casos de uso.**

Hoje são cerca de 20. Anotar 20 casos de uso é uma tarde. Anotar 60, revisando cada um para descobrir o que auditar, é dias — e a chance de esquecer alguns é praticamente 100%.

A tabela `audit_log` existe desde a V9 e continua sem escritor.

## O que construir

### 1. `@Auditable` via AOP

Aspecto em torno dos casos de uso, gravando em `audit_log`: ator, tipo de ator, ação, tipo e id da entidade, `lead_id` desnormalizado, dados antes e depois, IP.

- **O caso de uso não sabe que está sendo auditado.** Nenhuma chamada a logger no meio da regra de negócio.
- Gravação **fora da transação do caso de uso** (`AFTER_COMMIT` ou executor próprio) — auditoria não pode ser o motivo de uma operação falhar, nem entrar no caminho crítico de mensagem.
- Se a gravação da auditoria falhar, **alarme**, não silêncio.

Anote pelo menos o que o requisito interno cita: transferências, alterações de tag, vínculo de tag a lead, mudança de configuração da automação, alteração de credencial de canal, criação e desativação de usuário.

### 2. Antes e depois

`dados_antes` e `dados_depois` em JSONB. **Nunca grave segredo** — `token_ref`, hash de senha e `payload` de webhook ficam de fora, por allowlist de campos, não por blocklist. Blocklist esquece o campo novo que alguém acrescentar.

### 3. `GET /api/v1/audit-log`

Filtros por ator, ação, tipo de entidade, id de entidade, lead e período. Paginação obrigatória — é a tabela que mais cresce depois de `mensagem`.

Restrito a gestor e administrador. Os índices já existem (V9), incluindo o BRIN em `criado_em`.

### 4. Teste que a regra reprova

No espírito das outras cinco: um teste que **prova que uma ação auditável não anotada seria pega**. Concretamente, uma regra ArchUnit ou um teste que percorre os casos de uso de uma lista de ações sensíveis e falha se algum não tiver `@Auditable`.

Sem isso, a auditoria degrada em silêncio — o caso de uso novo simplesmente não aparece no log, e ninguém descobre até precisar dele numa investigação.

## Testes

- Transferência de lead gera linha em `audit_log` com antes e depois
- Rollback do caso de uso **não** deixa linha de auditoria órfã
- Falha ao gravar auditoria não derruba o caso de uso, mas alarma
- Nenhum segredo aparece em `dados_antes`/`dados_depois`
- Consulta com filtros combinados retorna o esperado, paginada
- Atendente não acessa `/audit-log` (403)
- Caso de uso sensível sem `@Auditable` reprova no build

## Definição de pronto

- [ ] `@Auditable` gravando fora do caminho crítico
- [ ] Ações do requisito interno cobertas
- [ ] Endpoint com filtros e paginação, restrito por papel
- [ ] Allowlist de campos impedindo vazamento de segredo
- [ ] Teste que reprova caso de uso sensível não anotado
- [ ] CI verde

Commit: `feat: auditoria via AOP e consulta de log`.
