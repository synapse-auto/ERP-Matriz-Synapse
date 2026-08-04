# Prompt E01b — Ajustes de fechamento da E01

> Curto. Aplica as decisões sobre as divergências que você levantou, antes de seguir para a E02.

---

Três ajustes na E01 a partir da revisão. Os demais itens do seu relatório foram aceitos como estão e já constam em `docs/03-modelo-dados-postgres.md` §6.

## 1. Mover os índices únicos de regra de negócio para junto das tabelas

Saem da V10 e voltam para a migration da tabela que protegem:

- `idx_canal_credencial_ativa` → V3
- `idx_etapa_ordem` → V3
- `idx_msg_rapida_atendente_chave` → V4

Os demais índices permanecem na V10.

Motivo: esses três não são índices, são **constraints de unicidade** escritas com sintaxe de índice. Constraint pertence à tabela que ela protege — separá-la abre uma janela em que a tabela existe sem a garantia. Sua observação sobre migração parcial estava certa; a correção é essa.

## 2. Criar partição `DEFAULT` em `mensagem` — com alarme

Seu raciocínio contra a `DEFAULT` está correto quanto ao custo (linhas presas impedem anexar a partição do mês sem mover dados). Ainda assim, vamos criá-la, por causa da regra de precedência do `CLAUDE.md`.

Comparando os modos de falha no cenário em que todas as salvaguardas falham juntas:

- **Sem `DEFAULT`:** `INSERT` falha → mensagem não persistida → aba Atendimentos para. Irrecuperável: a mensagem do cliente se perdeu.
- **Com `DEFAULT`:** linhas caem na padrão → sistema segue funcionando → dívida de manutenção recuperável.

O cenário não é hipotético: basta o job mensal falhar em silêncio por três meses sem que a aplicação reinicie — a verificação de boot só protege quem reinicia.

Implemente:

- `CREATE TABLE mensagem_default PARTITION OF mensagem DEFAULT;`
- Um `@Scheduled` diário que conta linhas em `mensagem_default` e **alerta** (mesmo canal do watchdog da E09; por ora, log em nível `ERROR` com marcador claro) se houver qualquer linha
- `COMMENT ON TABLE mensagem_default` explicando que linhas ali são anomalia a ser drenada, não estado normal

**Mantenha** a verificação de boot e a janela de 3 meses. A `DEFAULT` é último recurso, não substituto — uma rede de segurança silenciosa é pior que nenhuma, porque some do radar até a limpeza ficar cara.

## 3. Verificar as extensões antes da homologação

Não é mudança de código agora, é uma anotação a fazer no `README.md`, seção de deploy:

> `pgcrypto` e `pg_trgm` exigem privilégio elevado. Em Postgres gerenciado (RDS, Cloud SQL, etc.) podem precisar ser habilitadas fora da migration, senão a V1 falha. Verificar antes do primeiro deploy de homologação.

Verifique também se o Postgres do projeto é 13+: nesse caso `gen_random_uuid()` é nativo e a dependência de `pgcrypto` pode ser removida por completo. Se for o caso, remova — uma extensão a menos é um obstáculo a menos no deploy gerenciado.

## Definição de pronto

- [ ] Os três índices únicos migrados para V3/V4, testes ainda passando
- [ ] Partição `DEFAULT` criada, com job de alerta e comentário no schema
- [ ] Teste que insere numa data fora da janela e confirma que a linha cai na `DEFAULT` (não falha)
- [ ] Nota de extensões no README
- [ ] `pgcrypto` removida se a versão do Postgres permitir

Commit: `fix: constraints junto das tabelas e partição default de segurança`.
