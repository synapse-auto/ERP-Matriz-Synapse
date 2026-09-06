package com.synapse.crm.automacaoconfig.application;

import java.time.Clock;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.automacaoconfig.domain.MarcaDaInstanciaInvalidaException;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** Persiste o tema enviado pela gestao, sem cache de leitura ou validacao profunda do schema. */
@Service
public class AtualizarTemaDaInstanciaUseCase {

    private final MarcaDaInstanciaRepositorio marcas;
    private final UsuarioContext usuario;
    private final Clock relogio;

    public AtualizarTemaDaInstanciaUseCase(
            MarcaDaInstanciaRepositorio marcas, UsuarioContext usuario, Clock relogio) {
        this.marcas = marcas;
        this.usuario = usuario;
        this.relogio = relogio;
    }

    @PreAuthorize("hasAnyRole('GESTOR', 'SUBGESTOR', 'ADMINISTRADOR')")
    @Transactional
    public void executar(JsonNode tema) {
        if (tema == null || tema.isNull()) {
            throw new MarcaDaInstanciaInvalidaException("tema da instancia e obrigatorio");
        }
        marcas.salvarTema(tema.toString(), usuario.atual().id(), Instant.now(relogio));
    }
}
