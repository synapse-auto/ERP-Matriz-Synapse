package com.synapse.crm.atendimento.domain.atendimento;

/** Estado do atendimento. Espelha o ENUM {@code status_atendimento} do banco. */
public enum StatusAtendimento {

    /** Com a IA, sem atendente humano. O lead ainda esta no grupo "Potenciais". */
    EM_IA,

    /** Um atendente assumiu. */
    EM_ATENDIMENTO,

    /** Encerrado. Estado terminal: dele nao se sai. */
    FINALIZADO;

    public boolean estaAberto() {
        return this != FINALIZADO;
    }
}
