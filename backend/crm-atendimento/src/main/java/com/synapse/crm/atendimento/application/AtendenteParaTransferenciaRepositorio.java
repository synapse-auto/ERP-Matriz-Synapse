package com.synapse.crm.atendimento.application;

import java.util.Optional;
import java.util.UUID;

/** Consulta atômica do destino permitido pela distribuição interna. */
public interface AtendenteParaTransferenciaRepositorio {

    Optional<Destino> ativoAtendente(UUID atendenteId);

    AtendenteDestinoInvalidoException.Motivo motivoDaRecusa(UUID atendenteId);

    default Destino exigirAtendenteAtivo(UUID atendenteId) {
        return ativoAtendente(atendenteId)
                .orElseThrow(() -> new AtendenteDestinoInvalidoException(atendenteId, motivoDaRecusa(atendenteId)));
    }

    record Destino(UUID id, String nome) {}
}
