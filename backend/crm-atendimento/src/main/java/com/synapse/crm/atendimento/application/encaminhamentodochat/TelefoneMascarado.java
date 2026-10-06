package com.synapse.crm.atendimento.application.encaminhamentodochat;

/**
 * Telefone para a tela de confirmação: o usuário reconhece o cliente sem a tela expor o número inteiro.
 * Com 10 dígitos ou mais mantém o prefixo (país e DDD) e os quatro finais; com menos, só os dois finais.
 */
public final class TelefoneMascarado {

    private TelefoneMascarado() {}

    public static String de(String telefone) {
        if (telefone == null) {
            return "";
        }
        String digitos = telefone.replaceAll("\\D", "");
        int tamanho = digitos.length();
        if (tamanho <= 2) {
            return "*".repeat(tamanho);
        }
        if (tamanho < 10) {
            return "*".repeat(tamanho - 2) + digitos.substring(tamanho - 2);
        }
        return digitos.substring(0, 4) + "*".repeat(tamanho - 8) + digitos.substring(tamanho - 4);
    }
}
