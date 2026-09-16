package com.synapse.crm.atendimento.application;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Resolve nomes citados pela Automação para os destinos elegíveis de transferência. */
@Service
public class BuscarAtendentePorNomeUseCase {

    private final AtendenteParaTransferenciaRepositorio destinos;

    public BuscarAtendentePorNomeUseCase(AtendenteParaTransferenciaRepositorio destinos) {
        this.destinos = destinos;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public List<AtendenteParaTransferenciaRepositorio.Destino> executar(String nome) {
        return destinos.buscarPorNome(nome);
    }
}
