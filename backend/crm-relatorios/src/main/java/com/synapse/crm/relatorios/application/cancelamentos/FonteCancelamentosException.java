package com.synapse.crm.relatorios.application.cancelamentos;

/** Somente codigo sanitizado: nunca propagar erro bruto de integracao, SQL ou payload. */
public class FonteCancelamentosException extends RuntimeException {
    public enum Codigo { INTEGRACAO_NAO_CONFIGURADA, FONTE_INDISPONIVEL }
    private final Codigo codigo;
    public FonteCancelamentosException(Codigo codigo) {
        super(codigo.name());
        this.codigo = codigo;
    }
    public Codigo codigo() { return codigo; }
}
