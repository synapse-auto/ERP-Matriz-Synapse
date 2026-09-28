-- Executar somente no banco da instancia explicitamente escolhida pelo operador.
-- Nao e migration Flyway: a Base PAI nao habilita aniversarios em outros filhos.
-- Um unico DO e atomico. NOWAIT impede disputa prolongada com outra escrita.
DO $habilitar_data_nascimento$
DECLARE
    atual campo_customizado%ROWTYPE;
    proxima_ordem INTEGER;
BEGIN
    LOCK TABLE campo_customizado IN SHARE ROW EXCLUSIVE MODE NOWAIT;

    SELECT * INTO atual
      FROM campo_customizado
     WHERE chave = 'data_nascimento';

    IF FOUND THEN
        IF atual.rotulo IS DISTINCT FROM 'Data de nascimento'
           OR atual.tipo IS DISTINCT FROM 'DATA'
           OR atual.opcoes IS NOT NULL
           OR atual.obrigatorio IS DISTINCT FROM FALSE
           OR atual.filtravel IS DISTINCT FROM FALSE THEN
            RAISE EXCEPTION
                'data_nascimento ja existe com metadados incompatíveis; nenhuma alteração aplicada';
        END IF;
        -- A ordem ja cadastrada e preservada. Repetir o procedimento e neutro.
        RETURN;
    END IF;

    SELECT COALESCE(MAX(ordem), 0) + 1 INTO proxima_ordem
      FROM campo_customizado;
    IF proxima_ordem > 32767 THEN
        RAISE EXCEPTION 'sem posicao SMALLINT disponivel para data_nascimento';
    END IF;

    INSERT INTO campo_customizado
        (chave, rotulo, tipo, opcoes, obrigatorio, filtravel, ordem)
    VALUES
        ('data_nascimento', 'Data de nascimento', 'DATA', NULL, FALSE, FALSE, proxima_ordem);
END;
$habilitar_data_nascimento$;
