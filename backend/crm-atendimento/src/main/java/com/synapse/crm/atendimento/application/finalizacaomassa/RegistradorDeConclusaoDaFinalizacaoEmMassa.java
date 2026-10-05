package com.synapse.crm.atendimento.application.finalizacaomassa;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio.OperacaoDeFinalizacao;
import com.synapse.crm.sharedkernel.auditoria.Auditable;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Grava a auditoria da conclusao. Bean proprio e chamado so quando a operacao concluiu AGORA: o aspecto audita
 * toda chamada que retorna, e rodadas que apenas avancam o lote nao podem virar linhas de auditoria.
 */
@Service
public class RegistradorDeConclusaoDaFinalizacaoEmMassa {

    private final FinalizacaoEmMassaRepositorio repositorio;

    public RegistradorDeConclusaoDaFinalizacaoEmMassa(FinalizacaoEmMassaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Auditable(acao = "CONCLUIR_FINALIZACAO_EM_MASSA", entidadeTipo = "FINALIZACAO_EM_MASSA")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public OperacaoDeFinalizacao registrar(UUID operacaoId) {
        return repositorio.porId(operacaoId).orElseThrow();
    }
}
