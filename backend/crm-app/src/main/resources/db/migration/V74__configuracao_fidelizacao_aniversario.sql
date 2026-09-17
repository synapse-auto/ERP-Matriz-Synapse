-- E192: configuracao da tela Automacao > Fidelizacao.
-- A configuracao e persistida nas estruturas existentes; o executor de mensagens
-- permanece fora desta etapa e continua sendo uma decisao operacional do n8n.

-- Datas festivas sao registros dinamicos, nunca um catalogo fixo no codigo.
ALTER TABLE mensagem_festiva
    ADD COLUMN titulo VARCHAR(100) NOT NULL DEFAULT 'Data festiva',
    ADD COLUMN icone VARCHAR(80) NOT NULL DEFAULT 'calendar-days';

ALTER TABLE mensagem_festiva
    ALTER COLUMN titulo DROP DEFAULT,
    ALTER COLUMN icone DROP DEFAULT;

INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('fidelizacao.aniversario.habilitado', 'false', NULL, 'BOOLEAN', NULL, NULL,
     'Habilita a mensagem de aniversario quando a regra de negocio e o canal forem liberados.'),
    ('fidelizacao.aniversario.mensagem', 'Feliz aniversário, [nome]! A equipe deseja um ótimo dia.', NULL, 'TEXT', NULL, NULL,
     'Mensagem configurada para aniversario; [nome] sera substituido pelo nome do lead no executor.')
ON CONFLICT (chave) DO NOTHING;
