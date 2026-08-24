package com.synapse.crm.core.application.lead;
import java.util.UUID;
public record LeadParaEntrada(UUID id, String nome, String empresa, UUID responsavelId, String responsavelNome) {}
