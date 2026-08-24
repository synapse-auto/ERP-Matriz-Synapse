package com.synapse.crm.equipe.infrastructure.tempo_real;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.equipe.application.chat.EventoDeChatInterno;

/** Publica apenas depois do commit; reconexão sempre pode recarregar o histórico via HTTP. */
@Component
class RelayDeChatInterno {
    private static final String PREFIXO = "synapse:chat-interno:";
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    RelayDeChatInterno(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis; this.json = json;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void publicar(EventoDeChatInterno.MensagemEnviada evento) {
        try {
            redis.convertAndSend(PREFIXO + evento.conversaId(), json.writeValueAsString(new Envelope(
                    "CHAT_INTERNO_MENSAGEM", evento.destinatarios(), evento.conversaId(), evento.mensagemId(),
                    evento.remetenteId(), evento.conteudo(), evento.enviadoEm())));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Nao foi possivel publicar mensagem do chat interno.", e);
        }
    }

    private record Envelope(String tipo, List<UUID> destinatarios, UUID conversaId, UUID mensagemId,
            UUID remetenteId, String conteudo, Instant enviadoEm) {}
}
