# E209 — bancada do painel de Atendimentos

Linha de base reproduzível das consultas do painel (`PainelDeAtendimentosRepositorioJdbc`) sob a
RLS real (`SET ROLE synapse_app` + `app.usuario_id`/`app.papel`, como `AplicadorDeContextoRls`).
Mede **SQL**, não HTTP: tempo de execução no Postgres, blocos lidos e os mesmos contadores de
tabela observados no incidente de 24/09 (`idx_scan` em `atendimento`/`mensagem`, `seq_scan` em
`usuario`).

**Não é produção.** A massa é sintética (`seed.sql`): 20 mil leads, ~40 mil atendimentos (~5 mil
abertos), ~506 mil mensagens (~449 mil na partição `mensagem_default`, como uma instância com
histórico anterior ao particionamento mensal), 12 usuários. Números absolutos dependem do volume
real; a comparação antes/depois é válida porque usa a mesma massa, o mesmo Postgres 15 e o mesmo
contexto de RLS.

## Reproduzir

```bash
# 1. Postgres 15 descartável, com pg_stat_statements (só na bancada)
docker run -d --name bench-e209 -e POSTGRES_PASSWORD=bench -e POSTGRES_DB=crm -p 55439:5432 \
  postgres:15-alpine -c shared_preload_libraries=pg_stat_statements -c shared_buffers=256MB

# 2. Schema: todas as migrations, em ordem numérica
cd backend/crm-app/src/main/resources/db/migration
for f in $(ls V*.sql | sort -t_ -k1.2 -n); do
  sed 's/\${telefone_ddi_padrao}/55/g' "$f" | docker exec -i bench-e209 psql -q -U postgres -d crm -v ON_ERROR_STOP=1
done

# 3. Massa (parâmetros: -v leads=... -v msgs_por_atendimento=...)
docker exec -i bench-e209 psql -1 -U postgres -d crm -v ON_ERROR_STOP=1 < docs/benchmarks/e209-painel/seed.sql

# 4. SQLs exatamente como o adaptador compilado as monta (uma vez por versão do código)
cd backend && ./mvnw -q -DskipTests install -pl crm-atendimento -am
./mvnw -q dependency:build-classpath -pl crm-atendimento -Dmdep.outputFile=cp.txt
java -cp "crm-atendimento/target/classes;$(cat crm-atendimento/cp.txt)" \
  ../docs/benchmarks/e209-painel/ExtrairSql.java /tmp/cenarios-<versao>   # ':' no Linux

# 5. Medir (N execuções por cenário) e comparar resultados entre versões
bash docs/benchmarks/e209-painel/medir.sh bench-e209 /tmp/cenarios-<versao> 3
bash docs/benchmarks/e209-painel/comparar.sh bench-e209 /tmp/cenarios-antes /tmp/cenarios-depois
```

`ExtrairSql.java` instancia o próprio adaptador com um `DataSource` que só captura o
`prepareStatement`: a bancada mede o código compilado, inclusive a SQL montada em tempo de
execução por `listarPaginado`, e não uma transcrição manual.

## Cenários

Para GESTOR e ATENDENTE, cada visão em `contar`, `listar` (sem paginação) e `pagina1`
(`listarPaginado`, limite 51 como a inbox), mais `pagina2` de FINALIZADOS com cursor.

## Calibrar com a instância real

Os volumes reais podem ser obtidos com uma consulta somente leitura, barata e fora de pico
(sem `EXPLAIN ANALYZE`, sem dados de cliente):

```sql
SELECT (SELECT count(*) FROM lead) leads, (SELECT count(*) FROM atendimento) atendimentos,
       (SELECT count(*) FROM atendimento WHERE status <> 'FINALIZADO') abertos,
       (SELECT count(*) FROM usuario WHERE ativo) usuarios_ativos,
       (SELECT reltuples::bigint FROM pg_class WHERE relname = 'mensagem_default') msgs_default_estimadas;
```

Os resultados da E209 estão em `docs/42-auditoria-performance-pos-incidente-21-09.md`.
