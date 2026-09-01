-- E113: V34 só preencheu ATENDENTE. Subgestor ativo passa a receber lead
-- (PapelUsuario.recebeAtendimento()). Quem já tem linha não é reescrito —
-- o toggle que a gestão ligou ou desligou depois da V34 permanece.
--
-- A coluna continua se chamando atendente_id: renomear agora, com a V50
-- já armada na main, não paga o risco. Dívida registrada no relatório da E113.
INSERT INTO disponibilidade_atendente_ia (atendente_id, disponivel_para_ia)
SELECT id, TRUE
FROM usuario
WHERE ativo = TRUE
  AND papel = 'SUBGESTOR'
ON CONFLICT (atendente_id) DO NOTHING;
