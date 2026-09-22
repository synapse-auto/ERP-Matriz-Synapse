package com.synapse.crm.relatorios.infrastructure.persistencia.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.synapse.crm.relatorios.application.vendas.AgregacaoDeVendasRepositorio;
import com.synapse.crm.relatorios.domain.IntervaloTemporal;
import com.synapse.crm.relatorios.domain.dashboard.FiltroTemporalDashboard;
import com.synapse.crm.relatorios.domain.vendas.AgregacaoDeVendas;

@ExtendWith(MockitoExtension.class)
class DashboardVisaoGeralRepositorioJdbcTest {
    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private AgregacaoDeVendasRepositorio vendas;

    private DashboardVisaoGeralRepositorioJdbc repositorio;

    @BeforeEach
    void prepararConsultasSemDados() throws Exception {
        repositorio = new DashboardVisaoGeralRepositorioJdbc(jdbc, vendas);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForObject(
                        anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class)))
                .thenAnswer(chamada -> {
                    ResultSet linha = org.mockito.Mockito.mock(ResultSet.class);
                    lenient().when(linha.getLong(anyString())).thenReturn(0L);
                    lenient().when(linha.getBigDecimal(anyString())).thenReturn(BigDecimal.ZERO);
                    return ((RowMapper<?>) chamada.getArgument(1)).mapRow(linha, 0);
                });
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class)))
                .thenReturn(List.of());
        when(vendas.agregar(org.mockito.ArgumentMatchers.<List<IntervaloTemporal>>any(), any()))
                .thenReturn(new AgregacaoDeVendas(0, 0, List.of()));
        when(vendas.totalDeVendas(org.mockito.ArgumentMatchers.<List<IntervaloTemporal>>any(), any()))
                .thenReturn(0L);
        when(vendas.contarAte(any(), any())).thenReturn(0L);
    }

    @Test
    void usaAgregacaoCompletaSomenteNoPeriodoAtualEProjecaoEstreitaNoAnteriorENoStatusAoVivo() {
        FiltroTemporalDashboard filtro = FiltroTemporalDashboard.de(2026, List.of(9), null, null, ZoneId.of("UTC"));

        repositorio.consultar(filtro);

        verify(vendas, times(1)).agregar(same(filtro.periodoAtual()), isNull());
        verify(vendas).totalDeVendas(eq(List.of(filtro.periodoAnterior())), isNull());
        verify(vendas, times(2))
                .totalDeVendas(org.mockito.ArgumentMatchers.<List<IntervaloTemporal>>any(), isNull());
    }
}
