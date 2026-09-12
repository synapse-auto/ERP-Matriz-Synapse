package com.synapse.crm.atendimento.application.tempo_real;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Resolve a audiencia da fila pessoal sem colocar nomes ou conteudo no evento canonico. */
@Service
public class ListarDestinatariosTempoRealUseCase {

    private final DestinatariosTempoRealRepositorio destinatarios;

    public ListarDestinatariosTempoRealUseCase(DestinatariosTempoRealRepositorio destinatarios) {
        this.destinatarios = destinatarios;
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<UUID> executar(UUID atendimentoId) {
        return destinatarios.listarAutorizados(atendimentoId);
    }
}
