package com.synapse.crm.atendimento.application.resumo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Porta da reserva durável do ciclo de geração de resumo. */
public interface SolicitacaoResumoIaRepositorio {

    void criar(UUID solicitacaoId, UUID leadId, UUID atendimentoId, Instant solicitadoEm);

    Optional<Solicitacao> porId(UUID solicitacaoId);

    Optional<Solicitacao> ultimaDoAtendimento(UUID atendimentoId);

    /** Atualiza o estado somente se a transição ainda for válida para o ciclo informado. */
    boolean atualizarStatus(
            UUID solicitacaoId,
            UUID leadId,
            UUID atendimentoId,
            Status status,
            String erroCodigo,
            String erroMensagem,
            Instant atualizadoEm);

    record Solicitacao(
            UUID solicitacaoId,
            UUID leadId,
            UUID atendimentoId,
            Status status,
            Instant solicitadoEm,
            Instant atualizadoEm,
            String erroCodigo,
            String erroMensagem) {}

    enum Status {
        PENDENTE,
        PROCESSANDO,
        CONCLUIDO,
        FALHOU
    }
}
