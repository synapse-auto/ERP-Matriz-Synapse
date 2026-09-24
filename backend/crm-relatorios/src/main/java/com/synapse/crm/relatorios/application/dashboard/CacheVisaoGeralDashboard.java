package com.synapse.crm.relatorios.application.dashboard;

import java.util.function.Supplier;

import com.synapse.crm.relatorios.domain.dashboard.FiltroTemporalDashboard;
import com.synapse.crm.relatorios.domain.dashboard.VisaoGeralDashboard;

/** Cache somente dos agregados do dashboard; o status ao vivo e consultado em cada leitura. */
public interface CacheVisaoGeralDashboard {

    Resultado buscarOuCalcular(
            FiltroTemporalDashboard filtro, Supplier<VisaoGeralDashboard> consulta);

    record Resultado(VisaoGeralDashboard valor, boolean encontradoNoCache) {}
}
