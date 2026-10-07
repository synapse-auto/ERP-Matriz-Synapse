package com.synapse.crm.atendimento.infrastructure.persistencia.painel;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

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
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * E225 (B5): as contagens das abas de andamento (ATIVOS, PENDENTES, POTENCIAIS, TODOS) passaram de "EXISTS por lead" para
 * "filtrar direto as linhas abertas". Este teste prova que o NUMERO nao mudou: a consulta ANTIGA (recomposta com os
 * {@code WHERE_*} por lead, que continuam sendo a fonte da listagem) e a NOVA rodam sob a RLS real e devolvem o mesmo valor,
 * nos papeis ATENDENTE (dois usuarios), OPERADOR e GESTOR.
 *
 * <p>O cenario cobre o que a RLS faz diferente por papel: atendimento de outro atendente (invisivel), participante ativo e
 * participante que saiu, convite pendente vigente e convite expirado, lead visivel cujo unico atendimento aberto e de um
 * colega, lead com dois atendimentos abertos (conta uma vez), ultima mensagem de sistema depois da do lead e atendimento
 * sem mensagens. Cada combinacao tambem precisa devolver um valor que nao e zero em algum lugar, e os papeis precisam
 * divergir entre si onde a RLS manda, para o teste nao ser vacuo.
 */
