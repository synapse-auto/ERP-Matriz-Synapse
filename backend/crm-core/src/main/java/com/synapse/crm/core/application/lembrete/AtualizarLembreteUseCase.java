package com.synapse.crm.core.application.lembrete;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.domain.lembrete.Lembrete;
import com.synapse.crm.core.domain.lembrete.StatusLembrete;

@Service
public class AtualizarLembreteUseCase {
    private final LembreteRepositorio lembretes;

    public AtualizarLembreteUseCase(LembreteRepositorio lembretes) {
        this.lembretes = lembretes;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE', 'SUBGESTOR', 'GESTOR', 'ADMINISTRADOR')")
    @Transactional
    public Optional<Lembrete> executar(UUID id, String texto, Instant dataHora, StatusLembrete status) {
        return lembretes.atualizar(id, texto.trim(), dataHora, status);
    }
}
