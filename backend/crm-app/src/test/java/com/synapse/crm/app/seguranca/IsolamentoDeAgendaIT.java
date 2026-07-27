package com.synapse.crm.app.seguranca;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;

/**
 * RN-CRM-01 ponta a ponta, pelo HTTP.
 *
 * <p>Os atendentes trabalham por comissao e disputam leads. Cada teste aqui representa um caminho
 * pelo qual um atendente poderia alcancar o lead de um colega: listagem, busca por nome, filtro por
 * status e — o que mais escapa em revisao — acesso direto por id.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class IsolamentoDeAgendaIT extends PostgresIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID idAna;
    private UUID idBruno;
    private UUID leadDaAna;
    private UUID leadDoBruno;
    private UUID leadEmIa;
    private String marcador;

    @BeforeEach
    void prepararAgendas() {
        idAna = idDoUsuario(EMAIL_ANA);
        idBruno = idDoUsuario(EMAIL_BRUNO);
        marcador = "iso" + UUID.randomUUID().toString().substring(0, 8);

        leadDaAna = criarLead("Cliente da Ana " + marcador, idAna, "EM_ATENDIMENTO");
        leadDoBruno = criarLead("Cliente do Bruno " + marcador, idBruno, "EM_ATENDIMENTO");
        leadEmIa = criarLead("Potencial " + marcador, null, "IA");
    }

    @Test
    @DisplayName("por id direto: atendente NAO alcanca o lead do colega")
    void porId_leadDeOutroAtendente_devolve404() {
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();

        var resposta = ApoioAutenticacao.comToken(
                http, token, HttpMethod.GET, "/api/v1/leads/" + leadDoBruno, String.class);

        // 404 e nao 403: dizer "existe, mas nao e seu" ja entrega informacao
        // sobre a carteira do colega.
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody()).doesNotContain("Cliente do Bruno");
    }

    @Test
    @DisplayName("por id direto: atendente alcanca o proprio lead")
    void porId_leadProprio_devolve200() {
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();

        var resposta = ApoioAutenticacao.comToken(
                http, token, HttpMethod.GET, "/api/v1/leads/" + leadDaAna, String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("Cliente da Ana");
    }

    @Test
    @DisplayName("por listagem: a agenda do colega nao aparece")
    void listagem_atendente_naoTrazLeadDeColega() {
        String corpo = listarComo(EMAIL_ANA, SENHA_ATENDENTE, "");

        assertThat(corpo).contains(leadDaAna.toString());
        assertThat(corpo).doesNotContain(leadDoBruno.toString());
    }

    @Test
    @DisplayName("por busca: procurar pelo nome do lead do colega nao o revela")
    void busca_porNomeDoLeadDeColega_naoRevela() {
        String corpo = listarComo(EMAIL_ANA, SENHA_ATENDENTE, "?busca=Cliente%20do%20Bruno");

        assertThat(corpo).doesNotContain(leadDoBruno.toString());
        assertThat(corpo).doesNotContain("Cliente do Bruno");
    }

    @Test
    @DisplayName("por filtro: filtrar por status nao contorna o isolamento")
    void filtro_porStatus_naoContornaIsolamento() {
        String corpo = listarComo(EMAIL_ANA, SENHA_ATENDENTE, "?status=EM_ATENDIMENTO");

        assertThat(corpo).contains(leadDaAna.toString());
        assertThat(corpo).doesNotContain(leadDoBruno.toString());
    }

    @Test
    @DisplayName("atendente ve os leads em IA, que sao de todos")
    void listagem_leadEmIa_apareceParaOAtendente() {
        String corpo = listarComo(EMAIL_ANA, SENHA_ATENDENTE, "");

        assertThat(corpo).contains(leadEmIa.toString());
    }

    @Test
    @DisplayName("gestor ve a base inteira, inclusive por id")
    void gestor_qualquerLead_enxerga() {
        String token = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();

        String listagem = ApoioAutenticacao.comToken(
                        http, token, HttpMethod.GET, "/api/v1/leads", String.class)
                .getBody();
        assertThat(listagem).contains(leadDaAna.toString()).contains(leadDoBruno.toString());

        var porId = ApoioAutenticacao.comToken(
                http, token, HttpMethod.GET, "/api/v1/leads/" + leadDoBruno, String.class);
        assertThat(porId.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("o outro atendente enxerga a propria agenda, e so ela")
    void listagem_bruno_veApenasOProprio() {
        String corpo = listarComo(EMAIL_BRUNO, SENHA_ATENDENTE, "");

        assertThat(corpo).contains(leadDoBruno.toString());
        assertThat(corpo).doesNotContain(leadDaAna.toString());
    }

    private String listarComo(String email, String senha, String querystring) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        return ApoioAutenticacao.comToken(
                        http, token, HttpMethod.GET, "/api/v1/leads" + querystring, String.class)
                .getBody();
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private UUID criarLead(String nome, UUID dono, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico)"
                        + " VALUES (?, ?, ?, CAST(? AS status_basico_lead))",
                id,
                nome,
                dono,
                status);
        return id;
    }
}
