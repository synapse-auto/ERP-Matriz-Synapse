-- Ordenacao monotonicamente crescente dos eventos de estado de cada atendimento.
--
-- A versao pertence ao ciclo de atendimento, nao ao lead: um lead pode encerrar um ciclo e
-- iniciar outro sem que eventos atrasados do ciclo anterior sejam aceitos pelo navegador.
ALTER TABLE atendimento
    ADD COLUMN versao_evento BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN atendimento.versao_evento IS
    'Sequencia monotona dos eventos canonicos de estado deste atendimento; incrementada na mesma transacao da acao.';

-- Operacao tecnica estreita para os casos em que o solicitante ainda nao enxerga o atendimento
-- (pedido de entrada). A aplicacao somente chama esta funcao depois de a operacao de negocio ter
-- sido autorizada e gravada. Nenhum dado do atendimento e devolvido alem da nova versao.
CREATE OR REPLACE FUNCTION app_avancar_versao_evento_atendimento(p_atendimento UUID)
RETURNS BIGINT
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    UPDATE atendimento
       SET versao_evento = versao_evento + 1
     WHERE id = p_atendimento
     RETURNING versao_evento;
$$;

REVOKE ALL ON FUNCTION app_avancar_versao_evento_atendimento(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_avancar_versao_evento_atendimento(UUID) TO synapse_app;

COMMENT ON FUNCTION app_avancar_versao_evento_atendimento(UUID) IS
    'Avanca a ordem tecnica do atendimento dentro da transacao corrente; nao concede leitura nem altera estado de negocio.';
