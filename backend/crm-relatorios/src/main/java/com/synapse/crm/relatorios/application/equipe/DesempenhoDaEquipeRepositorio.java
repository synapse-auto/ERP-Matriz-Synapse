package com.synapse.crm.relatorios.application.equipe;

import java.util.List;
import java.util.UUID;

/** Porta da parcela de atendimentos do desempenho da equipe. */
public interface DesempenhoDaEquipeRepositorio {

    List<AtendimentosPorAtendente> contarAtendimentos();

    record AtendimentosPorAtendente(UUID atendenteId, String atendenteNome, long atendimentos) {}
}
