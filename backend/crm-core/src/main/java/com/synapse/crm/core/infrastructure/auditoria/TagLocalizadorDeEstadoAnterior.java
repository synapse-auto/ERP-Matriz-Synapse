package com.synapse.crm.core.infrastructure.auditoria;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.synapse.crm.core.application.tag.TagRepositorio;
import com.synapse.crm.sharedkernel.auditoria.LocalizadorDeEstadoAnterior;

/** Carrega a Tag antes de {@code atualizar}/{@code remover}, para o aspecto de auditoria (E09a). */
@Component
class TagLocalizadorDeEstadoAnterior implements LocalizadorDeEstadoAnterior {

    private final TagRepositorio tags;

    TagLocalizadorDeEstadoAnterior(TagRepositorio tags) {
        this.tags = tags;
    }

    @Override
    public String entidadeTipo() {
        return "TAG";
    }

    @Override
    public Optional<?> carregar(UUID id) {
        return tags.porId(id);
    }
}
