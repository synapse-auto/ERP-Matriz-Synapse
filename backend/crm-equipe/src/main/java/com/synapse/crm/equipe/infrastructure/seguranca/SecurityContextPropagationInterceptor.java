package com.synapse.crm.equipe.infrastructure.seguranca;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Publica no {@code SecurityContextHolder} o {@link Authentication} da sessao STOMP, para a duracao
 * do processamento de cada frame.
 *
 * <p>Mora aqui, e nao no modulo de atendimento que a usa (E06), porque a regra de arquitetura deste
 * projeto reserva o toque em {@code SecurityContextHolder} a este pacote — o mesmo motivo que existe
 * {@code UsuarioContextSpring}. E publica porque o WebSocket, em crm-atendimento, precisa registra-la
 * na cadeia de interceptadores do canal STOMP.
 *
 * <p>Equivalente manual de {@code SecurityContextChannelInterceptor} (Spring Security). Escrito a mao
 * para nao trazer {@code spring-security-messaging} so por uma classe de ~15 linhas cujo contrato e
 * simples e estavel: preencher no {@code preSend}, limpar no {@code afterSendCompletion}.
 *
 * <p>Sem isto, {@code UsuarioContextSpring.atualSeHouver()} — e por tabela, todo caso de uso, toda
 * politica RLS — enxergaria "sem usuario" em toda mensagem STOMP, porque o {@code SecurityContextHolder}
 * e por thread e o handshake HTTP que autenticou a conexao ja terminou ha muito quando a primeira
 * mensagem chega.
 */
@Component
public class SecurityContextPropagationInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> mensagem, MessageChannel canal) {
        SimpMessageHeaderAccessor acessor = SimpMessageHeaderAccessor.wrap(mensagem);

        if (acessor.getUser() instanceof Authentication autenticacao) {
            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(autenticacao);
            SecurityContextHolder.setContext(contexto);
        }
        return mensagem;
    }

    @Override
    public void afterSendCompletion(
            Message<?> mensagem, MessageChannel canal, boolean enviada, Exception excecao) {
        // Sempre limpa, mesmo em frame sem usuario: a thread do executor e
        // reaproveitada entre mensagens, e um contexto que vazasse contaminaria
        // o proximo frame processado por ela — de outra sessao, outro usuario.
        SecurityContextHolder.clearContext();
    }
}
