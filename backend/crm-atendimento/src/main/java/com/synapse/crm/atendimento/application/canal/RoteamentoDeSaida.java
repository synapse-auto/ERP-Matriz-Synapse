package com.synapse.crm.atendimento.application.canal;

import java.util.UUID;

/** Resolve a identidade apenas ao criar uma intencao nova; nunca no retry do dispatcher. */
public interface RoteamentoDeSaida {
    Rota resolver(UUID atendimentoId, UUID credencialHistorica, String contextoExterno);
    record Rota(UUID credencialId, UUID transicaoId) {}
}
