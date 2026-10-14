package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.atendimento.application.painel.operacional.DestinatariosDoPainelOperacional;
import com.synapse.crm.equipe.application.permissao.ResolvedorDePermissoesEfetivas;
import com.synapse.crm.equipe.domain.permissao.Capacidade;
import com.synapse.crm.equipe.domain.usuario.PresencaDoUsuarioAlterada;

/** Mesmo backplane pessoal dos avisos existentes; não é fila de comandos nem estado do painel. */
@Component
class RelayDePresencaOperacional {
    private static final Logger log = LoggerFactory.getLogger(RelayDePresencaOperacional.class);
    private final DestinatariosDoPainelOperacional destinatarios;
    private final ResolvedorDePermissoesEfetivas permissoes;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final ExecutorService executor;

    RelayDePresencaOperacional(DestinatariosDoPainelOperacional destinatarios,
            ResolvedorDePermissoesEfetivas permissoes, StringRedisTemplate redis,
            ObjectMapper json, TempoRealProperties propriedades) {
        this.destinatarios = destinatarios;
        this.permissoes = permissoes;
        this.redis = redis;
        this.json = json;
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(propriedades.filaMaxima()), r -> {
                    Thread t = new Thread(r, "relay-presenca-operacional");
                    t.setDaemon(true);
                    return t;
                }, new ThreadPoolExecutor.DiscardOldestPolicy());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void aoAlterar(PresencaDoUsuarioAlterada evento) {
        // Sem query ou Redis no thread da alteração de presença / da conexão STOMP.
        executor.execute(() -> publicar(evento));
    }

    private void publicar(PresencaDoUsuarioAlterada evento) {
        try {
            String eventoId = UUID.randomUUID().toString();
            for (UUID id : destinatarios.candidatosDaPresenca(evento.usuarioId())) {
                boolean autorizado = permissoes.de(id).filter(p -> p.ativo()
                        && p.efetivas().permite(Capacidade.ATENDIMENTOS_PAINEL_OPERACIONAL)).isPresent();
                if (!autorizado) continue;
                var corpo = json.createObjectNode();
                corpo.put("usuarioId", id.toString());
                corpo.put("tipo", "PAINEL_OPERACIONAL_ALTERADO");
                corpo.put("eventoId", eventoId);
                // Invalidação sem nome, IDs de terceiros, presença ou dados de leads.
                corpo.putObject("dados");
                redis.convertAndSend(CanaisRedis.AVISO_USUARIO, corpo.toString());
            }
        } catch (RuntimeException erro) {
            log.warn("Aviso operacional de presença não entregue; foco/reconexão recuperam o snapshot.");
        }
    }

    @PreDestroy
    void encerrar() { executor.shutdownNow(); }
}
