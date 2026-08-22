package com.synapse.crm.automacaoconfig.application.regras;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.synapse.crm.automacaoconfig.domain.regras.RegraFollowUp;

@Service
public class ListarRegrasFollowUpAdminUseCase {
    private final RegraFollowUpRepositorio repositorio;
    public ListarRegrasFollowUpAdminUseCase(RegraFollowUpRepositorio repositorio) { this.repositorio = repositorio; }
    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public List<RegraFollowUp> executar() { return repositorio.listarTodas(); }
}
