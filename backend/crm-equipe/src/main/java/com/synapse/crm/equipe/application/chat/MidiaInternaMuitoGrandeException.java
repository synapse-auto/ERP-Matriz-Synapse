package com.synapse.crm.equipe.application.chat;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
public class MidiaInternaMuitoGrandeException extends RuntimeException {
    public MidiaInternaMuitoGrandeException(long limiteEmBytes) {
        super("A midia excede o limite permitido de " + limiteEmBytes + " bytes");
    }
}
