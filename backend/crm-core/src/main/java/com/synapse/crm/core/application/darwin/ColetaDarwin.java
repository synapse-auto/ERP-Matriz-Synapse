package com.synapse.crm.core.application.darwin;

import java.util.UUID;

/** Porta de trabalho assíncrono; solicitar não consulta o provedor na thread HTTP. */
public interface ColetaDarwin {
    void solicitar(UUID leadId, boolean tentativaManual);
    Situacao situacao(UUID leadId);
    record Situacao(String estado) {}
}
