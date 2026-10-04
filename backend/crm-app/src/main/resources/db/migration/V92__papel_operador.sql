-- Papel operacional sem heranca administrativa. Uso do enum somente a partir da V93.
ALTER TYPE papel_usuario ADD VALUE IF NOT EXISTS 'OPERADOR';
