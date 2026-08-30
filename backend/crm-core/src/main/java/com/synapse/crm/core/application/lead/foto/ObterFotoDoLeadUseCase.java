package com.synapse.crm.core.application.lead.foto;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.domain.lead.FotoDoLead;

/**
 * Entrega a foto do lead pelo backend, nunca por URL assinada exposta ao navegador.
 *
 * <p>Requisicao de usuario, nao de servico: {@code ContextoDeServico} <b>nao</b> entra aqui. A
 * leitura passa pela mesma porta de lead das outras telas, entao a RN-CRM-01 vale — atendente que
 * nao enxerga o lead nao baixa a foto dele, e o resultado e o mesmo 404 de "nao existe".
 */
@Service
public class ObterFotoDoLeadUseCase {

    private final LeadRepositorio leads;
    private final ArmazenamentoDeFotoDeLead armazenamento;

    public ObterFotoDoLeadUseCase(LeadRepositorio leads, ArmazenamentoDeFotoDeLead armazenamento) {
        this.leads = leads;
        this.armazenamento = armazenamento;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public Optional<ArmazenamentoDeFotoDeLead.Arquivo> executar(UUID leadId) {
        return leads.fotoDoLead(leadId)
                .map(FotoDoLead::referencia)
                .flatMap(armazenamento::buscar);
    }
}
