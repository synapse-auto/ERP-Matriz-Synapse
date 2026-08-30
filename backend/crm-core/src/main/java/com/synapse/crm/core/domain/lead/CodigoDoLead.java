package com.synapse.crm.core.domain.lead;

import java.util.regex.Pattern;

/**
 * Codigo interno do cliente: somente digitos, tamanho limitado pelo schema.
 *
 * <p>A normalizacao mora no dominio para que a ficha, a Automacao e uma importacao futura usem a
 * mesma regra. String vazia vira {@code null} (campo opcional). Nao e {@code Long}: zeros a
 * esquerda fazem parte do identificador em varios filhos.
 */
public final class CodigoDoLead {

    public static final int TAMANHO_MAXIMO = 20;

    private static final Pattern SOMENTE_DIGITOS = Pattern.compile("^[0-9]+$");

    private CodigoDoLead() {}

    /**
     * @param bruto valor enviado pelo cliente; nao chame com {@code null} — ausencia no PUT
     *     significa "nao mexa" e fica no caso de uso, nao aqui
     * @return codigo canonico, ou {@code null} quando o atendente limpou o campo
     */
    public static String normalizar(String bruto) {
        String texto = bruto.trim();
        if (texto.isEmpty()) {
            return null;
        }
        if (texto.length() > TAMANHO_MAXIMO || !SOMENTE_DIGITOS.matcher(texto).matches()) {
            throw new CodigoInvalidoException();
        }
        return texto;
    }
}
