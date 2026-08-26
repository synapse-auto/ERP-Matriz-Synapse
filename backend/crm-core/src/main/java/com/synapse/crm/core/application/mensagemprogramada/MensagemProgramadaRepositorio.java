package com.synapse.crm.core.application.mensagemprogramada;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.core.domain.mensagemprogramada.MensagemProgramada;

public interface MensagemProgramadaRepositorio {
    PaginaMensagensProgramadas listar(FiltroMensagensProgramadas filtro);
    MensagemProgramada criar(UUID leadId, UUID atendenteId, String conteudo, Instant dataEnvio);
    Optional<MensagemProgramada> porIdVisivel(UUID id);
    Optional<MensagemProgramada> atualizarAgendada(UUID id, String conteudo, Instant dataEnvio);
    Optional<MensagemProgramada> cancelarAgendada(UUID id);

    List<UUID> idsVencidos(Instant agora, int limite);
    Optional<MensagemProgramada> reservarVencida(UUID id, Instant agora);
}
