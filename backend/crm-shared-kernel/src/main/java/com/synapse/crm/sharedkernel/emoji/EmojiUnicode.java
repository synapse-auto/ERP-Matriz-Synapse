package com.synapse.crm.sharedkernel.emoji;

import java.text.BreakIterator;
import java.util.Locale;

/**
 * Valida um unico emoji Unicode, incluindo tom de pele, seletor de variacao e sequencias ZWJ.
 *
 * <p>Nao reduz o catalogo a seis atalhos nem a ASCII: o servidor aceita qualquer grapheme
 * legitimo e rejeita texto comum, vazio, concatenacao e payloads grandes.
 */
public final class EmojiUnicode {

    public static final int LIMITE_CARACTERES = 32;

    private EmojiUnicode() {}

    public static String validar(String bruto) {
        if (bruto == null || bruto.isEmpty()) {
            throw new EmojiInvalidoException("vazio");
        }
        if (bruto.length() > LIMITE_CARACTERES) {
            throw new EmojiInvalidoException("excede " + LIMITE_CARACTERES + " caracteres");
        }
        if (contaGrafemas(bruto) != 1) {
            throw new EmojiInvalidoException("deve ser um unico emoji");
        }
        if (!pareceEmoji(bruto)) {
            throw new EmojiInvalidoException("nao e um emoji Unicode");
        }
        return bruto;
    }

    static int contaGrafemas(String texto) {
        BreakIterator limite = BreakIterator.getCharacterInstance(Locale.ROOT);
        limite.setText(texto);
        int count = 0;
        for (int inicio = limite.first(), fim = limite.next();
                fim != BreakIterator.DONE;
                inicio = fim, fim = limite.next()) {
            if (inicio != fim) {
                count++;
            }
        }
        return count;
    }

    static boolean pareceEmoji(String texto) {
        boolean algumSimbolo = false;
        int offset = 0;
        while (offset < texto.length()) {
            int cp = texto.codePointAt(offset);
            if (!codePointPermitido(cp)) {
                return false;
            }
            if (eNucleoDeEmoji(cp)) {
                algumSimbolo = true;
            }
            offset += Character.charCount(cp);
        }
        return algumSimbolo;
    }

    private static boolean codePointPermitido(int cp) {
        if (cp == 0x200D || cp == 0xFE0F || cp == 0xFE0E) {
            return true;
        }
        if (cp == 0x20E3) {
            return true;
        }
        if (cp >= 0x1F3FB && cp <= 0x1F3FF) {
            return true;
        }
        if (cp >= 0x1F1E6 && cp <= 0x1F1FF) {
            return true;
        }
        if (cp >= 0xE0020 && cp <= 0xE007F) {
            return true;
        }
        if (cp == '#' || cp == '*' || (cp >= '0' && cp <= '9')) {
            return true;
        }
        return eNucleoDeEmoji(cp);
    }

    private static boolean eNucleoDeEmoji(int cp) {
        if (cp == 0x20E3) {
            return true;
        }
        if (cp >= 0x1F000 && cp <= 0x1FAFF) {
            return true;
        }
        if (cp >= 0x2600 && cp <= 0x27BF) {
            return true;
        }
        if (cp >= 0x2300 && cp <= 0x23FF) {
            return true;
        }
        if (cp >= 0x2B00 && cp <= 0x2BFF) {
            return true;
        }
        if (cp >= 0x1F1E6 && cp <= 0x1F1FF) {
            return true;
        }
        int tipo = Character.getType(cp);
        return tipo == Character.OTHER_SYMBOL || tipo == Character.MODIFIER_SYMBOL;
    }
}
