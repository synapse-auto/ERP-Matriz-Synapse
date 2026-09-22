package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class DestinosDeTransferenciaIT extends PostgresIT {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void normalizarElegibilidade() {
        jdbc.update(
                "UPDATE usuario SET ativo=TRUE, status_presenca='OFFLINE' WHERE email IN (?, ?, ?, ?, ?)",
                EMAIL_ANA,
                ApoioAutenticacao.EMAIL_BRUNO,
                ApoioAutenticacao.EMAIL_SUBGESTOR,
                EMAIL_GESTOR,
                ApoioAutenticacao.EMAIL_ADMINISTRADOR);
        jdbc.update(
                "INSERT INTO disponibilidade_atendente_ia (atendente_id, disponivel_para_ia) "
                        + "SELECT id, FALSE FROM usuario WHERE email IN (?, ?) "
                        + "ON CONFLICT (atendente_id) DO UPDATE SET disponivel_para_ia=EXCLUDED.disponivel_para_ia",
                EMAIL_ANA,
                ApoioAutenticacao.EMAIL_SUBGESTOR);
    }

    @Test
    @DisplayName("atendente recebe id, nome e papel, sem e-mail")
    void atendente_listaIncluiPapelSemEmail() throws Exception {
        ResponseEntity<String> resposta = chamar(EMAIL_ANA, SENHA_ATENDENTE, "/api/v1/atendimentos/destinos-de-transferencia");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode itens = json.readTree(resposta.getBody());
        assertThat(itens.isArray()).isTrue();
        assertThat(itens).isNotEmpty();

        List<String> ids = new ArrayList<>();
        List<String> nomes = new ArrayList<>();
        for (JsonNode item : itens) {
            List<String> campos = new ArrayList<>();
            item.fieldNames().forEachRemaining(campos::add);
            assertThat(campos).containsExactlyInAnyOrder("id", "nome", "papel");
            ids.add(item.get("id").asText());
            nomes.add(item.get("nome").asText());
        }
        assertThat(nomes).contains("Ana Atendente", "Bruno Atendente");
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(nomes).doesNotContain("Gestora", "Administrador");
        assertThat(resposta.getBody()).doesNotContain("\"email\"");
        assertThat(itens.findValuesAsText("papel")).contains("ATENDENTE");
    }

    @Test
    @DisplayName("atendente ativo continua aparecendo fora da disponibilidade da IA")
    void atendenteAtivoForaDoRodizioContinuaNaLista() throws Exception {
        String corpo = respostaPara(EMAIL_ANA, SENHA_ATENDENTE);

        assertThat(corpo).contains("Ana Atendente");
    }

    @Test
    @DisplayName("subgestor online e disponivel para IA aparece na lista")
    void subgestorOnlineDisponivelAparece() {
        prepararSubgestor(true, true);

        assertThat(respostaPara(EMAIL_ANA, SENHA_ATENDENTE)).contains("Subgestora");
    }

    @Test
    @DisplayName("subgestor online mas fora da IA nao aparece")
    void subgestorIndisponivelNaoAparece() {
        prepararSubgestor(true, false);

        assertThat(respostaPara(EMAIL_ANA, SENHA_ATENDENTE)).doesNotContain("Subgestora");
    }

    @Test
    @DisplayName("subgestor inativo nao aparece mesmo com disponibilidade")
    void subgestorInativoNaoAparece() {
        prepararSubgestor(false, true);

        assertThat(respostaPara(EMAIL_ANA, SENHA_ATENDENTE)).doesNotContain("Subgestora");
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
        assertThat(resposta.getBody())
                .contains("Ana Atendente")
                .contains("Bruno Atendente")
                .doesNotContain("Subgestora")
                .doesNotContain("Gestora")
                .doesNotContain("Administrador");
        assertThat(resposta.getBody()).doesNotContain("\"email\"");
    }

    @Test
    @DisplayName("gestor tambem ve subgestor quando ele esta elegivel")
    void gestorVeSubgestorElegivel() {
        prepararSubgestor(true, true);

        assertThat(respostaPara(EMAIL_GESTOR, SENHA_GESTOR)).contains("Subgestora");
    }

    private void prepararSubgestor(boolean ativo, boolean disponivel) {
        UUID id = jdbc.queryForObject(
                "SELECT id FROM usuario WHERE email=?", UUID.class, ApoioAutenticacao.EMAIL_SUBGESTOR);
        jdbc.update(
                "UPDATE usuario SET ativo=?, status_presenca=CAST(? AS status_presenca) WHERE id=?",
                ativo,
                ativo ? "ONLINE" : "OFFLINE",
                id);
        jdbc.update(
                "INSERT INTO disponibilidade_atendente_ia (atendente_id, disponivel_para_ia) VALUES (?, ?) "
                        + "ON CONFLICT (atendente_id) DO UPDATE SET disponivel_para_ia=EXCLUDED.disponivel_para_ia",
                id,
                disponivel);
    }

    private String respostaPara(String email, String senha) {
        return chamar(email, senha, "/api/v1/atendimentos/destinos-de-transferencia").getBody();
    }

    private ResponseEntity<String> chamar(String email, String senha, String url) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
    }
}
