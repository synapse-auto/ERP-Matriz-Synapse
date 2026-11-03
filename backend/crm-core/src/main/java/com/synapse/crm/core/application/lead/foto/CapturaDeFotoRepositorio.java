package com.synapse.crm.core.application.lead.foto;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Cache/lease duráveis de enriquecimento; nenhuma consulta de carteira. */
public interface CapturaDeFotoRepositorio {
    boolean credencialAtiva(UUID credencial, String instancia);
    Optional<Reserva> reservar(UUID leadId, String instancia, UUID credencial, String telefoneHash, Instant agora, Instant ate);
    boolean concluir(Reserva reserva, String referencia, Instant proximaConsulta, String resultado);
    record Reserva(UUID leadId, UUID token, String instancia, UUID credencial, String referenciaGerada) {}
}
