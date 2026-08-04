package com.synapse.crm.core.domain.mensagemrapida;

import java.util.Objects;
import java.util.UUID;

public record MensagemRapida(UUID id, UUID atendenteId, String atendenteNome, String palavraChave,
        String conteudo, String tipoMidia) {
    public MensagemRapida { Objects.requireNonNull(id); Objects.requireNonNull(atendenteId); Objects.requireNonNull(palavraChave); Objects.requireNonNull(conteudo); }
}
