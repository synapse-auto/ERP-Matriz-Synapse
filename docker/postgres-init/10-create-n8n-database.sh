#!/bin/sh
set -eu

# Executado pelo entrypoint oficial do Postgres somente na inicializacao de um
# volume novo. As variaveis chegam pelo ambiente da stack; nenhum segredo fica
# gravado neste arquivo.
psql --set ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set n8n_db="$N8N_DB_NAME" \
  --set n8n_user="$N8N_DB_USER" \
  --set n8n_password="$N8N_DB_PASSWORD" <<-'SQL'
SELECT format('CREATE ROLE %I WITH LOGIN PASSWORD %L', :'n8n_user', :'n8n_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = :'n8n_user')
\gexec

SELECT format('CREATE DATABASE %I WITH OWNER %I', :'n8n_db', :'n8n_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_catalog.pg_database WHERE datname = :'n8n_db')
\gexec
SQL
