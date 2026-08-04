package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/** Contrato HTTP da ficha lateral, dos vinculos de tag e da timeline paginada (E12). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class PainelDoLeadIT extends PostgresIT {

    private static final String PREFIXO = "E12-";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private UUID leadDaAna;
    private UUID leadDoBruno;
    private UUID tagId;

    @BeforeEach
    void prepararCenario() {
        jdbc.update(
                "DELETE FROM evento_timeline WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM lead_tag WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");

        UUID ana = idDoUsuario(EMAIL_ANA);
        UUID bruno = idDoUsuario(EMAIL_BRUNO);
        UUID canal = jdbc.queryForObject("SELECT id FROM canal ORDER BY nome LIMIT 1", UUID.class);
        tagId = jdbc.queryForObject("SELECT id FROM tag ORDER BY nome LIMIT 1", UUID.class);
        leadDaAna = criarLead("E12-Lead da Ana", ana, canal, 7, 42);
        leadDoBruno = criarLead("E12-Lead do Bruno", bruno, canal, 3, 5);
    }

    @Test
    @DisplayName("ficha traz campos longos e os contadores denormalizados; listagem continua enxuta")
    void ficha_trazDetalhesEContadores_semVazarNaListagem() {
        String ficha = comoAna(HttpMethod.GET, "/api/v1/leads/" + leadDaAna, null).getBody();

        assertThat(ficha)
                .contains("Nota E12")
                .contains("Resumo E12")
                .contains("\"numAtendimentos\":7")
                .contains("\"numMensagens\":42");

        String lista = comoAna(HttpMethod.GET, "/api/v1/leads", null).getBody();
        assertThat(lista).contains("E12-Lead da Ana");
        assertThat(lista).doesNotContain("Nota E12").doesNotContain("Resumo E12");
        assertThat(lista).doesNotContain("\"notas\"").doesNotContain("\"resumoIa\"");
    }

    @Test
    @DisplayName("atendente NAO abre ficha, tags nem timeline do lead de colega")
    void atendente_leadDeColega_recebe404EmTodosOsPontosDoPainel() {
        assertThat(comoAna(HttpMethod.GET, "/api/v1/leads/" + leadDoBruno, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(comoAna(HttpMethod.GET, "/api/v1/leads/" + leadDoBruno + "/tags", null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(comoAna(HttpMethod.GET, "/api/v1/leads/" + leadDoBruno + "/timeline", null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(comoAna(
                                HttpMethod.PUT,
                                "/api/v1/leads/" + leadDoBruno + "/tags/" + tagId,
                                null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM lead_tag WHERE lead_id = ?", Integer.class, leadDoBruno))
                .isZero();
    }

    @Test
    @DisplayName("adicionar e remover tag persiste e a resposta reflete o estado atual")
    void vinculoDeTag_cicloCompleto_persiste() {
        ResponseEntity<String> vinculada = comoAna(
                HttpMethod.PUT, "/api/v1/leads/" + leadDaAna + "/tags/" + tagId, null);

        assertThat(vinculada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(vinculada.getBody()).contains(tagId.toString());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM lead_tag WHERE lead_id = ? AND tag_id = ?",
                        Integer.class,
                        leadDaAna,
                        tagId))
                .isOne();

        ResponseEntity<String> removida = comoAna(
                HttpMethod.DELETE, "/api/v1/leads/" + leadDaAna + "/tags/" + tagId, null);

        assertThat(removida.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(removida.getBody()).doesNotContain(tagId.toString());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM lead_tag WHERE lead_id = ? AND tag_id = ?",
                        Integer.class,
                        leadDaAna,
                        tagId))
                .isZero();
    }

    @Test
    @DisplayName("timeline pagina sem COUNT global e devolve a origem de cada evento")
    void timeline_duasPaginas_ordemDecrescenteEOrigemVisivel() throws Exception {
        Instant inicio = Instant.parse("2026-08-03T12:00:00Z");
        String[] origens = {"SISTEMA", "AUTOMACAO", "USUARIO"};
        for (int indice = 0; indice < 21; indice++) {
            jdbc.update(
                    """
                    INSERT INTO evento_timeline
                        (id, lead_id, tipo, descricao, origem, criado_em)
                    VALUES (?, ?, ?, ?, ?::origem_evento, ?)
                    """,
                    UUID.randomUUID(),
                    leadDaAna,
                    "E12_EVENTO_" + indice,
                    "Evento E12 " + indice,
                    origens[indice % origens.length],
                    Timestamp.from(inicio.plusSeconds(indice)));
        }

        JsonNode primeira = json.readTree(
                comoAna(HttpMethod.GET, "/api/v1/leads/" + leadDaAna + "/timeline?pagina=0", null)
                        .getBody());
        JsonNode segunda = json.readTree(
                comoAna(HttpMethod.GET, "/api/v1/leads/" + leadDaAna + "/timeline?pagina=1", null)
                        .getBody());

        assertThat(primeira.path("eventos")).hasSize(20);
        assertThat(primeira.path("temMais").asBoolean()).isTrue();
        assertThat(primeira.path("eventos").get(0).path("descricao").asText())
                .isEqualTo("Evento E12 20");
        assertThat(primeira.toString())
                .contains("\"origem\":\"SISTEMA\"")
                .contains("\"origem\":\"AUTOMACAO\"")
                .contains("\"origem\":\"USUARIO\"");
        assertThat(segunda.path("eventos")).hasSize(1);
        assertThat(segunda.path("temMais").asBoolean()).isFalse();
        assertThat(segunda.path("eventos").get(0).path("descricao").asText())
                .isEqualTo("Evento E12 0");
    }

    @Test
    @DisplayName("canal de origem e exposto sem qualquer credencial")
    void canais_listaSomenteMetadadosPublicos() {
        String corpo = comoAna(HttpMethod.GET, "/api/v1/canais", null).getBody();

        assertThat(corpo).contains("WhatsApp Principal").contains("WHATSAPP");
        assertThat(corpo).doesNotContain("tokenRef").doesNotContain("identificadorExterno");
    }

    private UUID criarLead(
            String nome, UUID atendente, UUID canal, int atendimentos, int mensagens) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO lead
                    (id, nome, atendente_responsavel_id, canal_origem_id, status_basico,
                     notas, resumo_ia, num_atendimentos, num_mensagens)
                VALUES (?, ?, ?, ?, 'EM_ATENDIMENTO', 'Nota E12', 'Resumo E12', ?, ?)
                """,
                id,
                nome,
                atendente,
                canal,
                atendimentos,
                mensagens);
        return id;
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private ResponseEntity<String> comoAna(HttpMethod metodo, String url, Object corpo) {
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }
}
