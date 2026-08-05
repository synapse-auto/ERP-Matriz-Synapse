# Smoke test de RLS

Este teste prova a protecao que o CI nao consegue provar: a credencial da
aplicacao no ambiente real consegue assumir `synapse_app` e, nessa role, a
politica de `lead` bloqueia a carteira do colega.

Execute no VPS, a partir de um checkout desta branch. Nao passe senha ou outro
segredo por argumento. `SYNAPSE_DB_NAME` e `SYNAPSE_DB_USER` nao sao segredos;
use os mesmos valores `POSTGRES_DB` e `POSTGRES_USER` cadastrados na stack:

```bash
export SYNAPSE_DB_NAME='nome-do-banco-da-instancia'
export SYNAPSE_DB_USER='usuario-das-migrations-e-da-aplicacao'
./docker/verificacao/executar-smoke-rls.sh
```

O executor resolve somente o container cujo nome e a task Swarm
`_postgres.1.`. Ele falha se encontrar zero ou mais de um candidato; assim o
Postgres do Dokploy nunca e escolhido por engano.

O sucesso termina com:

```text
Smoke RLS passou: cada atendente viu somente o proprio lead; gestor viu os dois.
```

O arquivo SQL abre uma transacao e termina em `ROLLBACK`. Se uma verificacao
falhar, `ON_ERROR_STOP` encerra o `psql` e o rollback implicito tambem remove
os tres usuarios e os dois leads de teste.

## Prova negativa obrigatoria

Em um banco descartavel com as migrations aplicadas, desligue a politica,
execute o smoke e religue-a:

```bash
docker exec <container-postgres-descartavel> \
  psql -U "$SYNAPSE_DB_USER" -d "$SYNAPSE_DB_NAME" \
  -c 'ALTER TABLE lead DISABLE ROW LEVEL SECURITY;'

./docker/verificacao/executar-smoke-rls.sh
# esperado: FALHA RLS: atendente A ... viu 2 leads ... esperado 1

docker exec <container-postgres-descartavel> \
  psql -U "$SYNAPSE_DB_USER" -d "$SYNAPSE_DB_NAME" \
  -c 'ALTER TABLE lead ENABLE ROW LEVEL SECURITY; ALTER TABLE lead FORCE ROW LEVEL SECURITY;'
```

Nunca desligue RLS no banco de homologacao para esta prova. Se o smoke do
ambiente real falhar, pare o provisionamento e trate como incidente comercial.
