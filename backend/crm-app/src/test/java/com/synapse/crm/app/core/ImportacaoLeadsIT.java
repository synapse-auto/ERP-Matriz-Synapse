package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.login;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;

import com.synapse.crm.app.PostgresIT;

/** Fluxo real da Agenda: preview/confirmacao e idempotencia por telefone. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ImportacaoLeadsIT extends PostgresIT {

    private static final String PREFIXO = "E158 importado";
    private static final String TELEFONE = "556199990001";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limparCenario() {
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    @Test
    void previewEConfirmacaoNaoCriamDuplicataParaTelefoneExistente() {
        String csv = "nome,empresa,telefone,cidade,tags\n"
                + PREFIXO + ",Acme," + TELEFONE + ",Brasilia,\"VIP\"\n";
        String token = login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();

        ResponseEntity<Map> previa = importar(token, "/preview", csv);
        assertThat(previa.getStatusCode().value()).isEqualTo(200);
        assertThat(previa.getBody()).containsEntry("validas", 1);

        ResponseEntity<Map> primeira = importar(token, "/confirmar", csv);
        assertThat(primeira.getStatusCode().value()).isEqualTo(200);
        assertThat(primeira.getBody()).containsEntry("validas", 1);

        ResponseEntity<Map> segunda = importar(token, "/confirmar", csv);
        assertThat(segunda.getStatusCode().value()).isEqualTo(200);
        assertThat(segunda.getBody()).containsEntry("validas", 0).containsEntry("jaExistiam", 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lead WHERE nome LIKE ?", Integer.class, PREFIXO + "%"))
                .isOne();

        ResponseEntity<String> exportado = exportar(token);
        assertThat(exportado.getStatusCode().value()).isEqualTo(200);
        assertThat(exportado.getBody())
                .startsWith("nome,empresa,telefone,cnpj/cpf,cidade,etapa,tags\n")
                .contains(PREFIXO);
    }

    @Test
    void atendenteNaoPodeImportarNemExportar() {
        String token = login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        assertThat(importar(token, "/preview", "nome,telefone\nBloqueado,61988888888\n")
                        .getStatusCode()
                        .value())
                .isEqualTo(403);
        assertThat(exportar(token).getStatusCode().value()).isEqualTo(403);
    }

    private ResponseEntity<Map> importar(String token, String operacao, String csv) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        var partes = new LinkedMultiValueMap<String, Object>();
        partes.add("arquivo", new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "leads.csv";
            }
        });
        return http.exchange(
                "/api/v1/leads/importacao" + operacao,
                HttpMethod.POST,
                new HttpEntity<>(partes, cabecalhos),
                Map.class);
    }

    private ResponseEntity<String> exportar(String token) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        return http.exchange(
                "/api/v1/leads/exportar", HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
    }
}
