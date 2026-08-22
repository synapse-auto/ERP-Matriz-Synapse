package com.synapse.crm.automacaoconfig.application.regras;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.synapse.crm.automacaoconfig.domain.regras.*;

@Service
public class AlternarRegraFidelizacaoUseCase {
    private final RegraFidelizacaoRepositorio repositorio;
    public AlternarRegraFidelizacaoUseCase(RegraFidelizacaoRepositorio repositorio) { this.repositorio = repositorio; }
    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")
    @Transactional
    public RegraFidelizacao executar(UUID id, boolean ativo) { RegraFidelizacao atual = repositorio.porId(id).orElseThrow(() -> new RegraAutomacaoNaoEncontradaException(id)); return repositorio.salvar(new RegraFidelizacao(id, atual.diasSemContato(), atual.mensagem(), ativo)); }
}
