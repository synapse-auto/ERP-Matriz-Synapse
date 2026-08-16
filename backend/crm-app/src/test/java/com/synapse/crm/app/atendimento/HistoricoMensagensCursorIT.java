package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.atendimento.historico.tamanho-pagina=2")
class HistoricoMensagensCursorIT extends PostgresIT {

    private static final String PREFIXO = "E13-CURSOR-";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    private UUID atendimentoId;
    private UUID anaId;
    private UUID brunoId;
    private Instant inicio;

    @BeforeEach
    void preparar() {
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?))",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");

        anaId = jdbc.queryForObject(
                "SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_ANA);
        brunoId = jdbc.queryForObject(
                "SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_BRUNO);
        UUID leadId = UUID.randomUUID();
        atendimentoId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico) VALUES (?, ?, ?, 'EM_ATENDIMENTO')",
                leadId,
                PREFIXO + leadId,
                anaId);
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, atendente_id, status) VALUES (?, ?, ?, 'EM_ATENDIMENTO')",
                atendimentoId,
                leadId,
                anaId);
        inicio = Instant.parse("2026-08-04T10:00:00Z");
        for (int indice = 1; indice <= 4; indice++) inserir("original-" + indice, inicio.plusSeconds(indice));
    }

    @Test
    @DisplayName("autoria historica vem do remetente da mensagem, nao do responsavel atual")
    void resolveAutoriaPeloRemetenteId() throws Exception {
        inserirDoAtendente("escrita-pelo-bruno", inicio.plusSeconds(20), brunoId);
        inserirDoAtendente("escrita-pela-ana", inicio.plusSeconds(21), anaId);

        JsonNode mensagens = pagina(null).path("mensagens");

        assertThat(nomeDoRemetente(mensagens, "escrita-pelo-bruno"))
                .isEqualTo(nomeDoUsuario(brunoId));
        assertThat(nomeDoRemetente(mensagens, "escrita-pela-ana"))
                .isEqualTo(nomeDoUsuario(anaId));
    }

    @Test
    @DisplayName("cursor nao duplica nem pula historico quando mensagem nova chega entre paginas")
    void cursorEstavelComMensagemNova() throws Exception {
        JsonNode primeira = pagina(null);
        assertThat(textos(primeira)).containsExactly("original-3", "original-4");
        String cursor = primeira.path("proximoCursor").asText();
        assertThat(cursor).isNotBlank();

        inserir("nova-durante-rolagem", inicio.plusSeconds(10));
        JsonNode segunda = pagina(cursor);

        assertThat(textos(segunda)).containsExactly("original-1", "original-2");
        Set<String> combinadas = new HashSet<>();
        combinadas.addAll(textos(primeira));
        combinadas.addAll(textos(segunda));
        assertThat(combinadas)
                .containsExactlyInAnyOrder("original-1", "original-2", "original-3", "original-4")
                .doesNotContain("nova-durante-rolagem");
        assertThat(segunda.path("proximoCursor").isNull()).isTrue();
    }

    private JsonNode pagina(String cursor) throws Exception {
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        String url = "/api/v1/atendimentos/" + atendimentoId + "/mensagens"
                + (cursor == null ? "" : "?cursor=" + cursor);
        String corpo = http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class)
                .getBody();
        return json.readTree(corpo);
    }

    private static java.util.List<String> textos(JsonNode pagina) {
        return pagina.path("mensagens").findValuesAsText("conteudo");
    }

    private static String nomeDoRemetente(JsonNode mensagens, String conteudo) {
        for (JsonNode mensagem : mensagens) {
            if (conteudo.equals(mensagem.path("conteudo").asText())) {
                return mensagem.path("remetenteNome").asText();
            }
        }
        throw new AssertionError("Mensagem nao encontrada: " + conteudo);
    }

    private String nomeDoUsuario(UUID usuarioId) {
        return jdbc.queryForObject("SELECT nome FROM usuario WHERE id = ?", String.class, usuarioId);
    }

    private void inserir(String conteudo, Instant enviadoEm) {
        jdbc.update(
                """
                INSERT INTO mensagem
                    (id, atendimento_id, remetente_tipo, tipo, conteudo, status_entrega, enviado_em)
                VALUES (?, ?, 'LEAD', 'TEXTO', ?, 'ENTREGUE', ?)
                """,
                UUID.randomUUID(),
                atendimentoId,
                conteudo,
                Timestamp.from(enviadoEm));
    }

    private void inserirDoAtendente(String conteudo, Instant enviadoEm, UUID remetenteId) {
        jdbc.update(
                """
                INSERT INTO mensagem
                    (id, atendimento_id, remetente_tipo, remetente_id, tipo, conteudo,
                     status_entrega, enviado_em)
                VALUES (?, ?, 'ATENDENTE', ?, 'TEXTO', ?, 'ENTREGUE', ?)
                """,
                UUID.randomUUID(),
                atendimentoId,
                remetenteId,
                conteudo,
                Timestamp.from(enviadoEm));
    }
}
