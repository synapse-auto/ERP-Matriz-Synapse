package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Leva o aviso de um usuario do Redis para a fila pessoal de notificacoes, em cada no onde ele esta conectado. */
@Component
class SubscriberDeAvisoDeUsuario implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(SubscriberDeAvisoDeUsuario.class);
    private static final String DESTINO = "/queue/notificacoes";

    private final SimpMessagingTemplate template;
    private final ObjectMapper json;

    SubscriberDeAvisoDeUsuario(SimpMessagingTemplate template, ObjectMapper json) {
        this.template = template;
        this.json = json;
    }

    @Override
    public void onMessage(Message mensagem, byte[] padrao) {
        try {
            JsonNode envelope = json.readTree(new String(mensagem.getBody(), StandardCharsets.UTF_8));
            UUID usuarioId = UUID.fromString(envelope.path("usuarioId").asText());
            ObjectNode paraOCliente = ((ObjectNode) envelope).deepCopy();
            paraOCliente.remove("usuarioId");
            template.convertAndSendToUser(usuarioId.toString(), DESTINO, paraOCliente.toString());
        } catch (Exception ilegivel) {
            log.warn("Aviso de usuario ilegivel ou sem destinatario valido; descartado.", ilegivel);
        }
    }
}
