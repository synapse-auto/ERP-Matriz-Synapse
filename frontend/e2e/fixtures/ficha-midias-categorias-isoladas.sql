-- Complementa MidiasFichaLocal para conferir vazio independente e duas paginas nas duas categorias.
-- Somente banco descartavel do helper; reutiliza arquivos sinteticos ja salvos no MinIO local.
BEGIN;
DO $$ BEGIN
    IF current_database() <> 'midias_ficha' THEN
        RAISE EXCEPTION 'Fixture exclusiva do banco descartavel midias_ficha';
    END IF;
END $$;

INSERT INTO lead (id, nome, telefone, status_basico, atendente_responsavel_id, num_atendimentos)
SELECT v.id::uuid, v.nome, v.telefone, 'EM_ATENDIMENTO', u.id, 1
FROM (VALUES
    ('d0c00000-0000-4000-8000-000000000003', 'Cliente Sintético Só Documentos', '5561999900883'),
    ('d0c00000-0000-4000-8000-000000000004', 'Cliente Sintético Só Mídias', '5561999900884')
) AS v(id, nome, telefone)
CROSS JOIN usuario u WHERE u.email = 'ana@dev.local'
ON CONFLICT (id) DO NOTHING;

INSERT INTO atendimento (id, lead_id, canal_id, atendente_id, status, iniciado_em)
SELECT v.id::uuid, v.lead_id::uuid, 'ca000000-0000-4000-8000-000000000001', u.id,
       'EM_ATENDIMENTO', now()
FROM (VALUES
    ('d0c00000-0000-4000-8000-0000000000a3', 'd0c00000-0000-4000-8000-000000000003'),
    ('d0c00000-0000-4000-8000-0000000000a4', 'd0c00000-0000-4000-8000-000000000004')
) AS v(id, lead_id)
CROSS JOIN usuario u WHERE u.email = 'ana@dev.local'
ON CONFLICT (id) DO NOTHING;

INSERT INTO mensagem (atendimento_id, remetente_tipo, remetente_id, tipo, midia_url,
                      midia_metadados, enviado_em, status_entrega)
SELECT 'd0c00000-0000-4000-8000-0000000000a3', m.remetente_tipo, m.remetente_id, m.tipo,
       m.midia_url, m.midia_metadados, now(), m.status_entrega
FROM mensagem m
WHERE m.atendimento_id = 'd0c00000-0000-4000-8000-0000000000a1'
  AND m.midia_metadados->>'nome' = 'imagem-como-documento.png'
  AND NOT EXISTS (SELECT 1 FROM mensagem atual
                  WHERE atual.atendimento_id = 'd0c00000-0000-4000-8000-0000000000a3');

INSERT INTO mensagem (atendimento_id, remetente_tipo, remetente_id, tipo, midia_url,
                      midia_metadados, enviado_em, status_entrega)
SELECT 'd0c00000-0000-4000-8000-0000000000a4', m.remetente_tipo, m.remetente_id, m.tipo,
       m.midia_url, m.midia_metadados || jsonb_build_object('nome', 'pagina-imagem-' || n || '.png'),
       now() - n * interval '1 second', m.status_entrega
FROM mensagem m CROSS JOIN generate_series(1, 21) n
WHERE m.atendimento_id = 'd0c00000-0000-4000-8000-0000000000a1'
  AND m.midia_metadados->>'nome' = 'foto-sintetica.png'
  AND NOT EXISTS (SELECT 1 FROM mensagem atual
                  WHERE atual.atendimento_id = 'd0c00000-0000-4000-8000-0000000000a4');
COMMIT;
