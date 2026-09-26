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

/**
 * Em cada instancia: descarta as assinaturas de quem perdeu a sessao (papel mudou, desativado) e
 * avisa as abas abertas pela fila pessoal para recarregarem as permissoes sem F5.
 *
 * <p>O aviso nao carrega permissao nenhuma — so "mudou, revisao N". A tela busca o efetivo de novo
 * no backend; nada e decidido a partir deste payload.
 */
@Component
class SubscriberDeAcessoAlterado implements MessageListener {

    static final String TIPO = "ACESSO_ALTERADO";
    private static final Logger log = LoggerFactory.getLogger(SubscriberDeAcessoAlterado.class);

    private final RegistroDeAssinaturas registro;
    private final SimpMessagingTemplate template;
    private final ObjectMapper json;

    SubscriberDeAcessoAlterado(RegistroDeAssinaturas registro, SimpMessagingTemplate template, ObjectMapper json) {
        this.registro = registro;
        this.template = template;
        this.json = json;
    }

    @Override
    public void onMessage(Message mensagem, byte[] padrao) {
        JsonNode corpo;
        try {
            corpo = json.readTree(new String(mensagem.getBody(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Aviso de acesso alterado ilegivel.", e);
            return;
        }
        boolean sessaoInvalidada = corpo.path("sessaoInvalidada").asBoolean(false);
        long revisao = corpo.path("revisao").asLong();
        for (JsonNode id : corpo.path("usuarios")) {
            UUID usuarioId;
            try {
                usuarioId = UUID.fromString(id.asText());
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (sessaoInvalidada) {
                registro.removerDoUsuario(usuarioId);
            }
            ObjectNode aviso = json.createObjectNode();
            aviso.put("tipo", TIPO);
            aviso.put("eventoId", TIPO + ":" + revisao + ":" + usuarioId);
            ObjectNode dados = aviso.putObject("dados");
            dados.put("sessaoInvalidada", sessaoInvalidada);
            dados.put("revisao", revisao);
            template.convertAndSendToUser(usuarioId.toString(), RedisSubscriberDeAtendimento.DESTINO_NOTIFICACOES,
                    aviso.toString());
        }
    }
}
