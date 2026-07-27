-- =========================================================
-- Extensoes e tipos enumerados.
-- Base de tudo o que vem depois: os ENUMs sao referenciados por
-- praticamente todas as tabelas das migrations seguintes.
-- =========================================================

-- pg_trgm e a unica extensao necessaria.
--
-- pgcrypto foi removida de proposito: `gen_random_uuid()`, usada como DEFAULT
-- em quase toda tabela, e nativa desde o PostgreSQL 13, e o projeto exige 15+.
-- Uma extensao a menos e um obstaculo a menos no deploy em Postgres gerenciado,
-- onde habilitar extensao costuma exigir privilegio que a aplicacao nao tem.
CREATE EXTENSION IF NOT EXISTS pg_trgm;    -- busca fuzzy por nome (RF-CRM-07)

CREATE TYPE papel_usuario      AS ENUM ('ATENDENTE', 'SUBGESTOR', 'GESTOR', 'ADMINISTRADOR');
CREATE TYPE status_presenca    AS ENUM ('ONLINE', 'AUSENTE', 'OFFLINE');
CREATE TYPE status_basico_lead AS ENUM ('IA', 'EM_ATENDIMENTO', 'FINALIZADO');
CREATE TYPE status_atendimento AS ENUM ('EM_IA', 'EM_ATENDIMENTO', 'FINALIZADO');
CREATE TYPE remetente_tipo     AS ENUM ('LEAD', 'ATENDENTE', 'SISTEMA', 'IA');
CREATE TYPE tipo_mensagem      AS ENUM ('TEXTO', 'AUDIO', 'IMAGEM', 'DOCUMENTO');
CREATE TYPE status_entrega     AS ENUM ('ENVIADO', 'ENTREGUE', 'LIDO');
CREATE TYPE status_lembrete    AS ENUM ('PENDENTE', 'CONCLUIDO');
CREATE TYPE status_msg_prog    AS ENUM ('AGENDADA', 'ENVIADA', 'CANCELADA');
CREATE TYPE status_campanha    AS ENUM ('RASCUNHO', 'ATIVA', 'PAUSADA', 'ENCERRADA');
CREATE TYPE contexto_filtro    AS ENUM ('ATENDIMENTOS', 'AGENDA', 'CAMPANHA');
CREATE TYPE tipo_rotina        AS ENUM ('PLANTAO', 'FECHADO');
CREATE TYPE dia_semana         AS ENUM ('SEG', 'TER', 'QUA', 'QUI', 'SEX', 'SAB', 'DOM');
CREATE TYPE origem_evento      AS ENUM ('SISTEMA', 'AUTOMACAO', 'USUARIO');
CREATE TYPE gatilho_resumo     AS ENUM ('A_CADA_X_MENSAGENS', 'AO_FINALIZAR', 'AMBOS');
CREATE TYPE tipo_conversa_chat AS ENUM ('DIRETA', 'GRUPO');
