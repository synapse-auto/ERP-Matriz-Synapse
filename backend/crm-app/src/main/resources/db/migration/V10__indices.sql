-- =========================================================
-- Indices, reunidos numa migration so para facilitar a revisao.
--
-- Cada um responde a um acesso real descrito em
-- docs/03-modelo-dados-postgres.md secao 3. Os parciais existem porque a
-- consulta comum filtra um subconjunto pequeno: indexar so esse subconjunto
-- deixa o indice menor e mais quente em cache.
-- =========================================================

-- --- Equipe ----------------------------------------------------------------
CREATE INDEX idx_usuario_papel ON usuario (papel) WHERE ativo;
CREATE INDEX idx_avaliacao_atendente ON avaliacao (atendente_id);

-- --- Configuracao base -----------------------------------------------------
CREATE UNIQUE INDEX idx_etapa_ordem ON etapa_atendimento (ordem);

-- Regra de negocio garantida pelo banco, nao otimizacao: nunca pode haver
-- duas credenciais ativas para o mesmo canal. Protege mesmo se o codigo errar.
CREATE UNIQUE INDEX idx_canal_credencial_ativa
    ON canal_credencial (canal_id) WHERE ativo;

-- --- CRM core --------------------------------------------------------------
-- Isolamento de agenda (RN-CRM-01): toda listagem de atendente passa por aqui.
CREATE INDEX idx_lead_atendente ON lead (atendente_responsavel_id);
CREATE INDEX idx_lead_etapa ON lead (etapa_atendimento_id);

-- Listas "Ativos"/"Pendentes" (RF-CRM-20/21): finalizados sao a maioria da
-- base e nunca aparecem nessas telas.
CREATE INDEX idx_lead_status_basico
    ON lead (status_basico) WHERE status_basico <> 'FINALIZADO';

-- Busca por nome (RF-CRM-07) e ILIKE '%termo%': sem trigram, table scan certo.
CREATE INDEX idx_lead_nome_trgm ON lead USING gin (nome gin_trgm_ops);
CREATE INDEX idx_lead_telefone ON lead (telefone);
CREATE INDEX idx_lead_cpf ON lead (cpf);

CREATE INDEX idx_lead_tag_tag ON lead_tag (tag_id);  -- metricas por tag (RF-CRM-49)
CREATE INDEX idx_lembrete_atendente ON lembrete (atendente_id, status);
CREATE INDEX idx_msg_prog_atendente ON mensagem_programada (atendente_id, status);
CREATE INDEX idx_msg_prog_envio
    ON mensagem_programada (data_envio) WHERE status = 'AGENDADA';
CREATE UNIQUE INDEX idx_msg_rapida_atendente_chave
    ON mensagem_rapida (atendente_id, palavra_chave);
CREATE INDEX idx_evento_timeline_lead ON evento_timeline (lead_id, criado_em DESC);

-- --- Atendimento -----------------------------------------------------------
CREATE INDEX idx_atendimento_lead ON atendimento (lead_id);
CREATE INDEX idx_atendimento_atendente_status ON atendimento (atendente_id, status);

-- Criado na tabela particionada: o Postgres propaga para as particoes
-- existentes e para as que forem criadas depois.
CREATE INDEX idx_mensagem_atendimento ON mensagem (atendimento_id, enviado_em);

-- --- Campanhas -------------------------------------------------------------
CREATE INDEX idx_filtro_criterios ON filtro_modular USING gin (criterios);
CREATE INDEX idx_campanha_metrica_lead ON campanha_mensagem_metrica (lead_id);

-- --- Chat interno ----------------------------------------------------------
CREATE INDEX idx_chat_interno_msg_conversa
    ON chat_interno_mensagem (conversa_id, enviado_em);

-- --- Infra transversal -----------------------------------------------------
CREATE INDEX idx_audit_ator ON audit_log (ator_id, criado_em DESC);
CREATE INDEX idx_audit_acao ON audit_log (acao, criado_em DESC);
CREATE INDEX idx_audit_entidade ON audit_log (entidade_tipo, entidade_id);
CREATE INDEX idx_audit_lead ON audit_log (lead_id, criado_em DESC);

-- BRIN: audit_log e append-only e cronologica, entao os blocos ja saem
-- ordenados por criado_em. Custa uma fracao de um B-tree.
CREATE INDEX idx_audit_criado_em ON audit_log USING brin (criado_em);

-- O publisher da outbox so varre o que ainda nao publicou. Sem o parcial,
-- o indice cresceria para sempre carregando linhas ja publicadas.
CREATE INDEX idx_outbox_pendente
    ON outbox_evento (criado_em) WHERE publicado_em IS NULL;
