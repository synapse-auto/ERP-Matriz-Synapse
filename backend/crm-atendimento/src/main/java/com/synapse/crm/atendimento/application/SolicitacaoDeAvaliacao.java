package com.synapse.crm.atendimento.application;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;

/** Intencao local e duravel; nunca executa rede na transacao de finalizacao. */
public interface SolicitacaoDeAvaliacao {
    void preparar(Atendimento finalizado);
}
