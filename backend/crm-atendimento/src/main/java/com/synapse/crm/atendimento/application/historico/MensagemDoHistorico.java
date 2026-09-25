package com.synapse.crm.atendimento.application.historico;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.mensagem.CitacaoDeMensagem;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.sharedkernel.emoji.ResumoDeReacao;

/**
 * Mensagem enriquecida exclusivamente para leitura, sem levar nome de usuario ao dominio.
 *
 * <p>{@code reacoes} sao as dos usuarios do CRM (com o "reagi" de quem le); {@code reacaoDoCliente}
 * e o emoji atual do cliente no WhatsApp, ou {@code null} (E214). Ficam separados porque o cliente
 * nao e usuario.
 */
public record MensagemDoHistorico(
        Mensagem mensagem,
        String remetenteNome,
        UUID atendimentoId,
        Instant atendimentoIniciadoEm,
        Instant atendimentoFinalizadoEm,
        String atendimentoResponsavelNome,
        ErroDeEntrega erroEntrega,
        List<ResumoDeReacao> reacoes,
        CitacaoDeMensagem citacao,
        String chaveIdempotencia,
        String reacaoDoCliente) {

    public MensagemDoHistorico {
        reacoes = reacoes == null ? List.of() : List.copyOf(reacoes);
    }

    public MensagemDoHistorico(
            Mensagem mensagem,
            String remetenteNome,
            UUID atendimentoId,
            Instant atendimentoIniciadoEm,
            Instant atendimentoFinalizadoEm,
            String atendimentoResponsavelNome,
            ErroDeEntrega erroEntrega,
            List<ResumoDeReacao> reacoes,
            CitacaoDeMensagem citacao,
            String chaveIdempotencia) {
        this(
                mensagem,
                remetenteNome,
                atendimentoId,
                atendimentoIniciadoEm,
                atendimentoFinalizadoEm,
                atendimentoResponsavelNome,
                erroEntrega,
                reacoes,
                citacao,
                chaveIdempotencia,
                null);
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
                null,
                List.of(),
                null,
                null,
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
                erroEntrega,
                novas,
                citacao,
                chaveIdempotencia,
                reacaoDoCliente);
    }

    public MensagemDoHistorico comReacaoDoCliente(String emoji) {
        return new MensagemDoHistorico(
                mensagem,
                remetenteNome,
                atendimentoId,
                atendimentoIniciadoEm,
                atendimentoFinalizadoEm,
                atendimentoResponsavelNome,
                erroEntrega,
                reacoes,
                citacao,
                chaveIdempotencia,
                emoji);
    }
}
