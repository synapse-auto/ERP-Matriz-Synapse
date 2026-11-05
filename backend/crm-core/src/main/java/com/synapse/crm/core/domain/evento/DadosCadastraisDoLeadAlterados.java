package com.synapse.crm.core.domain.evento;

import java.util.UUID;

/** Sinal sem dados pessoais: a ficha deve ser relida pelo endpoint autorizado. */
public record DadosCadastraisDoLeadAlterados(UUID leadId) {}
