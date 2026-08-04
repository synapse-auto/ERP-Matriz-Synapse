package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class LembretesIT extends PostgresIT {
    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;

    private UUID ana;
    private UUID bruno;
    private UUID leadAna;
    private UUID leadBruno;
    private UUID lembreteBruno;

    @BeforeEach
    void preparar() {
        ana = usuario(EMAIL_ANA);
        bruno = usuario(EMAIL_BRUNO);
        leadAna = lead("E13 lembrete Ana " + UUID.randomUUID(), ana);
        leadBruno = lead("E13 lembrete Bruno " + UUID.randomUUID(), bruno);
        lembreteBruno = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO lembrete (id, lead_id, atendente_id, texto, data_hora)
                VALUES (?, ?, ?, 'Privado do Bruno', ?)
                """, lembreteBruno, leadBruno, bruno, java.sql.Timestamp.from(Instant.parse("2026-08-05T14:00:00Z")));
    }

    @Test
    @DisplayName("CRUD persiste, conclui e remove lembrete sem aceitar origem automatica")
    void crudCompleto() {
        ResponseEntity<String> criada = chamar(EMAIL_ANA, SENHA_ATENDENTE, HttpMethod.POST,
                "/api/v1/lembretes", Map.of("leadId", leadAna, "texto", "Retornar proposta",
                        "dataHora", "2026-08-05T13:00:00Z"));
        assertThat(criada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(criada.getBody()).contains("Retornar proposta").contains("\"origemAutomatica\":false");
        String id = criada.getBody().split("\"id\":\"")[1].split("\"")[0];

        ResponseEntity<String> atualizada = chamar(EMAIL_ANA, SENHA_ATENDENTE, HttpMethod.PUT,
                "/api/v1/lembretes/" + id, Map.of("texto", "Proposta retornada",
                        "dataHora", "2026-08-05T15:00:00Z", "status", "CONCLUIDO"));
        assertThat(atualizada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(atualizada.getBody()).contains("Proposta retornada").contains("CONCLUIDO");

        assertThat(chamar(EMAIL_ANA, SENHA_ATENDENTE, HttpMethod.DELETE,
                "/api/v1/lembretes/" + id, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lembrete WHERE id = ?", Integer.class,
                UUID.fromString(id))).isZero();
    }

    @Test
    @DisplayName("RN-CRM-04: atendente NAO lista, altera, remove nem cria no lead do colega")
    void negativoPrivacidadeDoAtendente() {
        String listaAna = chamar(EMAIL_ANA, SENHA_ATENDENTE, HttpMethod.GET,
                "/api/v1/lembretes?status=PENDENTE", null).getBody();
        assertThat(listaAna).doesNotContain(lembreteBruno.toString()).doesNotContain("Privado do Bruno");

        var corpo = Map.of("texto", "Tentativa", "dataHora", "2026-08-05T16:00:00Z",
                "status", "CONCLUIDO");
        assertThat(chamar(EMAIL_ANA, SENHA_ATENDENTE, HttpMethod.PUT,
                "/api/v1/lembretes/" + lembreteBruno, corpo).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(chamar(EMAIL_ANA, SENHA_ATENDENTE, HttpMethod.DELETE,
                "/api/v1/lembretes/" + lembreteBruno, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(chamar(EMAIL_ANA, SENHA_ATENDENTE, HttpMethod.POST, "/api/v1/lembretes",
                Map.of("leadId", leadBruno, "texto", "Invadir agenda",
                        "dataHora", "2026-08-05T17:00:00Z")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(jdbc.queryForObject("SELECT texto FROM lembrete WHERE id = ?", String.class,
                lembreteBruno)).isEqualTo("Privado do Bruno");
    }

    @Test
    @DisplayName("gestor ve todos e a coluna de origem identifica o atendente")
    void gestorVeTodosComOrigem() {
        String lista = chamar(EMAIL_GESTOR, SENHA_GESTOR, HttpMethod.GET,
                "/api/v1/lembretes", null).getBody();
        assertThat(lista).contains(lembreteBruno.toString()).contains("Bruno Atendente");
    }

    private ResponseEntity<String> chamar(String email, String senha, HttpMethod metodo, String url, Object corpo) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, headers), String.class);
    }

    private UUID usuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private UUID lead(String nome, UUID dono) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico) VALUES (?, ?, ?, 'EM_ATENDIMENTO')",
                id, nome, dono);
        return id;
    }
}
