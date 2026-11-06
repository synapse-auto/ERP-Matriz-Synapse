-- Operacao explicita, nao Flyway. Exige: psql -v ON_ERROR_STOP=1 -v banco_esperado=...
BEGIN;
SELECT set_config('operacao.banco_esperado', :'banco_esperado', true);
LOCK TABLE campo_customizado, feature_flag IN SHARE ROW EXCLUSIVE MODE NOWAIT;
DO $$
BEGIN
    IF current_database() <> current_setting('operacao.banco_esperado') THEN
        RAISE EXCEPTION 'banco diferente do autorizado';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM feature_flag WHERE chave='dados_cliente_ficha') THEN
        RAISE EXCEPTION 'aplique primeiro a versao com a migration de dados_cliente_ficha';
    END IF;
    IF EXISTS (SELECT 1 FROM campo_customizado WHERE chave='data_nascimento' AND tipo <> 'DATA') THEN
        RAISE EXCEPTION 'data_nascimento existente possui tipo incompativel';
    END IF;
    INSERT INTO campo_customizado(chave, rotulo, tipo, obrigatorio, filtravel, ordem)
    SELECT 'data_nascimento', 'Data de nascimento', 'DATA', false, false, COALESCE(MAX(ordem),0)+1
    FROM campo_customizado
    ON CONFLICT (chave) DO NOTHING;
    UPDATE feature_flag SET habilitado=true WHERE chave='dados_cliente_ficha';
END $$;
COMMIT;
