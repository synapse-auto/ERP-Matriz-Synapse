package com.synapse.crm.equipe.domain.usuario;

/**
 * Motivo pelo qual uma troca de senha (E29) foi recusada. Ao contrario de
 * {@link AutenticacaoInvalidaException} (que esconde o motivo de proposito, para nao ajudar quem
 * tenta adivinhar credenciais), aqui o usuario ja provou quem e — a senha atual confere — entao a
 * mensagem pode ser especifica sem virar vazamento de informacao.
 */
public class SenhaInvalidaException extends RuntimeException {

    private SenhaInvalidaException(String mensagem) {
        super(mensagem);
    }

    public static SenhaInvalidaException atualIncorreta() {
        return new SenhaInvalidaException("Senha atual incorreta");
    }

    public static SenhaInvalidaException foraDaPolitica(int tamanhoMinimo) {
        return new SenhaInvalidaException(
                "A nova senha precisa ter pelo menos " + tamanhoMinimo + " caracteres");
    }

    public static SenhaInvalidaException igualAAtual() {
        return new SenhaInvalidaException("A nova senha nao pode ser igual a atual");
    }
}
