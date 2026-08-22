-- E36b: antes da separação, atualizarPresenca gravava FALSE ao sair do expediente.
-- Preservar esse valor deixaria todos os atendentes fora do rodízio no primeiro dia
-- após o deploy, pois ONLINE não liga mais a flag automaticamente. O backfill liga
-- somente atendentes ativos; inativos continuam fora da distribuição.
INSERT INTO disponibilidade_atendente_ia (atendente_id, disponivel_para_ia)
SELECT id, TRUE
FROM usuario
WHERE ativo = TRUE
  AND papel = 'ATENDENTE'
ON CONFLICT (atendente_id) DO UPDATE
SET disponivel_para_ia = TRUE,
    atualizado_em = now();
