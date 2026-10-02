-- Origem da participacao (docs/51). A excecao a RN-CRM-06 — responder sem herdar o lead — vale so
-- para participacao CONSENTIDA: convite aceito pelo convidado ou pedido aprovado pelo responsavel.
-- Entrada direta de gestor e abertura colaborativa pela Agenda continuam assumindo ao enviar;
-- sem esta coluna, as tres formas seriam indistinguiveis na linha de atendimento_participante.
ALTER TABLE atendimento_participante
    ADD COLUMN origem VARCHAR(20) NOT NULL DEFAULT 'ENTRADA_DIRETA';

ALTER TABLE atendimento_participante
    ADD CONSTRAINT ck_atendimento_participante_origem
    CHECK (origem IN ('ENTRADA_DIRETA', 'CONVITE', 'PEDIDO_APROVADO'));

-- Participacoes ativas que nasceram de um pedido/convite aprovado recebem a origem real. As demais
-- (entrada direta, Agenda) ja ficam com o default.
UPDATE atendimento_participante ap
   SET origem = CASE p.tipo WHEN 'CONVITE' THEN 'CONVITE' ELSE 'PEDIDO_APROVADO' END
  FROM pedido_entrada_atendimento p
 WHERE p.atendimento_id = ap.atendimento_id
   AND p.solicitante_id = ap.usuario_id
   AND p.status = 'APROVADO'
   AND ap.saiu_em IS NULL;

COMMENT ON COLUMN atendimento_participante.origem IS
    'CONVITE e PEDIDO_APROVADO respondem sem assumir o lead; ENTRADA_DIRETA (gestor, Agenda) assume ao enviar.';
