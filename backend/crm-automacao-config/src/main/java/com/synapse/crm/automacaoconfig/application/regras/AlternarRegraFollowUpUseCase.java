package com.synapse.crm.automacaoconfig.application.regras;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.domain.regras.*;

@Service
public class AlternarRegraFollowUpUseCase {
    private final RegraFollowUpRepositorio repositorio;
    public AlternarRegraFollowUpUseCase(RegraFollowUpRepositorio repositorio) { this.repositorio = repositorio; }
    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")
    @Transactional
    public RegraFollowUp executar(UUID id, boolean ativo) {
        RegraFollowUp atual = repositorio.porId(id).orElseThrow(() -> new RegraAutomacaoNaoEncontradaException(id));
        return repositorio.salvar(new RegraFollowUp(id, atual.nome(), atual.tempoMinutos(), atual.texto(), ativo));
    }
}
