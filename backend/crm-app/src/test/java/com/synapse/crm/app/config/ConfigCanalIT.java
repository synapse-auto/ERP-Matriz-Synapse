package com.synapse.crm.app.config;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/** Contrato da capacidade do canal consumida pelo diálogo de novo contato. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.canal.whatsapp.provedor=meta-cloud")
class ConfigCanalIT extends PostgresIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Test
    @DisplayName("capacidade do canal exige autenticacao e devolve somente o booleano")
    void capacidadeDoCanal_exigeAutenticacaoEDevolveApenasCapacidade() throws Exception {
        assertThat(http.getForEntity("/api/v1/config/canal", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        var resposta = ApoioAutenticacao.comToken(
                http, token, org.springframework.http.HttpMethod.GET, "/api/v1/config/canal", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode corpo = json.readTree(resposta.getBody());
        assertThat(corpo.fieldNames()).toIterable().containsExactly("exigeTemplateForaDaJanela");
        assertThat(corpo.path("exigeTemplateForaDaJanela").asBoolean()).isTrue();
        assertThat(corpo.size()).isEqualTo(1);
        assertThat(resposta.getBody()).doesNotContain("token", "telefone", "url");
    }
}
