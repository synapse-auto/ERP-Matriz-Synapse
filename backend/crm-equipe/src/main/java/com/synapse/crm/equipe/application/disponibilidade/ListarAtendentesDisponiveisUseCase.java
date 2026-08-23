package com.synapse.crm.equipe.application.disponibilidade;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.disponibilidade.AtendenteDisponivelParaIa;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * {@code GET /internal/v1/atendentes/disponiveis} — roteamento da IA. So a Automacao chama isto: nao
 * e tela de gestor, e a lista que a IA consulta para decidir a quem entregar um lead sem dono.
 */
@Service
public class ListarAtendentesDisponiveisUseCase {

    private final AtendenteDisponivelRepositorio disponibilidade;

    public ListarAtendentesDisponiveisUseCase(AtendenteDisponivelRepositorio disponibilidade) {
        this.disponibilidade = disponibilidade;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<AtendenteDisponivelParaIa> executar() {
        return disponibilidade.listarDisponiveisParaIa();
    }
}
