package com.synapse.crm.automacaoconfig.application.telemetria;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.domain.telemetria.StatusAutomacaoTelemetria;

/**
 * Os quatro cards do topo da tela de Automação (E17b §Bloco 6).
 *
 * <p>Mesma autorização de {@code ListarConfiguracoesAutomacaoAdminUseCase} — GESTOR/SUBGESTOR — e
 * pelo mesmo motivo: essa tela e administrativa, e o singleton nao carrega recorte por atendente
 * (telemetria e da operacao inteira, nao de um dono de lead), entao a unica linha de defesa aqui e
 * o papel de quem pede, nao um filtro de visibilidade por linha.
 */
@Service
public class ObterStatusAutomacaoTelemetriaUseCase {

    private final StatusAutomacaoTelemetriaRepositorio telemetria;

    public ObterStatusAutomacaoTelemetriaUseCase(StatusAutomacaoTelemetriaRepositorio telemetria) {
        this.telemetria = telemetria;
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR')")
    @Transactional(readOnly = true)
    public StatusAutomacaoTelemetria executar() {
        return telemetria.obter();
    }
}
