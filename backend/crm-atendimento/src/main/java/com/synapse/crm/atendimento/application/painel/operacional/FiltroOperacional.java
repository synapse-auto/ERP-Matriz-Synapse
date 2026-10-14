package com.synapse.crm.atendimento.application.painel.operacional;

import java.util.UUID;

/** Critérios tipados, interpretados no servidor antes de contar ou paginar. */
public record FiltroOperacional(Fila fila, UUID atendenteId, boolean entrada, String busca, UUID etapaId, Sinal sinal) {
    public enum Fila { EM_ABERTO, NOVAS, ATIVOS, PENDENTES, SEM_RESPOSTA, FINALIZADOS, POTENCIAIS }
    public enum Sinal { TODOS, NOVAS, SEM_RESPOSTA }

    public FiltroOperacional {
        if (fila == null) fila = Fila.EM_ABERTO;
        if (sinal == null) sinal = Sinal.TODOS;
        if (entrada && atendenteId != null) throw new IllegalArgumentException("entrada e atendenteId são incompatíveis");
        if (fila == Fila.FINALIZADOS && sinal != Sinal.TODOS) throw new IllegalArgumentException("finalizados não possuem sinal de fila aberta");
        busca = busca == null ? "" : busca.strip();
    }
}
