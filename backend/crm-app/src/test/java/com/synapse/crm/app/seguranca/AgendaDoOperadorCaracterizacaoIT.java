package com.synapse.crm.app.seguranca;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;

/**
 * Caracteriza, sem alterar nada, o que a Agenda colaborativa entrega hoje (D5a da Disbrel, docs/71).
 *
 * <p>Estes testes existem para sustentar uma decisao, nao para travar um comportamento desejado:
 * <ul>
 *   <li>a ficha aberta pela Agenda ({@code GET /api/v1/leads/{id}/agenda}) devolve {@code notas} e
 *       {@code atendenteResponsavelId} do lead de OUTRO atendente, e {@code resumoIa} para quem tem
 *       {@code resumo_ia.ver} (padrao: todos os papeis);
 *   <li>{@code app_e_agenda()} (V60) nao inclui OPERADOR, entao hoje o OPERADOR recebe 404 na Agenda;
 *   <li>o contexto de Agenda nao concede nada ao OPERADOR fora dela.
 * </ul>
 * Se alguem estender {@code app_e_agenda()} ao OPERADOR, o teste do OPERADOR vai falhar de proposito: a
 * mudanca precisa vir junto de uma decisao sobre o que a ficha da Agenda pode mostrar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class AgendaDoOperadorCaracterizacaoIT extends PostgresIT {

    private static final String SENHA_OPERADOR = "senha-operador-it-1";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private String marcador;
    private UUID leadDoBruno;
    private UUID operador;
    private String tokenOperador;

    @BeforeEach
    void preparar() {
        marcador = "agop" + UUID.randomUUID().toString().substring(0, 8);
        leadDoBruno = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico, notas, resumo_ia)"
                        + " VALUES (?, ?, ?, 'EM_ATENDIMENTO'::status_basico_lead, ?, ?)",
                leadDoBruno,
                "Cliente do Bruno " + marcador,
                idDe(EMAIL_BRUNO),
                "NOTA-DO-BRUNO-" + marcador,
                "RESUMO-DO-BRUNO-" + marcador);

        String tokenGestor = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
        String email = "operador-agenda-" + marcador + "@teste.local";
        ResponseEntity<Map> criada = chamar(
                tokenGestor, HttpMethod.POST, "/api/v1/usuarios",
                Map.of("nome", "Operador agenda IT", "email", email, "senha", SENHA_OPERADOR, "papel", "OPERADOR"));
        assertThat(criada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        operador = UUID.fromString((String) criada.getBody().get("id"));
        jdbc.update("UPDATE usuario SET senha_alterada_em = now() WHERE id = ?", operador);
        tokenOperador = ApoioAutenticacao.login(http, email, SENHA_OPERADOR).accessToken();
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM atendimento WHERE lead_id = ?", leadDoBruno);
        jdbc.update("DELETE FROM lead WHERE id = ?", leadDoBruno);
        if (operador != null) {
            jdbc.update("DELETE FROM usuario WHERE id = ?", operador);
        }
    }

    @Test
    @DisplayName("hoje: a ficha da Agenda entrega a um ATENDENTE as notas, o responsavel e o resumo do lead do colega")
    void fichaDaAgendaExpoeNotasEResumoDoLeadDoColega() {
        String tokenAna = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();

        ResponseEntity<String> ficha = comToken(tokenAna, HttpMethod.GET, "/api/v1/leads/" + leadDoBruno + "/agenda");

        assertThat(ficha.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ficha.getBody())
                .contains("NOTA-DO-BRUNO-" + marcador)
                .contains(idDe(EMAIL_BRUNO).toString())
                .contains("RESUMO-DO-BRUNO-" + marcador);
    }

    @Test
    @DisplayName("hoje: o OPERADOR nao entra na Agenda (app_e_agenda exclui OPERADOR): ficha e abertura de conversa dao 404")
    void operadorNaoAlcancaOLeadDoColegaPelaAgenda() {
        ResponseEntity<String> ficha = comToken(tokenOperador, HttpMethod.GET, "/api/v1/leads/" + leadDoBruno + "/agenda");
        ResponseEntity<String> abertura =
                comToken(tokenOperador, HttpMethod.POST, "/api/v1/atendimentos/leads/" + leadDoBruno + "/novo");

        assertThat(ficha.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ficha.getBody()).doesNotContain("Cliente do Bruno").doesNotContain("NOTA-DO-BRUNO");
        assertThat(abertura.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM atendimento WHERE lead_id = ?", Integer.class, leadDoBruno))
                .as("nenhum atendimento foi criado para o OPERADOR")
                .isZero();
    }

    @Test
    @DisplayName("hoje: a lista de leads do OPERADOR tambem nao revela o lead do colega")
    void listaDoOperadorNaoRevelaOLeadDoColega() {
        ResponseEntity<String> lista = comToken(tokenOperador, HttpMethod.GET, "/api/v1/leads");

        assertThat(lista.getBody() == null ? "" : lista.getBody()).doesNotContain(leadDoBruno.toString());
    }

    private UUID idDe(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private ResponseEntity<String> comToken(String token, HttpMethod metodo, String url) {
        return ApoioAutenticacao.comToken(http, token, metodo, url, String.class);
    }

    private ResponseEntity<Map> chamar(String token, HttpMethod metodo, String url, Object corpo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), Map.class);
    }
}
