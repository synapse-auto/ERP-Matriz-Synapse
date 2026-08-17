-- O historico anterior a V24 pode conter telefone formatado; a V24 remove primeiro os
-- nao digitos. Esta migration completa o DDI somente depois dessa limpeza. Nenhuma linha
-- e mesclada ou apagada: colisao envolve conversa, dono e comissao e exige decisao comercial.
DO $$
DECLARE
    ddi_padrao TEXT := '${telefone_ddi_padrao}';
    invalidos TEXT;
    colisoes TEXT;
BEGIN
    IF ddi_padrao !~ '^[0-9]{1,3}$' THEN
        RAISE EXCEPTION 'DDI padrao deve conter de um a tres digitos; recebido: %', ddi_padrao;
    END IF;

    SELECT string_agg(
               format('%s | %s | %s', id, nome, telefone),
               '; ' ORDER BY id
           )
      INTO invalidos
      FROM lead
     WHERE telefone IS NOT NULL
       AND length(telefone) < 10;

    IF invalidos IS NOT NULL THEN
        RAISE EXCEPTION
            'Telefones com menos de 10 digitos; correcao manual obrigatoria:%',
            E'\n' || invalidos;
    END IF;

    WITH candidatos AS (
        SELECT id,
               nome,
               telefone,
               CASE
                   WHEN length(telefone) BETWEEN 10 AND 11 THEN ddi_padrao || telefone
                   ELSE telefone
               END AS canonico
          FROM lead
         WHERE telefone IS NOT NULL
    ), duplicados AS (
        SELECT canonico,
               string_agg(
                   format('%s | %s | %s', id, nome, telefone),
                   '; ' ORDER BY id
               ) AS pares
          FROM candidatos
         GROUP BY canonico
        HAVING count(*) > 1
    )
    SELECT string_agg(format('%s -> [%s]', canonico, pares), E'\n' ORDER BY canonico)
      INTO colisoes
      FROM duplicados;

    IF colisoes IS NOT NULL THEN
        RAISE EXCEPTION
            'Telefones duplicados apos completar o DDI; reconciliacao manual obrigatoria:%',
            E'\n' || colisoes;
    END IF;

    UPDATE lead
       SET telefone = ddi_padrao || telefone
     WHERE telefone IS NOT NULL
       AND length(telefone) BETWEEN 10 AND 11;
END $$;

ALTER TABLE lead DROP CONSTRAINT ck_lead_telefone_canonico;

-- Com DDI de um digito, um fixo local de dez digitos resulta em onze. Para DDIs de dois
-- ou tres digitos, todo canonico tem ao menos doze. A constraint usa a configuracao aplicada
-- nesta instancia e impede que escrita SQL lateral volte a aceitar um numero local cru.
ALTER TABLE lead
    ADD CONSTRAINT ck_lead_telefone_canonico
    CHECK (
        telefone IS NULL
        OR (
            telefone ~ '^[0-9]+$'
            AND (
                length(telefone) >= 12
                OR (
                    length('${telefone_ddi_padrao}') = 1
                    AND length(telefone) = 11
                    AND telefone LIKE '${telefone_ddi_padrao}%'
                )
            )
        )
    );

COMMENT ON CONSTRAINT ck_lead_telefone_canonico ON lead IS
    'Telefone somente com digitos e DDI da instancia; NULL continua permitido.';
