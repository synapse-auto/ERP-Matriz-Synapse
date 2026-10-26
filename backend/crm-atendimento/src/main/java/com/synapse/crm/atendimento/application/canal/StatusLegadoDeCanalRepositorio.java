package com.synapse.crm.atendimento.application.canal;

public interface StatusLegadoDeCanalRepositorio {
    boolean destinoPermitido(String identificador);
    boolean mensagemAnteriorAoCorte(String idExterno);
}
