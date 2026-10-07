package com.synapse.crm.atendimento.infrastructure.persistencia.painel;

import static org.assertj.core.api.Assertions.assertThat;

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
 * E225: com o teto MAIOR que o total de cartoes, a lista simples devolve exatamente o que devolvia antes do teto (mesmas
 * linhas, mesma ordem) — comparada com a consulta antiga (sem LIMIT, recomposta por reflexao) sob a RLS real. Complementa
 * {@link ListaLegadaLimitadaIT}, que prova o corte com teto pequeno.
 */
@SpringBootTest(classes = SynapseCrmApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.painel.listagem-maxima=1000")
class ListaLegadaSemCorteIT extends PostgresIT {

    private static final String PREFIXO = "E225-semcorte-";

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

        for (int i = 0; i < 4; i++) {
            UUID atendimento = atendimento(lead("potencial-" + i, null, "IA"), null, "EM_IA", -100 - i);
            if (i % 2 == 0) {
                mensagem(atendimento, -50 + (i * 5));
            }
        }
        mensagem(atendimento(lead("ativo-1", ana, "EM_ATENDIMENTO"), ana, "EM_ATENDIMENTO", -40), -30);
        mensagem(atendimento(lead("ativo-2", ana, "EM_ATENDIMENTO"), ana, "EM_ATENDIMENTO", -35), -20);
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
    @DisplayName("com o teto acima do total: POTENCIAIS, TODOS e PENDENTES (gestao) e ATIVOS (atendente) saem identicos aos de antes")
    void listaIdenticaAAntigaQuandoOTotalCabeNoTeto() throws Exception {
        comparar("GESTOR", gestor, "gestor@dev.local", "gestor123", "POTENCIAIS", "WHERE_POTENCIAIS", gestor);
        comparar("GESTOR", gestor, "gestor@dev.local", "gestor123", "TODOS", "WHERE_TODOS_ATIVOS", gestor);
        comparar("GESTOR", gestor, "gestor@dev.local", "gestor123", "PENDENTES", "WHERE_PENDENTES_TODOS", gestor);
        comparar("ATENDENTE", ana, "ana@dev.local", "atendente123", "ATIVOS", "WHERE_ATIVOS", ana, ana);
    }

    private void comparar(
            String papel, UUID usuario, String email, String senha, String visao, String nomeDoWhere, Object... argumentos)
            throws Exception {
        List<String> antiga = idsDaConsultaAntiga(papel, usuario, nomeDoWhere, argumentos);
        List<String> nova = idsDoEndpoint(email, senha, visao);

        assertThat(antiga.size()).as("o total precisa caber no teto (%s)", visao).isLessThan(1000);
        assertThat(nova).as("%s / %s", papel, visao).isEqualTo(antiga);
    }

    // --- apoio ----------------------------------------------------------------------------------------------------

    private List<String> idsDoEndpoint(String email, String senha, String visao) throws Exception {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        var resposta = ApoioAutenticacao.comToken(
                http, token, HttpMethod.GET, "/api/v1/atendimentos?visao=" + visao, String.class);
        // Cabendo no teto, nao ha sinal de truncamento: a resposta e a de sempre.
        assertThat(resposta.getHeaders().containsKey("X-Lista-Truncada")).as("cabecalho em " + visao).isFalse();
        assertThat(resposta.getHeaders().containsKey("X-Lista-Teto")).as("cabecalho de teto em " + visao).isFalse();
        JsonNode corpo = json.readTree(resposta.getBody());
        List<String> ids = new ArrayList<>();
        corpo.forEach(cartao -> ids.add(cartao.path("atendimentoId").asText()));
        return ids;
    }

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
        // PENDENTES de gestao nao tem filtro por usuario: o unico "?" e o de nao_lidas.
        Object[] parametros = argumentos.length == 0 ? new Object[] {usuario} : argumentos;
        return new TransactionTemplate(new DataSourceTransactionManager(chat)).execute(status -> {
            consulta.execute("SET LOCAL ROLE synapse_app");
            consulta.queryForList("SELECT set_config('app.papel', ?, TRUE)", papel);
            consulta.queryForList("SELECT set_config('app.usuario_id', ?, TRUE)", usuario.toString());
            return consulta.query(sql, (linha, i) -> linha.getString("atendimento_id"), parametros);
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
