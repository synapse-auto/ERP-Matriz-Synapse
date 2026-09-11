package com.synapse.crm.atendimento.domain.canal;

/**
 * O provedor respondeu, mas ainda nao disponibilizou os bytes da midia recebida.
 *
 * <p>O processador trata esta falha como uma tentativa real e agenda a proxima consulta com
 * backoff. A mensagem carrega somente identificadores tecnicos e o status HTTP; nunca o corpo da
 * resposta, uma URL temporaria ou uma credencial.
 */
public class MidiaRecebidaTemporariamenteIndisponivelException extends RuntimeException {

    public MidiaRecebidaTemporariamenteIndisponivelException(String motivo) {
        super(motivo);
    }

    /**
     * Acrescenta o tipo normalizado da mensagem sem incluir payload ou resposta do provedor na
     * causa persistida da retentativa.
     */
    public MidiaRecebidaTemporariamenteIndisponivelException comTipo(String tipo) {
        return new MidiaRecebidaTemporariamenteIndisponivelException(
                "tipo=" + tipo + "; " + getMessage());
    }
}
