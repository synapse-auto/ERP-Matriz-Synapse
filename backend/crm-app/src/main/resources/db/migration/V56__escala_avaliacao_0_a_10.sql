-- E128: escala CSAT passa de 1–5 para 0–10 (contrato EV-08 do n8n: Ruim=2, Bom=7, Otimo=10).
-- So a estrutura. O dado antigo em producao ja foi arquivado e removido fora do Flyway —
-- esta migration NAO faz UPDATE, DELETE nem CREATE TABLE de arquivo.

DO $$
DECLARE
    nome_check text;
BEGIN
    SELECT c.conname
      INTO nome_check
      FROM pg_constraint c
     WHERE c.conrelid = 'avaliacao'::regclass
       AND c.contype = 'c'
     LIMIT 1;

    IF nome_check IS NULL THEN
        RAISE EXCEPTION 'E128: nenhum CHECK encontrado em avaliacao; abortando';
    END IF;

    EXECUTE format('ALTER TABLE avaliacao DROP CONSTRAINT %I', nome_check);
END $$;

ALTER TABLE avaliacao
    ADD CONSTRAINT avaliacao_nota_entre_0_e_10 CHECK (nota BETWEEN 0 AND 10);
