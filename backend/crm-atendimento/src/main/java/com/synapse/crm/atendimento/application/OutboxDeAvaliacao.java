package com.synapse.crm.atendimento.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Fila de avaliacao separada por tipo; todas as operacoes exigem a transacao do chat. */
public interface OutboxDeAvaliacao {
    void enfileirar(UUID atendimentoId, UUID leadId, UUID atendenteId, String telefone, Instant quando);
    List<Reserva> reservar(int limite, int maximoTentativas, Instant agora, Instant ate);
    boolean concluir(Reserva reserva, Instant quando);
    boolean falhar(Reserva reserva, Instant quando, Instant proxima, String motivo, boolean esgotada);
    long esgotadas();

    /** O token cerca a reserva: resultado antigo nunca altera uma reserva posterior. */
    record Reserva(UUID eventoId, UUID token, String payload, int tentativas) {
        @Override
        public String toString() {
            return "Reserva[eventoId=" + eventoId + ", tentativas=" + tentativas + "]";
        }
    }
}
