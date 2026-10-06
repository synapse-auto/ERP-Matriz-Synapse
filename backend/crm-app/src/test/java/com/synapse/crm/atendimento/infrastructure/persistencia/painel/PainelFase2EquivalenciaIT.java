package com.synapse.crm.atendimento.infrastructure.persistencia.painel;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.SynapseCrmApplication;
import com.synapse.crm.atendimento.application.painel.VisaoAtendimento;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * E224 (B1): a fase 2 da listagem do painel passou a ser dirigida pelas ids escolhidas ({@code FROM (ids) JOIN
 * atendimento}) em vez de {@code a.id IN (subconsulta)}. Este teste prova que o resultado nao mudou: a consulta
 * ANTIGA (recomposta aqui com os mesmos blocos de texto) e a NOVA rodam sob a mesma RLS e devolvem as mesmas linhas,
 * na mesma ordem, com o mesmo {@code linha_do_lead} — comparando todas as colunas do cartao.
 *
 * <p>Cobre as seis abas, nos papeis ATENDENTE (RLS completa de {@code atendimento}/{@code lead}, participante e
 * convite) e GESTOR (visao total), em pagina do tamanho do painel, em paginas de 2 com cursor e, para FINALIZADOS, com
 * o filtro por atendente. O contexto reproduz o {@code AplicadorDeContextoRls}: {@code SET LOCAL ROLE synapse_app} +
 * {@code app.papel} + {@code app.usuario_id}.
 */
