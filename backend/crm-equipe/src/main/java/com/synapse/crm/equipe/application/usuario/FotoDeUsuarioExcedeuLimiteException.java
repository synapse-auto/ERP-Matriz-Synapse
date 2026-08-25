package com.synapse.crm.equipe.application.usuario;

public class FotoDeUsuarioExcedeuLimiteException extends RuntimeException {

    public FotoDeUsuarioExcedeuLimiteException(long limite) {
        super("foto excede o limite de " + limite + " bytes");
    }
}
