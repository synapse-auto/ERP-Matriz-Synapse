package com.synapse.crm.atendimento.application.painel.operacional;

import java.util.List;
import java.util.UUID;

/** Candidatos à invalidação de presença, não dados de equipe. A capacidade efetiva ainda é verificada. */
public interface DestinatariosDoPainelOperacional {
    List<UUID> candidatosDaPresenca(UUID usuarioAlterado);
}
