package com.synapse.crm.core.application.mensagemprogramada;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.domain.mensagemprogramada.MensagemProgramada;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class CriarMensagemProgramadaUseCase {
    private final LeadRepositorio leads;
    private final MensagemProgramadaRepositorio mensagens;
    private final UsuarioContext usuario;
    public CriarMensagemProgramadaUseCase(LeadRepositorio leads, MensagemProgramadaRepositorio mensagens,
            UsuarioContext usuario) { this.leads = leads; this.mensagens = mensagens; this.usuario = usuario; }

    @PreAuthorize("hasAnyRole('ATENDENTE','SUBGESTOR','GESTOR','ADMINISTRADOR')")
    @Transactional
    public Optional<MensagemProgramada> executar(UUID leadId, String conteudo, Instant dataEnvio) {
        return leads.porId(leadId).map(lead -> mensagens.criar(lead.id(), usuario.atual().id(), conteudo.trim(), dataEnvio));
    }
}
