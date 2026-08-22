package com.synapse.crm.automacaoconfig.application;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.domain.ConfiguracaoResumoIa;

@Service
public class AtualizarConfiguracaoResumoIaUseCase {
    private final ConfiguracaoResumoIaRepositorio repositorio;

    public AtualizarConfiguracaoResumoIaUseCase(ConfiguracaoResumoIaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")
    @Transactional
    public ConfiguracaoResumoIa executar(ConfiguracaoResumoIa configuracao) {
        return repositorio.salvar(configuracao);
    }
}
