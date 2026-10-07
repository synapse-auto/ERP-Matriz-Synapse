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
            int inicioDaEscolha = sql.indexOf("FROM (SELECT atendimento_id FROM (SELECT");
            int fimDaEscolha = sql.indexOf(") escolhidos\nJOIN atendimento a ON a.id = escolhidos.atendimento_id");
            assertThat(inicioDaEscolha).as(campo).isPositive();
            assertThat(fimDaEscolha).as(campo).isGreaterThan(inicioDaEscolha);
            String escolha = sql.substring(inicioDaEscolha, fimDaEscolha);

            assertThat(escolha)
                    .as(campo)
                    .contains("JOIN lead l ON l.id = a.lead_id", "ROW_NUMBER() OVER", "WHERE linha_do_lead = 1")
                    .doesNotContain("atendimento_leitura", "JOIN usuario", "AS atendimento_ativo_id");
        }
    }

    /**
     * E224 (B1): as ids escolhidas dirigem o join. A forma antiga ({@code a.id IN (subconsulta com LIMIT)}) foi medida
     * como semi join dirigido pelas ~3.900 linhas ja unidas; a nova poe a subconsulta no {@code FROM}, com {@code JOIN}
     * explicito a {@code atendimento}. O teste de integracao prova que o resultado e o mesmo.
     */
    @Test
    void asIdsEscolhidasDirigemOJoinEmVezDeUmInComSubconsulta() throws Exception {
        for (String campo : new String[] {
            "SQL_ATIVOS", "SQL_PENDENTES_PROPRIOS", "SQL_PENDENTES_TODOS", "SQL_POTENCIAIS", "SQL_TODOS",
            "SQL_FINALIZADOS"
        }) {
            assertThat(constante(campo))
                    .as(campo)
                    .contains("JOIN atendimento a ON a.id = escolhidos.atendimento_id")
                    .doesNotContain("a.id IN (");
        }
    }

    @Test
    void buscasPontuaisContinuamPartindoDeAtendimento() throws Exception {
        for (String campo : new String[] {"SQL_POR_ATENDIMENTO", "SQL_POR_LEAD"}) {
            assertThat(constante(campo))
                    .as(campo)
                    .contains("FROM atendimento a\nJOIN lead l ON l.id = a.lead_id")
                    .doesNotContain("escolhidos");
        }
    }

    /** A ordem dos {@code ?} e a dos argumentos: o {@code ?} de nao_lidas precede o filtro da primeira fase. */
    @Test
    void aEscolhaPaginadaTemUmArgumentoParaCadaInterrogacaoDoTextoFinal() {
        java.util.UUID usuario = java.util.UUID.randomUUID();
        java.util.UUID cursor = java.util.UUID.randomUUID();
        java.time.Instant data = java.time.Instant.parse("2026-10-06T12:00:00Z");

        for (var visao : com.synapse.crm.atendimento.application.painel.VisaoAtendimento.values()) {
            for (boolean restrito : new boolean[] {true, false}) {
                for (boolean comCursor : new boolean[] {false, true}) {
                    var escolha = PainelDeAtendimentosRepositorioJdbc.escolherPagina(
                            visao, usuario, restrito, false, comCursor ? data : null, comCursor ? cursor : null, 101, null);
                    String sql = PainelDeAtendimentosRepositorioJdbc.cartoesDe(escolha.sql());

                    assertThat(sql.chars().filter(caractere -> caractere == '?').count())
                            .as(visao + " restrito=" + restrito + " cursor=" + comCursor)
                            .isEqualTo((long) escolha.parametros().size());
                }
            }
        }
    }

    @Test
    void escolhaDaInboxDisponibilizaResponsavelDoCartaoAposElegerOCicloAtual() throws Exception {
        String campos = constante("CAMPOS_ESCOLHA");

        assertThat(campos)
                .contains("a.lead_id AS lead_id", "a.atendente_id AS responsavel_atendimento_id", "ROW_NUMBER() OVER");
    }

    @Test
    void filtroPorResponsavelUsaODonoDoAtendimentoAbertoDepoisDeElegerOCartao() throws Exception {
        var metodo = PainelDeAtendimentosRepositorioJdbc.class.getDeclaredMethod("escolherPorResponsavelAtual", String.class);
        metodo.setAccessible(true);
        String sql = (String) metodo.invoke(null, " WHERE EXISTS (SELECT 1 FROM atendimento ativo WHERE ativo.lead_id = a.lead_id)");

        assertThat(sql)
                .contains("WHERE linha_do_lead = 1", "LEFT JOIN LATERAL", "ativo_filtro.atendente_id",
                        "representante.responsavel_atendimento_id", "CASE WHEN ativo_filtro.id IS NULL")
                .doesNotContain("OFFSET");
    }

    @Test
    void contagensTemSomenteOsParametrosDeCadaFiltro() throws Exception {
        for (Map.Entry<String, Integer> entrada : parametrosEsperados().entrySet()) {
            long quantidade = constante(entrada.getKey()).chars().filter(caractere -> caractere == '?').count();

            assertThat(quantidade).as(entrada.getKey()).isEqualTo(entrada.getValue().longValue());
        }
    }

    /**
     * E225 (B5): as contagens de andamento filtram direto as linhas abertas, sem procurar com {@code EXISTS}, para cada
     * linha de atendimento, outro atendimento aberto do mesmo lead. FINALIZADOS e um {@code NOT EXISTS} e nao tem forma
     * equivalente: continua como era. O teste de integracao prova que o valor e o mesmo.
     */
    @Test
    void contagensDeAndamentoFiltramPeloStatusEFinalizadosContinuaComNotExists() throws Exception {
        for (String campo : new String[] {
            "SQL_CONTAR_ATIVOS", "SQL_CONTAR_PENDENTES_PROPRIOS", "SQL_CONTAR_PENDENTES_TODOS", "SQL_CONTAR_POTENCIAIS",
            "SQL_CONTAR_TODOS"
        }) {
            assertThat(constante(campo))
                    .as(campo)
                    .contains("WHERE a.status ")
                    .doesNotContain("EXISTS (SELECT 1 FROM atendimento aberto", "EXISTS (SELECT 1 FROM atendimento visivel");
        }
        assertThat(constante("SQL_CONTAR_FINALIZADOS"))
                .contains("NOT EXISTS (SELECT 1 FROM atendimento aberto")
                .doesNotContain("WHERE a.status ");
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
