package com.synapse.crm.atendimento.application.internal;

import java.util.Optional;
import java.util.UUID;

import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;

/**
 * Resolve o atendimento que a Automacao deve usar, por consulta estreita e sem paginacao.
 *
 * Nao reaproveita {@link AtendimentosEmAndamentoRepositorio#porLeadEmAtendimento}: aquela busca e
 * do ciclo humano do EV-05 e exclui EM_IA de proposito.
 */
public interface AtendimentoDaAutomacaoRepositorio {

    /**
     * Atendimento em que o CRM registrou a mensagem RECEBIDA do cliente com este id do provedor.
     * Vazio enquanto o processamento da entrada nao gravou a mensagem (ou se o id nao for de entrada).
     */
    Optional<Vinculo> porMensagemRecebida(String idExternoDaEntrada);

    /** Atendimento aberto (EM_IA ou EM_ATENDIMENTO) mais recente do lead. */
    Optional<Vinculo> abertoDoLead(UUID leadId);

    boolean leadExiste(UUID leadId);

    record Vinculo(UUID atendimentoId, UUID leadId, StatusAtendimento status) {}
}
