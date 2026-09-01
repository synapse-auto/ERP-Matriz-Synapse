-- E118: motivo do FALHOU informado pelo provedor (statuses[].errors).
--
-- Nao vai em midia_metadados: aquele JSONB e arquivo, mimetype, tamanho, legenda.
-- Nao vai so no log: o atendente precisa distinguir "falhou" de "falhou porque o
-- arquivo nao e suportado" (o 131053 do audio). JSONB {codigo, titulo} cabe na
-- propria mensagem — a bolha ja le status_entrega; o motivo fica ao lado.
-- Coluna anulavel: ADD COLUMN em tabela particionada no PG 11+ nao reescreve as
-- particoes. V50 ja rodou em producao; nao se edita migration aplicada.

ALTER TABLE mensagem ADD COLUMN erro_entrega JSONB;

COMMENT ON COLUMN mensagem.erro_entrega IS
    'Motivo do FALHOU informado pelo provedor (codigo e titulo). Null quando a mensagem nao falhou.';
