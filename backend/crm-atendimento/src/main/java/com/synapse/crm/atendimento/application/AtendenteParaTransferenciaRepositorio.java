package com.synapse.crm.atendimento.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Consulta atômica do destino permitido pela distribuição interna. */
public interface AtendenteParaTransferenciaRepositorio {

    Optional<Destino> ativoAtendente(UUID atendenteId);

    /**
     * Destinos exibidos no diálogo de transferência.
     *
     * <p>Inclui todos os atendentes ativos. Subgestores só entram quando estão online e disponíveis
     * para a IA. Isso é deliberadamente mais restrito que {@link #exigirAtendenteAtivo(UUID)}, que
     * continua validando transferências explícitas e não altera o contrato da Automação.
     */
    List<Destino> listarAtivos();

    /**
     * Mesmo critério de {@link #listarAtivos()}, filtrado por nome (case-insensitive, substring).
     * Usado pela Automação para resolver o UUID de um atendente citado pelo cliente.
     */
    List<Destino> buscarPorNome(String busca);

    AtendenteDestinoInvalidoException.Motivo motivoDaRecusa(UUID atendenteId);

    default Destino exigirAtendenteAtivo(UUID atendenteId) {
        return ativoAtendente(atendenteId)
                .orElseThrow(() -> new AtendenteDestinoInvalidoException(atendenteId, motivoDaRecusa(atendenteId)));
    }

    record Destino(UUID id, String nome) {}
}