@SpringBootTest(classes = SynapseCrmApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class PainelContagemEquivalenciaIT extends PostgresIT {

    private static final String PREFIXO = "E225-b5-";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    @Qualifier(Pools.CHAT_DATA_SOURCE) private DataSource chat;

    private UUID ana;
    private UUID bruno;
    private UUID gestor;
    private UUID operador;
    private Instant base;

    @BeforeEach
    void semear() {
        ana = idDoUsuario("ana@dev.local");
        bruno = idDoUsuario("bruno@dev.local");
        gestor = idDoUsuario("gestor@dev.local");
        operador = criarOperador();
        // Meio-dia do dia 2 do mes corrente (UTC): os deslocamentos ficam no mesmo dia e no mes que ja tem particao.
        base = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(2).atTime(12, 0).toInstant(ZoneOffset.UTC);

        // L1: ciclo antigo FINALIZADO + atual da Ana, pendente (ultima mensagem do lead).
        UUID l1 = lead("pendente-ana", ana, "EM_ATENDIMENTO");
        mensagem(atendimento(l1, ana, "FINALIZADO", -400), "ATENDENTE", -390);
        mensagem(atendimento(l1, ana, "EM_ATENDIMENTO", -100), "LEAD", -80);

        // L2: ativo da Ana, ultima do atendente (nao e pendente).
        UUID a2 = atendimento(lead("ativo-ana", ana, "EM_ATENDIMENTO"), ana, "EM_ATENDIMENTO", -75);
        mensagem(a2, "LEAD", -70);
        mensagem(a2, "ATENDENTE", -60);

        // L3: pendente do Bruno; invisivel para Ana e Operador.
        mensagem(atendimento(lead("pendente-bruno", bruno, "EM_ATENDIMENTO"), bruno, "EM_ATENDIMENTO", -55), "LEAD", -50);

        // L4: do Bruno; Ana e participante ativa, o Operador participou mas saiu.
        UUID a4 = atendimento(lead("participante", bruno, "EM_ATENDIMENTO"), bruno, "EM_ATENDIMENTO", -45);
        mensagem(a4, "LEAD", -40);
        jdbc.update("INSERT INTO atendimento_participante (atendimento_id, usuario_id) VALUES (?, ?)", a4, ana);
        jdbc.update(
                "INSERT INTO atendimento_participante (atendimento_id, usuario_id, entrou_em, saiu_em)"
                        + " VALUES (?, ?, now() - interval '2 hours', now() - interval '1 hour')",
                a4,
                operador);

        // L5: do Bruno; convite VIGENTE para a Ana e convite EXPIRADO para o Operador.
        UUID a5 = atendimento(lead("convite-ana", bruno, "EM_ATENDIMENTO"), bruno, "EM_ATENDIMENTO", -35);
        mensagem(a5, "LEAD", -30);
        convite(a5, ana, "now()");
        convite(a5, operador, "now() - interval '3 days'");

        // L6: do Bruno; convite VIGENTE para o Operador.
        UUID a6 = atendimento(lead("convite-operador", bruno, "EM_ATENDIMENTO"), bruno, "EM_ATENDIMENTO", -33);
        mensagem(a6, "LEAD", -29);
        convite(a6, operador, "now()");

        // L7/L8: potenciais (EM_IA), um sem mensagem e outro com.
        atendimento(lead("potencial-vazio", null, "IA"), null, "EM_IA", -25);
        mensagem(atendimento(lead("potencial-msg", null, "IA"), null, "EM_IA", -22), "LEAD", -20);

        // L9: lead visivel (status IA) com ciclo FINALIZADO visivel e um EM_ATENDIMENTO do Bruno (invisivel a Ana/Operador).
        UUID l9 = lead("aberto-invisivel", null, "IA");
        atendimento(l9, ana, "FINALIZADO", -300);
        mensagem(atendimento(l9, bruno, "EM_ATENDIMENTO", -18), "LEAD", -17);

        // L10: dois atendimentos abertos no mesmo lead (conta uma vez so).
        UUID l10 = lead("dois-abertos", ana, "EM_ATENDIMENTO");
        mensagem(atendimento(l10, ana, "EM_ATENDIMENTO", -16), "LEAD", -15);
        mensagem(atendimento(l10, ana, "EM_ATENDIMENTO", -14), "LEAD", -13);

        // L11: ultima mensagem de sistema depois da do lead: a de sistema nao conta para "pendente".
        UUID a11 = atendimento(lead("sistema-depois", ana, "EM_ATENDIMENTO"), ana, "EM_ATENDIMENTO", -12);
        mensagem(a11, "LEAD", -11);
        mensagem(a11, "SISTEMA", -10);

        // L12: sem mensagens (ultima_visivel nula).
        atendimento(lead("sem-mensagens", ana, "EM_ATENDIMENTO"), ana, "EM_ATENDIMENTO", -9);

        // L13: ciclo FINALIZADO e outro EM_IA aberto.
        UUID l13 = lead("ia-e-finalizado", null, "IA");
        atendimento(l13, ana, "FINALIZADO", -200);
        atendimento(l13, null, "EM_IA", -8);

        // L14: so finalizado (nao entra em nenhuma das quatro).
        atendimento(lead("so-finalizado", bruno, "FINALIZADO"), bruno, "FINALIZADO", -7);
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
        jdbc.update("DELETE FROM usuario WHERE nome LIKE ?", PREFIXO + "%");
    }

    @Test
    @DisplayName("a contagem nova devolve o mesmo valor da antiga em todas as abas e papeis (RLS real)")
    void mesmoValorNasQuatroAbasEmTodosOsPapeis() {
        long somaDeTudo = 0;
        for (Papel papel : papeis()) {
            for (Aba aba : abas(papel.usuario())) {
                long antiga = contarComo(papel, PainelDeAtendimentosRepositorioJdbc.contar(aba.whereAntigo()), aba.argumentos());
                long nova = contarComo(papel, aba.sqlNovo(), aba.argumentos());

                assertThat(nova).as("%s / %s", papel.nome(), aba.nome()).isEqualTo(antiga);
                somaDeTudo += nova;
            }
        }
        assertThat(somaDeTudo).as("o cenario precisa produzir contagens nao nulas").isGreaterThan(20);
    }

    @Test
    @DisplayName("o cenario distingue os papeis onde a RLS manda (o teste nao e vacuo)")
    void osPapeisDivergemOndeARlsManda() {
        long todosDoGestor = contarComo(
                new Papel("GESTOR", "GESTOR", gestor), PainelDeAtendimentosRepositorioJdbc.SQL_CONTAR_TODOS);
        long todosDaAna = contarComo(
                new Papel("ATENDENTE", "Ana", ana), PainelDeAtendimentosRepositorioJdbc.SQL_CONTAR_TODOS);
        long todosDoOperador = contarComo(
                new Papel("OPERADOR", "Operador", operador), PainelDeAtendimentosRepositorioJdbc.SQL_CONTAR_TODOS);

        // A gestao enxerga tudo; Ana e Operador enxergam menos (atendimentos de colega, convite expirado, participante que saiu).
        assertThat(todosDoGestor).isGreaterThan(todosDaAna);
        assertThat(todosDoGestor).isGreaterThan(todosDoOperador);
        // Ana ve L4 (participante) e L5 (convite); o Operador, L6 (convite) mas nao L4 nem L5.
        assertThat(todosDaAna).isNotEqualTo(todosDoOperador);
    }

    @Test
    @DisplayName("a consulta nova e de fato outra: o teste nao compara o mesmo texto com ele mesmo")
    void asConsultasNovaEAntigaSaoTextosDiferentes() {
        assertThat(PainelDeAtendimentosRepositorioJdbc.SQL_CONTAR_TODOS)
                .doesNotContain("EXISTS")
                .contains("a.status IN ('EM_ATENDIMENTO', 'EM_IA')")
                .isNotEqualTo(PainelDeAtendimentosRepositorioJdbc.contar(PainelDeAtendimentosRepositorioJdbc.WHERE_TODOS_ATIVOS));
        assertThat(PainelDeAtendimentosRepositorioJdbc.contar(PainelDeAtendimentosRepositorioJdbc.WHERE_TODOS_ATIVOS))
                .contains("EXISTS (SELECT 1 FROM atendimento aberto");
    }

    // --- comparacao -----------------------------------------------------------------------------------------------

    private record Papel(String papelRls, String nome, UUID usuario) {}

    private record Aba(String nome, String whereAntigo, String sqlNovo, Object[] argumentos) {}

    private List<Papel> papeis() {
        return List.of(
                new Papel("ATENDENTE", "ATENDENTE Ana", ana),
                new Papel("ATENDENTE", "ATENDENTE Bruno", bruno),
                new Papel("OPERADOR", "OPERADOR", operador),
                new Papel("GESTOR", "GESTOR", gestor));
    }

    /** As cinco consultas por visao, com os argumentos na ordem dos {@code ?} de cada texto. */
    private static List<Aba> abas(UUID usuario) {
        return List.of(
                new Aba("ATIVOS", PainelDeAtendimentosRepositorioJdbc.WHERE_ATIVOS,
                        PainelDeAtendimentosRepositorioJdbc.SQL_CONTAR_ATIVOS, new Object[] {usuario}),
                new Aba("PENDENTES (proprios)", PainelDeAtendimentosRepositorioJdbc.WHERE_PENDENTES_PROPRIOS,
                        PainelDeAtendimentosRepositorioJdbc.SQL_CONTAR_PENDENTES_PROPRIOS, new Object[] {usuario, usuario}),
                new Aba("PENDENTES (todos)", PainelDeAtendimentosRepositorioJdbc.WHERE_PENDENTES_TODOS,
                        PainelDeAtendimentosRepositorioJdbc.SQL_CONTAR_PENDENTES_TODOS, new Object[0]),
                new Aba("POTENCIAIS", PainelDeAtendimentosRepositorioJdbc.WHERE_POTENCIAIS,
                        PainelDeAtendimentosRepositorioJdbc.SQL_CONTAR_POTENCIAIS, new Object[0]),
                new Aba("TODOS", PainelDeAtendimentosRepositorioJdbc.WHERE_TODOS_ATIVOS,
                        PainelDeAtendimentosRepositorioJdbc.SQL_CONTAR_TODOS, new Object[0]));
    }

    private long contarComo(Papel papel, String sql, Object... argumentos) {
        JdbcTemplate consulta = new JdbcTemplate(chat);
        Long total = new TransactionTemplate(new DataSourceTransactionManager(chat)).execute(status -> {
            consulta.execute("SET LOCAL ROLE synapse_app");
            consulta.queryForList("SELECT set_config('app.papel', ?, TRUE)", papel.papelRls());
            consulta.queryForList("SELECT set_config('app.usuario_id', ?, TRUE)", papel.usuario().toString());
            return consulta.queryForObject(sql, Long.class, argumentos);
        });
        return total == null ? 0 : total;
    }

    // --- dados ----------------------------------------------------------------------------------------------------

    private Instant em(int minutos) {
        return base.plus(minutos, ChronoUnit.MINUTES);
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private UUID criarOperador() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel, status_presenca, ativo, senha_alterada_em)"
                        + " VALUES (?, ?, ?, 'hash-de-teste', 'OPERADOR', 'ONLINE', TRUE, now())",
                id,
                PREFIXO + "operador",
                "operador-" + id + "@e225.local");
        return id;
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

    private UUID atendimento(UUID leadId, UUID atendenteId, String status, int iniciadoEmMinutos) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, atendente_id, status, iniciado_em)"
                        + " VALUES (?, ?, ?, ?::status_atendimento, ?)",
                id,
                leadId,
                atendenteId,
                status,
                Timestamp.from(em(iniciadoEmMinutos)));
        return id;
    }

    private void mensagem(UUID atendimentoId, String remetenteTipo, int enviadoEmMinutos) {
        jdbc.update(
                "INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, conteudo, status_entrega, enviado_em)"
                        + " VALUES (?, ?, ?::remetente_tipo, 'TEXTO'::tipo_mensagem, ?, 'ENVIADO'::status_entrega, ?)",
                UUID.randomUUID(),
                atendimentoId,
                remetenteTipo,
                remetenteTipo + " " + enviadoEmMinutos,
                Timestamp.from(em(enviadoEmMinutos)));
    }

    private void convite(UUID atendimentoId, UUID solicitanteId, String solicitadoEm) {
        jdbc.update(
                "INSERT INTO pedido_entrada_atendimento (atendimento_id, solicitante_id, status, tipo, solicitado_em)"
                        + " VALUES (?, ?, 'PENDENTE', 'CONVITE', " + solicitadoEm + ")",
                atendimentoId,
                solicitanteId);
    }
}
