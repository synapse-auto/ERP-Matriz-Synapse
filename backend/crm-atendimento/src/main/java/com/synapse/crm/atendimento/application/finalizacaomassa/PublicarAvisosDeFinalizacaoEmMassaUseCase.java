package com.synapse.crm.atendimento.application.finalizacaomassa;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Drena a outbox de avisos em rodadas curtas. Cada aviso e uma transacao: ver {@link TransacoesDosAvisosDeFinalizacao}. */
@Service
public class PublicarAvisosDeFinalizacaoEmMassaUseCase {

    private final TransacoesDosAvisosDeFinalizacao transacoes;

    public PublicarAvisosDeFinalizacaoEmMassaUseCase(TransacoesDosAvisosDeFinalizacao transacoes) {
        this.transacoes = transacoes;
    }

    /** @return quantos avisos foram tratados (entregues ou reagendados). */
    @PreAuthorize("hasRole('SERVICO')")
    public int executar(int maximoPorRodada) {
        int tratados = 0;
        while (tratados < maximoPorRodada && transacoes.publicarProximo()) {
            tratados++;
        }
        return tratados;
    }
}
