package com.synapse.crm.automacaoconfig.application;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.automacaoconfig.domain.ConfiguracaoResumoIa;

@Service
public class ObterConfiguracaoResumoIaUseCase {
    private final ConfiguracaoResumoIaRepositorio repositorio;

    public ObterConfiguracaoResumoIaUseCase(ConfiguracaoResumoIaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")
    public ConfiguracaoResumoIa executar() {
        return repositorio.obter();
    }
}
