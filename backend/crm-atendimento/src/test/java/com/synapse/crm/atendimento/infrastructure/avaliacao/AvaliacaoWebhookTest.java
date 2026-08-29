package com.synapse.crm.atendimento.infrastructure.avaliacao;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AvaliacaoWebhookTest {
    static AvaliacaoWebhookProperties config(String url, String token, String header) {
        return new AvaliacaoWebhookProperties(url, token, header, Duration.ofSeconds(2),
                Duration.ofSeconds(10), 2, 1, 2, 3, Duration.ofSeconds(1),
                Duration.ofSeconds(4), 3, Duration.ofSeconds(10));
    }
    static Stream<Arguments> incompletas() {
        return Stream.of(
                Arguments.of("", "", ""), Arguments.of("", "fixture", "X-Fixture"),
                Arguments.of("http://127.0.0.1:1/avaliacao", "", "X-Fixture"),
                Arguments.of("http://127.0.0.1:1/avaliacao", "fixture", ""),
                Arguments.of("nao-url", "fixture", "X-Fixture"),
                Arguments.of("ftp://localhost/avaliacao", "fixture", "X-Fixture"),
                Arguments.of("http://usuario:senha@localhost/avaliacao", "fixture", "X-Fixture"),
                Arguments.of("http://localhost/avaliacao", "fixture\r\nX-Extra: sim", "X-Fixture"),
                Arguments.of("http://localhost/avaliacao", "fixture", "Content-Type"),
                Arguments.of("http://localhost/avaliacao", "fixture", "Host"),
                Arguments.of("http://localhost/avaliacao", "fixture", "X\nExtra"));
    }
    @ParameterizedTest @MethodSource("incompletas")
    void configuracaoIncompletaNaoTentaRede(String url, String token, String header) {
        var config = config(url, token, header);
        assertThat(config.configurada()).isFalse();
        var adaptador = new AvaliacaoWebhookHttp(config, new ObjectMapper(), CircuitBreakerRegistry.ofDefaults());
        try {
            assertThat(adaptador.enviar("{}").classe()).isEqualTo("CONFIGURACAO_INVALIDA");
        } finally { adaptador.fechar(); }
    }
    @Test
    void segredoNaoApareceNaSerializacaoOuToString() throws Exception {
        var config = config("https://avaliacao.example.test/webhook", "segredo-sintetico", "X-Fixture");
        assertThat(config.configurada()).isTrue();
        assertThat(config.toString()).doesNotContain("segredo-sintetico", "example.test");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(config))
                .doesNotContain("segredo-sintetico", "\"token\"");
    }
    @Test
    void transacaoAtivaProibeRede() {
        var adaptador = new AvaliacaoWebhookHttp(config("", "", ""), new ObjectMapper(),
                CircuitBreakerRegistry.ofDefaults());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> adaptador.enviar("{}")).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("nao pode reter transacao");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            adaptador.fechar();
        }
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "null", "[]", "invalido",
        "{\"modo\":\"OUTRO\",\"status_finalizacao\":\"FINALIZADO\",\"atendimento_id\":\"uuid\",\"lead_id\":\"uuid\",\"atendente_id\":\"uuid\",\"wa_id\":\"5561999999999\"}"})
    void payloadCorrompidoEsgotaSemEnviar(String payload) {
        var adaptador = new AvaliacaoWebhookHttp(config("http://127.0.0.1:1/avaliacao", "fixture", "X-Fixture"),
                new ObjectMapper(), CircuitBreakerRegistry.ofDefaults());
        try {
            assertThat(adaptador.enviar(payload).classe()).isEqualTo("PAYLOAD_INVALIDO");
            assertThat(adaptador.enviar(payload).permanente()).isTrue();
        } finally { adaptador.fechar(); }
    }
    @Test
    void backoffCresceComTetoELeasePrecisaSuperarTimeout() {
        var config = config("", "", "");
        assertThat(config.esperaApos(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(config.esperaApos(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(config.esperaApos(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(config.esperaApos(30)).isEqualTo(Duration.ofSeconds(4));
        assertThatThrownBy(() -> new AvaliacaoWebhookProperties("", "", "", Duration.ofSeconds(10),
                Duration.ofSeconds(10), 2, 1, 2, 3, Duration.ofSeconds(1), Duration.ofSeconds(4),
                3, Duration.ofSeconds(10))).isInstanceOf(IllegalArgumentException.class);
    }
}
