package com.synapse.crm.atendimento.application.tempo_real;

import java.util.List;
import java.util.UUID;

/** Porta da lista de usuarios ativos que a RN-CRM-01 autoriza no estado atual do atendimento. */
public interface DestinatariosTempoRealRepositorio {
    List<UUID> listarAutorizados(UUID atendimentoId);
}
