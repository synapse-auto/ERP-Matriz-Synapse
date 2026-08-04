package com.synapse.crm.core.application.mensagemrapida;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.core.domain.mensagemrapida.MensagemRapida;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@Service
public class CriarMensagemRapidaUseCase {
    private final MensagemRapidaRepositorio repo;
    private final UsuarioContext usuario;

    public CriarMensagemRapidaUseCase(MensagemRapidaRepositorio repo, UsuarioContext usuario) {
        this.repo = repo;
        this.usuario = usuario;
    }

    @PreAuthorize("hasAnyRole('ATENDENTE','SUBGESTOR','GESTOR','ADMINISTRADOR')")
    @Transactional
    public MensagemRapida executar(String chave, String conteudo) {
        return repo.criar(
                usuario.atual().id(), chave.trim().toLowerCase(), conteudo.trim());
    }
}
