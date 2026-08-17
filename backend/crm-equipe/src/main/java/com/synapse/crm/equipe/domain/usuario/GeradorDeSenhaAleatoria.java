package com.synapse.crm.equipe.domain.usuario;

import java.security.SecureRandom;

/**
 * Gera a senha provisoria que o gestor repassa ao atendente (E29 bloco 3). Java puro: {@link
 * SecureRandom} nao e dependencia de framework, entao isto continua vivendo em {@code domain}.
 *
 * <p>Alfabeto sem caracteres ambiguos (sem {@code 0/O}, {@code 1/l/I}) — a senha e lida em voz alta
 * ou digitada de memoria por alguem que a recebeu de outra pessoa, e ambiguidade ali vira ticket de
 * suporte, nao risco de seguranca.
 */
public final class GeradorDeSenhaAleatoria {

    private static final String ALFABETO =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private GeradorDeSenhaAleatoria() {}

    public static String gerar(int tamanho) {
        StringBuilder senha = new StringBuilder(tamanho);
        for (int i = 0; i < tamanho; i++) {
            senha.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        return senha.toString();
    }
}
