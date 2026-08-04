package com.synapse.crm.core.application.lembrete;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RemoverLembreteUseCase {
    private final LembreteRepositorio lembretes;

    public RemoverLembreteUseCase(LembreteRepositorio lembretes) {
        this.lembretes = lembretes;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE', 'SUBGESTOR', 'GESTOR', 'ADMINISTRADOR')")
    @Transactional
    public boolean executar(UUID id) {
        return lembretes.remover(id);
    }
}
