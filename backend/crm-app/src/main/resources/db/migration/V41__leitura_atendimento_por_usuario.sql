-- E55: a leitura e uma propriedade do usuario que abriu a conversa, nao do atendimento.
--
-- O campo atendimento.lido_ate permanece por compatibilidade durante esta transicao, mas a
-- aplicacao deixa de escreve-lo. O backfill e deliberadamente limitado ao responsavel atual:
-- para os demais usuarios, a ausencia de linha significa que todas as mensagens sao novas.
CREATE TABLE atendimento_leitura (
    atendimento_id UUID NOT NULL REFERENCES atendimento(id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    lido_ate TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (atendimento_id, usuario_id)
);

COMMENT ON TABLE atendimento_leitura IS
    'Ultimo instante lido por usuario em cada atendimento. Ausencia de linha = nunca abriu.';
COMMENT ON COLUMN atendimento.lido_ate IS
    'Legado da V25; mantido para compatibilidade e nao e mais atualizado pela aplicacao.';

-- O backfill precisa ocorrer antes da RLS: a migration roda sem app.usuario_id.
INSERT INTO atendimento_leitura (atendimento_id, usuario_id, lido_ate)
SELECT id, atendente_id, lido_ate
  FROM atendimento
 WHERE atendente_id IS NOT NULL
   AND lido_ate IS NOT NULL
ON CONFLICT (atendimento_id, usuario_id) DO NOTHING;

ALTER TABLE atendimento_leitura ENABLE ROW LEVEL SECURITY;
ALTER TABLE atendimento_leitura FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_atendimento_leitura ON atendimento_leitura
    FOR ALL
    USING (app_e_servico() OR usuario_id = app_usuario_id())
    WITH CHECK (app_e_servico() OR usuario_id = app_usuario_id());
