package com.synapse.crm.atendimento.application.template;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;

/** Lista os templates do provedor ativo. Nao toca o banco do chat. */
@Service
public class ListarTemplatesWhatsAppUseCase {

    private final CanalGateway canal;

    public ListarTemplatesWhatsAppUseCase(CanalGateway canal) {
        this.canal = canal;
    }

    @PreAuthorize("isAuthenticated()")
    public List<TemplateDoCanal> executar() {
        return canal.listarTemplates();
    }
}
