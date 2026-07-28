package com.synapse.crm.sharedkernel.identidade;

/**
 * Nomes de claim do JWT emitido por este sistema.
 *
 * <p>Existe para que o nome da claim de papel seja escrito uma unica vez. Antes da E06 ele estava
 * duplicado como {@code String} literal em tres lugares de crm-equipe (emissao, conversor de
 * autoridades, leitura em {@code UsuarioContext}) — e a E06 precisou de um quarto lugar, no handshake
 * do WebSocket, que fica em outro modulo. Duplicar pela quarta vez era o momento de parar de
 * duplicar: um erro de digitacao aqui teria feito o token continuar valido para autenticacao, mas o
 * papel virar sempre vazio — falha silenciosa, nao erro.
 */
public final class ClaimsJwt {

    /** Nome da claim que carrega {@link PapelUsuario#name()}. */
    public static final String PAPEL = "papel";

    private ClaimsJwt() {}
}
