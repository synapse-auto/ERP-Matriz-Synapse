package com.synapse.crm.app.automacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;

/**
 * Resolucao do atendimento para a Automacao pelos pontos de entrada HTTP (docs/50): estados do
 * atendimento, lead inexistente, mais atendimentos ativos que uma pagina de /em-andamento, e a
 * ancora pela mensagem recebida. A corrida com o processamento da entrada esta em
 * RepasseWebhookAutomacaoIT, com o webhook real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = {"synapse.seguranca.token-interno=e50-token", "synapse.suporte.tamanho-pagina=20"})
class AtendimentoDaAutomacaoIT extends PostgresIT {

    private static final String TOKEN = "e50-token";
    private static final String PREFIXO = "E50-ATIVO-";
    private static final Instant INICIO = Instant.parse("2036-02-01T10:00:00Z");

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    @AfterEach
    void limpar() {
        String leads = "SELECT id FROM lead WHERE nome LIKE '" + PREFIXO + "%'";
        String atendimentos = "SELECT id FROM atendimento WHERE lead_id IN (" + leads + ")";
        jdbc.update("DELETE FROM mensagem_id_externo WHERE atendimento_id IN (" + atendimentos + ")");
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (" + atendimentos + ")");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (" + leads + ")");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    @Test
    @DisplayName("lead EM_IA e lead EM_ATENDIMENTO: devolve o atendimento aberto de cada um")
    void leadEmIaEEmAtendimento_devolvemOAberto() throws Exception {
        UUID leadIa = lead("IA");
        UUID atendimentoIa = atendimento(leadIa, "EM_IA", INICIO);
        UUID leadHumano = lead("HUMANO");
        UUID atendimentoHumano = atendimento(leadHumano, "EM_ATENDIMENTO", INICIO);

        JsonNode ia = corpo(get("/internal/v1/leads/" + leadIa + "/atendimento-ativo"), HttpStatus.OK);
        JsonNode humano = corpo(get("/internal/v1/leads/" + leadHumano + "/atendimento-ativo"), HttpStatus.OK);

        assertThat(ia.path("atendimentoId").asText()).isEqualTo(atendimentoIa.toString());
        assertThat(ia.path("status").asText()).isEqualTo("EM_IA");
        assertThat(ia.path("ativo").asBoolean()).isTrue();
        assertThat(humano.path("atendimentoId").asText()).isEqualTo(atendimentoHumano.toString());
        assertThat(humano.path("status").asText()).isEqualTo("EM_ATENDIMENTO");
    }

    @Test
    @DisplayName("atendimento finalizado nunca e devolvido: sem aberto = 404; com novo aberto = o novo, nao o antigo")
    void finalizado_naoDevolveIdAntigo() throws Exception {
        UUID lead = lead("FINAL");
        UUID antigo = atendimento(lead, "FINALIZADO", INICIO.minusSeconds(7_200));

        ResponseEntity<String> semAberto = get("/internal/v1/leads/" + lead + "/atendimento-ativo");
        assertThat(semAberto.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(semAberto.getBody()).contains("Lead sem atendimento aberto").doesNotContain(antigo.toString());

        UUID novo = atendimento(lead, "EM_IA", INICIO);
        JsonNode resolvido = corpo(get("/internal/v1/leads/" + lead + "/atendimento-ativo"), HttpStatus.OK);
        assertThat(resolvido.path("atendimentoId").asText()).isEqualTo(novo.toString());
    }

    @Test
    @DisplayName("lead inexistente: 404 com titulo proprio, distinto de 'sem atendimento aberto'")
    void leadInexistente_404() {
        ResponseEntity<String> resposta = get("/internal/v1/leads/" + UUID.randomUUID() + "/atendimento-ativo");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody()).contains("Lead inexistente").doesNotContain("sem atendimento aberto");
    }

    @Test
    @DisplayName("mais de 20 atendimentos ativos: o lead fora da primeira pagina de /em-andamento e resolvido")
    void maisDeVinteAtivos_resolveForaDaPrimeiraPagina() throws Exception {
        UUID alvo = lead("ALVO");
        UUID atendimentoAlvo = atendimento(alvo, "EM_IA", INICIO.minusSeconds(86_400));
        for (int i = 0; i < 25; i++) {
            atendimento(lead("OUTRO-" + i), "EM_IA", INICIO.plusSeconds(i));
        }

        ResponseEntity<String> primeiraPagina = get("/internal/v1/atendimentos/em-andamento?pagina=0&tamanho=20");
        assertThat(primeiraPagina.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Premissa do defeito: quem so le a primeira pagina nao encontra o lead.
        assertThat(primeiraPagina.getBody()).doesNotContain(atendimentoAlvo.toString());

        JsonNode resolvido = corpo(get("/internal/v1/leads/" + alvo + "/atendimento-ativo"), HttpStatus.OK);
        assertThat(resolvido.path("atendimentoId").asText()).isEqualTo(atendimentoAlvo.toString());
    }

    @Test
    @DisplayName("mensagem recebida: ancora no atendimento dela; finalizado responde ativo=false; id de saida nao ancora")
    void mensagemRecebida_ancoraNoAtendimentoDaEntrada() throws Exception {
        UUID lead = lead("ENTRADA");
        UUID atendimento = atendimento(lead, "EM_IA", INICIO);
        mensagemComIdExterno(atendimento, "LEAD", "wamid.E50-entrada=", INICIO.plusSeconds(1));
        mensagemComIdExterno(atendimento, "IA", "wamid.E50-saida", INICIO.plusSeconds(2));

        JsonNode resolvido = corpo(get(entrada("wamid.E50-entrada=")), HttpStatus.OK);
        assertThat(resolvido.path("atendimentoId").asText()).isEqualTo(atendimento.toString());
        assertThat(resolvido.path("leadId").asText()).isEqualTo(lead.toString());
        assertThat(resolvido.path("ativo").asBoolean()).isTrue();

        assertThat(get(entrada("wamid.E50-saida")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        jdbc.update("UPDATE atendimento SET status = 'FINALIZADO', finalizado_em = now() WHERE id = ?", atendimento);
        JsonNode finalizado = corpo(get(entrada("wamid.E50-entrada=")), HttpStatus.OK);
        assertThat(finalizado.path("status").asText()).isEqualTo("FINALIZADO");
        assertThat(finalizado.path("ativo").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("mensagem ainda nao registrada: 404 com Retry-After; id vazio: 400; sem token: 401")
    void negativos() {
        ResponseEntity<String> desconhecida = get(entrada("wamid.E50-nunca-chegou"));
        assertThat(desconhecida.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(desconhecida.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("2");
        assertThat(desconhecida.getBody()).contains("ainda nao registrada");

        assertThat(get("/internal/v1/mensagens-recebidas/atendimento?idExterno=").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> semToken = http.exchange(
                entrada("wamid.E50-nunca-chegou"), HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertThat(semToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static String entrada(String idExterno) {
        return "/internal/v1/mensagens-recebidas/atendimento?idExterno=" + idExterno;
    }

    private ResponseEntity<String> get(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Synapse-Token", TOKEN);
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private JsonNode corpo(ResponseEntity<String> resposta, HttpStatus esperado) throws Exception {
        assertThat(resposta.getStatusCode()).as(resposta.getBody()).isEqualTo(esperado);
        return json.readTree(resposta.getBody());
    }

    private UUID lead(String marcador) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO lead (id, nome, status_basico) VALUES (?, ?, 'IA')", id, PREFIXO + marcador);
        return id;
    }

    private UUID atendimento(UUID lead, String status, Instant inicio) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, status, iniciado_em, finalizado_em) VALUES (?, ?, ?::status_atendimento, ?, ?)",
                id,
                lead,
                status,
                Timestamp.from(inicio),
                "FINALIZADO".equals(status) ? Timestamp.from(inicio.plusSeconds(60)) : null);
        return id;
    }

    private void mensagemComIdExterno(UUID atendimento, String remetente, String wamid, Instant enviadaEm) {
        UUID mensagem = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, conteudo, status_entrega, enviado_em)"
                        + " VALUES (?, ?, ?::remetente_tipo, 'TEXTO', 'ok', 'ENTREGUE', ?)",
                mensagem,
                atendimento,
                remetente,
                Timestamp.from(enviadaEm));
        jdbc.update(
                "INSERT INTO mensagem_id_externo (wamid, mensagem_id, mensagem_enviada_em, atendimento_id) VALUES (?, ?, ?, ?)",
                wamid,
                mensagem,
                Timestamp.from(enviadaEm),
                atendimento);
    }
}
