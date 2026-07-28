-- =========================================================
-- lead.ultima_interacao_em: a coluna que o filtro "sem retorno ha N dias"
-- (E03b) precisa para existir.
--
-- Por que uma coluna, e nao um calculo:
--
--   "sem retorno ha 30 dias" e, na definicao natural, MAX(mensagem.enviado_em)
--   por lead. Traduzir assim poria uma subconsulta na tabela particionada de
--   mensagens dentro do filtro de TODA listagem de lead — a tabela de maior
--   volume de escrita do sistema, no caminho critico do produto. A regra de
--   precedencia do CLAUDE.md decide: a tela de filtro nao pode competir por
--   I/O de mensagem com a aba Atendimentos.
--
-- E o mesmo desenho ja adotado em num_atendimentos/num_mensagens (V4): valor
-- denormalizado, atualizado na mesma transacao que registra o fato.
--
-- Ate a E04 escrever aqui, a coluna fica NULL e o filtro cai no COALESCE com
-- criado_em: lead sem interacao conta a partir da criacao, que e a leitura que
-- o atendente espera de qualquer forma.
-- =========================================================

ALTER TABLE lead ADD COLUMN ultima_interacao_em TIMESTAMPTZ;

COMMENT ON COLUMN lead.ultima_interacao_em IS
    'Denormalizado (E03b): instante da ultima mensagem ou atendimento do lead. '
    'Escrito na mesma transacao que registra o fato, junto dos contadores. '
    'NULL = nenhuma interacao ainda; o filtro semRetornoDias usa criado_em nesse caso.';

-- O filtro compara COALESCE(ultima_interacao_em, criado_em), entao o indice
-- precisa ser sobre a MESMA expressao — um indice sobre a coluna crua nao seria
-- usado pelo planejador.
CREATE INDEX idx_lead_ultima_interacao
    ON lead (COALESCE(ultima_interacao_em, criado_em));
