package com.synapse.crm.campanhas.domain;

/** A acao nao vale no status atual da campanha (por exemplo retomar uma campanha cancelada). */
public class TransicaoDeStatusInvalidaException extends RuntimeException {

    private final StatusDaCampanha statusAtual;

    public TransicaoDeStatusInvalidaException(StatusDaCampanha statusAtual, String acao) {
        super("nao e possivel " + acao + " uma campanha " + statusAtual);
        this.statusAtual = statusAtual;
    }

    public StatusDaCampanha statusAtual() {
        return statusAtual;
    }
}
