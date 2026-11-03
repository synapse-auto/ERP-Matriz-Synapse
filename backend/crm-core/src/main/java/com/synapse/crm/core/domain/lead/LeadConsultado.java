package com.synapse.crm.core.domain.lead;

import java.util.UUID;

/** Fato de leitura autorizada; consumidores opcionais não participam da resposta HTTP. */
public record LeadConsultado(UUID leadId) {}
