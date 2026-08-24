-- =========================================================
-- As duas linhas singleton da Automacao.
--
-- Por que esta migracao existe: a V7 criou `configuracao_resumo_ia` e
-- `status_automacao_telemetria` com CHECK (id = 1), mas nunca inseriu a linha.
-- Toda instancia nascia com as tabelas vazias, e a aba Automacao respondia 500
-- na primeira leitura -- foi o que aconteceu em homologacao em 23/08, corrigido
-- na mao por INSERT direto. Corrigir na mao nao protege a proxima instancia:
-- o cliente real nasceria com o mesmo defeito.
--
-- Uma tabela singleton sem a linha nao e "vazia", e invalida: o CHECK (id = 1)
-- declara que existe exatamente uma. A linha faz parte do schema, e por isso
-- pertence a uma migracao, e nao ao provisionamento nem ao seed.
--
-- Todas as colunas tem DEFAULT, entao inserir so o id basta. `ON CONFLICT DO
-- NOTHING` mantem a migracao idempotente e preserva o valor de quem ja rodou o
-- INSERT manual -- nada e sobrescrito.
-- =========================================================

INSERT INTO configuracao_resumo_ia (id) VALUES (1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO status_automacao_telemetria (id) VALUES (1)
ON CONFLICT (id) DO NOTHING;
