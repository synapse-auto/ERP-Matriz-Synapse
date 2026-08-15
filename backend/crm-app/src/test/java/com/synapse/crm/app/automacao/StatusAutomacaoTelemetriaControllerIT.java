package com.synapse.crm.app.automacao;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_SUBGESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_SUBGESTOR;
import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * {@code GET /api/v1/automacao/telemetria} (E17b §Bloco 6) — os quatro cards do topo da Automação.
 *
 * <p>Telemetria e singleton da operacao inteira, sem dono por lead: nao existe "atendente enxerga
 * menos linhas" aqui, porque nao ha linha por atendente para restringir — so uma. A defesa que
 * corresponde ao "teste negativo" do enunciado, portanto, e por papel (mesma exigida por
 * {@code GET /api/v1/automacao/config}), nao por contagem restrita. Registrado explicitamente para
 * nao parecer um achado forcado: a exigencia do prompt nao se aplica do mesmo jeito aqui.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class StatusAutomacaoTelemetriaControllerIT extends PostgresIT {

    @Autowired
    private TestRestTemplate http;

    @Test
    @DisplayName("sem token, devolve 401")
    void semToken_devolve401() {
        ResponseEntity<String> resposta = http.exchange(
                "/api/v1/automacao/telemetria", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("atendente nao pode ler — 403, o mesmo papel que ja nao le configuracao")
    void atendente_devolve403() {
        ResponseEntity<String> resposta = comoUsuario(EMAIL_ANA, SENHA_ATENDENTE);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("gestor le os quatro campos da telemetria")
    void gestor_leQuatroCampos() {
        ResponseEntity<String> resposta = comoUsuario(EMAIL_GESTOR, SENHA_GESTOR);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody())
                .contains("mensagensEnviadas")
                .contains("clientesTransferidos")
                .contains("conexaoAutomacaoAtiva")
                .contains("crmOnline");
    }

    @Test
    @DisplayName("subgestor tambem le — mesma autorizacao do gestor")
    void subgestor_tambemLe() {
        ResponseEntity<String> resposta = comoUsuario(EMAIL_SUBGESTOR, SENHA_SUBGESTOR);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("administrador le a telemetria global")
    void administrador_tambemLe() {
        ResponseEntity<String> resposta = comoUsuario(EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> comoUsuario(String email, String senha) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        return ApoioAutenticacao.comToken(
                http, token, HttpMethod.GET, "/api/v1/automacao/telemetria", String.class);
    }
}
