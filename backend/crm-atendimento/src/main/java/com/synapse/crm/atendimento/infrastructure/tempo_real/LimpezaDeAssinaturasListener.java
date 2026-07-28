package com.synapse.crm.atendimento.infrastructure.tempo_real;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Esvazia {@link RegistroDeAssinaturas} quando a sessao cai — queda de rede, fechar a aba, logout.
 *
 * <p>Sem isto, o registro cresceria para sempre com sessoes mortas, e — mais serio — continuaria
 * tentando entregar para {@code sessionId}s que nao existem mais, o que e inofensivo (o
 * {@code SimpMessagingTemplate} so nao acha destino), mas mascara o vazamento de memoria de longo
 * prazo em produtor de alto volume de conexoes indo e vindo.
 */
@Component
class LimpezaDeAssinaturasListener {

    private final RegistroDeAssinaturas registro;

    LimpezaDeAssinaturasListener(RegistroDeAssinaturas registro) {
        this.registro = registro;
    }

    @EventListener
    void aoDesconectar(SessionDisconnectEvent evento) {
        String sessionId = StompHeaderAccessor.wrap(evento.getMessage()).getSessionId();
        if (sessionId != null) {
            registro.removerDaSessao(sessionId);
        }
    }
}
