package com.synapse.crm.automacaoconfig.application.regras;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.domain.regras.RegraFollowUp;

/** {@code GET /internal/v1/regras/follow-up} — so as ativas, a Automacao nao aplica regra desligada. */
@Service
public class ListarRegrasFollowUpUseCase {

    private final RegraFollowUpRepositorio regras;

    public ListarRegrasFollowUpUseCase(RegraFollowUpRepositorio regras) {
        this.regras = regras;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<RegraFollowUp> executar() {
        return regras.listarAtivas();
    }
}
