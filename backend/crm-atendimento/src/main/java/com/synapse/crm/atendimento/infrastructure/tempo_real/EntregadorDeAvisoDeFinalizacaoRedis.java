package com.synapse.crm.atendimento.infrastructure.tempo_real;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.application.finalizacaomassa.AvisosDeFinalizacaoEmMassaRepositorio.AvisoPendente;
import com.synapse.crm.atendimento.application.finalizacaomassa.EntregadorDeAvisoDeFinalizacao;

/**
 * Entrega o aviso de conclusao ao backplane Redis, de onde cada no o leva a fila pessoal do usuario. Roda dentro
 * da transacao do aviso (ver {@code TransacoesDosAvisosDeFinalizacao}): se o Redis falhar, lanca, e a outbox
 * agenda nova tentativa em vez de perder o aviso.
 */
@Component
class EntregadorDeAvisoDeFinalizacaoRedis implements EntregadorDeAvisoDeFinalizacao {

    static final String TIPO = "FINALIZACAO_EM_MASSA_CONCLUIDA";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    EntregadorDeAvisoDeFinalizacaoRedis(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public void entregar(AvisoPendente aviso) {
        ObjectNode dados = json.createObjectNode();
        dados.put("operacaoId", aviso.operacaoId().toString());
        dados.put("finalizadosDoUsuario", aviso.finalizadosDoUsuario());
        dados.put("totalFinalizados", aviso.totalFinalizados());
        dados.put("ignorados", aviso.ignorados());
        dados.put("falhas", aviso.falhas());
        dados.put("parcial", aviso.parcial());
        var afetados = dados.putArray("afetados");
        aviso.afetados().forEach(a -> afetados.addObject().put("nome", a.nome()).put("finalizados", a.finalizados()));

        ObjectNode envelope = json.createObjectNode();
        envelope.put("usuarioId", aviso.usuarioId().toString());
        envelope.put("tipo", TIPO);
        // Mesmo identificador em todo reenvio: o frontend deduplica por ele.
        envelope.put("eventoId", aviso.operacaoId() + ":" + aviso.usuarioId());
        envelope.set("dados", dados);
        redis.convertAndSend(CanaisRedis.AVISO_USUARIO, envelope.toString());
    }
}
