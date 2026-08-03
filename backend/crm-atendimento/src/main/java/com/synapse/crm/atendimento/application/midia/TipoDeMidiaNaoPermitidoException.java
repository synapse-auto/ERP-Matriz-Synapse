package com.synapse.crm.atendimento.application.midia;

/**
 * O mimetype real do arquivo (por magic bytes) nao esta na allowlist — mesmo que a extensao ou o
 * {@code Content-Type} declarado pelo cliente dissessem outra coisa.
 */
public class TipoDeMidiaNaoPermitidoException extends RuntimeException {

    public TipoDeMidiaNaoPermitidoException(String mimetypeReal) {
        super("tipo de arquivo nao permitido: " + mimetypeReal);
    }
}
