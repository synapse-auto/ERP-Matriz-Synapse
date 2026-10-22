package com.synapse.crm.core.domain.lead;

/** Projecao pontual do cadastro atual; nunca usada em listagens. */
public record IdentificacaoLead(String cpf, String telefone) {
    @Override public String toString() { return "IdentificacaoLead[protegida]"; }
}
