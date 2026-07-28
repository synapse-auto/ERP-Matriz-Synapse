package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.application.tempo_real.AutorizarAssinaturaAtendimentoUseCase;
import com.synapse.crm.sharedkernel.identidade.ClaimsJwt;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * O nucleo de seguranca da etapa: nenhum SUBSCRIBE a um atendimento passa sem autorizacao.
 *
 * <p>Um broadcast mal desenhado entrega mensagem de lead alheio a quem estiver escutando, e nenhuma
 * Specification, politica RLS ou trava de transacao alcanca esse caminho — o WebSocket e uma segunda
 * porta de leitura. Este interceptador e o unico lugar que fecha essa porta.
 *
 * <p>Quando a autorizacao falha, o metodo devolve {@code null}. Um {@link ChannelInterceptor} que
 * devolve {@code null} em {@code preSend} <b>cancela</b> a mensagem: ela nunca chega ao broker, a
 * assinatura nunca existe no registro nativo do Spring, e nunca existe em {@link RegistroDeAssinaturas}
 * — as duas fontes que decidem quem recebe o que concordam desde o inicio.
 */
@Component
class AutorizacaoDeAssinaturaInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AutorizacaoDeAssinaturaInterceptor.class);

    /**
     * Prefixo do destino de atendimento. {@code /user/...} e o prefixo de destino-por-usuario do
     * Spring: o broker resolve, por sessao, uma fila que so aquela sessao alcanca — nunca um topico
     * de broadcast nativo, que entregaria a QUALQUER assinante sem checar nada.
     */
    static final String PREFIXO_DESTINO = "/user/queue/atendimento.";

    private final AutorizarAssinaturaAtendimentoUseCase autorizar;
    private final RegistroDeAssinaturas registro;

    AutorizacaoDeAssinaturaInterceptor(
            AutorizarAssinaturaAtendimentoUseCase autorizar, RegistroDeAssinaturas registro) {
        this.autorizar = autorizar;
        this.registro = registro;
    }

    @Override
    public Message<?> preSend(Message<?> mensagem, MessageChannel canal) {
        StompHeaderAccessor acessor = StompHeaderAccessor.wrap(mensagem);

        if (acessor.getCommand() == StompCommand.UNSUBSCRIBE) {
            registro.removerPorSubscriptionId(acessor.getSessionId(), acessor.getSubscriptionId());
            return mensagem;
        }
        if (acessor.getCommand() != StompCommand.SUBSCRIBE) {
            return mensagem;
        }

        String destino = acessor.getDestination();
        if (destino == null || !destino.startsWith(PREFIXO_DESTINO)) {
            // Outras filas pessoais (ex.: /user/queue/atendimento.revogado) nao
            // precisam de autorizacao adicional: o mecanismo de destino-por-usuario
            // do Spring ja garante que so a propria sessao as alcanca.
            return mensagem;
        }

        UUID atendimentoId = extrairAtendimentoId(destino);
        if (atendimentoId == null) {
            log.warn("SUBSCRIBE com destino malformado recusado: {}", destino);
            return null;
        }

        if (!(acessor.getUser() instanceof Authentication autenticacao)
                || !(autenticacao.getPrincipal() instanceof Jwt jwt)) {
            // SecurityContextPropagationInterceptor roda antes deste na cadeia
            // (ordem de registro em WebSocketConfig); chegar aqui sem usuario e o
            // mesmo "sem contexto = nega tudo" do RLS.
            return null;
        }

        // A checagem que importa: mesma porta, mesma politica RLS que o REST usa.
        if (!autorizar.autorizar(atendimentoId)) {
            log.warn(
                    "Assinatura de /queue/atendimento.{} recusada: usuario {} nao alcanca este atendimento.",
                    atendimentoId,
                    jwt.getSubject());
            return null;
        }

        registro.registrar(new AssinaturaAutorizada(
                acessor.getSessionId(),
                acessor.getSubscriptionId(),
                atendimentoId,
                UUID.fromString(jwt.getSubject()),
                PapelUsuario.valueOf(jwt.getClaimAsString(ClaimsJwt.PAPEL))));
        return mensagem;
    }

    private static UUID extrairAtendimentoId(String destino) {
        try {
            return UUID.fromString(destino.substring(PREFIXO_DESTINO.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
