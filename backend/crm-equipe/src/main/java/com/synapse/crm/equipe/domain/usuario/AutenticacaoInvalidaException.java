package com.synapse.crm.equipe.domain.usuario;

/**
 * Falha de autenticacao.
 *
 * <p>A mensagem e sempre a mesma, independentemente da causa. Dizer "usuario nao existe" ou "usuario
 * inativo" entregaria a quem tenta invadir quais e-mails sao validos na empresa; o detalhe util fica
 * no log do servidor, nao na resposta.
 */
public class AutenticacaoInvalidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final String MENSAGEM_PUBLICA = "Credenciais invalidas.";

    private final String motivoInterno;

    private AutenticacaoInvalidaException(String motivoInterno) {
        super(MENSAGEM_PUBLICA);
        this.motivoInterno = motivoInterno;
    }

    public static AutenticacaoInvalidaException credenciaisIncorretas() {
        return new AutenticacaoInvalidaException("e-mail inexistente ou senha incorreta");
    }

    public static AutenticacaoInvalidaException usuarioInativo() {
        return new AutenticacaoInvalidaException("usuario desativado");
    }

    public static AutenticacaoInvalidaException sessaoInvalida() {
        return new AutenticacaoInvalidaException("refresh token ausente, expirado ou ja utilizado");
    }

    /** Para o log do servidor. Nunca vai para a resposta HTTP. */
    public String motivoInterno() {
        return motivoInterno;
    }
}
