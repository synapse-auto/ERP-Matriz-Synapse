package com.synapse.crm.campanhas.infrastructure;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Executor proprio dos ouvintes de campanha (bulkhead).
 *
 * <p>Os ouvintes rodam em {@code AFTER_COMMIT}, quando a conexao da transacao original ainda esta presa. Abrir
 * ali uma segunda transacao no MESMO pool do chat faz cada evento segurar duas conexoes; com carga, as threads se
 * seguram mutuamente ate o timeout do pool e o caminho de mensagens degrada (a regra de precedencia do projeto).
 * Por isso o ouvinte so enfileira o trabalho aqui e volta: a conexao original ja foi liberada quando a transacao
 * nova abre.
 *
 * <p>Fila limitada. Se encher, o trabalho e descartado com alarme: a entrega e reconciliada no ciclo da campanha e
 * "respondeu" e so um contador, entao perder um evento e preferivel a deixar a fila crescer sem limite.
 */
@Configuration
class ExecutorDeCampanhasConfig {

    static final String NOME = "campanhasEventosExecutor";

    private static final Logger log = LoggerFactory.getLogger(ExecutorDeCampanhasConfig.class);
    private static final int THREADS = 2;
    private static final int CAPACIDADE_DA_FILA = 1000;

    @Bean(name = NOME, destroyMethod = "shutdown")
    Executor campanhasEventosExecutor() {
        AtomicInteger numero = new AtomicInteger();
        ThreadFactory fabrica = tarefa -> {
            Thread thread = new Thread(tarefa, "campanhas-evento-" + numero.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                THREADS,
                THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(CAPACIDADE_DA_FILA),
                fabrica,
                (tarefa, executor) -> log.error(
                        "[ALERTA_CAMPANHA_EVENTO_DESCARTADO] fila de eventos de campanha cheia ({}); evento descartado. "
                                + "A entrega e reconciliada no ciclo da campanha.",
                        CAPACIDADE_DA_FILA));
    }
}
