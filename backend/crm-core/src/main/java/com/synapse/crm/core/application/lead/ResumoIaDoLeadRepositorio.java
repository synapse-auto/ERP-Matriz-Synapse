package com.synapse.crm.core.application.lead;

import java.util.UUID;

/** Escrita restrita do resumo produzido pela IA; a leitura continua na ficha completa do lead. */
public interface ResumoIaDoLeadRepositorio {

    void sobrescrever(UUID leadId, String resumo);
}
