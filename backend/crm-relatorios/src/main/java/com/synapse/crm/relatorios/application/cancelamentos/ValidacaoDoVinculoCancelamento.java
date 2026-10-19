package com.synapse.crm.relatorios.application.cancelamentos;

import java.util.UUID;

/** Composicao usa as portas oficiais (Specification e RLS), sem inferencia por contato. */
public interface ValidacaoDoVinculoCancelamento {
    void validar(UUID leadId, UUID atendimentoId);
}
