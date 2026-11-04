-- Exclusivo banco NOVO e ISOLADO: preserva todos os guards do procedimento de homologacao.
\set ON_ERROR_STOP on
\set provedor evolution
\ir ativar-canal-z-api-homologacao.sql
