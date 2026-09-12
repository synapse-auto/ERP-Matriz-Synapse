package com.synapse.crm.atendimento.application.internal;

import java.time.Instant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.persistencia.Pools;

@Service
public class ListarCandidatosEv05UseCase {
    private final CandidatosEv05Repositorio candidatos;

    public ListarCandidatosEv05UseCase(CandidatosEv05Repositorio candidatos) {
        this.candidatos = candidatos;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public CandidatosEv05Repositorio.Pagina executar(int pagina, int tamanho, Instant atualizadoDesde) {
        if (pagina < 0 || tamanho < 1) {
            throw new IllegalArgumentException("pagina e tamanho devem ser positivos");
        }
        return candidatos.listar(pagina, tamanho, atualizadoDesde);
    }
}
