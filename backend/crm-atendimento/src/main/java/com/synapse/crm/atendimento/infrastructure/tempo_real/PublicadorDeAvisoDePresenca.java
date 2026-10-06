package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.synapse.crm.equipe.domain.usuario.StatusPresenca;

/**
 * Avisa a fila pessoal do usuario que o SISTEMA mudou a presenca dele, para a sidebar refletir sem F5. Mesmo
 * caminho do aviso de finalizacao em massa (Redis {@code synapse:aviso-usuario} para cada no onde ele esta
 * conectado). So id e estado: nenhum dado pessoal.
 */
@Component
class PublicadorDeAvisoDePresenca {

    static final String TIPO = "PRESENCA_ALTERADA";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    PublicadorDeAvisoDePresenca(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    void presencaAlterada(UUID usuarioId, StatusPresenca novo) {
        ObjectNode envelope = json.createObjectNode();
        envelope.put("usuarioId", usuarioId.toString());
        envelope.put("tipo", TIPO);
        envelope.put("eventoId", UUID.randomUUID().toString());
        envelope.putObject("dados").put("status", novo.name());
        redis.convertAndSend(CanaisRedis.AVISO_USUARIO, envelope.toString());
    }
}
