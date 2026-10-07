package com.synapse.crm.atendimento.infrastructure.persistencia.painel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.SynapseCrmApplication;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * E225: {@code GET /api/v1/atendimentos?visao=} deixou de ser ilimitado. Com o teto em 3
 * ({@code synapse.painel.listagem-maxima}), o endpoint devolve os 3 primeiros cartoes <b>na mesma ordem</b> da consulta
 * antiga (ilimitada, recomposta aqui por reflexao com os mesmos blocos de texto), sob a RLS real; e uma visao com no
 * maximo 3 cartoes devolve exatamente o mesmo resultado de antes.
 */
@SpringBootTest(classes = SynapseCrmApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.painel.listagem-maxima=3")
class ListaLegadaLimitadaIT extends PostgresIT {

    private static final String PREFIXO = "E225-lista-";
    private static final int TETO = 3;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    @Qualifier(Pools.CHAT_DATA_SOURCE) private DataSource chat;

    private UUID ana;
    private UUID gestor;
    private Instant base;

    @BeforeEach
    void semear() {
        ana = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, "ana@dev.local");
        gestor = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, "gestor@dev.local");
        base = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(2).atTime(12, 0).toInstant(ZoneOffset.UTC);

        // Seis potenciais (EM_IA), com ultimas mensagens em horarios diferentes e dois sem mensagem: a ordem nao e trivial.
        for (int i = 0; i < 6; i++) {
            UUID atendimento = atendimento(lead("potencial-" + i, null, "IA"), null, "EM_IA", -100 - i);
            if (i % 3 != 2) {
                mensagem(atendimento, -50 + (i * 7));
            }
        }
        // Dois ativos da Ana (cabem no teto) e um finalizado.
        mensagem(atendimento(lead("ativo-1", ana, "EM_ATENDIMENTO"), ana, "EM_ATENDIMENTO", -40), -30);
        mensagem(atendimento(lead("ativo-2", ana, "EM_ATENDIMENTO"), ana, "EM_ATENDIMENTO", -35), -20);
        atendimento(lead("finalizado", ana, "FINALIZADO"), ana, "FINALIZADO", -300);
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
    @DisplayName("POTENCIAIS (mais de 3 cartoes): devolve os 3 primeiros, na ordem da consulta antiga")
    void potenciaisLimitadosSaoOPrefixoDaListaAntiga() throws Exception {
        // O unico "?" da consulta antiga de POTENCIAIS e o de nao_lidas (o usuario).
        List<String> antiga = idsDaConsultaAntiga("GESTOR", gestor, "WHERE_POTENCIAIS", gestor);
        List<String> nova = idsDoEndpoint("gestor@dev.local", "gestor123", "POTENCIAIS");

        assertThat(antiga).as("o cenario precisa ter mais cartoes que o teto").hasSizeGreaterThan(TETO);
        assertThat(nova).hasSize(TETO).isEqualTo(antiga.subList(0, TETO));
    }

    @Test
    @DisplayName("ATIVOS da Ana: os N primeiros da consulta antiga, N = min(teto, total), independente do volume do banco")
    void ativosSaoOPrefixoDaListaAntigaQualquerQueSejaOVolume() throws Exception {
        // Dois "?": nao_lidas e o filtro "atendente = usuario". O banco dos ITs e compartilhado: o total de ativos da Ana
        // varia com o que outros testes deixaram, entao o teste so afirma a relacao com a consulta antiga.
        List<String> antiga = idsDaConsultaAntiga("ATENDENTE", ana, "WHERE_ATIVOS", ana, ana);
        List<String> nova = idsDoEndpoint("ana@dev.local", "atendente123", "ATIVOS");

        assertThat(antiga).hasSizeGreaterThanOrEqualTo(2);
        assertThat(nova).isEqualTo(antiga.subList(0, Math.min(TETO, antiga.size())));
    }

    @Test
    @DisplayName("o teto invalido e recusado na criacao do repositorio")
    void tetoInvalidoERecusado() {
        assertThatThrownBy(() -> {
                    var construtor = PainelDeAtendimentosRepositorioJdbc.class.getDeclaredConstructors()[0];
                    construtor.setAccessible(true);
                    construtor.newInstance(chat, 0);
                })
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    // --- apoio ----------------------------------------------------------------------------------------------------

    private List<String> idsDoEndpoint(String email, String senha, String visao) throws Exception {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        JsonNode corpo = json.readTree(ApoioAutenticacao.comToken(
                        http, token, HttpMethod.GET, "/api/v1/atendimentos?visao=" + visao, String.class)
                .getBody());
        List<String> ids = new ArrayList<>();
        corpo.forEach(cartao -> ids.add(cartao.path("atendimentoId").asText()));
        return ids;
    }

    /** A consulta ANTERIOR (sem LIMIT): cartoesDe(escolher(WHERE_X)), executada sob a RLS do papel. */
    private List<String> idsDaConsultaAntiga(String papel, UUID usuario, String nomeDoWhere, Object... argumentos)
            throws Exception {
        Method escolher = PainelDeAtendimentosRepositorioJdbc.class.getDeclaredMethod("escolher", String.class);
        Method cartoesDe = PainelDeAtendimentosRepositorioJdbc.class.getDeclaredMethod("cartoesDe", String.class);
        Field where = PainelDeAtendimentosRepositorioJdbc.class.getDeclaredField(nomeDoWhere);
        escolher.setAccessible(true);
        cartoesDe.setAccessible(true);
        where.setAccessible(true);
        String sql = (String) cartoesDe.invoke(null, escolher.invoke(null, (String) where.get(null)));

        JdbcTemplate consulta = new JdbcTemplate(chat);
        return new TransactionTemplate(new DataSourceTransactionManager(chat)).execute(status -> {
            consulta.execute("SET LOCAL ROLE synapse_app");
            consulta.queryForList("SELECT set_config('app.papel', ?, TRUE)", papel);
            consulta.queryForList("SELECT set_config('app.usuario_id', ?, TRUE)", usuario.toString());
            return consulta.query(sql, (linha, i) -> linha.getString("atendimento_id"), argumentos);
        });
    }

    private Instant em(int minutos) {
        return base.plus(minutos, ChronoUnit.MINUTES);
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

    private void mensagem(UUID atendimentoId, int enviadoEmMinutos) {
        jdbc.update(
                "INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, conteudo, status_entrega, enviado_em)"
                        + " VALUES (?, ?, 'LEAD'::remetente_tipo, 'TEXTO'::tipo_mensagem, ?, 'ENVIADO'::status_entrega, ?)",
                UUID.randomUUID(),
                atendimentoId,
                "msg " + enviadoEmMinutos,
                Timestamp.from(em(enviadoEmMinutos)));
    }
}
