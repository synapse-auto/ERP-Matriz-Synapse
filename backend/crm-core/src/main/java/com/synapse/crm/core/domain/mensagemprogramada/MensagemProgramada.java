package com.synapse.crm.core.domain.mensagemprogramada;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MensagemProgramada(
        UUID id, UUID leadId, String leadNome, UUID atendenteId, String atendenteNome,
        String conteudo, Instant dataEnvio, StatusMensagemProgramada status) {
    public MensagemProgramada {
        Objects.requireNonNull(id);
        Objects.requireNonNull(leadId);
        Objects.requireNonNull(atendenteId);
        Objects.requireNonNull(conteudo);
        Objects.requireNonNull(dataEnvio);
        Objects.requireNonNull(status);
    }
}
