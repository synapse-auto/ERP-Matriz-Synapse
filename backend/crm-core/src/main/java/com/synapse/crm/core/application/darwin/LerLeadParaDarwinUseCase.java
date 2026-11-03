package com.synapse.crm.core.application.darwin;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.domain.lead.Lead;

/** Transação curta encerrada antes da primeira chamada externa. */
@Service
public class LerLeadParaDarwinUseCase {
    private final LeadRepositorio leads;
    public LerLeadParaDarwinUseCase(LeadRepositorio leads) { this.leads = leads; }
    @PreAuthorize("isAuthenticated() and @capacidades.permite('clientes.acessar') and @capacidades.permite('contatos.editar')")
    @Transactional(readOnly = true)
    public Lead executar(UUID id) {
        return leads.porId(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
