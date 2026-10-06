-- E223 (PR A): historico de presenca e linha de disponibilidade para todo usuario que recebe atendimento.
--
-- 1) A presenca (usuario.status_presenca) so mudava por clique e nao deixava rastro: o 409 do rodizio de 05/10
--    nao pode ser provado depois do fato (docs/62). Cada mudanca passa a gravar uma linha aqui. So ids, estados,
--    origem e instante: nenhum dado pessoal.
--
-- 2) O rodizio consulta disponibilidade_atendente_ia com JOIN e criar usuario nao criava a linha: quem nunca passou
--    pelo toggle ficava fora do rodizio em silencio. O preenchimento abaixo cria a linha com FALSE, que e o efeito
--    de hoje (quem nao tem linha nao entra), entao NINGUEM entra no rodizio por causa desta migration.
--
-- Sem RLS de proposito, como usuario e disponibilidade_atendente_ia: nao e dado de lead e o preenchimento do item 2
-- precisa enxergar usuario. (Em tabela com FORCE ROW LEVEL SECURITY o usuario do Flyway nao enxerga as linhas e o
-- INSERT ... SELECT afetaria zero linhas sem avisar.)

CREATE TABLE presenca_historico (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    estado_anterior status_presenca NOT NULL,
    estado_novo     status_presenca NOT NULL,
    origem          VARCHAR(10) NOT NULL,
    motivo          VARCHAR(40),
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_presenca_historico_origem CHECK (origem IN ('MANUAL', 'SISTEMA')),
    -- Historico de mudanca: gravar "ONLINE -> ONLINE" seria ruido que esconde quando algo mudou de verdade.
    CONSTRAINT ck_presenca_historico_mudou CHECK (estado_anterior <> estado_novo)
);

CREATE INDEX idx_presenca_historico_usuario_instante ON presenca_historico (usuario_id, criado_em DESC);

INSERT INTO disponibilidade_atendente_ia (atendente_id, disponivel_para_ia)
SELECT u.id, FALSE
  FROM usuario u
 WHERE u.ativo = TRUE
   AND u.papel IN ('ATENDENTE', 'SUBGESTOR', 'OPERADOR')
ON CONFLICT (atendente_id) DO NOTHING;
