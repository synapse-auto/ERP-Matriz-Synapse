-- =========================================================
-- Parametros da Automacao que vem do provisionamento.
--
-- Por que este arquivo existe: `provisionar-instancia.sql` popula
-- `configuracao_automacao` a partir de SYNAPSE_AUTOMACAO_JSON. Em homologacao
-- esse bloco nao rodou -- em 24/08 a tabela tinha apenas as cinco chaves
-- inseridas por migration (V23, V27, V33, V36) e nenhuma das dez do
-- provisionamento.
--
-- Consequencia: `GET /internal/v1/automation-config` devolvia uma lista sem
-- nenhum tempo de follow-up, sem o gatilho de transferencia para humano e sem
-- `automacao.habilitada`. A Automacao rodava com os defaults internos do n8n,
-- nao com o que o CRM configura -- o oposto da RN-CRM-07. E nao havia
-- interruptor para desligar a IA pelo CRM.
--
-- ON CONFLICT (chave) DO NOTHING: a chave e a PK. Rodar de novo nao sobrescreve
-- valor que alguem ja ajustou pela aba Automacao -- so preenche o que falta.
-- Isso e proposital: a tela existe para mandar nesses numeros, e um script nao
-- pode desfazer o que o gestor decidiu.
--
-- Os valores abaixo sao os genericos do `instancia.exemplo.env.example`.
-- CONFIRME COM O CLIENTE antes do go-live; depois disso, o ajuste e pela tela.
-- =========================================================

\set ON_ERROR_STOP on

BEGIN;

INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('followup.primeiro.minutos',      '15',   'minutos',    'INT',      1,  1440,
     'Minutos ate o primeiro follow-up automatico.'),
    ('followup.segundo.minutos',       '120',  'minutos',    'INT',      1, 10080,
     'Minutos ate o segundo follow-up automatico.'),
    ('followup.tentativas.maximo',     '3',    'tentativas', 'INT',      1,    10,
     'Tentativas maximas de follow-up por atendimento.'),
    ('fidelizacao.dias_sem_contato',   '30',   'dias',       'INT',      1,   365,
     'Dias sem contato para disparar reengajamento.'),
    ('ia.transferir_apos_mensagens',   '8',    'mensagens',  'INT',      1,    50,
     'Mensagens trocadas com a IA antes de sugerir transferencia para humano.'),
    ('ia.resumo.a_cada_mensagens',     '20',   'mensagens',  'INT',      5,   200,
     'Intervalo de mensagens para gerar resumo por IA.'),
    ('atendimento.finalizar_apos_horas','48',  'horas',      'INT',      1,   720,
     'Horas de inatividade para finalizar o atendimento.'),
    ('automacao.habilitada',           'true', NULL,         'BOOLEAN', NULL, NULL,
     'Liga e desliga a Automacao. E o interruptor de emergencia pelo CRM.'),
    ('anexo.tamanho_maximo_imagem_mb', '5',    'MB',         'INT',      1,     5,
     'Tamanho maximo de imagem; conferir limite da Meta antes de aumentar.'),
    ('anexo.tamanho_maximo_audio_mb',  '16',   'MB',         'INT',      1,    16,
     'Tamanho maximo de audio; conferir limite da Meta antes de aumentar.')
ON CONFLICT (chave) DO NOTHING;

COMMIT;

\echo 'Parametros da Automacao apos o script:'
SELECT chave, valor, unidade, tipo FROM configuracao_automacao ORDER BY chave;
