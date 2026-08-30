package com.synapse.crm.core.domain.lead;

/**
 * Nome do cliente na ficha. Obrigatorio quando a edicao envia o campo: string vazia nao e
 * "limpar" — o schema e {@code NOT NULL}, e a tela inteira (card, cabecalho, busca) depende dele.
 */
public final class NomeDoLead {

    public static final int TAMANHO_MAXIMO = 150;

    private NomeDoLead() {}

    /**
     * @param bruto valor enviado pelo cliente; nao chame com {@code null} — ausencia no PUT
     *     significa "nao mexa"
     */
    public static String normalizar(String bruto) {
        String texto = bruto.trim();
        if (texto.isEmpty() || texto.length() > TAMANHO_MAXIMO) {
            throw new NomeInvalidoException();
        }
        return texto;
    }
}
