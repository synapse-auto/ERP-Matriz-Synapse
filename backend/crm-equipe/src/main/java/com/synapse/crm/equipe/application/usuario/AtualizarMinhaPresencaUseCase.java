package com.synapse.crm.equipe.application.usuario;

import java.util.Optional;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.equipe.domain.usuario.MudancaDePresenca;
import com.synapse.crm.equipe.domain.usuario.OrigemDaPresenca;
import com.synapse.crm.equipe.domain.usuario.StatusPresenca;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Clique do proprio usuario no rodape: a mudanca e gravada com origem MANUAL (e historico), so do usuario logado. */
@Service
public class AtualizarMinhaPresencaUseCase {

    private final RegistradorDePresenca registrador;
    private final UsuarioContext usuario;

    public AtualizarMinhaPresencaUseCase(RegistradorDePresenca registrador, UsuarioContext usuario) {
        this.registrador = registrador;
        this.usuario = usuario;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public Optional<StatusPresenca> executar(StatusPresenca status) {
        return registrador
                .registrar(usuario.atual().id(), status, OrigemDaPresenca.MANUAL, null)
                .map(MudancaDePresenca::novo);
    }
}
