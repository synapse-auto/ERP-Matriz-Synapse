package com.synapse.crm.atendimento.application.historico;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.mensagem.CitacaoDeMensagem;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.sharedkernel.emoji.ResumoDeReacao;

/** Mensagem enriquecida exclusivamente para leitura, sem levar nome de usuario ao dominio. */
public record MensagemDoHistorico(
        Mensagem mensagem,
        String remetenteNome,
        UUID atendimentoId,
        Instant atendimentoIniciadoEm,
        Instant atendimentoFinalizadoEm,
        String atendimentoResponsavelNome,
        List<ResumoDeReacao> reacoes,
        CitacaoDeMensagem citacao) {

    public MensagemDoHistorico {
        reacoes = reacoes == null ? List.of() : List.copyOf(reacoes);
    }

    public MensagemDoHistorico(
            Mensagem mensagem,
            String remetenteNome,
            UUID atendimentoId,
            Instant atendimentoIniciadoEm,
            Instant atendimentoFinalizadoEm,
            String atendimentoResponsavelNome) {
        this(
                mensagem,
                remetenteNome,
                atendimentoId,
                atendimentoIniciadoEm,
                atendimentoFinalizadoEm,
                atendimentoResponsavelNome,
                List.of(),
                null);
    }

    public MensagemDoHistorico comReacoes(List<ResumoDeReacao> novas) {
        return new MensagemDoHistorico(
                mensagem,
                remetenteNome,
                atendimentoId,
                atendimentoIniciadoEm,
                atendimentoFinalizadoEm,
                atendimentoResponsavelNome,
                novas,
                citacao);
    }
}
