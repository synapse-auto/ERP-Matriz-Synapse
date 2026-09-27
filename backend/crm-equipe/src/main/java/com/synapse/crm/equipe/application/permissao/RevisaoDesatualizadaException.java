package com.synapse.crm.equipe.application.permissao;

/** Outra pessoa salvou este escopo depois da leitura. Vira 409; o rascunho de quem salvou fica intacto. */
public class RevisaoDesatualizadaException extends RuntimeException {

    private final long revisaoAtual;

    public RevisaoDesatualizadaException(long revisaoAtual) {
        super("A configuracao foi alterada por outra pessoa (revisao atual " + revisaoAtual + ").");
        this.revisaoAtual = revisaoAtual;
    }

    public long revisaoAtual() {
        return revisaoAtual;
    }
}
