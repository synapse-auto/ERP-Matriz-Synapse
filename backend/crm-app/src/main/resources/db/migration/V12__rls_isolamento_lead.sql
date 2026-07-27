-- =========================================================
-- RLS: segunda camada de isolamento de agenda (RN-CRM-01).
--
-- A primeira camada continua sendo a aplicacao (VisibilidadeLead + o
-- adaptador unico de leitura). Esta camada cobre o que aquela nao alcanca:
-- SQL cru dos read models (dashboard e relatorios, ver docs/01 secao 2.2) e
-- acesso manual pelo psql.
--
-- O contexto vem de duas variaveis de sessao setadas com SET LOCAL a cada
-- transacao. SET LOCAL, e nunca SET: com pool em modo transaction o SET de
-- sessao sobreviveria a transacao e vazaria para o proximo usuario que
-- pegasse aquela conexao — o pior bug possivel neste sistema.
--
-- Sem contexto, tudo e negado. Falhar fechado deixa a tela vazia, que e
-- visivel e se diagnostica em segundos; falhar aberto mostra o lead do
-- colega, que ninguem percebe e e problema comercial.
-- =========================================================

-- --- Leitura do contexto ---------------------------------------------------
-- O segundo argumento de current_setting e "missing_ok": devolve NULL em vez
-- de erro quando a variavel nunca foi setada. E o que faz o caminho "sem
-- contexto" negar em vez de explodir.

CREATE OR REPLACE FUNCTION app_papel()
RETURNS TEXT LANGUAGE sql STABLE AS $$
    SELECT NULLIF(current_setting('app.papel', TRUE), '');
$$;

CREATE OR REPLACE FUNCTION app_usuario_id()
RETURNS UUID LANGUAGE sql STABLE AS $$
    SELECT NULLIF(current_setting('app.usuario_id', TRUE), '')::uuid;
$$;

-- Jobs, consumidores de fila e o publisher da outbox nao tem usuario, mas
-- precisam enxergar tudo. Sao contexto de servico, declarado explicitamente.
CREATE OR REPLACE FUNCTION app_e_servico()
RETURNS BOOLEAN LANGUAGE sql STABLE AS $$
    SELECT app_papel() = 'SERVICO';
$$;

-- Espelha PapelUsuario.enxergaTodosOsLeads(). Se um papel novo aparecer,
-- ele entra aqui e no enum — e o teste de paridade reprova se so um mudar.
CREATE OR REPLACE FUNCTION app_enxerga_todos_os_leads()
RETURNS BOOLEAN LANGUAGE sql STABLE AS $$
    SELECT app_e_servico()
        OR app_papel() IN ('SUBGESTOR', 'GESTOR', 'ADMINISTRADOR');
$$;

COMMENT ON FUNCTION app_papel() IS
    'Papel da transacao corrente, vindo de SET LOCAL app.papel. NULL = sem contexto = nega tudo.';

-- --- Politicas -------------------------------------------------------------
-- USING governa SELECT, UPDATE e DELETE: nao se le nem se altera linha que
-- nao se enxerga. WITH CHECK (TRUE) deixa INSERT livre de proposito — quem
-- decide de quem e o lead novo e a aplicacao, e prender o INSERT aqui
-- quebraria migrations de dados e fixtures sem cobrir nenhuma ameaca real:
-- o risco desta etapa e LEITURA indevida.
--
-- FORCE e obrigatorio: sem ele o dono da tabela (que e o usuario da
-- aplicacao, porque foi ele quem rodou as migrations) ignora RLS por padrao
-- e as politicas abaixo nao valeriam absolutamente nada.

ALTER TABLE lead ENABLE ROW LEVEL SECURITY;
ALTER TABLE lead FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_lead ON lead
    FOR ALL
    USING (
        app_enxerga_todos_os_leads()
        OR (
            app_papel() = 'ATENDENTE'
            AND (atendente_responsavel_id = app_usuario_id() OR status_basico = 'IA')
        )
    )
    WITH CHECK (TRUE);

ALTER TABLE atendimento ENABLE ROW LEVEL SECURITY;
ALTER TABLE atendimento FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_atendimento ON atendimento
    FOR ALL
    USING (
        app_enxerga_todos_os_leads()
        OR (
            app_papel() = 'ATENDENTE'
            AND (atendente_id = app_usuario_id() OR status = 'EM_IA')
        )
    )
    WITH CHECK (TRUE);

-- lembrete e mensagem_programada sao pessoais: pertencem a um atendente, nao
-- ao lead. Nao ha escape de "esta em IA" aqui — o lembrete de um colega nunca
-- e assunto de outro atendente, mesmo que o lead ainda nao tenha dono.
ALTER TABLE lembrete ENABLE ROW LEVEL SECURITY;
ALTER TABLE lembrete FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_lembrete ON lembrete
    FOR ALL
    USING (app_enxerga_todos_os_leads() OR atendente_id = app_usuario_id())
    WITH CHECK (TRUE);

ALTER TABLE mensagem_programada ENABLE ROW LEVEL SECURITY;
ALTER TABLE mensagem_programada FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_mensagem_programada ON mensagem_programada
    FOR ALL
    USING (app_enxerga_todos_os_leads() OR atendente_id = app_usuario_id())
    WITH CHECK (TRUE);
