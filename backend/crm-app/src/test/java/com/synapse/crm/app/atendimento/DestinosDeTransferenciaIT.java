package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class DestinosDeTransferenciaIT extends PostgresIT {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private TestRestTemplate http;

    @Test
    @DisplayName("atendente recebe so id e nome, sem e-mail nem papel")
    void atendente_listaSoIdENome() throws Exception {
        ResponseEntity<String> resposta = chamar(EMAIL_ANA, SENHA_ATENDENTE, "/api/v1/atendimentos/destinos-de-transferencia");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode itens = json.readTree(resposta.getBody());
        assertThat(itens.isArray()).isTrue();
        assertThat(itens).isNotEmpty();

        List<String> nomes = new ArrayList<>();
        for (JsonNode item : itens) {
            List<String> campos = new ArrayList<>();
            item.fieldNames().forEachRemaining(campos::add);
            assertThat(campos).containsExactlyInAnyOrder("id", "nome");
            nomes.add(item.get("nome").asText());
        }
        assertThat(nomes).contains("Ana Atendente", "Bruno Atendente");
        assertThat(nomes).doesNotContain("Gestora", "Subgestora", "Administrador");
        assertThat(resposta.getBody()).doesNotContain("\"email\"").doesNotContain("\"papel\"");
    }

    @Test
    @DisplayName("GET /api/v1/usuarios continua recusado ao atendente")
    void atendente_naoListaUsuariosCompletos() {
        ResponseEntity<String> resposta = chamar(EMAIL_ANA, SENHA_ATENDENTE, "/api/v1/usuarios");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("gestor tambem usa a lista estreita")
    void gestor_listaDestinos() {
        ResponseEntity<String> resposta = chamar(EMAIL_GESTOR, SENHA_GESTOR, "/api/v1/atendimentos/destinos-de-transferencia");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("Ana Atendente").contains("Bruno Atendente");
        assertThat(resposta.getBody()).doesNotContain("\"email\"").doesNotContain("\"papel\"");
    }

    private ResponseEntity<String> chamar(String email, String senha, String url) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
    }
}
