package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Autentica ANTES de aceitar a conexao — nao a cada frame.
 *
 * <p>O token vem da query string ({@code ?access_token=...}), nao do cabecalho
 * {@code Authorization}: a API nativa de WebSocket do navegador nao permite definir cabecalhos
 * customizados no handshake. E um padrao aceito para WebSocket (a limitacao e da API do browser, nao
 * escolha nossa) — mas significa que o token pode acabar em log de acesso e cache de proxy
 * intermediario. Duas mitigacoes: o token de acesso ja e de vida curta (mesma politica do REST), e
 * o handshake e servido sobre TLS em qualquer ambiente que nao seja localhost.
 *
 * <p>Falha aqui e {@code HTTP 401} <b>antes</b> do upgrade — a conexao nunca chega a abrir. Nao ha
 * frame STOMP CONNECT com token invalido para rejeitar depois; o WebSocket inteiro nunca existe.
 */
@Component
class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    /** Chave do {@link Jwt} decodificado nos atributos da sessao HTTP do handshake. */
    static final String ATRIBUTO_JWT = "jwtAutenticado";

    private static final String PARAMETRO_TOKEN = "access_token";

    private final JwtDecoder jwtDecoder;

    JwtHandshakeInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest requisicao,
            ServerHttpResponse resposta,
            WebSocketHandler manipulador,
            Map<String, Object> atributos) {

        String token = UriComponentsBuilder.fromUri(requisicao.getURI())
                .build()
                .getQueryParams()
                .getFirst(PARAMETRO_TOKEN);

        if (token == null || token.isBlank()) {
            resposta.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            atributos.put(ATRIBUTO_JWT, jwtDecoder.decode(token));
            return true;
        } catch (JwtException e) {
            log.warn("Handshake de WebSocket recusado: token invalido ou expirado.");
            resposta.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest requisicao,
            ServerHttpResponse resposta,
            WebSocketHandler manipulador,
            Exception excecao) {
        // Nada a fazer: sucesso ja aconteceu em beforeHandshake, falha ja recusou ali.
    }
}
