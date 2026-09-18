package com.synapse.crm.atendimento.infrastructure.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/** Ponto de entrada agendado do pedido assíncrono de resumo por IA. */
@Component
public class PublicadorDeSolicitacaoResumoIa {

    private final PublicadorDeSolicitacaoResumoIaOperacoes operacoes;

    public PublicadorDeSolicitacaoResumoIa(PublicadorDeSolicitacaoResumoIaOperacoes operacoes) {
        this.operacoes = operacoes;
    }

    @Scheduled(fixedDelayString = "${synapse.automacao.resumo-ia.intervalo-ms:1000}")
    public void publicarPendentes() {
        ContextoDeServico.executarComo("repasse-resumo-ia-automacao", operacoes::rodada);
    }
}
