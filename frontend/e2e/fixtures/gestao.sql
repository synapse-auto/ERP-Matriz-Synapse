-- Fixture de desenvolvimento para e2e/gestao.spec.ts (docs/47). Pressupõe o seed dev (R__seed_dev).
-- Integrantes fictícios @dev.local com a senha do seed de atendentes; presença variada, um
-- desativado e nenhuma permissão salva. Idempotente: pode rodar antes de cada execução.
INSERT INTO usuario (id, nome, email, senha_hash, papel, status_presenca, ativo, senha_alterada_em)
SELECT v.id::uuid, v.nome, v.email, (SELECT senha_hash FROM usuario WHERE email = 'ana@dev.local'),
       'ATENDENTE', v.presenca::status_presenca, v.ativo, now()
  FROM (VALUES
    ('e2e00000-0000-4000-8000-0000000000c1', 'Carla Atendente', 'carla@dev.local', 'ONLINE', TRUE),
    ('e2e00000-0000-4000-8000-0000000000c2', 'Diego Atendente', 'diego@dev.local', 'AUSENTE', TRUE),
    ('e2e00000-0000-4000-8000-0000000000c3', 'Elisa Atendente', 'elisa@dev.local', 'OFFLINE', TRUE),
    ('e2e00000-0000-4000-8000-0000000000c4', 'Fabio Atendente', 'fabio@dev.local', 'OFFLINE', FALSE)
  ) AS v(id, nome, email, presenca, ativo)
ON CONFLICT (email) DO UPDATE SET status_presenca = EXCLUDED.status_presenca, ativo = EXCLUDED.ativo;

UPDATE usuario SET status_presenca = 'ONLINE' WHERE email IN ('gestor@dev.local', 'subgestor@dev.local', 'ana@dev.local');
UPDATE usuario SET status_presenca = 'AUSENTE' WHERE email = 'bruno@dev.local';

INSERT INTO disponibilidade_atendente_ia (atendente_id, disponivel_para_ia)
SELECT id, email IN ('ana@dev.local', 'carla@dev.local') FROM usuario WHERE papel = 'ATENDENTE'
ON CONFLICT (atendente_id) DO UPDATE SET disponivel_para_ia = EXCLUDED.disponivel_para_ia;

-- usuários criados pelos próprios testes (nova-*@dev.local) não sobrevivem entre execuções
DELETE FROM disponibilidade_atendente_ia WHERE atendente_id IN (SELECT id FROM usuario WHERE email LIKE 'nova-%@dev.local');
DELETE FROM permissao_historico WHERE usuario_id IN (SELECT id FROM usuario WHERE email LIKE 'nova-%@dev.local');
DELETE FROM permissao_usuario WHERE usuario_id IN (SELECT id FROM usuario WHERE email LIKE 'nova-%@dev.local');
DELETE FROM usuario WHERE email LIKE 'nova-%@dev.local';

DELETE FROM permissao_perfil_item;
DELETE FROM permissao_usuario_excecao;
