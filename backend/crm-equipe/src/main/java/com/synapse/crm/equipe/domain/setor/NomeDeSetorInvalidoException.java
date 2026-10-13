package com.synapse.crm.equipe.domain.setor;

/** Nome de setor vazio ou longo demais. */
public class NomeDeSetorInvalidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NomeDeSetorInvalidoException(String mensagem) {
        super(mensagem);
    }
}
