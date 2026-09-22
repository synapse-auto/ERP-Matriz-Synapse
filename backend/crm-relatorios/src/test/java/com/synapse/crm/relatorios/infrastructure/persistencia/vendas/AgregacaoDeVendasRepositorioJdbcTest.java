package com.synapse.crm.relatorios.infrastructure.persistencia.vendas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.synapse.crm.relatorios.domain.IntervaloTemporal;

@ExtendWith(MockitoExtension.class)
class AgregacaoDeVendasRepositorioJdbcTest {
    @Mock
    private JdbcTemplate jdbc;

    @Test
    void totalDeVendasPreservaDefinicaoCanonicaSemCalcularQuebraPorResponsavel() {
        IntervaloTemporal periodo = new IntervaloTemporal(
                Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"));
        IntervaloTemporal origem = new IntervaloTemporal(
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));
        when(jdbc.queryForObject(any(String.class), eq(Long.class), any(Object[].class))).thenReturn(3L);

        long total = new AgregacaoDeVendasRepositorioJdbc(jdbc).totalDeVendas(List.of(periodo), origem);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        assertThat(total).isEqualTo(3L);
        assertThat(sql.getValue())
                .contains("SELECT DISTINCT ON (e.lead_id) e.lead_id")
                .contains("e.dados ->> 'resultado_novo' = 'GANHO'")
                .contains("SELECT count(*) FROM vendas")
                .doesNotContain("LEFT JOIN usuario")
                .doesNotContain("GROUP BY v.responsavel_id");
    }
}
