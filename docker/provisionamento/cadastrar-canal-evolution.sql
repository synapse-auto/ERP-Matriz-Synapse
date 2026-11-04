-- Reutiliza validacoes/cadastro/auditoria do procedimento compartilhado, sem tokens no SQL.
-- Mesmos parametros: banco_esperado, canal_nome, instancia_id, telefone, confirmar=CADASTRAR.
\set ON_ERROR_STOP on
\set provedor evolution
\ir cadastrar-canal-z-api.sql
