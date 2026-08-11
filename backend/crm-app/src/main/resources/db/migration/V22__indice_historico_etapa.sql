-- O historico de mudanca de etapa comeca vazio neste deploy. Nao existe
-- backfill confiavel e, deliberadamente, nada e reconstruido pelo audit_log:
-- auditoria tem retencao propria e nao e fonte de metrica comercial.
CREATE INDEX idx_evento_timeline_tipo_criado_em
    ON evento_timeline (tipo, criado_em);
