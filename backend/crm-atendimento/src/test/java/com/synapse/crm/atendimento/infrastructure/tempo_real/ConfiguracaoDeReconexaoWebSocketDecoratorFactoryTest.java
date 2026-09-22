package com.synapse.crm.atendimento.infrastructure.tempo_real;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;

class ConfiguracaoDeReconexaoWebSocketDecoratorFactoryTest {

    @Test
    void anunciaOBackoffConfiguradoNoFrameConnected() {
        var decorador = new ConfiguracaoDeReconexaoWebSocketDecoratorFactory(propriedades());

        TextMessage resultado = (TextMessage) decorador.enriquecer(new TextMessage("CONNECTED\nversion:1.2\n\n\u0000"));

        assertThat(resultado.getPayload())
                .startsWith("CONNECTED\n")
                .contains("x-synapse-reconexao-atraso-inicial-ms:1500\n")
                .contains("x-synapse-reconexao-fator:2.5\n")
                .contains("x-synapse-reconexao-atraso-maximo-ms:20000\n")
                .endsWith("version:1.2\n\n\u0000");
    }

    @Test
    void preservaFrameQueNaoEConnected() {
        TextMessage mensagem = new TextMessage("MESSAGE\ndestination:/queue/teste\n\n{}\u0000");
        var decorador = new ConfiguracaoDeReconexaoWebSocketDecoratorFactory(propriedades());

        assertThat(decorador.enriquecer(mensagem)).isSameAs(mensagem);
    }

    private TempoRealProperties propriedades() {
        return new TempoRealProperties(
                1, 1, 1, 1, "*", 60, 10_000L, 10_000L, Duration.ofMinutes(1), 1_500L, 2.5, 20_000L);
    }
}
