-- Somente descoberta do recurso. Nenhuma tabela clinica ou conexao externa no CRM.
INSERT INTO feature_flag (chave, habilitado, descricao)
VALUES ('cancelamentos', false, 'Consulta de cancelamentos de agendamentos; fonte depende de contrato de integracao.')
ON CONFLICT (chave) DO NOTHING;
