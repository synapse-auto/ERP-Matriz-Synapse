package com.synapse.crm.automacaoconfig.application.regras;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.synapse.crm.automacaoconfig.domain.regras.*;

@Service
public class SalvarRegraFollowUpUseCase {
    private final RegraFollowUpRepositorio repositorio;
    public SalvarRegraFollowUpUseCase(RegraFollowUpRepositorio repositorio) { this.repositorio = repositorio; }
    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")
    @Transactional
    public RegraFollowUp criar(int tempoMinutos, String texto, boolean ativo) {
        validarTempo(tempoMinutos);
        return repositorio.salvar(new RegraFollowUp(UUID.randomUUID(), NomeDaRegraFollowUp.derivar(tempoMinutos), tempoMinutos, ValidadorDeMensagemDeAutomacao.validar(texto), ativo));
    }
    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")
    @Transactional
    public RegraFollowUp atualizar(UUID id, int tempoMinutos, String texto, boolean ativo) {
        validarTempo(tempoMinutos);
        repositorio.porId(id).orElseThrow(() -> new RegraAutomacaoNaoEncontradaException(id));
        return repositorio.salvar(new RegraFollowUp(id, NomeDaRegraFollowUp.derivar(tempoMinutos), tempoMinutos, ValidadorDeMensagemDeAutomacao.validar(texto), ativo));
    }
    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")
    @Transactional
    public void excluir(UUID id) { repositorio.porId(id).orElseThrow(() -> new RegraAutomacaoNaoEncontradaException(id)); repositorio.excluir(id); }
    private static void validarTempo(int minutos) { if (minutos <= 0) throw new RegraAutomacaoInvalidaException("O tempo deve ser maior que zero"); }
}
