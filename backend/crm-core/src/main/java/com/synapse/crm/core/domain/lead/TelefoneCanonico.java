package com.synapse.crm.core.domain.lead;

import java.util.regex.Pattern;

/**
 * Telefone no formato persistido pelo CRM: somente digitos, incluindo o codigo do pais.
 *
 * <p>A normalizacao mora no dominio para que entradas da tela, webhook e futuras importacoes usem
 * exatamente a mesma regra. O DDI padrao e configuracao da instancia, recebida no construtor: o
 * dominio nao conhece ambiente, Spring nem o cliente que esta usando a Base PAI.
 */
public final class TelefoneCanonico {

    private static final Pattern NAO_DIGITO_ASCII = Pattern.compile("[^0-9]");
    private static final Pattern SOMENTE_DIGITOS = Pattern.compile("[0-9]{1,3}");

    private final String ddiPadrao;

    public TelefoneCanonico(String ddiPadrao) {
        if (ddiPadrao == null || !SOMENTE_DIGITOS.matcher(ddiPadrao).matches()) {
            throw new IllegalArgumentException("DDI padrao deve conter de um a tres digitos");
        }
        this.ddiPadrao = ddiPadrao;
    }

    /** Retorna {@code null} somente quando o telefone esta ausente. */
    public String normalizar(String telefone) {
        if (telefone == null) {
            return null;
        }
        String normalizado = NAO_DIGITO_ASCII.matcher(telefone).replaceAll("");
        return switch (normalizado.length()) {
            case 10, 11 -> ddiPadrao + normalizado;
            default -> {
                if (normalizado.length() < 10) {
                    throw new TelefoneInvalidoException();
                }
                yield normalizado;
            }
        };
    }
}
