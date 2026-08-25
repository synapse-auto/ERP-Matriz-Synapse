package com.synapse.crm.equipe.application.usuario;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entrega a foto pelo backend, nunca por URL assinada exposta ao navegador. */
@Service
public class ObterFotoDeUsuarioUseCase {

    private final EquipeRepositorio equipe;
    private final ArmazenamentoDeAvatar armazenamento;

    public ObterFotoDeUsuarioUseCase(EquipeRepositorio equipe, ArmazenamentoDeAvatar armazenamento) {
        this.equipe = equipe;
        this.armazenamento = armazenamento;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public Optional<ArmazenamentoDeAvatar.Arquivo> executar(UUID usuarioId) {
        return equipe.porId(usuarioId)
                .flatMap(usuario -> Optional.ofNullable(usuario.fotoReferencia()))
                .flatMap(armazenamento::buscar);
    }
}
