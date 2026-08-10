package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.synapse.crm.automacaoconfig.application.telemetria.StatusAutomacaoTelemetriaRepositorio;
import com.synapse.crm.automacaoconfig.domain.telemetria.StatusAutomacaoTelemetria;
import com.synapse.crm.automacaoconfig.domain.telemetria.TipoEventoAutomacao;

/**
 * {@code status_automacao_telemetria} e singleton ({@code id = 1}, criado pelo default da coluna nas
 * migrations — a linha ja existe sempre); aqui so incrementa.
 */
@Repository
class StatusAutomacaoTelemetriaRepositorioJpa implements StatusAutomacaoTelemetriaRepositorio {

    private static final short ID_SINGLETON = 1;

    private final StatusAutomacaoTelemetriaJpaRepository jpa;
    private final Clock relogio;

    StatusAutomacaoTelemetriaRepositorioJpa(StatusAutomacaoTelemetriaJpaRepository jpa, Clock relogio) {
        this.jpa = jpa;
        this.relogio = relogio;
    }

    @Override
    public void registrar(TipoEventoAutomacao tipo) {
        StatusAutomacaoTelemetriaEntity entidade = jpa.findById(ID_SINGLETON)
                .orElseThrow(() -> new IllegalStateException(
                        "status_automacao_telemetria sem a linha singleton (id=1) — migration nao rodou?"));

        Instant agora = Instant.now(relogio);
        switch (tipo) {
            case MENSAGEM_ENVIADA -> entidade.incrementarMensagensEnviadas(agora);
            case CLIENTE_TRANSFERIDO -> entidade.incrementarClientesTransferidos(agora);
        }
        jpa.save(entidade);
    }

    @Override
    public StatusAutomacaoTelemetria obter() {
        return jpa.findById(ID_SINGLETON)
                .orElseThrow(() -> new IllegalStateException(
                        "status_automacao_telemetria sem a linha singleton (id=1) — migration nao rodou?"))
                .paraDominio();
    }
}
