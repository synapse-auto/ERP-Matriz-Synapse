package com.synapse.crm.core.domain.lead;

/** Validação de CPF, sem consulta externa e sem inferir documento a partir de texto livre. */
public final class Cpf {
    private Cpf() {}

    public static String validoOuNulo(String bruto) {
        if (bruto == null || !bruto.matches("(?:[0-9]{11}|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2})")) return null;
        String numero = bruto.replace(".", "").replace("-", "");
        if (numero.chars().distinct().count() == 1) return null;
        for (int tamanho = 9; tamanho <= 10; tamanho++) {
            int soma = 0;
            for (int i = 0; i < tamanho; i++) soma += (numero.charAt(i) - '0') * (tamanho + 1 - i);
            int digito = 11 - soma % 11;
            if ((digito >= 10 ? 0 : digito) != numero.charAt(tamanho) - '0') return null;
        }
        return numero;
    }
}
