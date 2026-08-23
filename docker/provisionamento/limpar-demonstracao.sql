-- =========================================================
-- Desfaz seed-demonstracao.sql — e SOMENTE o que ele criou.
--
-- A selecao usa os prefixos UUID hexadecimais reservados pelo seed. Tags que
-- ja existiam antes do seed nao usam d5000000 e, por isso, sao preservadas.
-- Idempotente: reexecutar depois da limpeza nao falha.
-- =========================================================

\set ON_ERROR_STOP on

BEGIN;

DELETE FROM mensagem            WHERE id::text LIKE 'd1000000-%';
DELETE FROM evento_timeline      WHERE id::text LIKE 'd6000000-%';
DELETE FROM atendimento         WHERE id::text LIKE 'da000000-%';
DELETE FROM lembrete            WHERE id::text LIKE 'db000000-%';
DELETE FROM mensagem_programada WHERE id::text LIKE 'd3000000-%';
DELETE FROM lead_tag            WHERE lead_id::text LIKE 'de000000-%';
DELETE FROM lead                WHERE id::text LIKE 'de000000-%';
DELETE FROM tag                 WHERE id::text LIKE 'd5000000-%';
DELETE FROM usuario             WHERE id::text LIKE 'd4000000-%';

COMMIT;
\echo 'Demonstracao removida: somente os registros de ids reservados pelo seed foram apagados.'
