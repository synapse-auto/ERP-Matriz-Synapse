-- O telefone e a identidade de contato usada pelo webhook. Antes de normalizar, a migration
-- interrompe se dois leads convergirem para o mesmo numero: reconciliar conversa e comissao e
-- decisao comercial, portanto nenhuma linha e mesclada ou apagada automaticamente.
DO $$
DECLARE
    colisoes TEXT;
BEGIN
    WITH telefones AS (
        SELECT id,
               nome,
               telefone,
               NULLIF(regexp_replace(telefone, '[^0-9]', '', 'g'), '') AS canonico
          FROM lead
         WHERE telefone IS NOT NULL
    ), duplicados AS (
        SELECT canonico,
               string_agg(
                   format('%s | %s | %s', id, nome, telefone),
                   '; ' ORDER BY id
               ) AS pares
          FROM telefones
         WHERE canonico IS NOT NULL
         GROUP BY canonico
        HAVING count(*) > 1
    )
    SELECT string_agg(format('%s -> [%s]', canonico, pares), E'\n' ORDER BY canonico)
      INTO colisoes
      FROM duplicados;

    IF colisoes IS NOT NULL THEN
        RAISE EXCEPTION
            'Telefones duplicados apos normalizacao; reconciliacao manual obrigatoria:%',
            E'\n' || colisoes;
    END IF;
END $$;

UPDATE lead
   SET telefone = NULLIF(regexp_replace(telefone, '[^0-9]', '', 'g'), '')
 WHERE telefone IS NOT NULL;

ALTER TABLE lead
    ADD CONSTRAINT ck_lead_telefone_canonico
    CHECK (telefone IS NULL OR telefone ~ '^[0-9]+$');

CREATE UNIQUE INDEX ux_lead_telefone
    ON lead (telefone)
 WHERE telefone IS NOT NULL;
