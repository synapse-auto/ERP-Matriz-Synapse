package com.synapse.crm.app.saude;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDaOutbox;

/** Exercita autenticacao HTTP real contra um provedor que recusa a credencial. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class SaudeCanalInvalidoIT extends PostgresIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static HttpServer provedor;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private PublicadorDaOutbox consumidor;

    @DynamicPropertySource
    static void configurarProvedor(DynamicPropertyRegistry registro) {
        iniciarProvedor();
        registro.add("synapse.canal.whatsapp.provedor", () -> "meta-cloud");
        registro.add(
                "synapse.canal.whatsapp.url-base",
                () -> "http://127.0.0.1:" + provedor.getAddress().getPort());
        registro.add("synapse.canal.whatsapp.numero-principal", () -> "phone-id-e22");
        registro.add("synapse.canal.whatsapp.token", () -> "credencial-invalida-e22");
        registro.add("synapse.saude.critica.fila-sem-consumo-maximo", () -> "5m");
    }

    @BeforeAll
    static void garantirServidor() {
        iniciarProvedor();
    }

    @AfterAll
    static void pararServidor() {
        if (provedor != null) {
            provedor.stop(0);
        }
    }

    @Test
    @DisplayName("provedor recusa credencial por HTTP e critical identifica canal")
    void credencialInvalidaNoProvedor_identificaCanal() throws Exception {
        consumidor.publicarPendentes();

        var resposta = http.getForEntity("/health/critical", String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode corpo = JSON.readTree(resposta.getBody());
        JsonNode canal = java.util.stream.StreamSupport.stream(
                        corpo.path("componentes").spliterator(), false)
                .filter(c -> "canal".equals(c.path("nome").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(canal.path("status").asText()).isEqualTo("DOWN");
        assertThat(canal.path("detalhe").asText()).contains("HTTP 401");
    }

    private static synchronized void iniciarProvedor() {
        if (provedor != null) {
            return;
        }
        try {
            provedor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            provedor.createContext("/", troca -> {
                troca.sendResponseHeaders(401, -1);
                troca.close();
            });
            provedor.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
