-- O autocomplete ignora maiusculas/minusculas; o banco precisa garantir a mesma semantica.
DROP INDEX idx_msg_rapida_atendente_chave;
CREATE UNIQUE INDEX idx_msg_rapida_atendente_chave
    ON mensagem_rapida (atendente_id, lower(palavra_chave));
