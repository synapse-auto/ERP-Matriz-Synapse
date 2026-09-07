package com.synapse.crm.automacaoconfig.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.domain.MarcaDaInstanciaInvalidaException;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Persiste os dois campos de identidade da marca com a mesma autorizacao dos demais overrides. */
@Service
public class AtualizarIdentidadeDaInstanciaUseCase {

    private final MarcaDaInstanciaRepositorio marcas;
    private final UsuarioContext usuario;
    private final Clock relogio;

    public AtualizarIdentidadeDaInstanciaUseCase(
            MarcaDaInstanciaRepositorio marcas, UsuarioContext usuario, Clock relogio) {
        this.marcas = marcas;
        this.usuario = usuario;
        this.relogio = relogio;
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")
    @Transactional
    public void executar(String nomeDaMarca, String subtitulo) {
        if (nomeDaMarca == null || nomeDaMarca.isBlank()) {
            throw new MarcaDaInstanciaInvalidaException("nome da marca e obrigatorio");
        }
        if (subtitulo == null || subtitulo.isBlank()) {
            throw new MarcaDaInstanciaInvalidaException("subtitulo da marca e obrigatorio");
        }
        marcas.salvarIdentidade(nomeDaMarca, subtitulo, usuario.atual().id(), Instant.now(relogio));
    }
}
