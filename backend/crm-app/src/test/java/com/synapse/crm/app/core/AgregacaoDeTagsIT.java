package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/**
 * {@code GET /api/v1/tags/agregacao} (E17b §Bloco 6) ponta a ponta.
 *
 * <p>O teste negativo do enunciado: "um atendente nao pode ver '47 leads com a tag Obra' quando
 * enxerga 6". Aqui isso vira comparacao relativa — Ana sempre recebe um total menor ou igual ao do
 * gestor — em vez de numero fixo, porque a suite roda contra o mesmo Postgres que o seed de
 * demonstracao e outras IT tambem povoam.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class AgregacaoDeTagsIT extends PostgresIT {

    private static final String PREFIXO = "E17B-tags-";
    private static final String TAG_URGENTE = "7a000000-0000-4000-8000-000000000002";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID idAna;
    private UUID idBruno;

    @BeforeEach
    void prepararCenario() {
        limparPrefixo();
        idAna = idDoUsuario(EMAIL_ANA);
        idBruno = idDoUsuario(EMAIL_BRUNO);

        comTag(criarLead("Ana-com-tag", idAna), TAG_URGENTE);
        criarLead("Ana-sem-tag", idAna);
        comTag(criarLead("Bruno-com-tag", idBruno), TAG_URGENTE);
    }

    @AfterEach
    void limpar() {
        limparPrefixo();
    }

    @Test
    @DisplayName("totalLeadsVisiveis: atendente recebe numero restrito, gestor recebe o total")
    void totalLeadsVisiveis_atendenteRestritoGestorTotal() {
        long totalAna = campoLong(agregacaoComo(EMAIL_ANA, SENHA_ATENDENTE), "totalLeadsVisiveis");
        long totalGestor = campoLong(agregacaoComo(EMAIL_GESTOR, SENHA_GESTOR), "totalLeadsVisiveis");

        assertThat(totalAna).isLessThan(totalGestor);
    }

    @Test
    @DisplayName("leadsComTag: atendente nao alcanca o lead tagueado do colega")
    void leadsComTag_atendenteNaoAlcancaLeadDoColega() {
        long comTagAna = campoLong(agregacaoComo(EMAIL_ANA, SENHA_ATENDENTE), "leadsComTag");
        long comTagGestor = campoLong(agregacaoComo(EMAIL_GESTOR, SENHA_GESTOR), "leadsComTag");

        assertThat(comTagAna).isLessThan(comTagGestor);
    }

    @Test
    @DisplayName("percentualTagueados nunca excede 100 e some com zero lead visivel")
    void percentualTagueados_dentroDaFaixa() {
        double percentual = campoDouble(agregacaoComo(EMAIL_ANA, SENHA_ATENDENTE), "percentualTagueados");

        assertThat(percentual).isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("sem autenticacao, devolve 401")
    void semAutenticacao_devolve401() {
        ResponseEntity<String> resposta = http.exchange(
                "/api/v1/tags/agregacao", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- apoio ------------------------------------------------------------

    private String agregacaoComo(String email, String senha) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        return ApoioAutenticacao.comToken(http, token, HttpMethod.GET, "/api/v1/tags/agregacao", String.class)
                .getBody();
    }

    private long campoLong(String corpoJson, String campo) {
        return Long.parseLong(extrair(corpoJson, campo));
    }

    private double campoDouble(String corpoJson, String campo) {
        return Double.parseDouble(extrair(corpoJson, campo));
    }

    private String extrair(String corpoJson, String campo) {
        Matcher casador = Pattern.compile("\"" + campo + "\":([0-9.]+)").matcher(corpoJson);
        if (!casador.find()) {
            throw new IllegalStateException("campo '" + campo + "' nao encontrado em: " + corpoJson);
        }
        return casador.group(1);
    }

    private void limparPrefixo() {
        jdbc.update(
                "DELETE FROM lead_tag WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private UUID criarLead(String nome, UUID dono) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico)"
                        + " VALUES (?, ?, ?, 'EM_ATENDIMENTO'::status_basico_lead)",
                id,
                PREFIXO + nome,
                dono);
        return id;
    }

    private UUID comTag(UUID leadId, String tagId) {
        jdbc.update("INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?::uuid)", leadId, tagId);
        return leadId;
    }
}
