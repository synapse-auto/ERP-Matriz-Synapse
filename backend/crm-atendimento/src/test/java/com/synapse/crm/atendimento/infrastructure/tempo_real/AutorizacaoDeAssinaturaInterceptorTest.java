package com.synapse.crm.atendimento.infrastructure.tempo_real;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.synapse.crm.atendimento.application.tempo_real.AutorizarAssinaturaAtendimentoUseCase;
import com.synapse.crm.sharedkernel.identidade.ClaimsJwt;

class AutorizacaoDeAssinaturaInterceptorTest {

    private final AutorizacaoDeAssinaturaInterceptor interceptor =
            new AutorizacaoDeAssinaturaInterceptor(
                    mock(AutorizarAssinaturaAtendimentoUseCase.class), mock(RegistroDeAssinaturas.class));

    @Test
    void fila_pessoal_exige_autenticacao() {
        StompHeaderAccessor acessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        acessor.setDestination(AutorizacaoDeAssinaturaInterceptor.DESTINO_NOTIFICACOES);
        Message<?> mensagem = MessageBuilder.createMessage(new byte[0], acessor.getMessageHeaders());

        assertThat(interceptor.preSend(mensagem, mock(MessageChannel.class))).isNull();
    }

    @Test
    void usuario_autenticado_pode_assinar_a_propria_fila_pessoal() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim(ClaimsJwt.PAPEL, "ATENDENTE")
                .build();
        StompHeaderAccessor acessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        acessor.setDestination(AutorizacaoDeAssinaturaInterceptor.DESTINO_NOTIFICACOES);
        acessor.setUser(new JwtAuthenticationToken(jwt, List.of()));
        Message<?> mensagem = MessageBuilder.createMessage(new byte[0], acessor.getMessageHeaders());

        assertThat(interceptor.preSend(mensagem, mock(MessageChannel.class))).isSameAs(mensagem);
    }

    @Test
    void usuario_nao_pode_direcionar_assinatura_pessoal_para_outro() {
        UUID outro = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim(ClaimsJwt.PAPEL, "ATENDENTE")
                .build();
        StompHeaderAccessor acessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        acessor.setDestination("/user/" + outro + "/queue/notificacoes");
        acessor.setUser(new JwtAuthenticationToken(jwt, List.of()));
        Message<?> mensagem = MessageBuilder.createMessage(new byte[0], acessor.getMessageHeaders());

        assertThat(interceptor.preSend(mensagem, mock(MessageChannel.class))).isNull();
    }
}
