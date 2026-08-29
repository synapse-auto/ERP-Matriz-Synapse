package com.synapse.crm.equipe.infrastructure.tempo_real;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.equipe.application.chat.EventoDeChatInterno;

/** Publica apenas depois do commit; reconexão sempre pode recarregar o histórico via HTTP. */
@Component
class RelayDeChatInterno {
    private static final String PREFIXO = "synapse:chat-interno:";
    private static final Logger log = LoggerFactory.getLogger(RelayDeChatInterno.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    RelayDeChatInterno(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void publicar(EventoDeChatInterno.MensagemEnviada evento) {
        enviar(PREFIXO + evento.conversaId(), () -> json.writeValueAsString(new EnvelopeMensagem(
                "CHAT_INTERNO_MENSAGEM",
                evento.destinatarios(),
                evento.conversaId(),
                evento.mensagemId(),
                evento.remetenteId(),
                evento.conteudo(),
                evento.enviadoEm())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void publicarReacao(EventoDeChatInterno.ReacaoAlterada evento) {
        enviar(PREFIXO + evento.conversaId(), () -> json.writeValueAsString(new EnvelopeReacao(
                "CHAT_INTERNO_REACAO",
                evento.destinatarios(),
                evento.conversaId(),
                evento.mensagemId(),
                evento.reacoes().stream().map(r -> new ResumoPublico(r.emoji(), r.quantidade())).toList())));
    }

    private void enviar(String canal, Serializador serializador) {
        try {
            redis.convertAndSend(canal, serializador.serializar());
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Falha ao publicar evento de chat interno no Redis.", e);
        }
    }

    @FunctionalInterface
    private interface Serializador {
        String serializar() throws JsonProcessingException;
    }

    private record EnvelopeMensagem(String tipo, List<UUID> destinatarios, UUID conversaId, UUID mensagemId,
            UUID remetenteId, String conteudo, Instant enviadoEm) {}

    private record EnvelopeReacao(String tipo, List<UUID> destinatarios, UUID conversaId, UUID mensagemId,
            List<ResumoPublico> reacoes) {}

    private record ResumoPublico(String emoji, int quantidade) {}
}
