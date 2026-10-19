package com.synapse.crm.app.cancelamentos;

import java.util.UUID;
import java.util.concurrent.*;

import jakarta.annotation.PreDestroy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.equipe.application.permissao.ResolvedorDePermissoesEfetivas;
import com.synapse.crm.equipe.domain.permissao.Capacidade;
import com.synapse.crm.relatorios.application.cancelamentos.CancelamentoRegistrado;

/** Invalidacao best-effort pelo backplane existente, nao e fila de comandos. */
@Component
class AvisadorDeCancelamentos {
    private static final Logger log=LoggerFactory.getLogger(AvisadorDeCancelamentos.class);
    private final ExecutorService executor;
    private final JdbcTemplate jdbc;
    private final ResolvedorDePermissoesEfetivas permissoes;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    AvisadorDeCancelamentos(JdbcTemplate jdbc, ResolvedorDePermissoesEfetivas permissoes,
            StringRedisTemplate redis,ObjectMapper json,@Value("${synapse.cancelamentos.fila-avisos}") int fila) {
        this.jdbc=jdbc; this.permissoes=permissoes; this.redis=redis; this.json=json;
        executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(fila),
            r -> { var t=new Thread(r,"aviso-cancelamentos"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.DiscardOldestPolicy());
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    void aposCommit(CancelamentoRegistrado evento) { executor.execute(this::publicar); }

    private void publicar() {
        try {
            String eventoId=UUID.randomUUID().toString();
            for(UUID id:jdbc.query("SELECT id FROM usuario WHERE ativo",(r,n)->r.getObject("id",UUID.class))) {
                if (permissoes.de(id).filter(p -> p.ativo() && p.efetivas().permite(Capacidade.CANCELAMENTOS_VER)).isEmpty()) continue;
                var corpo=json.createObjectNode();
                corpo.put("usuarioId",id.toString()); corpo.put("tipo","CANCELAMENTOS_ALTERADOS");
                corpo.put("eventoId",eventoId); corpo.putObject("dados");
                redis.convertAndSend("synapse:aviso-usuario",corpo.toString());
            }
        } catch(RuntimeException falha) {
            log.warn("Invalidacao de cancelamentos nao entregue; foco/reconexao recuperam a leitura.");
        }
    }
    @PreDestroy void encerrar() { executor.shutdownNow(); }
}
