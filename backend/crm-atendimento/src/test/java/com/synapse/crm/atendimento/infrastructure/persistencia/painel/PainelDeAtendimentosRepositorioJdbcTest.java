package com.synapse.crm.atendimento.infrastructure.persistencia.painel;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PainelDeAtendimentosRepositorioJdbcTest {

    @Test
    void contagensUsamProjecaoMinimaSemCalculosDoCartao() throws Exception {
        for (String campo : parametrosEsperados().keySet()) {
            String sql = constante(campo);

            assertThat(sql)
                    .doesNotContain(
                            "JOIN lead",
                            "JOIN canal",
                            "JOIN etapa_atendimento",
                            "JOIN usuario",
                            "atendimento_leitura",
                            "AS atendimento_ativo_id",
                            "AS nao_lidas")
                    .contains("ROW_NUMBER() OVER", "LEFT JOIN LATERAL", "WHERE linha_do_lead = 1");
        }
    }

    @Test
    void contagensTemSomenteOsParametrosDeCadaFiltro() throws Exception {
        for (Map.Entry<String, Integer> entrada : parametrosEsperados().entrySet()) {
            long quantidade = constante(entrada.getKey()).chars().filter(caractere -> caractere == '?').count();

            assertThat(quantidade).as(entrada.getKey()).isEqualTo(entrada.getValue().longValue());
        }
    }

    @Test
    void buscaPorLeadMantemAProjecaoPontualSemPaginacaoExterna() throws Exception {
        String sql = constante("SQL_POR_LEAD");

        assertThat(sql)
                .contains("WHERE a.lead_id = ?", "ROW_NUMBER() OVER", "WHERE linha_do_lead = 1")
                .doesNotContain("OFFSET", "FETCH FIRST");
    }

    private static Map<String, Integer> parametrosEsperados() {
        return Map.of(
                "SQL_CONTAR_ATIVOS", 1,
                "SQL_CONTAR_PENDENTES_PROPRIOS", 2,
                "SQL_CONTAR_PENDENTES_TODOS", 0,
                "SQL_CONTAR_POTENCIAIS", 0,
                "SQL_CONTAR_TODOS", 0,
                "SQL_CONTAR_FINALIZADOS", 0);
    }

    private static String constante(String nome) throws Exception {
        Field campo = PainelDeAtendimentosRepositorioJdbc.class.getDeclaredField(nome);
        campo.setAccessible(true);
        return (String) campo.get(null);
    }
}
