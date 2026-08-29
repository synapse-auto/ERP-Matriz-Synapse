package com.synapse.crm.atendimento.domain.evento;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.synapse.crm.sharedkernel.emoji.ResumoDeReacao;

/**
 * Reacao persistida, pronta para a tela. Separado de {@link EventoDeAtendimento}: nao e fato de
 * timeline, so atualizacao da bolha depois do commit.
 */
public record ReacaoDaMensagemParaTempoReal(
        UUID atendimentoId,
        UUID mensagemId,
        Instant enviadoEm,
        List<ResumoDeReacao> reacoes) {

    public ReacaoDaMensagemParaTempoReal {
        reacoes = reacoes == null ? List.of() : List.copyOf(reacoes);
    }
}
