package com.synapse.crm.core.application.lead;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolve um telefone canônico no recorte de visibilidade da sessão.
 *
 * <p>Este caso de uso mantém a consulta JPA no gerente de transação do core. O painel de
 * atendimentos usa o pool reservado do chat em seguida; separar os dois pontos evita executar uma
 * consulta JPA de lead sob uma conexão JDBC sem o contexto RLS correspondente.
 */
@Service
public class ObterLeadIdPorTelefoneVisivelUseCase {

    private final LeadRepositorio leads;

    public ObterLeadIdPorTelefoneVisivelUseCase(LeadRepositorio leads) {
        this.leads = leads;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public Optional<UUID> executar(String telefoneCanonico) {
        return leads.porTelefone(telefoneCanonico);
    }
}
