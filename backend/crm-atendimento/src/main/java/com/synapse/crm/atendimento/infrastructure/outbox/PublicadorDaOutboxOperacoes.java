package com.synapse.crm.atendimento.infrastructure.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.application.Outbox;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeEnvio;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * Orquestra a drenagem da outbox sem manter uma transacao aberta durante a rede.
 *
 * <p>A reserva e o registro do resultado passam pelo bean transacional separado. O intervalo entre
 * os dois e deliberadamente fora de qualquer transacao: uma chamada lenta nao segura conexao do
 * pool de chat nem impede as outras chamadas do lote de avancarem em paralelo.
 */
@Component
public class PublicadorDaOutboxOperacoes {

    private static final Logger log = LoggerFactory.getLogger(PublicadorDaOutboxOperacoes.class);

    /** Marcador do alarme de esgotamento, fixo e greppavel no agregador de logs. */
    public static final String MARCADOR_ALARME = "[ALERTA_OUTBOX_ESGOTADA]";

    private final PublicadorDaOutboxTransacoes transacoes;
    private final CanalGateway canal;
    private final Clock relogio;
    private final Executor executor;

    public PublicadorDaOutboxOperacoes(
            PublicadorDaOutboxTransacoes transacoes,
            CanalGateway canal,
            Clock relogio,
            @Qualifier("publicadorDaOutboxExecutor") Executor executor) {
        this.transacoes = transacoes;
        this.canal = canal;
        this.relogio = relogio;
        this.executor = executor;
    }

    /** Reserva em uma transacao curta, envia em paralelo fora dela e registra cada resultado em outra. */
    public int rodada() {
        Instant agora = Instant.now(relogio);
        List<Outbox.EnvioPendente> pendentes = transacoes.reservar(agora);
        CompletableFuture<?>[] tarefas = pendentes.stream()
                .map(pendente -> CompletableFuture.runAsync(() -> processar(pendente), executor))
                .toArray(CompletableFuture[]::new);

        if (tarefas.length > 0) {
            CompletableFuture.allOf(tarefas).join();
        }
        return pendentes.size();
    }

    private void processar(Outbox.EnvioPendente pendente) {
        ContextoDeServico.executarComo("publicador-outbox-envio", () -> {
            ResultadoDeEnvio resultado = enviarSemDeixarEscapar(pendente);
            transacoes.registrarResultado(pendente, resultado, Instant.now(relogio));
        });
    }

    /** Uma excecao do adaptador vira recusa temporaria; uma linha nao derruba as demais. */
    private ResultadoDeEnvio enviarSemDeixarEscapar(Outbox.EnvioPendente pendente) {
        try {
            return canal.enviar(new CanalGateway.Envio(
                    pendente.mensagemId(),
                    pendente.telefoneDestino(),
                    pendente.conteudo(),
                    pendente.credencialId()));
        } catch (RuntimeException e) {
            log.warn("Falha nao tratada ao enviar a mensagem {}.", pendente.mensagemId(), e);
            return ResultadoDeEnvio.Recusado.temporario(
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public long contarEsgotadas() {
        return transacoes.contarEsgotadas();
    }
}
