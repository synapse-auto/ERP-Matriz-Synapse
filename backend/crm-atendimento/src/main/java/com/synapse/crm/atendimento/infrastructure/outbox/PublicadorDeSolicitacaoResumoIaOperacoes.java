package com.synapse.crm.atendimento.infrastructure.outbox;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.application.ResumoIaAutomacaoGateway;
import com.synapse.crm.atendimento.application.ResumoIaAutomacaoGateway.Resultado;

/** Orquestra HTTP fora da transação e do caminho de mensagens. */
@Component
public class PublicadorDeSolicitacaoResumoIaOperacoes {
    private static final Logger log = LoggerFactory.getLogger(PublicadorDeSolicitacaoResumoIaOperacoes.class);
    private final PublicadorDeSolicitacaoResumoIaTransacoes transacoes;
    private final ResumoIaAutomacaoGateway automacao;
    private final Clock relogio;

    public PublicadorDeSolicitacaoResumoIaOperacoes(PublicadorDeSolicitacaoResumoIaTransacoes transacoes,
            ResumoIaAutomacaoGateway automacao, Clock relogio) {
        this.transacoes = transacoes;
        this.automacao = automacao;
        this.relogio = relogio;
    }

    public int rodada() {
        if (!automacao.configurado()) return 0;
        var pendentes = transacoes.reservar(Instant.now(relogio));
        for (var pendente : pendentes) {
            if (!transacoes.precisaEntregar(pendente, Instant.now(relogio))) continue;
            Resultado resultado = automacao.enviar(pendente.solicitacaoId(), pendente.leadId(),
                    pendente.atendimentoId(), pendente.solicitadoEm());
            transacoes.registrarResultado(pendente, resultado, Instant.now(relogio));
            log.info("[RESUMO_IA_ENTREGA] solicitacaoId={} atendimentoId={} leadId={} etapa=outbox resultado={}",
                    pendente.solicitacaoId(), pendente.atendimentoId(), pendente.leadId(), resultado);
        }
        return pendentes.size();
    }
}
