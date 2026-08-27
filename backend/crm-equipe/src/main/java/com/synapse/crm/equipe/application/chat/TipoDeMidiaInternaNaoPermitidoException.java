package com.synapse.crm.equipe.application.chat;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class TipoDeMidiaInternaNaoPermitidoException extends RuntimeException {
    public TipoDeMidiaInternaNaoPermitidoException(String mimetypeReal) {
        super("Tipo de midia nao permitido para o chat interno: " + mimetypeReal);
    }
}
