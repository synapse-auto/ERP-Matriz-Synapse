package com.synapse.crm.core.application.tag;

import java.util.UUID;

/** Impede a Automacao de inventar classificacoes fora do catalogo da instancia. */
public class TagDoCatalogoNaoEncontradaException extends RuntimeException {

    public TagDoCatalogoNaoEncontradaException(UUID tagId) {
        super("Tag " + tagId + " nao existe no catalogo da instancia");
    }
}
