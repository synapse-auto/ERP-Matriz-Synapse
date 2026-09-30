package com.synapse.crm.atendimento.application.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Da a Automacao o atendimento correto sem varrer a paginacao de /em-andamento.
 *
 * Pela mensagem recebida: ancora a resposta ao evento que a disparou — nunca devolve atendimento de
 * outro lead nem um id antigo, porque o vinculo foi gravado no mesmo commit da mensagem. Pelo lead:
 * para fluxos que nao nascem de uma mensagem recebida.
 */
@Service
public class ResolverAtendimentoDaAutomacaoUseCase {

    private final AtendimentoDaAutomacaoRepositorio repositorio;

    public ResolverAtendimentoDaAutomacaoUseCase(AtendimentoDaAutomacaoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public Optional<AtendimentoDaAutomacaoRepositorio.Vinculo> porMensagemRecebida(String idExterno) {
        return repositorio.porMensagemRecebida(idExterno);
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public ResultadoDoLead abertoDoLead(UUID leadId) {
        return repositorio.abertoDoLead(leadId)
                .<ResultadoDoLead>map(ResultadoDoLead.Aberto::new)
                .orElseGet(() -> repositorio.leadExiste(leadId)
                        ? new ResultadoDoLead.SemAtendimentoAberto()
                        : new ResultadoDoLead.LeadInexistente());
    }

    public sealed interface ResultadoDoLead {
        record Aberto(AtendimentoDaAutomacaoRepositorio.Vinculo vinculo) implements ResultadoDoLead {}

        record SemAtendimentoAberto() implements ResultadoDoLead {}

        record LeadInexistente() implements ResultadoDoLead {}
    }
}
