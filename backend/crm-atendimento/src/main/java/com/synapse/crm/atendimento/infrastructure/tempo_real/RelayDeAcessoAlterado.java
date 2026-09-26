package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.sharedkernel.permissao.AcessoDeUsuariosAlterado;

/**
 * Leva a alteracao de acesso a todas as instancias depois do commit.
 *
 * <p>Redis fora do ar nao desfaz nem bloqueia a gravacao (ja commitada) e nao abre acesso: o cache
 * de permissoes de cada no revalida pela revisao do banco, e a assinatura de WebSocket e revalidada
 * pelo TTL com o papel atual. Este aviso e o caminho rapido — sem ele, o limite continua valendo.
 */
@Component
class RelayDeAcessoAlterado {

    private static final Logger log = LoggerFactory.getLogger(RelayDeAcessoAlterado.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    /**
     * Uma thread, fila curta, descarta o mais antigo se lotar. A publicacao nunca roda na thread da
     * requisicao: sem timeout de comando configurado no Lettuce, um Redis travado seguraria a resposta
     * do salvamento por ate 60s. Perder um aviso e aceitavel — revisao e TTL limitam o atraso.
     */
    private final ExecutorService executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(100), r -> {
                Thread t = new Thread(r, "relay-acesso-alterado");
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.DiscardOldestPolicy());

    RelayDeAcessoAlterado(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @PreDestroy
    void encerrar() {
        executor.shutdownNow();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void aoAlterarAcesso(AcessoDeUsuariosAlterado evento) {
        if (evento.usuarios().isEmpty()) {
            return;
        }
        ObjectNode corpo = json.createObjectNode();
        var usuarios = corpo.putArray("usuarios");
        evento.usuarios().forEach(id -> usuarios.add(id.toString()));
        corpo.put("sessaoInvalidada", evento.sessaoInvalidada());
        corpo.put("revisao", evento.revisao());
        String payload = corpo.toString();
        executor.execute(() -> publicar(payload));
    }

    void publicar(String payload) {
        try {
            redis.convertAndSend(CanaisRedis.ACESSO, payload);
        } catch (RuntimeException e) {
            log.warn("Aviso de acesso alterado nao publicado (Redis indisponivel); cache e TTL limitam o atraso.", e);
        }
    }
}
