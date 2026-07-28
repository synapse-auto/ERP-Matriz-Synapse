package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/** Ficha do lead: projecao de lista, edicao e isolamento na escrita. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class LeadFichaIT extends PostgresIT {

    private static final String NOTA_SECRETA = "NOTA-CONFIDENCIAL-DA-ANA";
    private static final String RESUMO_SECRETO = "RESUMO-IA-CONFIDENCIAL";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID leadDaAna;
    private UUID leadDoBruno;

    @BeforeEach
    void prepararLeads() {
        UUID idAna = idDoUsuario(EMAIL_ANA);
        UUID idBruno = idDoUsuario(EMAIL_BRUNO);
        leadDaAna = criarLead("Cliente da Ana", idAna);
        leadDoBruno = criarLead("Cliente do Bruno", idBruno);
    }

    /**
     * Campos longos nao entram em tela de lista. A garantia e de tipo — o repositorio devolve
     * LeadResumo, que nao tem onde guardar notas nem resumo de IA —, mas o teste confere pelo HTTP
     * porque e ali que o vazamento apareceria.
     */
    @Test
    @DisplayName("listagem nao traz notas nem resumo_ia")
    void listagem_qualquerAtendente_naoTrazCamposLongos() {
        String corpo = comoAna(HttpMethod.GET, "/api/v1/leads", null).getBody();

        assertThat(corpo).contains("Cliente da Ana");
        assertThat(corpo).doesNotContain(NOTA_SECRETA).doesNotContain(RESUMO_SECRETO);
        assertThat(corpo).doesNotContain("notas").doesNotContain("resumoIa");
    }

    @Test
    @DisplayName("a ficha por id traz os campos longos")
    void ficha_porId_trazCamposLongos() {
        String corpo = comoAna(HttpMethod.GET, "/api/v1/leads/" + leadDaAna, null).getBody();

        assertThat(corpo).contains(NOTA_SECRETA).contains(RESUMO_SECRETO);
    }

    @Test
    @DisplayName("atendente edita o proprio lead, preservando o que nao mandou")
    void editar_leadProprio_funciona() {
        var resposta = comoAna(
                HttpMethod.PUT,
                "/api/v1/leads/" + leadDaAna,
                Map.of("nome", "Cliente da Ana editado", "empresa", "Vidros ABC"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("Cliente da Ana editado").contains("Vidros ABC");
    }

    /** 404 e nao 403: 403 confirmaria que o lead existe e esta com um colega. */
    @Test
    @DisplayName("atendente NAO edita lead de colega — 404, nao 403")
    void editar_leadDeColega_devolve404() {
        var resposta = comoAna(
                HttpMethod.PUT, "/api/v1/leads/" + leadDoBruno, Map.of("nome", "Invadido"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        String nomeNoBanco =
                jdbc.queryForObject("SELECT nome FROM lead WHERE id = ?", String.class, leadDoBruno);
        assertThat(nomeNoBanco).isEqualTo("Cliente do Bruno");
    }

    @Test
    @DisplayName("a edicao nao mexe nos contadores")
    void editar_ficha_naoAlteraContadores() {
        jdbc.update("UPDATE lead SET num_atendimentos = 7, num_mensagens = 42 WHERE id = ?", leadDaAna);

        comoAna(HttpMethod.PUT, "/api/v1/leads/" + leadDaAna, Map.of("nome", "Outro nome"));

        assertThat(contador("num_atendimentos")).isEqualTo(7);
        assertThat(contador("num_mensagens")).isEqualTo(42);
    }

    // --- apoio ---------------------------------------------------------------

    private int contador(String coluna) {
        return jdbc.queryForObject(
                "SELECT " + coluna + " FROM lead WHERE id = ?", Integer.class, leadDaAna);
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private UUID criarLead(String nome, UUID dono) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico, notas, resumo_ia)
                VALUES (?, ?, ?, 'EM_ATENDIMENTO', ?, ?)
                """,
                id,
                nome,
                dono,
                NOTA_SECRETA,
                RESUMO_SECRETO);
        return id;
    }

    private ResponseEntity<String> comoAna(HttpMethod metodo, String url, Object corpo) {
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }
}
