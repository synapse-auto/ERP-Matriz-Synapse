-- Somente a base descartável da validação local. Nunca executar em banco de cliente.
BEGIN;
DO $$ BEGIN
  IF current_database() <> 'finalizacao_local' THEN
    RAISE EXCEPTION 'Fixture restrita ao banco descartavel finalizacao_local';
  END IF;
END $$;
-- Reprodutível com o seed dev; não depende dos dados deixados por outra validação.
INSERT INTO usuario (id,nome,email,senha_hash,papel,ativo,senha_alterada_em)
SELECT '90100000-0000-4000-8000-000000000001', 'Validação Lote', 'lote@dev.invalid',
       senha_hash, 'ATENDENTE', TRUE, now() FROM usuario WHERE email='ana@dev.local'
ON CONFLICT (id) DO NOTHING;
INSERT INTO lead (id,nome,status_basico,atendente_responsavel_id)
VALUES ('5cfbc0a5-a8a4-80de-3b5a-e0f3e3289a35','Validação em massa 54','EM_ATENDIMENTO',
        '90100000-0000-4000-8000-000000000001') ON CONFLICT (id) DO NOTHING;
INSERT INTO atendimento (id,lead_id,canal_id,atendente_id,status,iniciado_em)
SELECT 'e1d14e74-0dd4-5b72-1e77-adb12ace8077','5cfbc0a5-a8a4-80de-3b5a-e0f3e3289a35',
       id,'90100000-0000-4000-8000-000000000001','EM_ATENDIMENTO',now()
FROM canal ORDER BY id LIMIT 1 ON CONFLICT (id) DO NOTHING;
INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, conteudo, enviado_em)
SELECT '98100000-0000-4000-8000-000000000001', id, 'LEAD', 'TEXTO',
       'Mensagem sintética para validar recuperação de conexão.', now()
FROM atendimento WHERE id='e1d14e74-0dd4-5b72-1e77-adb12ace8077' AND status='EM_ATENDIMENTO'
AND NOT EXISTS (SELECT 1 FROM mensagem WHERE id='98100000-0000-4000-8000-000000000001');
UPDATE lead SET ultima_mensagem_do_lead_em=now()
WHERE id=(SELECT lead_id FROM atendimento WHERE id='e1d14e74-0dd4-5b72-1e77-adb12ace8077');
COMMIT;
