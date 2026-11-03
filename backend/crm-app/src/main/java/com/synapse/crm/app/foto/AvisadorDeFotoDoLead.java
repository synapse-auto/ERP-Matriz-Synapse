package com.synapse.crm.app.foto;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.synapse.crm.sharedkernel.permissao.ConsultaDeAcessoAClientes;

/** Sinal sem IDs de leads/conteúdo: cada destinatário relê somente o próprio recorte autorizado. */
@Component
public class AvisadorDeFotoDoLead {
    private final JdbcTemplate jdbc;
    private final ConsultaDeAcessoAClientes acesso;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    public AvisadorDeFotoDoLead(JdbcTemplate jdbc, ConsultaDeAcessoAClientes acesso, StringRedisTemplate redis, ObjectMapper json) {
        this.jdbc=jdbc; this.acesso=acesso; this.redis=redis; this.json=json;
    }
    public void publicar() {
        try {
            String evento=UUID.randomUUID().toString();
            for (UUID usuario : jdbc.query("SELECT id FROM usuario WHERE ativo",(rs,n)->rs.getObject(1,UUID.class))) {
                if (!acesso.permitido(usuario)) continue;
                var aviso=json.createObjectNode().put("usuarioId",usuario.toString()).put("eventoId",evento)
                        .put("tipo","FOTOS_LEADS_ATUALIZADAS");
                aviso.putObject("dados");
                redis.convertAndSend("synapse:aviso-usuario",aviso.toString());
            }
        } catch (RuntimeException falha) {
            LoggerFactory.getLogger(getClass()).warn("Z_API_FOTO_AVISO_NAO_ENTREGUE");
        }
    }
}
