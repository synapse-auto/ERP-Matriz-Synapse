-- O browser usa este valor para encerrar a captura sem perder o audio ja gravado.
-- O limite de tamanho continua sendo anexo.tamanho_maximo_audio_mb.
INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('gravacao_audio.duracao_maxima_segundos', '120', 'segundos', 'INT', 10, 600,
     'Duracao maxima de uma gravacao de audio feita no composer.')
ON CONFLICT (chave) DO NOTHING;
