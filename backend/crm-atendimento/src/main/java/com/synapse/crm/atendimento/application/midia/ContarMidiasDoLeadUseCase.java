package com.synapse.crm.atendimento.application.midia;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.persistencia.Pools;

@Service
public class ContarMidiasDoLeadUseCase {
    private final MidiasDoLeadRepositorio repositorio;

    public ContarMidiasDoLeadUseCase(MidiasDoLeadRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public ContagemDeMidiasDoLead executar(UUID leadId) {
        return repositorio.contar(leadId);
    }
}
