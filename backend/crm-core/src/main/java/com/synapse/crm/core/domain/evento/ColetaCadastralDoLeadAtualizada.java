package com.synapse.crm.core.domain.evento;

import java.util.UUID;

/** Sinal sem dados pessoais para atualizar a ficha mesmo quando o provedor falha. */
public record ColetaCadastralDoLeadAtualizada(UUID leadId) {}
