package com.synapse.crm.automacaoconfig.domain.regras;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Valida placeholders no ponto único de entrada das regras. */
public final class ValidadorDeMensagemDeAutomacao {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");
    private ValidadorDeMensagemDeAutomacao() {}
    public static String validar(String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            throw new RegraAutomacaoInvalidaException("A mensagem da regra nao pode ser vazia");
        }
        Matcher matcher = PLACEHOLDER.matcher(mensagem);
        while (matcher.find()) {
            if (!"nome".equals(matcher.group(1))) {
                throw new RegraAutomacaoInvalidaException("Placeholder nao suportado: {" + matcher.group(1) + "}. Placeholders validos: {nome}");
            }
        }
        return mensagem;
    }
}
