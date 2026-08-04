package com.synapse.crm.core.application.lembrete;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.domain.lembrete.Lembrete;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class CriarLembreteUseCase {
    private final LeadRepositorio leads;
    private final LembreteRepositorio lembretes;
    private final UsuarioContext usuario;

    public CriarLembreteUseCase(LeadRepositorio leads, LembreteRepositorio lembretes, UsuarioContext usuario) {
        this.leads = leads;
        this.lembretes = lembretes;
        this.usuario = usuario;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE', 'SUBGESTOR', 'GESTOR', 'ADMINISTRADOR')")
    @Transactional
    public Optional<Lembrete> executar(UUID leadId, String texto, Instant dataHora) {
        return leads.porId(leadId)
                .map(lead -> lembretes.criar(lead.id(), usuario.atual().id(), texto.trim(), dataHora));
    }
}
