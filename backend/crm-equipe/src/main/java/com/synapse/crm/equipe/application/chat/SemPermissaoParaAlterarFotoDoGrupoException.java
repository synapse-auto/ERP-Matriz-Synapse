package com.synapse.crm.equipe.application.chat;

/** O usuario participa do grupo, mas nao e quem o criou. */
public class SemPermissaoParaAlterarFotoDoGrupoException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SemPermissaoParaAlterarFotoDoGrupoException() {
        super("Somente quem criou o grupo pode alterar ou remover a foto.");
    }
}
