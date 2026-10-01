package com.synapse.crm.atendimento.infrastructure.precificacao;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.atendimento.application.precificacao.FilaDePrecificacaoMeta;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** Consome a fila de precificacao fora do request do webhook e da outbox de mensagens. */
@Component
public class ProcessadorDePrecificacaoMeta {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorDePrecificacaoMeta.class);

    private final FilaDePrecificacaoMeta fila;
    private final TransactionTemplate transacoes;
    private final int lote;

    public ProcessadorDePrecificacaoMeta(
            FilaDePrecificacaoMeta fila,
            @Qualifier("transactionManager") PlatformTransactionManager gerente,
            @Value("${synapse.canal.webhook.lote:50}") int lote) {
        this.fila = fila;
        this.transacoes = new TransactionTemplate(gerente);
        this.lote = lote;
    }

    @Scheduled(fixedDelayString = "${synapse.canal.meta-precificacao.intervalo-ms:30000}")
    public void processarPendentes() {
        ContextoDeServico.executarComo("processador-precificacao-meta", this::rodada);
    }

    private void rodada() {
        for (int i = 0; i < lote; i++) {
            Optional<String> proximo = transacoes.execute(status -> fila.proximoEventoId());
            if (proximo == null || proximo.isEmpty()) {
                return;
            }
            String idEvento = proximo.get();
            try {
                transacoes.executeWithoutResult(status -> fila.processar(idEvento));
            } catch (RuntimeException e) {
                // Nenhum dado financeiro ou payload nos logs. A linha permanece para retry
                // limitado, enquanto o processamento de mensagens segue independente.
                log.warn(
                        "Falha na apuracao de evento Meta; retry limitado na fila (tipo={}).",
                        e.getClass().getSimpleName());
                transacoes.executeWithoutResult(status -> fila.reagendar(idEvento));
            }
        }
    }
}
