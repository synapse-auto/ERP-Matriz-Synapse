package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ContratoFiltroDaAgendaIT extends PostgresIT {

    @Autowired
    private TestRestTemplate http;

    @Test
    @DisplayName("busca por texto da Agenda com OU devolve 200")
    void buscaPorTextoDaAgenda_comConectorOu_devolve200() {
        String corpoMontadoPeloFrontend = """
                {"criterio":{"tipo":"COMPOSTO","conector":"OU","criterios":[{"tipo":"SIMPLES","campo":"nome","operador":"CONTEM","valor":"busca-agenda-contrato"},{"tipo":"SIMPLES","campo":"telefone","operador":"CONTEM","valor":"busca-agenda-contrato"},{"tipo":"SIMPLES","campo":"cpf","operador":"CONTEM","valor":"busca-agenda-contrato"}]},"pagina":0,"tamanho":50}
                """;
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(
                ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken());
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resposta = http.postForEntity(
                "/api/v1/leads/filtrar",
                new HttpEntity<>(corpoMontadoPeloFrontend, cabecalhos),
                String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
