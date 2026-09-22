package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;

/**
 * Anexa configurações operacionais não sensíveis ao frame STOMP {@code CONNECTED}.
 *
 * <p>O Spring produz o {@code CONNECTED} depois de os interceptors do canal terem processado o
 * {@code CONNECT_ACK}; portanto, a decoração da sessão é a última borda segura em que cabeçalhos
 * STOMP podem ser adicionados sem alterar o protocolo interno do broker.
 */
@Component
class ConfiguracaoDeReconexaoWebSocketDecoratorFactory implements WebSocketHandlerDecoratorFactory {

    static final String CABECALHO_ATRASO_INICIAL = "x-synapse-reconexao-atraso-inicial-ms";
    static final String CABECALHO_FATOR = "x-synapse-reconexao-fator";
    static final String CABECALHO_ATRASO_MAXIMO = "x-synapse-reconexao-atraso-maximo-ms";

    private final TempoRealProperties propriedades;

    ConfiguracaoDeReconexaoWebSocketDecoratorFactory(TempoRealProperties propriedades) {
        this.propriedades = propriedades;
    }

    @Override
    public WebSocketHandler decorate(WebSocketHandler manipulador) {
        return new WebSocketHandlerDecorator(manipulador) {
            @Override
            public void afterConnectionEstablished(WebSocketSession sessao) throws Exception {
                super.afterConnectionEstablished(new WebSocketSessionDecorator(sessao) {
                    @Override
                    public void sendMessage(WebSocketMessage<?> mensagem) throws IOException {
                        super.sendMessage(enriquecer(mensagem));
                    }
                });
            }
        };
    }

    WebSocketMessage<?> enriquecer(WebSocketMessage<?> mensagem) {
        if (!(mensagem instanceof TextMessage texto)) {
            return mensagem;
        }

        String payload = texto.getPayload();
        String quebraDeLinha = payload.startsWith("CONNECTED\r\n") ? "\r\n" : "\n";
        String inicio = "CONNECTED" + quebraDeLinha;
        if (!payload.startsWith(inicio)) {
            return mensagem;
        }

        String cabecalhos = CABECALHO_ATRASO_INICIAL
                + ":"
                + propriedades.reconexaoAtrasoInicialMs()
                + quebraDeLinha
                + CABECALHO_FATOR
                + ":"
                + propriedades.reconexaoFator()
                + quebraDeLinha
                + CABECALHO_ATRASO_MAXIMO
                + ":"
                + propriedades.reconexaoAtrasoMaximoMs()
                + quebraDeLinha;
        return new TextMessage(inicio + cabecalhos + payload.substring(inicio.length()), texto.isLast());
    }

    void registrar(WebSocketTransportRegistration registro) {
        registro.addDecoratorFactory(this);
    }
}
