package com.synapse.crm.relatorios.application.cancelamentos;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ObterConfiguracaoCancelamentosUseCase {
    private final PoliticaConsultaCancelamentos politica;
    public ObterConfiguracaoCancelamentosUseCase(PoliticaConsultaCancelamentos politica) { this.politica=politica; }
    @PreAuthorize("hasAnyRole('ATENDENTE','OPERADOR','SUBGESTOR','GESTOR','ADMINISTRADOR') and @capacidades.permite('cancelamentos.ver')")
    public PoliticaConsultaCancelamentos.Configuracao executar() { return politica.configuracao(); }
}
