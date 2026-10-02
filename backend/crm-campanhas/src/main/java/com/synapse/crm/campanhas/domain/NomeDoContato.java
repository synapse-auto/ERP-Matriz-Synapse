package com.synapse.crm.campanhas.domain;

import java.util.Locale;

/** Regras sobre o nome do lead usado em campanha: o que e utilizavel e como extrair o primeiro nome. */
public final class NomeDoContato {

    private static final int MINIMO_DE_LETRAS = 2;

    private NomeDoContato() {}

    /**
     * Um nome utilizavel tem ao menos duas letras. Nome so com codigo ("1234", "5561999990000", "---")
     * nao se cumprimenta: a campanha o ignora em vez de mandar "Ola 5561999990000".
     */
    public static boolean utilizavel(String nome) {
        if (nome == null) {
            return false;
        }
        long letras = nome.codePoints().filter(Character::isLetter).count();
        return letras >= MINIMO_DE_LETRAS;
    }

    /** Primeiro termo com letras, sem pontuacao nas pontas; "MARIA SILVA" e "maria silva" viram "Maria". */
    public static String primeiroNome(String nome) {
        if (nome == null) {
            return "";
        }
        for (String termo : nome.trim().split("\\s+")) {
            String limpo = semPontuacaoNasPontas(termo);
            if (limpo.codePoints().anyMatch(Character::isLetter)) {
                return capitalizarSeUniforme(limpo);
            }
        }
        return "";
    }

    private static String semPontuacaoNasPontas(String termo) {
        int inicio = 0;
        int fim = termo.length();
        while (inicio < fim && !Character.isLetter(termo.charAt(inicio))) {
            inicio++;
        }
        while (fim > inicio && !Character.isLetter(termo.charAt(fim - 1))) {
            fim--;
        }
        return termo.substring(inicio, fim);
    }

    /** So mexe em tudo-maiusculo ou tudo-minusculo; "McDonald" e "de Souza" ficam como o usuario digitou. */
    private static String capitalizarSeUniforme(String termo) {
        boolean todoMaiusculo = termo.equals(termo.toUpperCase(Locale.ROOT));
        boolean todoMinusculo = termo.equals(termo.toLowerCase(Locale.ROOT));
        if (!todoMaiusculo && !todoMinusculo) {
            return termo;
        }
        StringBuilder resultado = new StringBuilder(termo.length());
        boolean inicioDeParte = true;
        for (char c : termo.toLowerCase(Locale.ROOT).toCharArray()) {
            resultado.append(inicioDeParte ? Character.toUpperCase(c) : c);
            inicioDeParte = c == '-' || c == '\'';
        }
        return resultado.toString();
    }
}
