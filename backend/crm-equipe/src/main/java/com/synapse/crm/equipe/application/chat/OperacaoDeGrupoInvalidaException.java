package com.synapse.crm.equipe.application.chat;

/** Operacao de grupo recusada (DIRETA, nome vazio, participante invalido, etc.). */
public class OperacaoDeGrupoInvalidaException extends RuntimeException {
    public OperacaoDeGrupoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
