-- A leitura operacional do gestor consulta a posicao do responsavel atual.
-- A politica de escrita da V41 continua restrita ao proprio usuario (ou servico).
CREATE POLICY rls_gestao_consulta_atendimento_leitura ON atendimento_leitura
    FOR SELECT
    USING (app_enxerga_todos_os_leads());
