package com.synapse.crm.atendimento.application.encaminhamentodochat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;

/**
 * Elo entre a mensagem do Chat Interno e a mensagem externa. Roda sempre dentro de uma transação do
 * pool do chat, a mesma do envio: ou o elo e a mensagem gravam juntos, ou nenhum dos dois.
 */
public interface EncaminhamentoDoChatRepositorio {

    Optional<Encaminhamento> porChave(String chave);

    Encaminhamento registrar(NovoEncaminhamento novo);

    /** O que {@code usuarioId} já encaminhou desta mensagem, mais recente primeiro, com o estado atual da entrega. */
    List<EncaminhamentoComStatus> daMensagem(UUID usuarioId, UUID mensagemInternaId);

    /** Estado atual da mensagem externa; vazio se o usuário não a alcança (RLS). */
    Optional<StatusEntrega> statusDaMensagemExterna(UUID mensagemExternaId, Instant enviadaEm);

    /**
     * Atendimentos abertos que o usuário alcança, do mais recente ao mais antigo. O alcance é da RLS de
     * {@code atendimento} e {@code lead}, não deste método. {@code termo} filtra por nome (qualquer parte) ou por
     * dígitos do telefone; {@code null} ou vazio lista os mais recentes. Curingas do usuário valem como texto.
     */
    List<DestinoEncontrado> buscarDestinosAbertos(String termo, int limite);

    record NovoEncaminhamento(
            String chave,
            UUID usuarioId,
            UUID conversaId,
            UUID mensagemInternaId,
            UUID atendimentoId,
            UUID leadId,
            UUID mensagemExternaId,
            Instant mensagemExternaEnviadaEm,
            String tipo,
            boolean transferiuOLead,
            boolean conviteCriado) {}

    record Encaminhamento(
            UUID id,
            String chave,
            UUID usuarioId,
            UUID conversaId,
            UUID mensagemInternaId,
            UUID atendimentoId,
            UUID leadId,
            UUID mensagemExternaId,
            Instant mensagemExternaEnviadaEm,
            String tipo,
            boolean transferiuOLead,
            boolean conviteCriado,
            Instant criadoEm) {}

    /** O telefone sai cru daqui e é mascarado antes de sair da aplicação. */
    record DestinoEncontrado(
            UUID atendimentoId, String clienteNome, String telefone, String statusAtendimento, String responsavelNome) {}

    /** {@code erroEntrega} é o JSON {codigo, titulo} do provedor, só quando {@code FALHOU}. */
    record EncaminhamentoComStatus(Encaminhamento encaminhamento, StatusEntrega status, String erroEntrega) {}
}
