package com.synapse.crm.atendimento.infrastructure.persistencia.painel;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PainelDeAtendimentosRepositorioJdbcTest {

    /**
     * E209: a contagem e {@code COUNT(DISTINCT lead_id)} sobre as mesmas condicoes de visao — sem
     * janela nem lateral de ultima mensagem, que nao mudam quantos leads existem. O {@code JOIN lead}
     * fica: e ele que aplica a RLS de lead, como a listagem.
     */
    @Test
    void contagensContamLeadsDistintosSemJanelaNemUltimaMensagem() throws Exception {
        for (String campo : parametrosEsperados().keySet()) {
            String sql = constante(campo);

            assertThat(sql)
                    .as(campo)
                    .startsWith("SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id")
                    .doesNotContain(
                            "ROW_NUMBER() OVER",
                            "linha_do_lead",
                            "ultima.enviado_em",
                            "JOIN canal",
                            "JOIN etapa_atendimento",
                            "JOIN usuario",
                            "atendimento_leitura",
                            "AS nao_lidas");
        }
    }

    /**
     * E209: a listagem escolhe o atendimento de cada lead numa primeira fase estreita e so depois
     * monta o cartao. Nao lidas, dono e atendimento ativo nao podem voltar para a fase que roda
     * para cada atendimento da visao.
     */
    @Test
    void listagensMontamOCartaoSoParaOsAtendimentosEscolhidos() throws Exception {
        for (String campo : new String[] {
            "SQL_ATIVOS", "SQL_PENDENTES_PROPRIOS", "SQL_PENDENTES_TODOS", "SQL_POTENCIAIS", "SQL_TODOS",
            "SQL_FINALIZADOS"
        }) {
            String sql = constante(campo);
            int inicioDaEscolha = sql.indexOf("WHERE a.id IN (SELECT atendimento_id FROM (SELECT");
            assertThat(inicioDaEscolha).as(campo).isPositive();
            String escolha = sql.substring(inicioDaEscolha);

            assertThat(escolha)
                    .as(campo)
                    .contains("JOIN lead l ON l.id = a.lead_id", "ROW_NUMBER() OVER", "WHERE linha_do_lead = 1")
                    .doesNotContain("atendimento_leitura", "JOIN usuario", "AS atendimento_ativo_id");
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