@SpringBootTest(classes = SynapseCrmApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class PainelFase2EquivalenciaIT extends PostgresIT {

    private static final String PREFIXO = "E224-b1-";
    private static final int PAGINA_PEQUENA = 2;
    private static final int PAGINA_DO_PAINEL = 101;
    private static final int MAXIMO_DE_PAGINAS = 30;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    @Qualifier(Pools.CHAT_DATA_SOURCE) private DataSource chat;

    private UUID ana;
    private UUID bruno;
    private UUID gestor;
    private Instant base;

    @BeforeEach
    void semear() {
        ana = idDoUsuario("ana@dev.local");
        bruno = idDoUsuario("bruno@dev.local");
        gestor = idDoUsuario("gestor@dev.local");
        // Meio-dia do dia 2 do mes corrente (UTC): os deslocamentos de ate ~10 h ficam no mesmo dia e no mes que ja
        // tem particao de mensagem, qualquer que seja a data em que o teste rode.
        base = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(2).atTime(12, 0).toInstant(ZoneOffset.UTC);

        // L1: dois ciclos. O antigo FINALIZADO e o atual EM_ATENDIMENTO da Ana, pendente (ultima do lead), com leitura.
        UUID l1 = lead("ciclos-ana", ana, "EM_ATENDIMENTO");
        UUID a1Antigo = atendimento(l1, ana, "FINALIZADO", minutos(-400));
        mensagem(a1Antigo, "LEAD", minutos(-300));
        mensagem(a1Antigo, "ATENDENTE", minutos(-290));
        UUID a1Atual = atendimento(l1, ana, "EM_ATENDIMENTO", minutos(-100));
        mensagem(a1Atual, "ATENDENTE", minutos(-90));
        mensagem(a1Atual, "LEAD", minutos(-80));
        leitura(a1Atual, ana, minutos(-85));

        // L2: ativo da Ana, ultima mensagem do atendente (nao e pendente).
        UUID l2 = lead("ativo-ana", ana, "EM_ATENDIMENTO");
        UUID a2 = atendimento(l2, ana, "EM_ATENDIMENTO", minutos(-75));
        mensagem(a2, "LEAD", minutos(-70));
        mensagem(a2, "ATENDENTE", minutos(-60));

        // L3: pendente do Bruno (a Ana nao enxerga: lead e atendimento de colega).
        UUID l3 = lead("pendente-bruno", bruno, "EM_ATENDIMENTO");
        mensagem(atendimento(l3, bruno, "EM_ATENDIMENTO", minutos(-55)), "LEAD", minutos(-50));

        // L4: do Bruno, mas a Ana e participante ativa -> visivel a ela pela RLS de participante.
        UUID l4 = lead("participante", bruno, "EM_ATENDIMENTO");
        UUID a4 = atendimento(l4, bruno, "EM_ATENDIMENTO", minutos(-45));
        mensagem(a4, "LEAD", minutos(-40));
        jdbc.update("INSERT INTO atendimento_participante (atendimento_id, usuario_id) VALUES (?, ?)", a4, ana);

        // L5: do Bruno, com convite pendente para a Ana -> visivel e pendente para ela pela politica de convite.
        UUID l5 = lead("convite", bruno, "EM_ATENDIMENTO");
        UUID a5 = atendimento(l5, bruno, "EM_ATENDIMENTO", minutos(-35));
        mensagem(a5, "LEAD", minutos(-30));
        jdbc.update(
                "INSERT INTO pedido_entrada_atendimento (atendimento_id, solicitante_id, status, tipo)"
                        + " VALUES (?, ?, 'PENDENTE', 'CONVITE')",
                a5,
                ana);

        // L6/L7: potenciais (EM_IA), um sem nenhuma mensagem (ultima_mensagem_em NULL) e outro com.
        UUID l6 = lead("potencial-vazio", null, "IA");
        atendimento(l6, null, "EM_IA", minutos(-25));
        UUID l7 = lead("potencial-com-msg", null, "IA");
        mensagem(atendimento(l7, null, "EM_IA", minutos(-22)), "LEAD", minutos(-20));

        // L8/L9: finalizados; L8 e um ciclo de L9 empatam na ultima mensagem (desempate por id).
        UUID l8 = lead("finalizado-simples", bruno, "FINALIZADO");
        mensagem(atendimento(l8, bruno, "FINALIZADO", minutos(-520)), "ATENDENTE", minutos(-500));
        UUID l9 = lead("finalizado-multiplos", ana, "FINALIZADO");
        mensagem(atendimento(l9, ana, "FINALIZADO", minutos(-530)), "ATENDENTE", minutos(-500));
        atendimento(l9, bruno, "FINALIZADO", minutos(-510));

        // L10: ciclo FINALIZADO e outro EM_IA aberto (aparece em TODOS/POTENCIAIS, nao em FINALIZADOS).
        UUID l10 = lead("reaberto-em-ia", null, "IA");
        mensagem(atendimento(l10, ana, "FINALIZADO", minutos(-620)), "ATENDENTE", minutos(-600));
        atendimento(l10, null, "EM_IA", minutos(-15));
    }

    @AfterEach
    void limpar() {
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN (SELECT a.id FROM atendimento a"
                        + " JOIN lead l ON l.id = a.lead_id WHERE l.nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    @Test
    @DisplayName("ATENDENTE (RLS completa): seis abas, pagina do painel e paginas de 2 com cursor")
    void atendenteMantemOResultadoNasSeisAbas() {
        Map<VisaoAtendimento, Integer> minimo = Map.of(
                VisaoAtendimento.ATIVOS, 2,
                VisaoAtendimento.PENDENTES, 2,
                VisaoAtendimento.POTENCIAIS, 3,
                VisaoAtendimento.TODOS, 6,
                VisaoAtendimento.FINALIZADOS, 2);

        for (VisaoAtendimento visao : VisaoAtendimento.values()) {
            Set<UUID> emPaginasDe2 = compararPaginas("ATENDENTE", ana, true, visao, PAGINA_PEQUENA, null);
            List<Map<String, Object>> paginaDoPainel =
                    compararPagina("ATENDENTE", ana, true, visao, PAGINA_DO_PAINEL, null, null, false, null);

            assertThat(emPaginasDe2).as("ids de " + visao + " em paginas de 2").hasSizeGreaterThanOrEqualTo(minimo.get(visao));
            assertThat(paginaDoPainel).as("pagina do painel de " + visao).hasSizeGreaterThanOrEqualTo(minimo.get(visao));
        }
    }

    @Test
    @DisplayName("GESTOR (visao total): seis abas, pagina do painel e paginas de 2 com cursor")
    void gestorMantemOResultadoNasSeisAbas() {
        Map<VisaoAtendimento, Integer> minimo = Map.of(
                VisaoAtendimento.ATIVOS, 0,
                VisaoAtendimento.PENDENTES, 4,
                VisaoAtendimento.POTENCIAIS, 3,
                VisaoAtendimento.TODOS, 8,
                VisaoAtendimento.FINALIZADOS, 2);

        for (VisaoAtendimento visao : VisaoAtendimento.values()) {
            Set<UUID> emPaginasDe2 = compararPaginas("GESTOR", gestor, false, visao, PAGINA_PEQUENA, null);
            List<Map<String, Object>> paginaDoPainel =
                    compararPagina("GESTOR", gestor, false, visao, PAGINA_DO_PAINEL, null, null, false, null);

            assertThat(emPaginasDe2).as("ids de " + visao + " em paginas de 2").hasSizeGreaterThanOrEqualTo(minimo.get(visao));
            assertThat(paginaDoPainel).as("pagina do painel de " + visao).hasSizeGreaterThanOrEqualTo(minimo.get(visao));
        }
    }

    @Test
    @DisplayName("GESTOR em FINALIZADOS filtrando por atendente: mesmo resultado nas duas formas")
    void gestorComFiltroDeAtendenteMantemOResultado() {
        Set<UUID> daAna = compararPaginas("GESTOR", gestor, false, VisaoAtendimento.FINALIZADOS, PAGINA_PEQUENA, ana);
        Set<UUID> doBruno = compararPaginas("GESTOR", gestor, false, VisaoAtendimento.FINALIZADOS, PAGINA_PEQUENA, bruno);

        assertThat(daAna).isNotEmpty();
        assertThat(doBruno).isNotEmpty();
        assertThat(daAna).doesNotContainAnyElementsOf(doBruno);
    }

    @Test
    @DisplayName("a consulta nova e de fato outra: o teste nao compara o mesmo texto com ele mesmo")
    void aConsultaAntigaENovaSaoTextosDiferentes() {
        var escolha = PainelDeAtendimentosRepositorioJdbc.escolherPagina(
                VisaoAtendimento.TODOS, ana, true, false, null, null, PAGINA_DO_PAINEL, null);

        assertThat(consultaAntiga(escolha.sql()))
                .contains("WHERE a.id IN (")
                .isNotEqualTo(PainelDeAtendimentosRepositorioJdbc.cartoesDe(escolha.sql()));
        assertThat(PainelDeAtendimentosRepositorioJdbc.cartoesDe(escolha.sql()))
                .contains("JOIN atendimento a ON a.id = escolhidos.atendimento_id")
                .doesNotContain("a.id IN (");
    }

    // --- comparacao -----------------------------------------------------------------------------------------------

    /** Percorre todas as paginas com cursor e compara antiga x nova em cada uma; devolve as ids vistas (sem repeticao). */
    private Set<UUID> compararPaginas(
            String papel, UUID usuario, boolean restrito, VisaoAtendimento visao, int limite, UUID filtroAtendente) {
        Set<UUID> vistas = new HashSet<>();
        Instant depoisDe = null;
        UUID depoisDoId = null;
        boolean depoisSemAberto = false;
        for (int pagina = 0; pagina < MAXIMO_DE_PAGINAS; pagina++) {
            List<Map<String, Object>> linhas = compararPagina(
                    papel, usuario, restrito, visao, limite, depoisDe, depoisDoId, depoisSemAberto, filtroAtendente);
            for (Map<String, Object> linha : linhas) {
                assertThat(vistas.add((UUID) linha.get("atendimento_id")))
                        .as("%s/%s: atendimento repetido entre paginas", papel, visao)
                        .isTrue();
            }
            if (linhas.size() < limite) {
                return vistas;
            }
            Map<String, Object> ultima = linhas.get(linhas.size() - 1);
            Timestamp enviadoEm = (Timestamp) ultima.get("ultima_mensagem_em");
            depoisDe = enviadoEm == null ? null : enviadoEm.toInstant();
            depoisDoId = (UUID) ultima.get("atendimento_id");
            depoisSemAberto = ultima.get("atendimento_ativo_id") == null;
        }
        throw new AssertionError("paginacao nao terminou em " + MAXIMO_DE_PAGINAS + " paginas: " + papel + "/" + visao);
    }

    private List<Map<String, Object>> compararPagina(
            String papel, UUID usuario, boolean restrito, VisaoAtendimento visao, int limite, Instant depoisDe,
            UUID depoisDoId, boolean depoisSemAberto, UUID filtroAtendente) {
        var escolha = PainelDeAtendimentosRepositorioJdbc.escolherPagina(
                visao, usuario, restrito, depoisSemAberto, depoisDe, depoisDoId, limite, filtroAtendente);
        String sqlAntigo = consultaAntiga(escolha.sql());
        String sqlNovo = PainelDeAtendimentosRepositorioJdbc.cartoesDe(escolha.sql());
        Object[] argumentos = escolha.parametros().toArray();

        List<List<Map<String, Object>>> resultados = naTransacaoDoChat(papel, usuario, consulta -> List.of(
                consulta.queryForList(sqlAntigo, argumentos), consulta.queryForList(sqlNovo, argumentos)));
        List<Map<String, Object>> antigas = resultados.get(0);
        List<Map<String, Object>> novas = resultados.get(1);

        // Mesmas linhas, na mesma ordem, com todas as colunas do cartao (inclui linha_do_lead).
        assertThat(novas).as("%s/%s limite=%d cursor=%s", papel, visao, limite, depoisDoId).isEqualTo(antigas);
        assertThat(novas.stream().map(linha -> linha.get("linha_do_lead")).distinct().toList())
                .as("linha_do_lead de %s/%s", papel, visao)
                .isSubsetOf(List.of(1L));
        return novas;
    }

    /** A consulta ANTIGA: ids escolhidas como {@code a.id IN (subconsulta)}, exatamente o texto anterior ao B1. */
    private static String consultaAntiga(String escolhaSql) {
        return PainelDeAtendimentosRepositorioJdbc.agrupar(
                PainelDeAtendimentosRepositorioJdbc.CAMPOS + PainelDeAtendimentosRepositorioJdbc.ORIGEM
                        + " WHERE a.id IN (" + escolhaSql + ")");
    }

    private <T> T naTransacaoDoChat(String papel, UUID usuario, Function<JdbcTemplate, T> corpo) {
        JdbcTemplate consulta = new JdbcTemplate(chat);
        return new TransactionTemplate(new DataSourceTransactionManager(chat)).execute(status -> {
            consulta.execute("SET LOCAL ROLE synapse_app");
            consulta.queryForList("SELECT set_config('app.papel', ?, TRUE)", papel);
            consulta.queryForList("SELECT set_config('app.usuario_id', ?, TRUE)", usuario.toString());
            return corpo.apply(consulta);
        });
    }

    // --- dados ----------------------------------------------------------------------------------------------------

    private Instant minutos(int deslocamento) {
        return base.plus(deslocamento, ChronoUnit.MINUTES);
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private UUID lead(String nome, UUID responsavel, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico)"
                        + " VALUES (?, ?, ?, ?::status_basico_lead)",
                id,
                PREFIXO + nome,
                responsavel,
                status);
        return id;
    }

    private UUID atendimento(UUID leadId, UUID atendenteId, String status, Instant iniciadoEm) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, atendente_id, status, iniciado_em)"
                        + " VALUES (?, ?, ?, ?::status_atendimento, ?)",
                id,
                leadId,
                atendenteId,
                status,
                Timestamp.from(iniciadoEm));
        return id;
    }

    private void mensagem(UUID atendimentoId, String remetenteTipo, Instant enviadoEm) {
        jdbc.update(
                "INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, conteudo, status_entrega, enviado_em)"
                        + " VALUES (?, ?, ?::remetente_tipo, 'TEXTO'::tipo_mensagem, ?, 'ENVIADO'::status_entrega, ?)",
                UUID.randomUUID(),
                atendimentoId,
                remetenteTipo,
                remetenteTipo + " " + enviadoEm,
                Timestamp.from(enviadoEm));
    }

    private void leitura(UUID atendimentoId, UUID usuarioId, Instant lidoAte) {
        jdbc.update(
                "INSERT INTO atendimento_leitura (atendimento_id, usuario_id, lido_ate) VALUES (?, ?, ?)",
                atendimentoId,
                usuarioId,
                Timestamp.from(lidoAte));
    }
}
