package com.synapse.crm.atendimento.application;

import java.util.Optional;
import java.util.UUID;

/** Consulta atômica do destino permitido pela distribuição interna. */
public interface AtendenteParaTransferenciaRepositorio {

    Optional<Destino> ativoAtendente(UUID atendenteId);

    record Destino(UUID id, String nome) {}
}
