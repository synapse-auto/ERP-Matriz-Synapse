package com.synapse.crm.relatorios.application.dashboard;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.relatorios.domain.dashboard.FiltroTemporalDashboard;
import com.synapse.crm.relatorios.domain.dashboard.VisaoGeralDashboard;

/** Consulta consolidada do dashboard gerencial. */
@Service
public class ObterVisaoGeralDashboardUseCase {

    private final DashboardVisaoGeralRepositorio repositorio;
    private final ZoneId fusoHorario;

    public ObterVisaoGeralDashboardUseCase(
            DashboardVisaoGeralRepositorio repositorio,
            @Value("${synapse.tenant.timezone}") String fusoHorario) {
        this.repositorio = repositorio;
        this.fusoHorario = ZoneId.of(fusoHorario);
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR')")
    @Transactional(readOnly = true)
    public VisaoGeralDashboard executar(
            int ano, List<Integer> meses, LocalDate origemInicio, LocalDate origemFim) {
        return repositorio.consultar(
                FiltroTemporalDashboard.de(ano, meses, origemInicio, origemFim, fusoHorario));
    }
}
