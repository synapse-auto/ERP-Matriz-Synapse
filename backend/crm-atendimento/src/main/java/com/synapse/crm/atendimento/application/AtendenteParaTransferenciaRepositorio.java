package com.synapse.crm.atendimento.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Consulta atômica do destino permitido pela distribuição interna. */
public interface AtendenteParaTransferenciaRepositorio {

    Optional<Destino> ativoAtendente(UUID atendenteId);

    /**
     * Atendentes ativos — o mesmo critério de {@link #exigirAtendenteAtivo(UUID)}, em lista.
     *
     * <p>Não filtra disponibilidade para a IA: um colega fora do rodízio continua podendo receber
     * uma conversa de outro atendente.
     */
    List<Destino> listarAtivos();

    AtendenteDestinoInvalidoException.Motivo motivoDaRecusa(UUID atendenteId);

    default Destino exigirAtendenteAtivo(UUID atendenteId) {
        return ativoAtendente(atendenteId)
                .orElseThrow(() -> new AtendenteDestinoInvalidoException(atendenteId, motivoDaRecusa(atendenteId)));
    }

    record Destino(UUID id, String nome) {}
}
