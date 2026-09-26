package com.synapse.crm.equipe.application.permissao;

/** Usuario alvo inexistente (ou fora do que quem pediu pode ver). Vira 404. */
public class AlvoDePermissaoNaoEncontradoException extends RuntimeException {

    public AlvoDePermissaoNaoEncontradoException() {
        super("Usuario nao encontrado");
    }
}
