-- E224 / B1 - EXPLAIN da listagem do painel: depois.
-- DEPOIS do B1: ids escolhidas como item do FROM, com JOIN explicito a atendimento (texto do PR).
--
-- Rode o par de arquivos (antes e depois) na mesma janela, um bloco por vez, fora do pico se possivel:
-- EXPLAIN ANALYZE EXECUTA a consulta (teto de 60 s abaixo). Nada altera dados: cada bloco e BEGIN ... ROLLBACK.
--
-- Uso (psql, superusuario ou dono do banco, no banco matriz_hml; o JIT ja esta desligado, entao a base de comparacao
-- sao os tempos sem JIT):
--   psql -d matriz_hml -v papel=ATENDENTE -v usuario=<uuid do atendente> -v limite=101 -f <este arquivo>
--   psql -d matriz_hml -v papel=GESTOR    -v usuario=<uuid do gestor>    -v limite=101 -f <este arquivo>
--
-- Cada aba tem DUAS medicoes:
--   N-literal : valores fixos (plano customizado), como nas primeiras execucoes do driver JDBC.
--   N-generico: PREPARE + SET LOCAL plan_cache_mode = force_generic_plan (o plano que o driver pode passar a usar depois
--               de 5 execucoes, porque o pgjdbc usa prepared statement do servidor). Os dois podem diferir.
--
-- O contexto reproduz o AplicadorDeContextoRls (SET LOCAL ROLE synapse_app + app.papel + app.usuario_id). Sem ele a
-- consulta rodaria como dona das tabelas, fora da RLS, e o plano nao seria o de producao.
-- O que olhar no plano da fase 2 (cartoes): a juncao externa deve ter ~101 linhas, e as LATERAL "ativo" e "ultima"
-- devem ter loops ~ numero de ids escolhidas (<= 101), nao ~3.900.
\set ON_ERROR_STOP on
\pset pager off

