package com.synapse.crm.atendimento.application.referencia;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrigemDeMensagemRepositorio {

    Optional<OrigemDeMensagem> buscar(UUID mensagemId, Instant enviadoEm);

    Optional<OrigemDeMensagem> buscarPorWamid(String wamid);

    /** Identidade comprovada e mesmo lead; nunca buscar por coincidencia no indice legado. */
    Optional<OrigemDeMensagem> buscarPorIdentidade(
            com.synapse.crm.atendimento.domain.canal.IdentidadeDeMensagemDoCanal identidade, UUID leadId);
}
