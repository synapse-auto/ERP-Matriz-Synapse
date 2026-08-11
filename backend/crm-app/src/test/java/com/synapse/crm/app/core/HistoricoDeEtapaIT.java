package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class HistoricoDeEtapaIT extends PostgresIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID leadId;
    private UUID gestorId;
    private UUID atendenteId;
    private UUID etapaInicialId;
    private UUID etapaGanhaId;
    private String tokenGestor;

    @BeforeEach
    void preparar() {
        leadId = UUID.randomUUID();
        gestorId = usuario(EMAIL_GESTOR);
        atendenteId = usuario("ana@dev.local");
        etapaInicialId = etapa("EM_ANDAMENTO");
        etapaGanhaId = etapa("GANHO");
        tokenGestor = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
        jdbc.update(
                """
                INSERT INTO lead
                    (id, nome, status_basico, etapa_atendimento_id, atendente_responsavel_id)
                VALUES (?, ?, 'EM_ATENDIMENTO', ?, ?)
                """,
                leadId,
                "Lead historico " + leadId,
                etapaInicialId,
                atendenteId);
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM lead WHERE id = ?", leadId);
    }

    @Test
    @DisplayName("gestor fecha lead alheio e a venda fica com o atendente responsavel")
    void gestorMoveLeadAlheioParaGanho_creditaAtendente() {
        var resposta = moverPara(etapaGanhaId);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Map<String, Object> evento = eventoMaisRecente();
            assertThat(evento.get("ator_id")).isEqualTo(gestorId);
            assertThat(evento.get("responsavel_id")).isEqualTo(atendenteId.toString());
            assertThat(evento.get("resultado_novo")).isEqualTo("GANHO");
        });
    }

    @Test
    @DisplayName("reabrir e fechar no mesmo periodo conta o lead uma unica vez")
    void reabrirEFecharNoMesmoPeriodo_contaUmaVenda() {
        Instant inicio = Instant.now().minusSeconds(1);
        assertThat(moverPara(etapaGanhaId).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(moverPara(etapaInicialId).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(moverPara(etapaGanhaId).getStatusCode()).isEqualTo(HttpStatus.OK);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(contarEventos()).isEqualTo(3));
        Instant fim = Instant.now().plusSeconds(1);

        Long vendas = jdbc.queryForObject(
                """
                SELECT count(DISTINCT lead_id)
                  FROM evento_timeline
                 WHERE tipo = 'ETAPA_ALTERADA'
                   AND dados->>'resultado_novo' = 'GANHO'
                   AND criado_em >= ? AND criado_em < ?
                   AND lead_id = ?
                """,
                Long.class,
                Timestamp.from(inicio),
                Timestamp.from(fim),
                leadId);

        assertThat(vendas).isEqualTo(1L);
    }

    private org.springframework.http.ResponseEntity<String> moverPara(UUID etapaId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenGestor);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "/api/v1/leads/" + leadId,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("etapaAtendimentoId", etapaId), headers),
                String.class);
    }

    private int contarEventos() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM evento_timeline WHERE lead_id = ? AND tipo = 'ETAPA_ALTERADA'",
                Integer.class,
                leadId);
    }

    private Map<String, Object> eventoMaisRecente() {
        return jdbc.queryForMap(
                """
                SELECT ator_id, dados->>'responsavel_id' AS responsavel_id,
                       dados->>'resultado_novo' AS resultado_novo
                  FROM evento_timeline
                 WHERE lead_id = ? AND tipo = 'ETAPA_ALTERADA'
                 ORDER BY criado_em DESC, id DESC
                 LIMIT 1
                """,
                leadId);
    }

    private UUID usuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private UUID etapa(String resultado) {
        return jdbc.queryForObject(
                "SELECT id FROM etapa_atendimento WHERE resultado = ?::resultado_etapa ORDER BY ordem LIMIT 1",
                UUID.class,
                resultado);
    }
}
