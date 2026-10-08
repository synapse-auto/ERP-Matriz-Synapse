CREATE TABLE IF NOT EXISTS public.agendamento_evento (
  id BIGSERIAL PRIMARY KEY,
  clinica_id INTEGER NOT NULL DEFAULT 1,
  schedule_id TEXT NOT NULL,
  lead_id TEXT NULL,
  atendimento_id TEXT NULL,
  paciente_nome TEXT NULL,
  telefone TEXT NULL,
  profissional_nome TEXT NULL,
  tipo_agendamento TEXT NULL,
  agendado_para TIMESTAMP WITH TIME ZONE NULL,
  acao TEXT NOT NULL,
  descricao TEXT NULL,
  resumo TEXT NULL,
  origem TEXT NOT NULL DEFAULT 'LEMBRETE_N8N',
  idempotency_key TEXT NOT NULL,
  darwin_payload JSONB NULL,
  criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT agendamento_evento_acao_check
    CHECK (
      acao IN (
        'CONFIRMADO',
        'CANCELADO',
        'REAGENDAMENTO_SOLICITADO'
      )
    ),
  CONSTRAINT agendamento_evento_idempotency_key_key
    UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS agendamento_evento_schedule_idx
  ON public.agendamento_evento (schedule_id);

CREATE INDEX IF NOT EXISTS agendamento_evento_acao_data_idx
  ON public.agendamento_evento (acao, criado_em DESC);

CREATE INDEX IF NOT EXISTS agendamento_evento_atendimento_idx
  ON public.agendamento_evento (atendimento_id)
  WHERE atendimento_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS agendamento_evento_lead_idx
  ON public.agendamento_evento (lead_id)
  WHERE lead_id IS NOT NULL;

CREATE OR REPLACE VIEW public.vw_cancelamentos_agendamento AS
SELECT
  id,
  schedule_id,
  lead_id,
  atendimento_id,
  paciente_nome,
  telefone,
  profissional_nome,
  tipo_agendamento,
  agendado_para,
  resumo,
  descricao,
  CASE
    WHEN NULLIF(btrim(descricao), '') IS NULL THEN 'SEM_DESCRICAO'
    ELSE 'COM_DESCRICAO'
  END AS classificacao,
  criado_em
FROM public.agendamento_evento
WHERE acao = 'CANCELADO';

COMMENT ON TABLE public.agendamento_evento IS
  'Historico de confirmacoes, cancelamentos e solicitacoes de reagendamento originados pelos lembretes.';

COMMENT ON COLUMN public.agendamento_evento.idempotency_key IS
  'Chave unica por evento para impedir registros duplicados em reexecucoes do n8n.';
