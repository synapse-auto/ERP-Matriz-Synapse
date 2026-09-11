package com.synapse.crm.automacaoconfig.application;

import com.fasterxml.jackson.databind.JsonNode;

/** Porta para os recursos de marca fornecidos pelo deploy quando nao ha override no banco. */
public interface RecursosDeMarcaDaInstancia {

    JsonNode tema();

    JsonNode textos();

    byte[] logo();
}
