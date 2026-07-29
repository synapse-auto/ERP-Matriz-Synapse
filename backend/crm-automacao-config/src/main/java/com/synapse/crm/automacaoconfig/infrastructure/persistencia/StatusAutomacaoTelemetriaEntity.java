package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Mapeamento JPA de {@code status_automacao_telemetria} (V7) — singleton, {@code id = 1}. */
@Entity
@Table(name = "status_automacao_telemetria")
class StatusAutomacaoTelemetriaEntity {

    @Id
    @Column(name = "id")
    private short id;

    @Column(name = "mensagens_enviadas", nullable = false)
    private long mensagensEnviadas;

    @Column(name = "clientes_transferidos", nullable = false)
    private long clientesTransferidos;

    @Column(name = "conexao_automacao_ativa", nullable = false)
    private boolean conexaoAutomacaoAtiva;

    @Column(name = "crm_online", nullable = false)
    private boolean crmOnline;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected StatusAutomacaoTelemetriaEntity() {
        // exigido pelo JPA
    }

    void incrementarMensagensEnviadas(Instant agora) {
        this.mensagensEnviadas++;
        marcarAtiva(agora);
    }

    void incrementarClientesTransferidos(Instant agora) {
        this.clientesTransferidos++;
        marcarAtiva(agora);
    }

    private void marcarAtiva(Instant agora) {
        this.conexaoAutomacaoAtiva = true;
        this.atualizadoEm = agora;
    }
}
