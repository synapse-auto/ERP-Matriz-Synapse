package com.synapse.crm.atendimento.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reserva persistente de um envio da Automacao, feita ANTES de chamar o provedor (docs/50).
 * A atomicidade vem da PK de {@code envio_automacao_reserva.chave}.
 */
public interface ReservaDeEnvioDaAutomacaoRepositorio {

    /** Insere a reserva; {@code false} se a chave ja existia (de qualquer atendimento). */
    boolean reservar(String chave, UUID atendimentoId, Instant agora);

    Optional<Reserva> buscar(String chave);

    /**
     * Marca ENVIADO com o wamid de saida, somente se a reserva for deste atendimento e ainda estiver
     * RESERVADO. {@code false} quando nada mudou (inexistente, de outro atendimento ou ja concluida).
     */
    boolean concluir(String chave, UUID atendimentoId, String wamidSaida, Instant agora);

    /** Reservas ainda RESERVADO feitas antes do limite: a janela ambigua, para conciliacao. */
    List<Reserva> pendentesAntesDe(Instant limite, int maximo);

    record Reserva(
            String chave,
            UUID atendimentoId,
            Estado estado,
            String wamidSaida,
            Instant reservadoEm,
            Instant enviadoEm) {}

    enum Estado {
        RESERVADO,
        ENVIADO
    }
}
