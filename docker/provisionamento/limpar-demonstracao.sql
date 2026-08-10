-- =========================================================
-- Desfaz seed-demonstracao.sql — e SOMENTE o que ele criou.
--
-- Obrigatorio antes do go-live: nenhum "Cliente Teste" pode sobreviver ao
-- primeiro lead real. A selecao e por prefixo de id (nunca por nome, e
-- nunca por LIKE em texto livre) — os prefixos "de"/"da"/"dm"/"db"/"dp"
-- sao exclusivos deste seed; nenhum gen_random_uuid() real colide com eles
-- por acaso.
--
-- Ordem das FKs: mensagem -> atendimento -> lead_tag/lembrete/
-- mensagem_programada -> lead. ON DELETE CASCADE de mensagem->atendimento
-- e atendimento->lead ja cobriria isto, mas apagar explicito deixa a
-- intencao legivel e nao depende de nenhuma CASCADE existir.
-- =========================================================

\set ON_ERROR_STOP on

BEGIN;

DELETE FROM mensagem      WHERE id::text LIKE 'dm000000-%';
DELETE FROM atendimento   WHERE id::text LIKE 'da000000-%';
DELETE FROM lembrete      WHERE id::text LIKE 'db000000-%';
DELETE FROM mensagem_programada WHERE id::text LIKE 'dp000000-%';
DELETE FROM lead_tag      WHERE lead_id::text LIKE 'de000000-%';
DELETE FROM lead          WHERE id::text LIKE 'de000000-%';

COMMIT;
\echo 'Demonstracao removida: leads, atendimentos, mensagens, lembretes e mensagens programadas do seed apagados.'
