-- Executar somente no banco da instancia explicitamente escolhida pelo operador (hoje: Disbrel).
-- Nao e migration Flyway: a Base PAI nao cria janelas por dia em outros filhos (Estrutural, Femina).
-- Ver docs/71 (D4) e docs/evidencias/disbrel-d4/auditoria-de-jobs-e-horarios.md.
--
-- O que faz: cria as chaves de janela por dia da semana usadas por HorarioComercialConfiguravel para decidir
-- quando um alerta CRITICO tambem pode ir ao grupo do cliente:
--   alerta.horario_cliente.sab = 08:00-12:00   (sabado so de manha)
--   alerta.horario_cliente.dom = fechado       (domingo nunca avisa o cliente)
-- Segunda a sexta nao ganham chave e continuam na janela padrao (alerta.horario_cliente.inicio/fim, 08:00-18:30).
-- A Synapse continua recebendo todos os alertas em qualquer horario.
--
-- CONFIRME OS HORARIOS COM A DISBREL antes de aplicar: 08:00-12:00 de sabado e domingo fechado vem do pedido
-- do cliente; os dias uteis ficam como estao porque nao foram especificados.
--
-- Idempotente e nao sobrescreve: se a chave ja existe (por exemplo, editada depois pelo CRUD de
-- configuracao_automacao, PUT /api/v1/automacao/config/{chave}), o script nao a toca.
--
-- Valores aceitos: 'HH:mm-HH:mm' (inicio inclusivo, fim exclusivo, no fuso da instancia) ou 'fechado'.
-- Valor ilegivel cai na janela padrao (e o backend registra um aviso).
--
-- Desfazer (volta ao comportamento anterior; a janela padrao nao e tocada):
--   DELETE FROM configuracao_automacao WHERE chave IN ('alerta.horario_cliente.sab', 'alerta.horario_cliente.dom');
\set ON_ERROR_STOP on

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM configuracao_automacao WHERE chave = 'alerta.horario_cliente.inicio') THEN
        RAISE EXCEPTION 'janela padrao ausente (alerta.horario_cliente.inicio): instancia nao provisionada; nada aplicado';
    END IF;
END $$;

INSERT INTO configuracao_automacao (chave, valor, unidade, tipo, valor_min, valor_max, descricao) VALUES
    ('alerta.horario_cliente.sab', '08:00-12:00', 'HH:mm-HH:mm', 'TEXT', NULL, NULL,
     'Janela de sabado em que alertas criticos tambem vao ao cliente (HH:mm-HH:mm ou fechado). Ausente = janela padrao.'),
    ('alerta.horario_cliente.dom', 'fechado', 'HH:mm-HH:mm', 'TEXT', NULL, NULL,
     'Janela de domingo em que alertas criticos tambem vao ao cliente (HH:mm-HH:mm ou fechado). Ausente = janela padrao.')
ON CONFLICT (chave) DO NOTHING;

SELECT chave, valor FROM configuracao_automacao WHERE chave LIKE 'alerta.horario_cliente.%' ORDER BY chave;

COMMIT;
