-- E209 — massa sintética do painel de Atendimentos.
--
-- Não é cópia de produção: os volumes são parâmetros. Calibre com os números reais antes de
-- tirar conclusões absolutas (ver README.md desta pasta). Determinística por setseed.
--
-- Uso: psql -v leads=20000 -v msgs_por_atendimento=12 -f seed.sql
\if :{?leads}
\else
  \set leads 20000
\endif
\if :{?msgs_por_atendimento}
\else
  \set msgs_por_atendimento 12
\endif

SELECT setseed(0.209);

INSERT INTO canal (id, nome, tipo, ativo)
VALUES ('00000000-0000-0000-0000-00000000c001', 'WhatsApp bancada', 'WHATSAPP', true);

INSERT INTO etapa_atendimento (id, nome, ordem, cor_visual)
SELECT ('00000000-0000-0000-0000-0000000e' || lpad(n::text, 4, '0'))::uuid, 'Etapa ' || n, n, '#123456'
  FROM generate_series(1, 6) n;

-- 8 atendentes, 1 subgestor, 2 gestores, 1 administrador.
INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo)
SELECT ('00000000-0000-0000-0000-0000000a' || lpad(n::text, 4, '0'))::uuid,
       'Usuario ' || n, 'bench' || n || '@bancada.local', 'x',
       (CASE WHEN n <= 8 THEN 'ATENDENTE' WHEN n = 9 THEN 'SUBGESTOR'
             WHEN n <= 11 THEN 'GESTOR' ELSE 'ADMINISTRADOR' END)::papel_usuario,
       true
  FROM generate_series(1, 12) n;

-- Perfil do lead: ~15% em atendimento humano, ~10% com a IA, o resto finalizado.
CREATE TEMP TABLE perfil_lead AS
SELECT gen_random_uuid() AS id, n,
       CASE WHEN random() < 0.15 THEN 'EM_ATENDIMENTO' WHEN random() < 0.12 THEN 'EM_IA'
            ELSE 'FINALIZADO' END AS situacao,
       ('00000000-0000-0000-0000-0000000a' || lpad((1 + (n % 8))::text, 4, '0'))::uuid AS atendente,
       1 + floor(random() * 3)::int AS ciclos
  FROM generate_series(1, :leads) n;

INSERT INTO lead (id, nome, telefone, empresa, status_basico, etapa_atendimento_id,
                  atendente_responsavel_id, codigo, criado_em, ultima_mensagem_do_lead_em)
SELECT p.id, 'Lead bancada ' || p.n, '55619' || lpad(p.n::text, 8, '0'), 'Empresa ' || (p.n % 500),
       (CASE p.situacao WHEN 'EM_ATENDIMENTO' THEN 'EM_ATENDIMENTO' WHEN 'EM_IA' THEN 'IA'
             ELSE 'FINALIZADO' END)::status_basico_lead,
       ('00000000-0000-0000-0000-0000000e' || lpad((1 + p.n % 6)::text, 4, '0'))::uuid,
       CASE WHEN p.situacao = 'EM_IA' THEN NULL ELSE p.atendente END,
       p.n::text, timestamptz '2026-05-01' + (p.n % 120) * interval '1 day',
       timestamptz '2026-09-20' - (p.n % 140) * interval '1 day'
  FROM perfil_lead p;

-- Ciclos: só o último pode estar aberto; os anteriores são histórico finalizado.
CREATE TEMP TABLE ciclo AS
SELECT gen_random_uuid() AS id, p.id AS lead_id, c AS ordem, p.ciclos,
       CASE WHEN c = p.ciclos THEN p.situacao ELSE 'FINALIZADO' END AS status,
       CASE WHEN c = p.ciclos AND p.situacao = 'EM_IA' THEN NULL ELSE p.atendente END AS atendente,
       timestamptz '2026-05-01' + (p.n % 120) * interval '1 day' + c * interval '10 days'
           + (p.n % 97) * interval '1 minute' AS inicio
  FROM perfil_lead p, generate_series(1, p.ciclos) c;

INSERT INTO atendimento (id, lead_id, canal_id, atendente_id, status, iniciado_em, finalizado_em)
SELECT id, lead_id, '00000000-0000-0000-0000-00000000c001', atendente,
       status::status_atendimento, inicio,
       CASE WHEN status = 'FINALIZADO' THEN inicio + interval '2 days' END
  FROM ciclo;

-- Mensagens: parte cai na partição default (antes de setembro) e parte na de setembro, como na
-- instância real, que tem histórico anterior ao particionamento mensal.
INSERT INTO mensagem (id, atendimento_id, remetente_tipo, remetente_id, tipo, conteudo,
                      status_entrega, enviado_em)
SELECT gen_random_uuid(), c.id,
       (CASE (k % 4) WHEN 0 THEN 'LEAD' WHEN 1 THEN 'ATENDENTE' WHEN 2 THEN 'LEAD' ELSE 'IA' END)::remetente_tipo,
       CASE WHEN k % 4 = 1 THEN c.atendente END,
       'TEXTO', 'mensagem ' || k, 'ENTREGUE',
       LEAST(c.inicio + k * interval '37 minutes', timestamptz '2026-09-23 23:00')
  FROM ciclo c, generate_series(1, 1 + abs(hashtext(c.id::text)) % (2 * :msgs_por_atendimento)) k;

-- Leitura por usuário: gestores e donos leram parte das conversas.
INSERT INTO atendimento_leitura (atendimento_id, usuario_id, lido_ate)
SELECT c.id, u.id, c.inicio + interval '1 day'
  FROM ciclo c
  JOIN usuario u ON u.papel IN ('GESTOR', 'SUBGESTOR') OR u.id = c.atendente
 WHERE random() < 0.5
ON CONFLICT DO NOTHING;

ANALYZE;

SELECT (SELECT count(*) FROM lead) AS leads,
       (SELECT count(*) FROM atendimento) AS atendimentos,
       (SELECT count(*) FROM atendimento WHERE status <> 'FINALIZADO') AS abertos,
       (SELECT count(*) FROM mensagem) AS mensagens,
       (SELECT count(*) FROM mensagem_default) AS mensagens_default;
