package com.synapse.crm.app.automacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;

/**
 * Envio unico da Automacao pelos pontos de entrada HTTP (docs/50). O "consumidor" aqui segue o
 * protocolo documentado para o n8n — reservar; enviar so se novaReserva=true; registrar com
 * chaveDeEnvio — e cada "envio ao provedor" e contado, sem provedor real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.seguranca.token-interno=e50-envio-token")
class EnvioUnicoDaAutomacaoIT extends PostgresIT {

    private static final String TOKEN = "e50-envio-token";
    private static final String PREFIXO = "E50-ENVIO-";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    @AfterEach
    void limpar() {
        String atendimentos = "SELECT a.id FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE l.nome LIKE '" + PREFIXO + "%'";
        jdbc.update("DELETE FROM envio_automacao_reserva WHERE atendimento_id IN (" + atendimentos + ")");
        jdbc.update("DELETE FROM mensagem_automacao_idempotencia WHERE atendimento_id IN (" + atendimentos + ")");
        jdbc.update("DELETE FROM mensagem_id_externo WHERE atendimento_id IN (" + atendimentos + ")");
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (" + atendimentos + ")");
        jdbc.update("DELETE FROM atendimento WHERE id IN (" + atendimentos + ")");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    @Test
    @DisplayName("mesmo evento entregue duas vezes: so a primeira reserva autoriza envio")
    void mesmoEventoDuasVezes_umSoEnvio() throws Exception {
        UUID atendimento = atendimento("DUAS-VEZES", "EM_IA");
        String chave = "evento-" + UUID.randomUUID() + ":resposta";

        int envios = consumirComoON8n(atendimento, chave, "wamid.E50-duas-1")
                + consumirComoON8n(atendimento, chave, "wamid.E50-duas-2");

        assertThat(envios).isOne();
        assertThat(mensagensDe(atendimento)).isOne();
        assertThat(estadoDa(chave)).isEqualTo("ENVIADO");
    }

    @Test
    @DisplayName("entregas SIMULTANEAS do mesmo evento: exatamente uma reserva nova")
    void entregasSimultaneas_exatamenteUmaReservaNova() throws Exception {
        UUID atendimento = atendimento("SIMULTANEAS", "EM_IA");
        String chave = "evento-" + UUID.randomUUID() + ":resposta";
        int concorrentes = 12;
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(concorrentes);
        try {
            List<Future<HttpStatus>> resultados = new ArrayList<>();
            for (int i = 0; i < concorrentes; i++) {
                resultados.add(threads.submit(() -> {
                    largada.await();
                    return HttpStatus.valueOf(reservar(atendimento, chave).getStatusCode().value());
                }));
            }
            largada.countDown();
            List<HttpStatus> status = new ArrayList<>();
            for (Future<HttpStatus> resultado : resultados) {
                status.add(resultado.get(30, TimeUnit.SECONDS));
            }

            assertThat(status).filteredOn(HttpStatus.CREATED::equals).hasSize(1);
            assertThat(status).filteredOn(HttpStatus.OK::equals).hasSize(concorrentes - 1);
        } finally {
            threads.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM envio_automacao_reserva WHERE chave = ?", Integer.class, chave))
                .isOne();
    }

    @Test
    @DisplayName("timeout do CRM apos a aceitacao duravel: a reentrega encontra ENVIADO e nao envia de novo")
    void reentregaDepoisDeConcluido_naoEnviaDeNovo() throws Exception {
        UUID atendimento = atendimento("REENTREGA", "EM_IA");
        String chave = "evento-" + UUID.randomUUID() + ":resposta";
        assertThat(consumirComoON8n(atendimento, chave, "wamid.E50-reentrega-1")).isOne();

        ResponseEntity<String> reentrega = reservar(atendimento, chave);

        assertThat(reentrega.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode reserva = json.readTree(reentrega.getBody());
        assertThat(reserva.path("novaReserva").asBoolean()).isFalse();
        assertThat(reserva.path("estado").asText()).isEqualTo("ENVIADO");
        assertThat(reserva.path("wamidSaida").asText()).isEqualTo("wamid.E50-reentrega-1");
    }

    @Test
    @DisplayName("provedor aceitou e o registro falhou: fica pendente para conciliar; o registro repetido fecha sem reenviar")
    void provedorAceitouRegistroFalhou_recuperaSemReenviar() throws Exception {
        UUID atendimento = atendimento("RECUPERA", "EM_IA");
        String chave = "evento-" + UUID.randomUUID() + ":resposta";
        assertThat(reservar(atendimento, chave).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // O "provedor" entregou wamid.E50-recupera, mas o POST /mensagens-enviadas nao chegou.

        assertThat(pendentes()).contains(chave);
        assertThat(mensagensDe(atendimento)).isZero();
        // Uma reexecucao do fluxo NAO pode reenviar: a reserva ja existe.
        assertThat(json.readTree(reservar(atendimento, chave).getBody()).path("novaReserva").asBoolean()).isFalse();

        ResponseEntity<String> recuperacao = registrar(atendimento, "wamid.E50-recupera", chave);
        ResponseEntity<String> repetida = registrar(atendimento, "wamid.E50-recupera", chave);

        assertThat(recuperacao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repetida.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repetida.getBody()).contains("\"idempotente\":true");
        assertThat(mensagensDe(atendimento)).isOne();
        assertThat(estadoDa(chave)).isEqualTo("ENVIADO");
        assertThat(pendentes()).doesNotContain(chave);
    }

    @Test
    @DisplayName("conflitos: chave de outro atendimento e segundo wamid para a mesma chave nao registram nada")
    void conflitos() {
        UUID a = atendimento("CONFLITO-A", "EM_IA");
        UUID b = atendimento("CONFLITO-B", "EM_IA");
        String chave = "evento-" + UUID.randomUUID() + ":resposta";
        assertThat(reservar(a, chave).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(reservar(b, chave).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(registrar(b, "wamid.E50-conflito-b", chave).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mensagensDe(b)).isZero();

        assertThat(registrar(a, "wamid.E50-conflito-1", chave).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> segundoEnvio = registrar(a, "wamid.E50-conflito-2", chave);
        assertThat(segundoEnvio.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(segundoEnvio.getBody()).contains("segundo envio");
        assertThat(mensagensDe(a)).isOne();
    }

    @Test
    @DisplayName("negativos: finalizado 409, inexistente 404, chave vazia ou longa 400, sem token 401; registro sem chave segue valendo")
    void negativos() {
        UUID finalizado = atendimento("FINALIZADO", "FINALIZADO");
        assertThat(reservar(finalizado, "evento-x:resposta").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reservar(UUID.randomUUID(), "evento-x:resposta").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        UUID aberto = atendimento("NEGATIVOS", "EM_IA");
        assertThat(reservar(aberto, " ").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reservar(aberto, "x".repeat(201)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        HttpHeaders semToken = new HttpHeaders();
        semToken.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.exchange(
                                "/internal/v1/atendimentos/" + aberto + "/envios-automacao/reservas",
                                HttpMethod.POST,
                                new HttpEntity<>("{\"chave\":\"evento-x\"}", semToken),
                                String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // Fluxo antigo, sem chaveDeEnvio, continua registrando.
        assertThat(registrar(aberto, "wamid.E50-sem-chave", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mensagensDe(aberto)).isOne();
    }

    /** Protocolo do n8n (docs/50): devolve 1 se "enviou ao provedor", 0 se a reserva mandou nao enviar. */
    private int consumirComoON8n(UUID atendimento, String chave, String wamidQueOProvedorDaria) throws Exception {
        ResponseEntity<String> reserva = reservar(atendimento, chave);
        if (!json.readTree(reserva.getBody()).path("novaReserva").asBoolean()) {
            return 0;
        }
        assertThat(registrar(atendimento, wamidQueOProvedorDaria, chave).getStatusCode()).isEqualTo(HttpStatus.OK);
        return 1;
    }

    private ResponseEntity<String> reservar(UUID atendimento, String chave) {
        return chamar(
                HttpMethod.POST,
                "/internal/v1/atendimentos/" + atendimento + "/envios-automacao/reservas",
                "{\"chave\":\"" + chave + "\"}");
    }

    private ResponseEntity<String> registrar(UUID atendimento, String wamid, String chave) {
        String chaveJson = chave == null ? "" : ",\"chaveDeEnvio\":\"" + chave + "\"";
        return chamar(
                HttpMethod.POST,
                "/internal/v1/atendimentos/" + atendimento + "/mensagens-enviadas",
                "{\"wamid\":\"" + wamid + "\",\"tipo\":\"TEXTO\",\"conteudo\":\"Seu orcamento esta pronto\"" + chaveJson + "}");
    }

    private String pendentes() {
        ResponseEntity<String> resposta = chamar(
                HttpMethod.GET, "/internal/v1/envios-automacao/pendentes?reservadosAntesDe=" + Instant.now().plusSeconds(60), null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private ResponseEntity<String> chamar(HttpMethod metodo, String url, String corpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Synapse-Token", TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, headers), String.class);
    }

    private long mensagensDe(UUID atendimento) {
        return jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Long.class, atendimento);
    }

    private String estadoDa(String chave) {
        return jdbc.queryForObject("SELECT estado FROM envio_automacao_reserva WHERE chave = ?", String.class, chave);
    }

    private UUID atendimento(String marcador, String status) {
        UUID lead = UUID.randomUUID();
        jdbc.update("INSERT INTO lead (id, nome, status_basico) VALUES (?, ?, 'IA')", lead, PREFIXO + marcador);
        UUID id = UUID.randomUUID();
        Instant inicio = Instant.now().minusSeconds(600);
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, status, iniciado_em, finalizado_em) VALUES (?, ?, ?::status_atendimento, ?, ?)",
                id,
                lead,
                status,
                Timestamp.from(inicio),
                "FINALIZADO".equals(status) ? Timestamp.from(inicio.plusSeconds(60)) : null);
        return id;
    }
}
