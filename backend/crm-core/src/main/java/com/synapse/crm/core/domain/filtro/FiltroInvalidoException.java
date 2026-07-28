package com.synapse.crm.core.domain.filtro;

/**
 * Criterio de filtro que o sistema se recusa a interpretar.
 *
 * <p>Toda rejeicao do filtro modular passa por aqui e vira 400. E de proposito que nao exista uma
 * variante "corrige e segue": campo fora da allowlist, operador incompativel, valor que nao converte
 * ou arvore funda demais sao <em>entrada rejeitada</em>, nunca entrada saneada. Sanear adivinha a
 * intencao de quem mandou — e quem manda pode nao ser a nossa tela.
 */
public class FiltroInvalidoException extends RuntimeException {

    private static final int LIMITE_DE_ECO = 60;

    public FiltroInvalidoException(String mensagem) {
        super(mensagem);
    }

    /**
     * Devolve o valor recebido em forma curta o bastante para caber numa mensagem de erro.
     *
     * <p>Ecoar o que chegou ajuda quem esta integrando a descobrir o erro sem abrir log do servidor.
     * Truncar evita que um campo de 2 MB vire uma resposta de 2 MB.
     */
    public static String eco(String recebido) {
        if (recebido == null) {
            return "(ausente)";
        }
        return recebido.length() <= LIMITE_DE_ECO
                ? recebido
                : recebido.substring(0, LIMITE_DE_ECO) + "...";
    }
}
