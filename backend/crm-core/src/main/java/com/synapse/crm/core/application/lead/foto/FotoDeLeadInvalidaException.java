package com.synapse.crm.core.application.lead.foto;

/** Conteudo que nao e JPEG/PNG/WebP, ou imagem ilegivel. Vira 422 no contrato interno. */
public class FotoDeLeadInvalidaException extends RuntimeException {

    public FotoDeLeadInvalidaException(String mensagem) {
        super(mensagem);
    }
}
