package com.synapse.crm.atendimento.application.resumo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Porta da reserva durável do ciclo de geração de resumo. */
public interface SolicitacaoResumoIaRepositorio {

    void criar(UUID solicitacaoId, UUID leadId, UUID atendimentoId, Instant solicitadoEm);

    Optional<Solicitacao> porId(UUID solicitacaoId);

    Optional<Solicitacao> ultimaDoAtendimento(UUID atendimentoId);

    /** Diagnóstico técnico de entrega, sem URL/payload; consulta feita só após autorizar o recurso. */
    String diagnosticoDeEntrega(UUID solicitacaoId);

    /** Ainda é o atendimento humano original, com o responsável capturado no pedido. */
    boolean cicloVigente(UUID solicitacaoId);

    /** A entrega só pode falhar um ciclo que ainda não recebeu PROCESSANDO/CONCLUIDO. */
    boolean falharEntregaSePendente(UUID solicitacaoId, String codigo, Instant quando);

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
            String erroMensagem,
            String diagnosticoCodigo) {
        public Solicitacao(UUID solicitacaoId, UUID leadId, UUID atendimentoId, Status status,
                Instant solicitadoEm, Instant atualizadoEm, String erroCodigo, String erroMensagem) {
            this(solicitacaoId, leadId, atendimentoId, status, solicitadoEm, atualizadoEm, erroCodigo, erroMensagem, null);
        }
    }

    enum Status {
        PENDENTE,
        PROCESSANDO,
        CONCLUIDO,
        FALHOU
    }
}
