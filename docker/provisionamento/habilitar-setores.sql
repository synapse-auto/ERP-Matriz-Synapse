-- Executar somente no banco da instancia explicitamente escolhida pelo operador (hoje: Disbrel).
-- Nao e migration Flyway: a Base PAI nao liga setores em outros filhos (Estrutural, Femina).
-- Ver docs/71 (D2) e backend/.../db/migration/V103__setores.sql.
--
-- O que liga: o cadastro de setores e a atribuicao de integrantes a um setor na aba Equipe da Gestao
-- (GESTOR/ADMINISTRADOR criam e editam; quem tem equipe.editar atribui, dentro da alcada de sempre).
-- O que NAO muda: papel, permissao e visibilidade de lead. Setor e so agrupamento (RN-CRM-01).
--
-- Pre-requisito: a versao com a V103 ja implantada nesta instancia (a V103 cria a linha da flag
-- desligada). O script recusa em vez de criar a linha por conta propria, para nao mascarar um
-- deploy que ainda nao aconteceu. Repetir o script e neutro.
--
-- Desfazer (nao apaga setores nem atribuicoes; so para de aceitar e de exibir):
--   UPDATE feature_flag SET habilitado = FALSE WHERE chave = 'setores';
DO $habilitar_setores$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM feature_flag WHERE chave = 'setores') THEN
        RAISE EXCEPTION
            'flag setores ausente: implante a versao com a V103 antes de habilitar; nenhuma alteracao aplicada';
    END IF;

    UPDATE feature_flag
       SET habilitado = TRUE
     WHERE chave = 'setores';
END;
$habilitar_setores$;
