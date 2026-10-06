package com.synapse.crm.equipe.application.chat;

/** Imagem recusada: tipo, conteudo, nome ou dimensoes que o grupo nao aceita. */
public class FotoDeGrupoInvalidaException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public FotoDeGrupoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
