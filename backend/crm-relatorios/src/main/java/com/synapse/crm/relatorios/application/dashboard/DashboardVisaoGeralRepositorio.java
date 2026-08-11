package com.synapse.crm.relatorios.application.dashboard;

import com.synapse.crm.relatorios.domain.dashboard.FiltroTemporalDashboard;
import com.synapse.crm.relatorios.domain.dashboard.VisaoGeralDashboard;

/** Porta do read model agregado da Visão Geral. */
public interface DashboardVisaoGeralRepositorio {

    VisaoGeralDashboard consultar(FiltroTemporalDashboard filtro);
}
