package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    private UUID leadId;
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
        leadId = UUID.randomUUID();
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

    @Test
    @DisplayName("historico atravessa os tres atendimentos do lead em ordem e marca a troca")
    void historicoAgrupaAtendimentosDoMesmoLead() throws Exception {
        UUID atendimentoAntigo = criarAtendimento("FINALIZADO", inicio.minusSeconds(100));
        UUID atendimentoIntermediario = criarAtendimento("FINALIZADO", inicio.minusSeconds(50));
        inserir(atendimentoAntigo, "historico-antigo", inicio.minusSeconds(10));
        inserir(atendimentoIntermediario, "historico-intermediario", inicio.plusSeconds(5));
        inserir(atendimentoId, "historico-atual", inicio.plusSeconds(25));

        List<JsonNode> paginas = new ArrayList<>();
        String cursor = null;
        do {
            JsonNode pagina = pagina(cursor);
            paginas.add(pagina);
            cursor = pagina.path("proximoCursor").isNull()
                    ? null
                    : pagina.path("proximoCursor").asText();
        } while (cursor != null);

        // A API devolve páginas do mais recente para o mais antigo; a conversa
        // monta a ordem cronológica invertendo as páginas, como o cliente faz.
        List<JsonNode> mensagens = paginas.reversed().stream()
                .flatMap(pagina -> {
                    List<JsonNode> itens = new ArrayList<>();
                    pagina.path("mensagens").forEach(itens::add);
                    return itens.stream();
                })
                .toList();
        List<String> textos = mensagens.stream()
                .map(item -> item.path("conteudo").asText())
                .toList();

        assertThat(textos).contains("historico-antigo", "historico-intermediario", "historico-atual");
        assertThat(textos.stream()
                        .filter(texto -> texto.startsWith("historico-"))
                        .toList())
                .containsExactly("historico-antigo", "historico-intermediario", "historico-atual");
        assertThat(textos.indexOf("historico-intermediario"))
                .isGreaterThan(textos.indexOf("historico-antigo"));
        assertThat(textos.indexOf("historico-atual"))
                .isGreaterThan(textos.indexOf("historico-intermediario"));
        JsonNode antigo = mensagens.stream()
                .filter(item -> "historico-antigo".equals(item.path("conteudo").asText()))
                .findFirst()
                .orElseThrow();
        JsonNode intermediario = mensagens.stream()
                .filter(item -> "historico-intermediario".equals(item.path("conteudo").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(antigo.path("atendimentoId").asText()).isEqualTo(atendimentoAntigo.toString());
        assertThat(intermediario.path("atendimentoId").asText())
                .isEqualTo(atendimentoIntermediario.toString());
        assertThat(antigo.path("atendimentoResponsavelNome").asText())
                .isEqualTo(nomeDoUsuario(anaId));
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
        inserir(atendimentoId, conteudo, enviadoEm);
    }

    private void inserir(UUID idAtendimento, String conteudo, Instant enviadoEm) {
        jdbc.update(
                """
                INSERT INTO mensagem
                    (id, atendimento_id, remetente_tipo, tipo, conteudo, status_entrega, enviado_em)
                VALUES (?, ?, 'LEAD', 'TEXTO', ?, 'ENTREGUE', ?)
                """,
                UUID.randomUUID(),
                idAtendimento,
                conteudo,
                Timestamp.from(enviadoEm));
    }

    private UUID criarAtendimento(String status, Instant iniciadoEm) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, atendente_id, status, iniciado_em) VALUES (?, ?, ?, ?::status_atendimento, ?)",
                id,
                leadId,
                anaId,
                status,
                Timestamp.from(iniciadoEm));
        return id;
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
