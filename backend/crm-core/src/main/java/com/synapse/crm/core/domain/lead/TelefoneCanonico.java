package com.synapse.crm.core.domain.lead;

import java.util.regex.Pattern;

/**
 * Telefone no formato persistido pelo CRM: somente digitos, incluindo o codigo do pais.
 *
 * <p>A normalizacao mora no dominio para que entradas da tela, webhook e futuras importacoes usem
 * exatamente a mesma regra. O codigo do pais nao e inferido: ele faz parte do dado recebido.
 */
public final class TelefoneCanonico {

    private static final Pattern NAO_DIGITO_ASCII = Pattern.compile("[^0-9]");

    private TelefoneCanonico() {}

    /** Retorna {@code null} para entrada nula ou sem nenhum digito. */
    public static String normalizar(String telefone) {
        if (telefone == null) {
            return null;
        }
        String normalizado = NAO_DIGITO_ASCII.matcher(telefone).replaceAll("");
        return normalizado.isEmpty() ? null : normalizado;
    }
}
