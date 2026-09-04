package com.synapse.crm.core.application.lead;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.domain.filtro.FiltroDeLeads;

/**
 * Aplica o filtro modular e devolve os leads que o usuario enxerga sob ele (E03b), paginados (E16
 * §Bloco 1).
 *
 * <p>Atendente pode chamar. O que muda entre papeis nao e a permissao, e o conjunto: o filtro do
 * usuario compoe com {@code AND} por cima da regra de visibilidade dentro do repositorio, entao ele
 * so consegue reduzir o proprio recorte — a paginacao acontece <b>depois</b> desse recorte, nunca
 * antes.
 */
@Service
public class FiltrarLeadsUseCase {

    private final LeadRepositorio leads;

    public FiltrarLeadsUseCase(LeadRepositorio leads) {
        this.leads = leads;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public PaginaDeLeads executar(FiltroDeLeads filtro, int pagina, int tamanho) {
        return leads.listar(filtro, pagina, tamanho);
    }
}
