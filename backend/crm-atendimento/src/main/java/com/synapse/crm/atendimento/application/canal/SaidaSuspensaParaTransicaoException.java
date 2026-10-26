package com.synapse.crm.atendimento.application.canal;

public class SaidaSuspensaParaTransicaoException extends RuntimeException {
    public SaidaSuspensaParaTransicaoException() { super("SAIDA_SUSPENSA_TRANSICAO_CANAL"); }
    public SaidaSuspensaParaTransicaoException(String codigo) { super(codigo); }
}
