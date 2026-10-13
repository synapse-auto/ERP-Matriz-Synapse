-- Somente a base descartável da validação local. Nunca executar em banco de cliente.
BEGIN;
DO $$ BEGIN
  IF current_database() <> 'finalizacao_local' THEN
    RAISE EXCEPTION 'Fixture restrita ao banco descartavel finalizacao_local';
  END IF;
END $$;
INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, conteudo, enviado_em)
SELECT '98100000-0000-4000-8000-000000000001', id, 'LEAD', 'TEXTO',
       'Mensagem sintética para validar recuperação de conexão.', now()
FROM atendimento WHERE id='e1d14e74-0dd4-5b72-1e77-adb12ace8077' AND status='EM_ATENDIMENTO'
AND NOT EXISTS (SELECT 1 FROM mensagem WHERE id='98100000-0000-4000-8000-000000000001');
UPDATE lead SET ultima_mensagem_do_lead_em=now()
WHERE id=(SELECT lead_id FROM atendimento WHERE id='e1d14e74-0dd4-5b72-1e77-adb12ace8077');
COMMIT;
