package com.synapse.crm.equipe.application.usuario;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Altera a disponibilidade para o rodizio da IA, sem alterar a presenca. */
@Service
public class AtualizarDisponibilidadeParaIaUseCase {

    private final EquipeRepositorio equipe;

    public AtualizarDisponibilidadeParaIaUseCase(EquipeRepositorio equipe) {
        this.equipe = equipe;
    }

    @PreAuthorize("hasAnyRole('GESTOR','SUBGESTOR','ADMINISTRADOR')")
    @Transactional
    public Optional<Boolean> executar(UUID atendenteId, boolean disponivel) {
        return equipe.atualizarDisponibilidadeParaIa(atendenteId, disponivel);
    }
}
