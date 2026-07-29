package com.synapse.crm.automacaoconfig.application.regras;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.domain.regras.RegraFidelizacao;

/** {@code GET /internal/v1/regras/fidelizacao} — so as ativas. */
@Service
public class ListarRegrasFidelizacaoUseCase {

    private final RegraFidelizacaoRepositorio regras;

    public ListarRegrasFidelizacaoUseCase(RegraFidelizacaoRepositorio regras) {
        this.regras = regras;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<RegraFidelizacao> executar() {
        return regras.listarAtivas();
    }
}
