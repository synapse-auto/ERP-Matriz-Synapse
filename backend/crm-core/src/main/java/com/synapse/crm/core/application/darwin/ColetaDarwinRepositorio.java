package com.synapse.crm.core.application.darwin;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Cache e lease persistentes: somente metadados, nunca CPF, resposta ou token Darwin. */
public interface ColetaDarwinRepositorio {
    Optional<UUID> reservar(UUID id, String identidade, Instant agora, Instant lease, boolean manual);
    boolean concluir(UUID id, UUID token, String estado, Instant proxima, Instant repeticaoManual);
    String estado(UUID id);
}