-- =========================================================================================================
-- 01 PAINEL aba ATIVOS (1a pagina) - DEPOIS
-- =========================================================================================================
-- 1-literal (plano customizado)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT atendimento_id, lead_id, lead_nome, lead_foto_url, lead_empresa, lead_codigo, canal_tipo, etapa_atendimento_id, etapa_nome, etapa_cor, status, atendente_id, atendente_nome, iniciado_em, atendimento_ativo_id, ultima_mensagem_preview, ultima_mensagem_remetente_tipo, ultima_mensagem_em, ultima_mensagem_do_lead_em, nao_lidas, em_negociacao, resultado_venda, valor_venda, venda_registrada_por_id, venda_registrada_por_nome, venda_registrada_em, origem_resultado_venda, linha_do_lead FROM (SELECT a.id AS atendimento_id, a.lead_id, l.nome AS lead_nome,
CASE WHEN l.foto_referencia IS NOT NULL
     THEN '/api/v1/leads/' || l.id::text || '/foto'
     ELSE l.foto_url END AS lead_foto_url,
l.empresa AS lead_empresa, l.codigo AS lead_codigo, c.tipo AS canal_tipo,
l.etapa_atendimento_id, et.nome AS etapa_nome,
et.cor_visual AS etapa_cor, a.status, dono.atendente_id, u.nome AS atendente_nome,
a.em_negociacao, a.resultado_venda, a.valor_venda, a.venda_registrada_por_id,
venda_usuario.nome AS venda_registrada_por_nome, a.venda_registrada_em, a.origem_resultado_venda,
a.iniciado_em AS iniciado_em,
ativo.id AS atendimento_ativo_id,
ultima.conteudo AS ultima_mensagem_preview,
ultima.remetente_tipo AS ultima_mensagem_remetente_tipo,
ultima.enviado_em AS ultima_mensagem_em,
-- E121: mesma fonte do envio (lead.ultima_mensagem_do_lead_em). A LATERAL da E114
-- saiu de proposito - duas definicoes de "janela aberta" divergem com o tempo.
l.ultima_mensagem_do_lead_em AS ultima_mensagem_do_lead_em,
(
    SELECT COALESCE(SUM((
        SELECT count(*) FROM mensagem nao_lida
         WHERE nao_lida.atendimento_id = atendimento_do_lead.id
           AND nao_lida.remetente_tipo = 'LEAD'
           AND nao_lida.enviado_em > COALESCE(leitura_do_lead.lido_ate, 'epoch'::timestamptz)
    )), 0)
      FROM atendimento atendimento_do_lead
      LEFT JOIN atendimento_leitura leitura_do_lead
        ON leitura_do_lead.atendimento_id = atendimento_do_lead.id
       -- Gestao acompanha a leitura do dono exibido no cartao. Sem dono, a
       -- leitura continua pessoal; atendentes/participantes mantem sua propria.
       AND leitura_do_lead.usuario_id = COALESCE(
           CASE WHEN app_enxerga_todos_os_leads() THEN dono.atendente_id END, :'usuario')
     WHERE atendimento_do_lead.lead_id = a.lead_id
) AS nao_lidas,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM (SELECT atendimento_id FROM (SELECT a.id AS atendimento_id,
ultima.enviado_em AS ultima_mensagem_em,
CASE WHEN EXISTS (
    SELECT 1 FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
) THEN 0 ELSE 1 END AS sem_atendimento_aberto,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM atendimento a
JOIN lead l ON l.id = a.lead_id
LEFT JOIN LATERAL (
    SELECT enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
 WHERE EXISTS (SELECT 1 FROM atendimento visivel WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND visivel.atendente_id = :'usuario')) escolha WHERE linha_do_lead = 1 ORDER BY sem_atendimento_aberto ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC LIMIT :limite) escolhidos
JOIN atendimento a ON a.id = escolhidos.atendimento_id
JOIN lead l ON l.id = a.lead_id
LEFT JOIN canal c ON c.id = a.canal_id
LEFT JOIN etapa_atendimento et ON et.id = l.etapa_atendimento_id
LEFT JOIN LATERAL (
    SELECT aberto.id, aberto.status, aberto.atendente_id FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
     ORDER BY COALESCE((
         SELECT max(m_aberto.enviado_em) FROM mensagem m_aberto
          WHERE m_aberto.atendimento_id = aberto.id
     ), aberto.iniciado_em) DESC, aberto.iniciado_em DESC, aberto.id DESC
     LIMIT 1
) ativo ON true
-- E206: o cartao abre o atendimento ativo, entao o dono exibido vem dele. Sem isso, a
-- linha da ultima mensagem (ja FINALIZADA) exibia o dono do ciclo anterior enquanto o
-- cabecalho, via /estado do ativo, exibia o atual. O status segue sendo o da linha.
CROSS JOIN LATERAL (
    SELECT CASE WHEN ativo.id IS NULL THEN a.atendente_id ELSE ativo.atendente_id END
               AS atendente_id
) dono
LEFT JOIN usuario u ON u.id = dono.atendente_id
LEFT JOIN usuario venda_usuario ON venda_usuario.id = a.venda_registrada_por_id
LEFT JOIN LATERAL (
    SELECT conteudo, remetente_tipo, enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
) cartoes WHERE linha_do_lead = 1 ORDER BY CASE WHEN atendimento_ativo_id IS NULL THEN 1 ELSE 0 END ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC;
ROLLBACK;

-- 1-generico (PREPARE + plan_cache_mode = force_generic_plan)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE q_depois_1(uuid, uuid, bigint) AS
SELECT atendimento_id, lead_id, lead_nome, lead_foto_url, lead_empresa, lead_codigo, canal_tipo, etapa_atendimento_id, etapa_nome, etapa_cor, status, atendente_id, atendente_nome, iniciado_em, atendimento_ativo_id, ultima_mensagem_preview, ultima_mensagem_remetente_tipo, ultima_mensagem_em, ultima_mensagem_do_lead_em, nao_lidas, em_negociacao, resultado_venda, valor_venda, venda_registrada_por_id, venda_registrada_por_nome, venda_registrada_em, origem_resultado_venda, linha_do_lead FROM (SELECT a.id AS atendimento_id, a.lead_id, l.nome AS lead_nome,
CASE WHEN l.foto_referencia IS NOT NULL
     THEN '/api/v1/leads/' || l.id::text || '/foto'
     ELSE l.foto_url END AS lead_foto_url,
l.empresa AS lead_empresa, l.codigo AS lead_codigo, c.tipo AS canal_tipo,
l.etapa_atendimento_id, et.nome AS etapa_nome,
et.cor_visual AS etapa_cor, a.status, dono.atendente_id, u.nome AS atendente_nome,
a.em_negociacao, a.resultado_venda, a.valor_venda, a.venda_registrada_por_id,
venda_usuario.nome AS venda_registrada_por_nome, a.venda_registrada_em, a.origem_resultado_venda,
a.iniciado_em AS iniciado_em,
ativo.id AS atendimento_ativo_id,
ultima.conteudo AS ultima_mensagem_preview,
ultima.remetente_tipo AS ultima_mensagem_remetente_tipo,
ultima.enviado_em AS ultima_mensagem_em,
-- E121: mesma fonte do envio (lead.ultima_mensagem_do_lead_em). A LATERAL da E114
-- saiu de proposito - duas definicoes de "janela aberta" divergem com o tempo.
l.ultima_mensagem_do_lead_em AS ultima_mensagem_do_lead_em,
(
    SELECT COALESCE(SUM((
        SELECT count(*) FROM mensagem nao_lida
         WHERE nao_lida.atendimento_id = atendimento_do_lead.id
           AND nao_lida.remetente_tipo = 'LEAD'
           AND nao_lida.enviado_em > COALESCE(leitura_do_lead.lido_ate, 'epoch'::timestamptz)
    )), 0)
      FROM atendimento atendimento_do_lead
      LEFT JOIN atendimento_leitura leitura_do_lead
        ON leitura_do_lead.atendimento_id = atendimento_do_lead.id
       -- Gestao acompanha a leitura do dono exibido no cartao. Sem dono, a
       -- leitura continua pessoal; atendentes/participantes mantem sua propria.
       AND leitura_do_lead.usuario_id = COALESCE(
           CASE WHEN app_enxerga_todos_os_leads() THEN dono.atendente_id END, $1)
     WHERE atendimento_do_lead.lead_id = a.lead_id
) AS nao_lidas,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM (SELECT atendimento_id FROM (SELECT a.id AS atendimento_id,
ultima.enviado_em AS ultima_mensagem_em,
CASE WHEN EXISTS (
    SELECT 1 FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
) THEN 0 ELSE 1 END AS sem_atendimento_aberto,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM atendimento a
JOIN lead l ON l.id = a.lead_id
LEFT JOIN LATERAL (
    SELECT enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
 WHERE EXISTS (SELECT 1 FROM atendimento visivel WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND visivel.atendente_id = $2)) escolha WHERE linha_do_lead = 1 ORDER BY sem_atendimento_aberto ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC LIMIT $3) escolhidos
JOIN atendimento a ON a.id = escolhidos.atendimento_id
JOIN lead l ON l.id = a.lead_id
LEFT JOIN canal c ON c.id = a.canal_id
LEFT JOIN etapa_atendimento et ON et.id = l.etapa_atendimento_id
LEFT JOIN LATERAL (
    SELECT aberto.id, aberto.status, aberto.atendente_id FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
     ORDER BY COALESCE((
         SELECT max(m_aberto.enviado_em) FROM mensagem m_aberto
          WHERE m_aberto.atendimento_id = aberto.id
     ), aberto.iniciado_em) DESC, aberto.iniciado_em DESC, aberto.id DESC
     LIMIT 1
) ativo ON true
-- E206: o cartao abre o atendimento ativo, entao o dono exibido vem dele. Sem isso, a
-- linha da ultima mensagem (ja FINALIZADA) exibia o dono do ciclo anterior enquanto o
-- cabecalho, via /estado do ativo, exibia o atual. O status segue sendo o da linha.
CROSS JOIN LATERAL (
    SELECT CASE WHEN ativo.id IS NULL THEN a.atendente_id ELSE ativo.atendente_id END
               AS atendente_id
) dono
LEFT JOIN usuario u ON u.id = dono.atendente_id
LEFT JOIN usuario venda_usuario ON venda_usuario.id = a.venda_registrada_por_id
LEFT JOIN LATERAL (
    SELECT conteudo, remetente_tipo, enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
) cartoes WHERE linha_do_lead = 1 ORDER BY CASE WHEN atendimento_ativo_id IS NULL THEN 1 ELSE 0 END ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC;
SET LOCAL plan_cache_mode = force_generic_plan;
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE q_depois_1(:'usuario', :'usuario', :limite);
ROLLBACK;
DEALLOCATE q_depois_1;

-- =========================================================================================================
-- 02 PAINEL aba PENDENTES, atendente (restrito ao proprio) - DEPOIS
-- =========================================================================================================
-- 2-literal (plano customizado)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT atendimento_id, lead_id, lead_nome, lead_foto_url, lead_empresa, lead_codigo, canal_tipo, etapa_atendimento_id, etapa_nome, etapa_cor, status, atendente_id, atendente_nome, iniciado_em, atendimento_ativo_id, ultima_mensagem_preview, ultima_mensagem_remetente_tipo, ultima_mensagem_em, ultima_mensagem_do_lead_em, nao_lidas, em_negociacao, resultado_venda, valor_venda, venda_registrada_por_id, venda_registrada_por_nome, venda_registrada_em, origem_resultado_venda, linha_do_lead FROM (SELECT a.id AS atendimento_id, a.lead_id, l.nome AS lead_nome,
CASE WHEN l.foto_referencia IS NOT NULL
     THEN '/api/v1/leads/' || l.id::text || '/foto'
     ELSE l.foto_url END AS lead_foto_url,
l.empresa AS lead_empresa, l.codigo AS lead_codigo, c.tipo AS canal_tipo,
l.etapa_atendimento_id, et.nome AS etapa_nome,
et.cor_visual AS etapa_cor, a.status, dono.atendente_id, u.nome AS atendente_nome,
a.em_negociacao, a.resultado_venda, a.valor_venda, a.venda_registrada_por_id,
venda_usuario.nome AS venda_registrada_por_nome, a.venda_registrada_em, a.origem_resultado_venda,
a.iniciado_em AS iniciado_em,
ativo.id AS atendimento_ativo_id,
ultima.conteudo AS ultima_mensagem_preview,
ultima.remetente_tipo AS ultima_mensagem_remetente_tipo,
ultima.enviado_em AS ultima_mensagem_em,
-- E121: mesma fonte do envio (lead.ultima_mensagem_do_lead_em). A LATERAL da E114
-- saiu de proposito - duas definicoes de "janela aberta" divergem com o tempo.
l.ultima_mensagem_do_lead_em AS ultima_mensagem_do_lead_em,
(
    SELECT COALESCE(SUM((
        SELECT count(*) FROM mensagem nao_lida
         WHERE nao_lida.atendimento_id = atendimento_do_lead.id
           AND nao_lida.remetente_tipo = 'LEAD'
           AND nao_lida.enviado_em > COALESCE(leitura_do_lead.lido_ate, 'epoch'::timestamptz)
    )), 0)
      FROM atendimento atendimento_do_lead
      LEFT JOIN atendimento_leitura leitura_do_lead
        ON leitura_do_lead.atendimento_id = atendimento_do_lead.id
       -- Gestao acompanha a leitura do dono exibido no cartao. Sem dono, a
       -- leitura continua pessoal; atendentes/participantes mantem sua propria.
       AND leitura_do_lead.usuario_id = COALESCE(
           CASE WHEN app_enxerga_todos_os_leads() THEN dono.atendente_id END, :'usuario')
     WHERE atendimento_do_lead.lead_id = a.lead_id
) AS nao_lidas,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM (SELECT atendimento_id FROM (SELECT a.id AS atendimento_id,
ultima.enviado_em AS ultima_mensagem_em,
CASE WHEN EXISTS (
    SELECT 1 FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
) THEN 0 ELSE 1 END AS sem_atendimento_aberto,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM atendimento a
JOIN lead l ON l.id = a.lead_id
LEFT JOIN LATERAL (
    SELECT enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
 WHERE EXISTS (SELECT 1 FROM atendimento visivel LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = visivel.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND ((visivel.atendente_id = :'usuario' AND ultima_visivel.remetente_tipo = 'LEAD') OR EXISTS (SELECT 1 FROM pedido_entrada_atendimento convite WHERE convite.atendimento_id = visivel.id AND convite.solicitante_id = :'usuario' AND convite.tipo = 'CONVITE' AND convite.status = 'PENDENTE' AND convite.solicitado_em > now() - app_validade_pedido_entrada())) )) escolha WHERE linha_do_lead = 1 ORDER BY sem_atendimento_aberto ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC LIMIT :limite) escolhidos
JOIN atendimento a ON a.id = escolhidos.atendimento_id
JOIN lead l ON l.id = a.lead_id
LEFT JOIN canal c ON c.id = a.canal_id
LEFT JOIN etapa_atendimento et ON et.id = l.etapa_atendimento_id
LEFT JOIN LATERAL (
    SELECT aberto.id, aberto.status, aberto.atendente_id FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
     ORDER BY COALESCE((
         SELECT max(m_aberto.enviado_em) FROM mensagem m_aberto
          WHERE m_aberto.atendimento_id = aberto.id
     ), aberto.iniciado_em) DESC, aberto.iniciado_em DESC, aberto.id DESC
     LIMIT 1
) ativo ON true
-- E206: o cartao abre o atendimento ativo, entao o dono exibido vem dele. Sem isso, a
-- linha da ultima mensagem (ja FINALIZADA) exibia o dono do ciclo anterior enquanto o
-- cabecalho, via /estado do ativo, exibia o atual. O status segue sendo o da linha.
CROSS JOIN LATERAL (
    SELECT CASE WHEN ativo.id IS NULL THEN a.atendente_id ELSE ativo.atendente_id END
               AS atendente_id
) dono
LEFT JOIN usuario u ON u.id = dono.atendente_id
LEFT JOIN usuario venda_usuario ON venda_usuario.id = a.venda_registrada_por_id
LEFT JOIN LATERAL (
    SELECT conteudo, remetente_tipo, enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
) cartoes WHERE linha_do_lead = 1 ORDER BY CASE WHEN atendimento_ativo_id IS NULL THEN 1 ELSE 0 END ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC;
ROLLBACK;

-- 2-generico (PREPARE + plan_cache_mode = force_generic_plan)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE q_depois_2(uuid, uuid, uuid, bigint) AS
SELECT atendimento_id, lead_id, lead_nome, lead_foto_url, lead_empresa, lead_codigo, canal_tipo, etapa_atendimento_id, etapa_nome, etapa_cor, status, atendente_id, atendente_nome, iniciado_em, atendimento_ativo_id, ultima_mensagem_preview, ultima_mensagem_remetente_tipo, ultima_mensagem_em, ultima_mensagem_do_lead_em, nao_lidas, em_negociacao, resultado_venda, valor_venda, venda_registrada_por_id, venda_registrada_por_nome, venda_registrada_em, origem_resultado_venda, linha_do_lead FROM (SELECT a.id AS atendimento_id, a.lead_id, l.nome AS lead_nome,
CASE WHEN l.foto_referencia IS NOT NULL
     THEN '/api/v1/leads/' || l.id::text || '/foto'
     ELSE l.foto_url END AS lead_foto_url,
l.empresa AS lead_empresa, l.codigo AS lead_codigo, c.tipo AS canal_tipo,
l.etapa_atendimento_id, et.nome AS etapa_nome,
et.cor_visual AS etapa_cor, a.status, dono.atendente_id, u.nome AS atendente_nome,
a.em_negociacao, a.resultado_venda, a.valor_venda, a.venda_registrada_por_id,
venda_usuario.nome AS venda_registrada_por_nome, a.venda_registrada_em, a.origem_resultado_venda,
a.iniciado_em AS iniciado_em,
ativo.id AS atendimento_ativo_id,
ultima.conteudo AS ultima_mensagem_preview,
ultima.remetente_tipo AS ultima_mensagem_remetente_tipo,
ultima.enviado_em AS ultima_mensagem_em,
-- E121: mesma fonte do envio (lead.ultima_mensagem_do_lead_em). A LATERAL da E114
-- saiu de proposito - duas definicoes de "janela aberta" divergem com o tempo.
l.ultima_mensagem_do_lead_em AS ultima_mensagem_do_lead_em,
(
    SELECT COALESCE(SUM((
        SELECT count(*) FROM mensagem nao_lida
         WHERE nao_lida.atendimento_id = atendimento_do_lead.id
           AND nao_lida.remetente_tipo = 'LEAD'
           AND nao_lida.enviado_em > COALESCE(leitura_do_lead.lido_ate, 'epoch'::timestamptz)
    )), 0)
      FROM atendimento atendimento_do_lead
      LEFT JOIN atendimento_leitura leitura_do_lead
        ON leitura_do_lead.atendimento_id = atendimento_do_lead.id
       -- Gestao acompanha a leitura do dono exibido no cartao. Sem dono, a
       -- leitura continua pessoal; atendentes/participantes mantem sua propria.
       AND leitura_do_lead.usuario_id = COALESCE(
           CASE WHEN app_enxerga_todos_os_leads() THEN dono.atendente_id END, $1)
     WHERE atendimento_do_lead.lead_id = a.lead_id
) AS nao_lidas,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM (SELECT atendimento_id FROM (SELECT a.id AS atendimento_id,
ultima.enviado_em AS ultima_mensagem_em,
CASE WHEN EXISTS (
    SELECT 1 FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
) THEN 0 ELSE 1 END AS sem_atendimento_aberto,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM atendimento a
JOIN lead l ON l.id = a.lead_id
LEFT JOIN LATERAL (
    SELECT enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
 WHERE EXISTS (SELECT 1 FROM atendimento visivel LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = visivel.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND ((visivel.atendente_id = $2 AND ultima_visivel.remetente_tipo = 'LEAD') OR EXISTS (SELECT 1 FROM pedido_entrada_atendimento convite WHERE convite.atendimento_id = visivel.id AND convite.solicitante_id = $3 AND convite.tipo = 'CONVITE' AND convite.status = 'PENDENTE' AND convite.solicitado_em > now() - app_validade_pedido_entrada())) )) escolha WHERE linha_do_lead = 1 ORDER BY sem_atendimento_aberto ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC LIMIT $4) escolhidos
JOIN atendimento a ON a.id = escolhidos.atendimento_id
JOIN lead l ON l.id = a.lead_id
LEFT JOIN canal c ON c.id = a.canal_id
LEFT JOIN etapa_atendimento et ON et.id = l.etapa_atendimento_id
LEFT JOIN LATERAL (
    SELECT aberto.id, aberto.status, aberto.atendente_id FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
     ORDER BY COALESCE((
         SELECT max(m_aberto.enviado_em) FROM mensagem m_aberto
          WHERE m_aberto.atendimento_id = aberto.id
     ), aberto.iniciado_em) DESC, aberto.iniciado_em DESC, aberto.id DESC
     LIMIT 1
) ativo ON true
-- E206: o cartao abre o atendimento ativo, entao o dono exibido vem dele. Sem isso, a
-- linha da ultima mensagem (ja FINALIZADA) exibia o dono do ciclo anterior enquanto o
-- cabecalho, via /estado do ativo, exibia o atual. O status segue sendo o da linha.
CROSS JOIN LATERAL (
    SELECT CASE WHEN ativo.id IS NULL THEN a.atendente_id ELSE ativo.atendente_id END
               AS atendente_id
) dono
LEFT JOIN usuario u ON u.id = dono.atendente_id
LEFT JOIN usuario venda_usuario ON venda_usuario.id = a.venda_registrada_por_id
LEFT JOIN LATERAL (
    SELECT conteudo, remetente_tipo, enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
) cartoes WHERE linha_do_lead = 1 ORDER BY CASE WHEN atendimento_ativo_id IS NULL THEN 1 ELSE 0 END ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC;
SET LOCAL plan_cache_mode = force_generic_plan;
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE q_depois_2(:'usuario', :'usuario', :'usuario', :limite);
ROLLBACK;
DEALLOCATE q_depois_2;

-- =========================================================================================================
-- 03 PAINEL aba PENDENTES, gestao (todos) - DEPOIS
-- =========================================================================================================
-- 3-literal (plano customizado)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT atendimento_id, lead_id, lead_nome, lead_foto_url, lead_empresa, lead_codigo, canal_tipo, etapa_atendimento_id, etapa_nome, etapa_cor, status, atendente_id, atendente_nome, iniciado_em, atendimento_ativo_id, ultima_mensagem_preview, ultima_mensagem_remetente_tipo, ultima_mensagem_em, ultima_mensagem_do_lead_em, nao_lidas, em_negociacao, resultado_venda, valor_venda, venda_registrada_por_id, venda_registrada_por_nome, venda_registrada_em, origem_resultado_venda, linha_do_lead FROM (SELECT a.id AS atendimento_id, a.lead_id, l.nome AS lead_nome,
CASE WHEN l.foto_referencia IS NOT NULL
     THEN '/api/v1/leads/' || l.id::text || '/foto'
     ELSE l.foto_url END AS lead_foto_url,
l.empresa AS lead_empresa, l.codigo AS lead_codigo, c.tipo AS canal_tipo,
l.etapa_atendimento_id, et.nome AS etapa_nome,
et.cor_visual AS etapa_cor, a.status, dono.atendente_id, u.nome AS atendente_nome,
a.em_negociacao, a.resultado_venda, a.valor_venda, a.venda_registrada_por_id,
venda_usuario.nome AS venda_registrada_por_nome, a.venda_registrada_em, a.origem_resultado_venda,
a.iniciado_em AS iniciado_em,
ativo.id AS atendimento_ativo_id,
ultima.conteudo AS ultima_mensagem_preview,
ultima.remetente_tipo AS ultima_mensagem_remetente_tipo,
ultima.enviado_em AS ultima_mensagem_em,
-- E121: mesma fonte do envio (lead.ultima_mensagem_do_lead_em). A LATERAL da E114
-- saiu de proposito - duas definicoes de "janela aberta" divergem com o tempo.
l.ultima_mensagem_do_lead_em AS ultima_mensagem_do_lead_em,
(
    SELECT COALESCE(SUM((
        SELECT count(*) FROM mensagem nao_lida
         WHERE nao_lida.atendimento_id = atendimento_do_lead.id
           AND nao_lida.remetente_tipo = 'LEAD'
           AND nao_lida.enviado_em > COALESCE(leitura_do_lead.lido_ate, 'epoch'::timestamptz)
    )), 0)
      FROM atendimento atendimento_do_lead
      LEFT JOIN atendimento_leitura leitura_do_lead
        ON leitura_do_lead.atendimento_id = atendimento_do_lead.id
       -- Gestao acompanha a leitura do dono exibido no cartao. Sem dono, a
       -- leitura continua pessoal; atendentes/participantes mantem sua propria.
       AND leitura_do_lead.usuario_id = COALESCE(
           CASE WHEN app_enxerga_todos_os_leads() THEN dono.atendente_id END, :'usuario')
     WHERE atendimento_do_lead.lead_id = a.lead_id
) AS nao_lidas,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM (SELECT atendimento_id FROM (SELECT a.id AS atendimento_id,
ultima.enviado_em AS ultima_mensagem_em,
CASE WHEN EXISTS (
    SELECT 1 FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
) THEN 0 ELSE 1 END AS sem_atendimento_aberto,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM atendimento a
JOIN lead l ON l.id = a.lead_id
LEFT JOIN LATERAL (
    SELECT enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
 WHERE EXISTS (SELECT 1 FROM atendimento visivel LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = visivel.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND ultima_visivel.remetente_tipo = 'LEAD')) escolha WHERE linha_do_lead = 1 ORDER BY sem_atendimento_aberto ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC LIMIT :limite) escolhidos
JOIN atendimento a ON a.id = escolhidos.atendimento_id
JOIN lead l ON l.id = a.lead_id
LEFT JOIN canal c ON c.id = a.canal_id
LEFT JOIN etapa_atendimento et ON et.id = l.etapa_atendimento_id
LEFT JOIN LATERAL (
    SELECT aberto.id, aberto.status, aberto.atendente_id FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
     ORDER BY COALESCE((
         SELECT max(m_aberto.enviado_em) FROM mensagem m_aberto
          WHERE m_aberto.atendimento_id = aberto.id
     ), aberto.iniciado_em) DESC, aberto.iniciado_em DESC, aberto.id DESC
     LIMIT 1
) ativo ON true
-- E206: o cartao abre o atendimento ativo, entao o dono exibido vem dele. Sem isso, a
-- linha da ultima mensagem (ja FINALIZADA) exibia o dono do ciclo anterior enquanto o
-- cabecalho, via /estado do ativo, exibia o atual. O status segue sendo o da linha.
CROSS JOIN LATERAL (
    SELECT CASE WHEN ativo.id IS NULL THEN a.atendente_id ELSE ativo.atendente_id END
               AS atendente_id
) dono
LEFT JOIN usuario u ON u.id = dono.atendente_id
LEFT JOIN usuario venda_usuario ON venda_usuario.id = a.venda_registrada_por_id
LEFT JOIN LATERAL (
    SELECT conteudo, remetente_tipo, enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
) cartoes WHERE linha_do_lead = 1 ORDER BY CASE WHEN atendimento_ativo_id IS NULL THEN 1 ELSE 0 END ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC;
ROLLBACK;

-- 3-generico (PREPARE + plan_cache_mode = force_generic_plan)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE q_depois_3(uuid, bigint) AS
SELECT atendimento_id, lead_id, lead_nome, lead_foto_url, lead_empresa, lead_codigo, canal_tipo, etapa_atendimento_id, etapa_nome, etapa_cor, status, atendente_id, atendente_nome, iniciado_em, atendimento_ativo_id, ultima_mensagem_preview, ultima_mensagem_remetente_tipo, ultima_mensagem_em, ultima_mensagem_do_lead_em, nao_lidas, em_negociacao, resultado_venda, valor_venda, venda_registrada_por_id, venda_registrada_por_nome, venda_registrada_em, origem_resultado_venda, linha_do_lead FROM (SELECT a.id AS atendimento_id, a.lead_id, l.nome AS lead_nome,
CASE WHEN l.foto_referencia IS NOT NULL
     THEN '/api/v1/leads/' || l.id::text || '/foto'
     ELSE l.foto_url END AS lead_foto_url,
l.empresa AS lead_empresa, l.codigo AS lead_codigo, c.tipo AS canal_tipo,
l.etapa_atendimento_id, et.nome AS etapa_nome,
et.cor_visual AS etapa_cor, a.status, dono.atendente_id, u.nome AS atendente_nome,
a.em_negociacao, a.resultado_venda, a.valor_venda, a.venda_registrada_por_id,
venda_usuario.nome AS venda_registrada_por_nome, a.venda_registrada_em, a.origem_resultado_venda,
a.iniciado_em AS iniciado_em,
ativo.id AS atendimento_ativo_id,
ultima.conteudo AS ultima_mensagem_preview,
ultima.remetente_tipo AS ultima_mensagem_remetente_tipo,
ultima.enviado_em AS ultima_mensagem_em,
-- E121: mesma fonte do envio (lead.ultima_mensagem_do_lead_em). A LATERAL da E114
-- saiu de proposito - duas definicoes de "janela aberta" divergem com o tempo.
l.ultima_mensagem_do_lead_em AS ultima_mensagem_do_lead_em,
(
    SELECT COALESCE(SUM((
        SELECT count(*) FROM mensagem nao_lida
         WHERE nao_lida.atendimento_id = atendimento_do_lead.id
           AND nao_lida.remetente_tipo = 'LEAD'
           AND nao_lida.enviado_em > COALESCE(leitura_do_lead.lido_ate, 'epoch'::timestamptz)
    )), 0)
      FROM atendimento atendimento_do_lead
      LEFT JOIN atendimento_leitura leitura_do_lead
        ON leitura_do_lead.atendimento_id = atendimento_do_lead.id
       -- Gestao acompanha a leitura do dono exibido no cartao. Sem dono, a
       -- leitura continua pessoal; atendentes/participantes mantem sua propria.
       AND leitura_do_lead.usuario_id = COALESCE(
           CASE WHEN app_enxerga_todos_os_leads() THEN dono.atendente_id END, $1)
     WHERE atendimento_do_lead.lead_id = a.lead_id
) AS nao_lidas,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM (SELECT atendimento_id FROM (SELECT a.id AS atendimento_id,
ultima.enviado_em AS ultima_mensagem_em,
CASE WHEN EXISTS (
    SELECT 1 FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
) THEN 0 ELSE 1 END AS sem_atendimento_aberto,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM atendimento a
JOIN lead l ON l.id = a.lead_id
LEFT JOIN LATERAL (
    SELECT enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
 WHERE EXISTS (SELECT 1 FROM atendimento visivel LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = visivel.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND ultima_visivel.remetente_tipo = 'LEAD')) escolha WHERE linha_do_lead = 1 ORDER BY sem_atendimento_aberto ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC LIMIT $2) escolhidos
JOIN atendimento a ON a.id = escolhidos.atendimento_id
JOIN lead l ON l.id = a.lead_id
LEFT JOIN canal c ON c.id = a.canal_id
LEFT JOIN etapa_atendimento et ON et.id = l.etapa_atendimento_id
LEFT JOIN LATERAL (
    SELECT aberto.id, aberto.status, aberto.atendente_id FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
     ORDER BY COALESCE((
         SELECT max(m_aberto.enviado_em) FROM mensagem m_aberto
          WHERE m_aberto.atendimento_id = aberto.id
     ), aberto.iniciado_em) DESC, aberto.iniciado_em DESC, aberto.id DESC
     LIMIT 1
) ativo ON true
-- E206: o cartao abre o atendimento ativo, entao o dono exibido vem dele. Sem isso, a
-- linha da ultima mensagem (ja FINALIZADA) exibia o dono do ciclo anterior enquanto o
-- cabecalho, via /estado do ativo, exibia o atual. O status segue sendo o da linha.
CROSS JOIN LATERAL (
    SELECT CASE WHEN ativo.id IS NULL THEN a.atendente_id ELSE ativo.atendente_id END
               AS atendente_id
) dono
LEFT JOIN usuario u ON u.id = dono.atendente_id
LEFT JOIN usuario venda_usuario ON venda_usuario.id = a.venda_registrada_por_id
LEFT JOIN LATERAL (
    SELECT conteudo, remetente_tipo, enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
) cartoes WHERE linha_do_lead = 1 ORDER BY CASE WHEN atendimento_ativo_id IS NULL THEN 1 ELSE 0 END ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC;
SET LOCAL plan_cache_mode = force_generic_plan;
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE q_depois_3(:'usuario', :limite);
ROLLBACK;
DEALLOCATE q_depois_3;

-- =========================================================================================================
-- 04 PAINEL aba POTENCIAIS - DEPOIS
-- =========================================================================================================
-- 4-literal (plano customizado)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT atendimento_id, lead_id, lead_nome, lead_foto_url, lead_empresa, lead_codigo, canal_tipo, etapa_atendimento_id, etapa_nome, etapa_cor, status, atendente_id, atendente_nome, iniciado_em, atendimento_ativo_id, ultima_mensagem_preview, ultima_mensagem_remetente_tipo, ultima_mensagem_em, ultima_mensagem_do_lead_em, nao_lidas, em_negociacao, resultado_venda, valor_venda, venda_registrada_por_id, venda_registrada_por_nome, venda_registrada_em, origem_resultado_venda, linha_do_lead FROM (SELECT a.id AS atendimento_id, a.lead_id, l.nome AS lead_nome,
CASE WHEN l.foto_referencia IS NOT NULL
     THEN '/api/v1/leads/' || l.id::text || '/foto'
     ELSE l.foto_url END AS lead_foto_url,
l.empresa AS lead_empresa, l.codigo AS lead_codigo, c.tipo AS canal_tipo,
l.etapa_atendimento_id, et.nome AS etapa_nome,
et.cor_visual AS etapa_cor, a.status, dono.atendente_id, u.nome AS atendente_nome,
a.em_negociacao, a.resultado_venda, a.valor_venda, a.venda_registrada_por_id,
venda_usuario.nome AS venda_registrada_por_nome, a.venda_registrada_em, a.origem_resultado_venda,
a.iniciado_em AS iniciado_em,
ativo.id AS atendimento_ativo_id,
ultima.conteudo AS ultima_mensagem_preview,
ultima.remetente_tipo AS ultima_mensagem_remetente_tipo,
ultima.enviado_em AS ultima_mensagem_em,
-- E121: mesma fonte do envio (lead.ultima_mensagem_do_lead_em). A LATERAL da E114
-- saiu de proposito - duas definicoes de "janela aberta" divergem com o tempo.
l.ultima_mensagem_do_lead_em AS ultima_mensagem_do_lead_em,
(
    SELECT COALESCE(SUM((
        SELECT count(*) FROM mensagem nao_lida
         WHERE nao_lida.atendimento_id = atendimento_do_lead.id
           AND nao_lida.remetente_tipo = 'LEAD'
           AND nao_lida.enviado_em > COALESCE(leitura_do_lead.lido_ate, 'epoch'::timestamptz)
    )), 0)
      FROM atendimento atendimento_do_lead
      LEFT JOIN atendimento_leitura leitura_do_lead
        ON leitura_do_lead.atendimento_id = atendimento_do_lead.id
       -- Gestao acompanha a leitura do dono exibido no cartao. Sem dono, a
       -- leitura continua pessoal; atendentes/participantes mantem sua propria.
       AND leitura_do_lead.usuario_id = COALESCE(
           CASE WHEN app_enxerga_todos_os_leads() THEN dono.atendente_id END, :'usuario')
     WHERE atendimento_do_lead.lead_id = a.lead_id
) AS nao_lidas,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM (SELECT atendimento_id FROM (SELECT a.id AS atendimento_id,
ultima.enviado_em AS ultima_mensagem_em,
CASE WHEN EXISTS (
    SELECT 1 FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
) THEN 0 ELSE 1 END AS sem_atendimento_aberto,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM atendimento a
JOIN lead l ON l.id = a.lead_id
LEFT JOIN LATERAL (
    SELECT enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
 WHERE EXISTS (SELECT 1 FROM atendimento visivel WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_IA')) escolha WHERE linha_do_lead = 1 ORDER BY sem_atendimento_aberto ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC LIMIT :limite) escolhidos
JOIN atendimento a ON a.id = escolhidos.atendimento_id
JOIN lead l ON l.id = a.lead_id
LEFT JOIN canal c ON c.id = a.canal_id
LEFT JOIN etapa_atendimento et ON et.id = l.etapa_atendimento_id
LEFT JOIN LATERAL (
    SELECT aberto.id, aberto.status, aberto.atendente_id FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
     ORDER BY COALESCE((
         SELECT max(m_aberto.enviado_em) FROM mensagem m_aberto
          WHERE m_aberto.atendimento_id = aberto.id
     ), aberto.iniciado_em) DESC, aberto.iniciado_em DESC, aberto.id DESC
     LIMIT 1
) ativo ON true
-- E206: o cartao abre o atendimento ativo, entao o dono exibido vem dele. Sem isso, a
-- linha da ultima mensagem (ja FINALIZADA) exibia o dono do ciclo anterior enquanto o
-- cabecalho, via /estado do ativo, exibia o atual. O status segue sendo o da linha.
CROSS JOIN LATERAL (
    SELECT CASE WHEN ativo.id IS NULL THEN a.atendente_id ELSE ativo.atendente_id END
               AS atendente_id
) dono
LEFT JOIN usuario u ON u.id = dono.atendente_id
LEFT JOIN usuario venda_usuario ON venda_usuario.id = a.venda_registrada_por_id
LEFT JOIN LATERAL (
    SELECT conteudo, remetente_tipo, enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
) cartoes WHERE linha_do_lead = 1 ORDER BY CASE WHEN atendimento_ativo_id IS NULL THEN 1 ELSE 0 END ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC;
ROLLBACK;

-- 4-generico (PREPARE + plan_cache_mode = force_generic_plan)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE q_depois_4(uuid, bigint) AS
SELECT atendimento_id, lead_id, lead_nome, lead_foto_url, lead_empresa, lead_codigo, canal_tipo, etapa_atendimento_id, etapa_nome, etapa_cor, status, atendente_id, atendente_nome, iniciado_em, atendimento_ativo_id, ultima_mensagem_preview, ultima_mensagem_remetente_tipo, ultima_mensagem_em, ultima_mensagem_do_lead_em, nao_lidas, em_negociacao, resultado_venda, valor_venda, venda_registrada_por_id, venda_registrada_por_nome, venda_registrada_em, origem_resultado_venda, linha_do_lead FROM (SELECT a.id AS atendimento_id, a.lead_id, l.nome AS lead_nome,
CASE WHEN l.foto_referencia IS NOT NULL
     THEN '/api/v1/leads/' || l.id::text || '/foto'
     ELSE l.foto_url END AS lead_foto_url,
l.empresa AS lead_empresa, l.codigo AS lead_codigo, c.tipo AS canal_tipo,
l.etapa_atendimento_id, et.nome AS etapa_nome,
et.cor_visual AS etapa_cor, a.status, dono.atendente_id, u.nome AS atendente_nome,
a.em_negociacao, a.resultado_venda, a.valor_venda, a.venda_registrada_por_id,
venda_usuario.nome AS venda_registrada_por_nome, a.venda_registrada_em, a.origem_resultado_venda,
a.iniciado_em AS iniciado_em,
ativo.id AS atendimento_ativo_id,
ultima.conteudo AS ultima_mensagem_preview,
ultima.remetente_tipo AS ultima_mensagem_remetente_tipo,
ultima.enviado_em AS ultima_mensagem_em,
-- E121: mesma fonte do envio (lead.ultima_mensagem_do_lead_em). A LATERAL da E114
-- saiu de proposito - duas definicoes de "janela aberta" divergem com o tempo.
l.ultima_mensagem_do_lead_em AS ultima_mensagem_do_lead_em,
(
    SELECT COALESCE(SUM((
        SELECT count(*) FROM mensagem nao_lida
         WHERE nao_lida.atendimento_id = atendimento_do_lead.id
           AND nao_lida.remetente_tipo = 'LEAD'
           AND nao_lida.enviado_em > COALESCE(leitura_do_lead.lido_ate, 'epoch'::timestamptz)
    )), 0)
      FROM atendimento atendimento_do_lead
      LEFT JOIN atendimento_leitura leitura_do_lead
        ON leitura_do_lead.atendimento_id = atendimento_do_lead.id
       -- Gestao acompanha a leitura do dono exibido no cartao. Sem dono, a
       -- leitura continua pessoal; atendentes/participantes mantem sua propria.
       AND leitura_do_lead.usuario_id = COALESCE(
           CASE WHEN app_enxerga_todos_os_leads() THEN dono.atendente_id END, $1)
     WHERE atendimento_do_lead.lead_id = a.lead_id
) AS nao_lidas,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM (SELECT atendimento_id FROM (SELECT a.id AS atendimento_id,
ultima.enviado_em AS ultima_mensagem_em,
CASE WHEN EXISTS (
    SELECT 1 FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
) THEN 0 ELSE 1 END AS sem_atendimento_aberto,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM atendimento a
JOIN lead l ON l.id = a.lead_id
LEFT JOIN LATERAL (
    SELECT enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
 WHERE EXISTS (SELECT 1 FROM atendimento visivel WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_IA')) escolha WHERE linha_do_lead = 1 ORDER BY sem_atendimento_aberto ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC LIMIT $2) escolhidos
JOIN atendimento a ON a.id = escolhidos.atendimento_id
JOIN lead l ON l.id = a.lead_id
LEFT JOIN canal c ON c.id = a.canal_id
LEFT JOIN etapa_atendimento et ON et.id = l.etapa_atendimento_id
LEFT JOIN LATERAL (
    SELECT aberto.id, aberto.status, aberto.atendente_id FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
     ORDER BY COALESCE((
         SELECT max(m_aberto.enviado_em) FROM mensagem m_aberto
          WHERE m_aberto.atendimento_id = aberto.id
     ), aberto.iniciado_em) DESC, aberto.iniciado_em DESC, aberto.id DESC
     LIMIT 1
) ativo ON true
-- E206: o cartao abre o atendimento ativo, entao o dono exibido vem dele. Sem isso, a
-- linha da ultima mensagem (ja FINALIZADA) exibia o dono do ciclo anterior enquanto o
-- cabecalho, via /estado do ativo, exibia o atual. O status segue sendo o da linha.
CROSS JOIN LATERAL (
    SELECT CASE WHEN ativo.id IS NULL THEN a.atendente_id ELSE ativo.atendente_id END
               AS atendente_id
) dono
LEFT JOIN usuario u ON u.id = dono.atendente_id
LEFT JOIN usuario venda_usuario ON venda_usuario.id = a.venda_registrada_por_id
LEFT JOIN LATERAL (
    SELECT conteudo, remetente_tipo, enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
) cartoes WHERE linha_do_lead = 1 ORDER BY CASE WHEN atendimento_ativo_id IS NULL THEN 1 ELSE 0 END ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC;
SET LOCAL plan_cache_mode = force_generic_plan;
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE q_depois_4(:'usuario', :limite);
ROLLBACK;
DEALLOCATE q_depois_4;

-- =========================================================================================================
-- 05 PAINEL aba TODOS - DEPOIS
-- =========================================================================================================
-- 5-literal (plano customizado)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT atendimento_id, lead_id, lead_nome, lead_foto_url, lead_empresa, lead_codigo, canal_tipo, etapa_atendimento_id, etapa_nome, etapa_cor, status, atendente_id, atendente_nome, iniciado_em, atendimento_ativo_id, ultima_mensagem_preview, ultima_mensagem_remetente_tipo, ultima_mensagem_em, ultima_mensagem_do_lead_em, nao_lidas, em_negociacao, resultado_venda, valor_venda, venda_registrada_por_id, venda_registrada_por_nome, venda_registrada_em, origem_resultado_venda, linha_do_lead FROM (SELECT a.id AS atendimento_id, a.lead_id, l.nome AS lead_nome,
CASE WHEN l.foto_referencia IS NOT NULL
     THEN '/api/v1/leads/' || l.id::text || '/foto'
     ELSE l.foto_url END AS lead_foto_url,
l.empresa AS lead_empresa, l.codigo AS lead_codigo, c.tipo AS canal_tipo,
l.etapa_atendimento_id, et.nome AS etapa_nome,
et.cor_visual AS etapa_cor, a.status, dono.atendente_id, u.nome AS atendente_nome,
a.em_negociacao, a.resultado_venda, a.valor_venda, a.venda_registrada_por_id,
venda_usuario.nome AS venda_registrada_por_nome, a.venda_registrada_em, a.origem_resultado_venda,
a.iniciado_em AS iniciado_em,
ativo.id AS atendimento_ativo_id,
ultima.conteudo AS ultima_mensagem_preview,
ultima.remetente_tipo AS ultima_mensagem_remetente_tipo,
ultima.enviado_em AS ultima_mensagem_em,
-- E121: mesma fonte do envio (lead.ultima_mensagem_do_lead_em). A LATERAL da E114
-- saiu de proposito - duas definicoes de "janela aberta" divergem com o tempo.
l.ultima_mensagem_do_lead_em AS ultima_mensagem_do_lead_em,
(
    SELECT COALESCE(SUM((
        SELECT count(*) FROM mensagem nao_lida
         WHERE nao_lida.atendimento_id = atendimento_do_lead.id
           AND nao_lida.remetente_tipo = 'LEAD'
           AND nao_lida.enviado_em > COALESCE(leitura_do_lead.lido_ate, 'epoch'::timestamptz)
    )), 0)
      FROM atendimento atendimento_do_lead
      LEFT JOIN atendimento_leitura leitura_do_lead
        ON leitura_do_lead.atendimento_id = atendimento_do_lead.id
       -- Gestao acompanha a leitura do dono exibido no cartao. Sem dono, a
       -- leitura continua pessoal; atendentes/participantes mantem sua propria.
       AND leitura_do_lead.usuario_id = COALESCE(
           CASE WHEN app_enxerga_todos_os_leads() THEN dono.atendente_id END, :'usuario')
     WHERE atendimento_do_lead.lead_id = a.lead_id
) AS nao_lidas,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM (SELECT atendimento_id FROM (SELECT a.id AS atendimento_id,
ultima.enviado_em AS ultima_mensagem_em,
CASE WHEN EXISTS (
    SELECT 1 FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
) THEN 0 ELSE 1 END AS sem_atendimento_aberto,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM atendimento a
JOIN lead l ON l.id = a.lead_id
LEFT JOIN LATERAL (
    SELECT enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
 WHERE EXISTS (SELECT 1 FROM atendimento aberto WHERE aberto.lead_id = a.lead_id AND aberto.status IN ('EM_ATENDIMENTO', 'EM_IA'))) escolha WHERE linha_do_lead = 1 ORDER BY sem_atendimento_aberto ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC LIMIT :limite) escolhidos
JOIN atendimento a ON a.id = escolhidos.atendimento_id
JOIN lead l ON l.id = a.lead_id
LEFT JOIN canal c ON c.id = a.canal_id
LEFT JOIN etapa_atendimento et ON et.id = l.etapa_atendimento_id
LEFT JOIN LATERAL (
    SELECT aberto.id, aberto.status, aberto.atendente_id FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
     ORDER BY COALESCE((
         SELECT max(m_aberto.enviado_em) FROM mensagem m_aberto
          WHERE m_aberto.atendimento_id = aberto.id
     ), aberto.iniciado_em) DESC, aberto.iniciado_em DESC, aberto.id DESC
     LIMIT 1
) ativo ON true
-- E206: o cartao abre o atendimento ativo, entao o dono exibido vem dele. Sem isso, a
-- linha da ultima mensagem (ja FINALIZADA) exibia o dono do ciclo anterior enquanto o
-- cabecalho, via /estado do ativo, exibia o atual. O status segue sendo o da linha.
CROSS JOIN LATERAL (
    SELECT CASE WHEN ativo.id IS NULL THEN a.atendente_id ELSE ativo.atendente_id END
               AS atendente_id
) dono
LEFT JOIN usuario u ON u.id = dono.atendente_id
LEFT JOIN usuario venda_usuario ON venda_usuario.id = a.venda_registrada_por_id
LEFT JOIN LATERAL (
    SELECT conteudo, remetente_tipo, enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
) cartoes WHERE linha_do_lead = 1 ORDER BY CASE WHEN atendimento_ativo_id IS NULL THEN 1 ELSE 0 END ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC;
ROLLBACK;

-- 5-generico (PREPARE + plan_cache_mode = force_generic_plan)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE q_depois_5(uuid, bigint) AS
SELECT atendimento_id, lead_id, lead_nome, lead_foto_url, lead_empresa, lead_codigo, canal_tipo, etapa_atendimento_id, etapa_nome, etapa_cor, status, atendente_id, atendente_nome, iniciado_em, atendimento_ativo_id, ultima_mensagem_preview, ultima_mensagem_remetente_tipo, ultima_mensagem_em, ultima_mensagem_do_lead_em, nao_lidas, em_negociacao, resultado_venda, valor_venda, venda_registrada_por_id, venda_registrada_por_nome, venda_registrada_em, origem_resultado_venda, linha_do_lead FROM (SELECT a.id AS atendimento_id, a.lead_id, l.nome AS lead_nome,
CASE WHEN l.foto_referencia IS NOT NULL
     THEN '/api/v1/leads/' || l.id::text || '/foto'
     ELSE l.foto_url END AS lead_foto_url,
l.empresa AS lead_empresa, l.codigo AS lead_codigo, c.tipo AS canal_tipo,
l.etapa_atendimento_id, et.nome AS etapa_nome,
et.cor_visual AS etapa_cor, a.status, dono.atendente_id, u.nome AS atendente_nome,
a.em_negociacao, a.resultado_venda, a.valor_venda, a.venda_registrada_por_id,
venda_usuario.nome AS venda_registrada_por_nome, a.venda_registrada_em, a.origem_resultado_venda,
a.iniciado_em AS iniciado_em,
ativo.id AS atendimento_ativo_id,
ultima.conteudo AS ultima_mensagem_preview,
ultima.remetente_tipo AS ultima_mensagem_remetente_tipo,
ultima.enviado_em AS ultima_mensagem_em,
-- E121: mesma fonte do envio (lead.ultima_mensagem_do_lead_em). A LATERAL da E114
-- saiu de proposito - duas definicoes de "janela aberta" divergem com o tempo.
l.ultima_mensagem_do_lead_em AS ultima_mensagem_do_lead_em,
(
    SELECT COALESCE(SUM((
        SELECT count(*) FROM mensagem nao_lida
         WHERE nao_lida.atendimento_id = atendimento_do_lead.id
           AND nao_lida.remetente_tipo = 'LEAD'
           AND nao_lida.enviado_em > COALESCE(leitura_do_lead.lido_ate, 'epoch'::timestamptz)
    )), 0)
      FROM atendimento atendimento_do_lead
      LEFT JOIN atendimento_leitura leitura_do_lead
        ON leitura_do_lead.atendimento_id = atendimento_do_lead.id
       -- Gestao acompanha a leitura do dono exibido no cartao. Sem dono, a
       -- leitura continua pessoal; atendentes/participantes mantem sua propria.
       AND leitura_do_lead.usuario_id = COALESCE(
           CASE WHEN app_enxerga_todos_os_leads() THEN dono.atendente_id END, $1)
     WHERE atendimento_do_lead.lead_id = a.lead_id
) AS nao_lidas,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM (SELECT atendimento_id FROM (SELECT a.id AS atendimento_id,
ultima.enviado_em AS ultima_mensagem_em,
CASE WHEN EXISTS (
    SELECT 1 FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
) THEN 0 ELSE 1 END AS sem_atendimento_aberto,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM atendimento a
JOIN lead l ON l.id = a.lead_id
LEFT JOIN LATERAL (
    SELECT enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
 WHERE EXISTS (SELECT 1 FROM atendimento aberto WHERE aberto.lead_id = a.lead_id AND aberto.status IN ('EM_ATENDIMENTO', 'EM_IA'))) escolha WHERE linha_do_lead = 1 ORDER BY sem_atendimento_aberto ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC LIMIT $2) escolhidos
JOIN atendimento a ON a.id = escolhidos.atendimento_id
JOIN lead l ON l.id = a.lead_id
LEFT JOIN canal c ON c.id = a.canal_id
LEFT JOIN etapa_atendimento et ON et.id = l.etapa_atendimento_id
LEFT JOIN LATERAL (
    SELECT aberto.id, aberto.status, aberto.atendente_id FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
     ORDER BY COALESCE((
         SELECT max(m_aberto.enviado_em) FROM mensagem m_aberto
          WHERE m_aberto.atendimento_id = aberto.id
     ), aberto.iniciado_em) DESC, aberto.iniciado_em DESC, aberto.id DESC
     LIMIT 1
) ativo ON true
-- E206: o cartao abre o atendimento ativo, entao o dono exibido vem dele. Sem isso, a
-- linha da ultima mensagem (ja FINALIZADA) exibia o dono do ciclo anterior enquanto o
-- cabecalho, via /estado do ativo, exibia o atual. O status segue sendo o da linha.
CROSS JOIN LATERAL (
    SELECT CASE WHEN ativo.id IS NULL THEN a.atendente_id ELSE ativo.atendente_id END
               AS atendente_id
) dono
LEFT JOIN usuario u ON u.id = dono.atendente_id
LEFT JOIN usuario venda_usuario ON venda_usuario.id = a.venda_registrada_por_id
LEFT JOIN LATERAL (
    SELECT conteudo, remetente_tipo, enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
) cartoes WHERE linha_do_lead = 1 ORDER BY CASE WHEN atendimento_ativo_id IS NULL THEN 1 ELSE 0 END ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC;
SET LOCAL plan_cache_mode = force_generic_plan;
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE q_depois_5(:'usuario', :limite);
ROLLBACK;
DEALLOCATE q_depois_5;

-- =========================================================================================================
-- 06 PAINEL aba FINALIZADOS (a que mais cresce com o historico) - DEPOIS
-- =========================================================================================================
-- 6-literal (plano customizado)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT atendimento_id, lead_id, lead_nome, lead_foto_url, lead_empresa, lead_codigo, canal_tipo, etapa_atendimento_id, etapa_nome, etapa_cor, status, atendente_id, atendente_nome, iniciado_em, atendimento_ativo_id, ultima_mensagem_preview, ultima_mensagem_remetente_tipo, ultima_mensagem_em, ultima_mensagem_do_lead_em, nao_lidas, em_negociacao, resultado_venda, valor_venda, venda_registrada_por_id, venda_registrada_por_nome, venda_registrada_em, origem_resultado_venda, linha_do_lead FROM (SELECT a.id AS atendimento_id, a.lead_id, l.nome AS lead_nome,
CASE WHEN l.foto_referencia IS NOT NULL
     THEN '/api/v1/leads/' || l.id::text || '/foto'
     ELSE l.foto_url END AS lead_foto_url,
l.empresa AS lead_empresa, l.codigo AS lead_codigo, c.tipo AS canal_tipo,
l.etapa_atendimento_id, et.nome AS etapa_nome,
et.cor_visual AS etapa_cor, a.status, dono.atendente_id, u.nome AS atendente_nome,
a.em_negociacao, a.resultado_venda, a.valor_venda, a.venda_registrada_por_id,
venda_usuario.nome AS venda_registrada_por_nome, a.venda_registrada_em, a.origem_resultado_venda,
a.iniciado_em AS iniciado_em,
ativo.id AS atendimento_ativo_id,
ultima.conteudo AS ultima_mensagem_preview,
ultima.remetente_tipo AS ultima_mensagem_remetente_tipo,
ultima.enviado_em AS ultima_mensagem_em,
-- E121: mesma fonte do envio (lead.ultima_mensagem_do_lead_em). A LATERAL da E114
-- saiu de proposito - duas definicoes de "janela aberta" divergem com o tempo.
l.ultima_mensagem_do_lead_em AS ultima_mensagem_do_lead_em,
(
    SELECT COALESCE(SUM((
        SELECT count(*) FROM mensagem nao_lida
         WHERE nao_lida.atendimento_id = atendimento_do_lead.id
           AND nao_lida.remetente_tipo = 'LEAD'
           AND nao_lida.enviado_em > COALESCE(leitura_do_lead.lido_ate, 'epoch'::timestamptz)
    )), 0)
      FROM atendimento atendimento_do_lead
      LEFT JOIN atendimento_leitura leitura_do_lead
        ON leitura_do_lead.atendimento_id = atendimento_do_lead.id
       -- Gestao acompanha a leitura do dono exibido no cartao. Sem dono, a
       -- leitura continua pessoal; atendentes/participantes mantem sua propria.
       AND leitura_do_lead.usuario_id = COALESCE(
           CASE WHEN app_enxerga_todos_os_leads() THEN dono.atendente_id END, :'usuario')
     WHERE atendimento_do_lead.lead_id = a.lead_id
) AS nao_lidas,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM (SELECT atendimento_id FROM (SELECT a.id AS atendimento_id,
ultima.enviado_em AS ultima_mensagem_em,
CASE WHEN EXISTS (
    SELECT 1 FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
) THEN 0 ELSE 1 END AS sem_atendimento_aberto,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM atendimento a
JOIN lead l ON l.id = a.lead_id
LEFT JOIN LATERAL (
    SELECT enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
 WHERE NOT EXISTS (SELECT 1 FROM atendimento aberto WHERE aberto.lead_id = a.lead_id AND aberto.status IN ('EM_ATENDIMENTO', 'EM_IA'))) escolha WHERE linha_do_lead = 1 ORDER BY sem_atendimento_aberto ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC LIMIT :limite) escolhidos
JOIN atendimento a ON a.id = escolhidos.atendimento_id
JOIN lead l ON l.id = a.lead_id
LEFT JOIN canal c ON c.id = a.canal_id
LEFT JOIN etapa_atendimento et ON et.id = l.etapa_atendimento_id
LEFT JOIN LATERAL (
    SELECT aberto.id, aberto.status, aberto.atendente_id FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
     ORDER BY COALESCE((
         SELECT max(m_aberto.enviado_em) FROM mensagem m_aberto
          WHERE m_aberto.atendimento_id = aberto.id
     ), aberto.iniciado_em) DESC, aberto.iniciado_em DESC, aberto.id DESC
     LIMIT 1
) ativo ON true
-- E206: o cartao abre o atendimento ativo, entao o dono exibido vem dele. Sem isso, a
-- linha da ultima mensagem (ja FINALIZADA) exibia o dono do ciclo anterior enquanto o
-- cabecalho, via /estado do ativo, exibia o atual. O status segue sendo o da linha.
CROSS JOIN LATERAL (
    SELECT CASE WHEN ativo.id IS NULL THEN a.atendente_id ELSE ativo.atendente_id END
               AS atendente_id
) dono
LEFT JOIN usuario u ON u.id = dono.atendente_id
LEFT JOIN usuario venda_usuario ON venda_usuario.id = a.venda_registrada_por_id
LEFT JOIN LATERAL (
    SELECT conteudo, remetente_tipo, enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
) cartoes WHERE linha_do_lead = 1 ORDER BY CASE WHEN atendimento_ativo_id IS NULL THEN 1 ELSE 0 END ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC;
ROLLBACK;

-- 6-generico (PREPARE + plan_cache_mode = force_generic_plan)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE q_depois_6(uuid, bigint) AS
SELECT atendimento_id, lead_id, lead_nome, lead_foto_url, lead_empresa, lead_codigo, canal_tipo, etapa_atendimento_id, etapa_nome, etapa_cor, status, atendente_id, atendente_nome, iniciado_em, atendimento_ativo_id, ultima_mensagem_preview, ultima_mensagem_remetente_tipo, ultima_mensagem_em, ultima_mensagem_do_lead_em, nao_lidas, em_negociacao, resultado_venda, valor_venda, venda_registrada_por_id, venda_registrada_por_nome, venda_registrada_em, origem_resultado_venda, linha_do_lead FROM (SELECT a.id AS atendimento_id, a.lead_id, l.nome AS lead_nome,
CASE WHEN l.foto_referencia IS NOT NULL
     THEN '/api/v1/leads/' || l.id::text || '/foto'
     ELSE l.foto_url END AS lead_foto_url,
l.empresa AS lead_empresa, l.codigo AS lead_codigo, c.tipo AS canal_tipo,
l.etapa_atendimento_id, et.nome AS etapa_nome,
et.cor_visual AS etapa_cor, a.status, dono.atendente_id, u.nome AS atendente_nome,
a.em_negociacao, a.resultado_venda, a.valor_venda, a.venda_registrada_por_id,
venda_usuario.nome AS venda_registrada_por_nome, a.venda_registrada_em, a.origem_resultado_venda,
a.iniciado_em AS iniciado_em,
ativo.id AS atendimento_ativo_id,
ultima.conteudo AS ultima_mensagem_preview,
ultima.remetente_tipo AS ultima_mensagem_remetente_tipo,
ultima.enviado_em AS ultima_mensagem_em,
-- E121: mesma fonte do envio (lead.ultima_mensagem_do_lead_em). A LATERAL da E114
-- saiu de proposito - duas definicoes de "janela aberta" divergem com o tempo.
l.ultima_mensagem_do_lead_em AS ultima_mensagem_do_lead_em,
(
    SELECT COALESCE(SUM((
        SELECT count(*) FROM mensagem nao_lida
         WHERE nao_lida.atendimento_id = atendimento_do_lead.id
           AND nao_lida.remetente_tipo = 'LEAD'
           AND nao_lida.enviado_em > COALESCE(leitura_do_lead.lido_ate, 'epoch'::timestamptz)
    )), 0)
      FROM atendimento atendimento_do_lead
      LEFT JOIN atendimento_leitura leitura_do_lead
        ON leitura_do_lead.atendimento_id = atendimento_do_lead.id
       -- Gestao acompanha a leitura do dono exibido no cartao. Sem dono, a
       -- leitura continua pessoal; atendentes/participantes mantem sua propria.
       AND leitura_do_lead.usuario_id = COALESCE(
           CASE WHEN app_enxerga_todos_os_leads() THEN dono.atendente_id END, $1)
     WHERE atendimento_do_lead.lead_id = a.lead_id
) AS nao_lidas,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM (SELECT atendimento_id FROM (SELECT a.id AS atendimento_id,
ultima.enviado_em AS ultima_mensagem_em,
CASE WHEN EXISTS (
    SELECT 1 FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
) THEN 0 ELSE 1 END AS sem_atendimento_aberto,
ROW_NUMBER() OVER (
    PARTITION BY a.lead_id
    ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.iniciado_em DESC, a.id DESC
) AS linha_do_lead
FROM atendimento a
JOIN lead l ON l.id = a.lead_id
LEFT JOIN LATERAL (
    SELECT enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
 WHERE NOT EXISTS (SELECT 1 FROM atendimento aberto WHERE aberto.lead_id = a.lead_id AND aberto.status IN ('EM_ATENDIMENTO', 'EM_IA'))) escolha WHERE linha_do_lead = 1 ORDER BY sem_atendimento_aberto ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC LIMIT $2) escolhidos
JOIN atendimento a ON a.id = escolhidos.atendimento_id
JOIN lead l ON l.id = a.lead_id
LEFT JOIN canal c ON c.id = a.canal_id
LEFT JOIN etapa_atendimento et ON et.id = l.etapa_atendimento_id
LEFT JOIN LATERAL (
    SELECT aberto.id, aberto.status, aberto.atendente_id FROM atendimento aberto
     WHERE aberto.lead_id = a.lead_id AND aberto.status <> 'FINALIZADO'
     ORDER BY COALESCE((
         SELECT max(m_aberto.enviado_em) FROM mensagem m_aberto
          WHERE m_aberto.atendimento_id = aberto.id
     ), aberto.iniciado_em) DESC, aberto.iniciado_em DESC, aberto.id DESC
     LIMIT 1
) ativo ON true
-- E206: o cartao abre o atendimento ativo, entao o dono exibido vem dele. Sem isso, a
-- linha da ultima mensagem (ja FINALIZADA) exibia o dono do ciclo anterior enquanto o
-- cabecalho, via /estado do ativo, exibia o atual. O status segue sendo o da linha.
CROSS JOIN LATERAL (
    SELECT CASE WHEN ativo.id IS NULL THEN a.atendente_id ELSE ativo.atendente_id END
               AS atendente_id
) dono
LEFT JOIN usuario u ON u.id = dono.atendente_id
LEFT JOIN usuario venda_usuario ON venda_usuario.id = a.venda_registrada_por_id
LEFT JOIN LATERAL (
    SELECT conteudo, remetente_tipo, enviado_em FROM mensagem m
     WHERE m.atendimento_id = a.id ORDER BY m.enviado_em DESC LIMIT 1
) ultima ON true
) cartoes WHERE linha_do_lead = 1 ORDER BY CASE WHEN atendimento_ativo_id IS NULL THEN 1 ELSE 0 END ASC, ultima_mensagem_em DESC NULLS LAST, atendimento_id DESC;
SET LOCAL plan_cache_mode = force_generic_plan;
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE q_depois_6(:'usuario', :limite);
ROLLBACK;
DEALLOCATE q_depois_6;

-- =========================================================================================================
-- 07 PAINEL contagem de FINALIZADOS (badge) - igual nas duas versoes
-- =========================================================================================================
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE NOT EXISTS (SELECT 1 FROM atendimento aberto WHERE aberto.lead_id = a.lead_id AND aberto.status IN ('EM_ATENDIMENTO', 'EM_IA'));
ROLLBACK;
