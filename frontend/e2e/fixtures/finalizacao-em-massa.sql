-- Fixture sintética para Playwright CLI headed. Nunca executar em banco de cliente.
BEGIN;
DO $$ BEGIN
  IF current_database() <> 'finalizacao_local' THEN
    RAISE EXCEPTION 'Fixture restrita ao banco descartavel finalizacao_local';
  END IF;
END $$;
INSERT INTO usuario (id,nome,email,senha_hash,papel,status_presenca,ativo,senha_alterada_em)
SELECT '90100000-0000-4000-8000-000000000001'::uuid, 'Validação Lote', 'lote@dev.invalid', senha_hash,
       'ATENDENTE','ONLINE',TRUE,now() FROM usuario WHERE email='ana@dev.local';

INSERT INTO lead (id,nome,status_basico,atendente_responsavel_id)
SELECT md5('finalizacao-visual-lead-'||g)::uuid, 'Validação em massa '||lpad(g::text,2,'0'),
       'EM_ATENDIMENTO', '90100000-0000-4000-8000-000000000001'::uuid FROM generate_series(1,54) g;

INSERT INTO atendimento (id,lead_id,canal_id,atendente_id,status,iniciado_em,em_negociacao)
SELECT md5('finalizacao-visual-ciclo-'||g)::uuid, md5('finalizacao-visual-lead-'||g)::uuid,
       (SELECT id FROM canal ORDER BY id LIMIT 1), '90100000-0000-4000-8000-000000000001'::uuid,
       'EM_ATENDIMENTO', now()-interval '1 hour', g=54 FROM generate_series(1,54) g;
UPDATE configuracao_automacao SET valor='7' WHERE chave='atendimento.finalizacao_em_massa.lote';
COMMIT;
