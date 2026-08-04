package com.synapse.crm.core.application.lembrete;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.core.domain.lembrete.Lembrete;
import com.synapse.crm.core.domain.lembrete.StatusLembrete;

/** Porta sem consultas irrestritas: toda listagem recebe filtro e toda mutacao depende da RLS. */
public interface LembreteRepositorio {
    PaginaLembretes listar(FiltroLembretes filtro);

    Lembrete criar(UUID leadId, UUID atendenteId, String texto, Instant dataHora);

    Optional<Lembrete> atualizar(UUID id, String texto, Instant dataHora, StatusLembrete status);

    boolean remover(UUID id);
}
