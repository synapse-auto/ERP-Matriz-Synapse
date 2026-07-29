package com.synapse.crm.automacaoconfig.application.telemetria;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.domain.telemetria.TipoEventoAutomacao;

/**
 * {@code POST /internal/v1/eventos}: a Automacao reporta uma acao executada.
 *
 * <p>{@code hasRole('SERVICO')}, e nao {@code isAuthenticated()}: este endpoint so existe para quem
 * ja passou pelo filtro de {@code X-Synapse-Token} (E07 §1) — um usuario humano autenticado por JWT
 * nunca deveria chamar isto.
 */
@Service
public class RegistrarEventoDeAutomacaoUseCase {

    private final StatusAutomacaoTelemetriaRepositorio telemetria;

    public RegistrarEventoDeAutomacaoUseCase(StatusAutomacaoTelemetriaRepositorio telemetria) {
        this.telemetria = telemetria;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional
    public void executar(TipoEventoAutomacao tipo) {
        telemetria.registrar(tipo);
    }
}
