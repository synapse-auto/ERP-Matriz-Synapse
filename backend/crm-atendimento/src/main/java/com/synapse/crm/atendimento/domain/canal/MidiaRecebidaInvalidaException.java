package com.synapse.crm.atendimento.domain.canal;

/** Referencia/conteudo recusado permanentemente; nunca carrega URL, corpo ou segredo. */
public final class MidiaRecebidaInvalidaException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public MidiaRecebidaInvalidaException() { super("MIDIA_RECEBIDA_INVALIDA"); }
    public MidiaRecebidaInvalidaException(int status) { super("MIDIA_RECEBIDA_INVALIDA; etapa=download; http_status=" + status); }
}
