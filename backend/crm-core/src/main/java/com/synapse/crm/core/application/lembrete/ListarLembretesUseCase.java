package com.synapse.crm.core.application.lembrete;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarLembretesUseCase {
    private final LembreteRepositorio lembretes;

    public ListarLembretesUseCase(LembreteRepositorio lembretes) {
        this.lembretes = lembretes;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE', 'SUBGESTOR', 'GESTOR', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public PaginaLembretes executar(FiltroLembretes filtro) {
        return lembretes.listar(filtro);
    }
}
