package com.synapse.crm.automacaoconfig.application;

import java.util.Optional;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;

/** Um parametro especifico — {@code GET /internal/v1/automation-config/{chave}}. */
@Service
public class ObterConfiguracaoAutomacaoUseCase {

    private final ConfiguracaoAutomacaoRepositorio configuracoes;

    public ObterConfiguracaoAutomacaoUseCase(ConfiguracaoAutomacaoRepositorio configuracoes) {
        this.configuracoes = configuracoes;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public Optional<ConfiguracaoAutomacao> executar(String chave) {
        return configuracoes.porChave(chave);
    }
}
