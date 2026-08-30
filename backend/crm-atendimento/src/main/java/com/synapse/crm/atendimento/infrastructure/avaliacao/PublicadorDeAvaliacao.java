package com.synapse.crm.atendimento.infrastructure.avaliacao;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** O tick nao espera rede. Reserva apenas quando um worker proprio ja esta disponivel. */
@Component
public class PublicadorDeAvaliacao {
    private static final Logger log = LoggerFactory.getLogger(PublicadorDeAvaliacao.class);
    private final AvaliacaoWebhookProperties config;
    private final AvaliacaoOutboxTransacoes transacoes;
    private final AvaliacaoWebhookHttp http;
    private final Executor executor;
    private final Clock relogio;
    private final Semaphore vagas;
    private final AtomicInteger emAndamento = new AtomicInteger();

    PublicadorDeAvaliacao(AvaliacaoWebhookProperties config, AvaliacaoOutboxTransacoes transacoes,
            AvaliacaoWebhookHttp http, @Qualifier("avaliacaoExecutor") Executor executor, Clock relogio) {
        this.config = config;
        this.transacoes = transacoes;
        this.http = http;
        this.executor = executor;
        this.relogio = relogio;
        this.vagas = new Semaphore(config.concorrencia() + config.fila());
    }

    @Scheduled(fixedDelayString = "${synapse.automacao.avaliacao.intervalo-ms:1000}")
    public void publicarPendentes() {
        if (!config.configurada()) {
            // Nao reserva, entrega ou esgota pendencias enquanto a integracao esta pausada.
            return;
        }
        for (int i = 0; i < config.lote() && vagas.tryAcquire(); i++) {
            emAndamento.incrementAndGet();
            try {
                executor.execute(() -> {
                    try {
                        processar();
                    } catch (RuntimeException e) {
                        // Nao propagar payload, URL, segredo ou excecao de banco ao scheduler.
                        log.error("Avaliacao: falha local no worker; lease recuperavel; classe={}",
                                e.getClass().getSimpleName());
                    } finally {
                        emAndamento.decrementAndGet();
                        vagas.release();
                    }
                });
            } catch (RuntimeException e) {
                emAndamento.decrementAndGet();
                vagas.release();
                log.warn("Avaliacao: executor saturado; nenhuma reserva foi criada");
                break;
            }
        }
    }

    private void processar() {
        var reserva = ContextoDeServico.buscarComo("avaliacao-reservar",
                () -> transacoes.reservar(Instant.now(relogio)));
        if (reserva.isEmpty()) {
            return;
        }
        var pendente = reserva.get();
        var resultado = http.enviar(pendente.payload());
        boolean gravado = ContextoDeServico.buscarComo("avaliacao-resultado",
                () -> transacoes.registrar(pendente, resultado, Instant.now(relogio)));
        if (resultado.sucesso() && gravado) {
            log.info("Avaliacao: evento={} resultado=RECEBIDO_HTTP status={}", pendente.eventoId(), resultado.status());
        } else {
            log.warn("Avaliacao: evento={} classe={} status={} tentativa={} resultadoPersistido={}",
                    pendente.eventoId(), resultado.classe(), resultado.status(), pendente.tentativas(), gravado);
        }
    }

    public int emAndamento() {
        return emAndamento.get();
    }
}
