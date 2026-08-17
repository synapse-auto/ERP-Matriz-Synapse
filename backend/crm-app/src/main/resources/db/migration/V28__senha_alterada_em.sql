-- E29: primeiro acesso e troca de senha.
--
-- NULL significa "senha nunca trocada pelo dono" — ou seja, ela e provisoria e
-- alguem alem do usuario a conhece (quem provisionou a conta). Um timestamp em
-- vez de um booleano porque ele responde "quando", serve para auditoria e
-- permite politica de expiracao futura sem outra migration.
--
-- Sem DEFAULT: toda linha existente fica com NULL. No proximo login, todo
-- usuario ja cadastrado (inclusive o administrador) cai no fluxo de primeiro
-- acesso — a senha dele nasceu no provisionamento e nunca foi trocada pelo
-- proprio dono.
ALTER TABLE usuario ADD COLUMN senha_alterada_em TIMESTAMPTZ NULL;

COMMENT ON COLUMN usuario.senha_alterada_em IS
    'NULL = senha provisoria (nunca trocada pelo dono); bloqueia toda rota autenticada exceto POST /api/v1/auth/senha e /api/v1/auth/logout (ver SenhaProvisoriaFilter).';
