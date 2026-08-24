package com.synapse.crm.atendimento.application.participacao;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipacaoAtendimentoRepositorio {
    Optional<UUID> solicitar(UUID atendimentoId, UUID solicitanteId);
    Optional<UUID> leadId(UUID atendimentoId);
    Optional<UUID> atendimentoAbertoDoLead(UUID leadId);
    Optional<UUID> donoId(UUID atendimentoId);
    java.time.Duration validadeConfigurada();
    Optional<PedidoEntradaAtendimento> pedido(UUID pedidoId);
    Optional<PedidoEntradaAtendimento> pedidoDoSolicitante(UUID atendimentoId, UUID solicitanteId, Instant limite);
    List<PedidoEntradaAtendimento> pendentes(UUID atendimentoId, Instant limite);
    boolean eDono(UUID atendimentoId, UUID usuarioId);
    boolean eParticipanteAtivo(UUID atendimentoId, UUID usuarioId);
    void aprovar(UUID pedidoId, UUID donoId, Instant agora);
    void recusar(UUID pedidoId, UUID donoId, Instant agora);
    void entrar(UUID atendimentoId, UUID usuarioId, Instant agora);
    void sair(UUID atendimentoId, UUID usuarioId, Instant agora);
    List<ParticipanteAtendimento> ativos(UUID atendimentoId);
}
