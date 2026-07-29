package com.synapse.crm.automacaoconfig.application;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;

/** Lista todos os parametros — o que {@code GET /internal/v1/automation-config} devolve. */
@Service
public class ListarConfiguracoesAutomacaoUseCase {

    private final ConfiguracaoAutomacaoRepositorio configuracoes;

    public ListarConfiguracoesAutomacaoUseCase(ConfiguracaoAutomacaoRepositorio configuracoes) {
        this.configuracoes = configuracoes;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<ConfiguracaoAutomacao> executar() {
        return configuracoes.listarTodas();
    }
}
