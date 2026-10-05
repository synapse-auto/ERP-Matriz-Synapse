package com.synapse.crm.atendimento.domain.finalizacaomassa;

/**
 * Recusa de uma finalizacao em massa. O {@code codigo} e estavel (contrato da API); o texto que a pessoa le
 * vem do catalogo de textos do frontend, nao daqui.
 */
public class FinalizacaoEmMassaException extends RuntimeException {

    /** Como a recusa chega ao cliente: 422, 409, 404 ou 403. */
    public enum Categoria { INVALIDA, CONFLITO, NAO_ENCONTRADA, PROIBIDA }

    private final Categoria categoria;
    private final String codigo;

    public FinalizacaoEmMassaException(Categoria categoria, String codigo, String mensagem) {
        super(mensagem);
        this.categoria = categoria;
        this.codigo = codigo;
    }

    public static FinalizacaoEmMassaException invalida(String codigo, String mensagem) {
        return new FinalizacaoEmMassaException(Categoria.INVALIDA, codigo, mensagem);
    }

    public static FinalizacaoEmMassaException conflito(String codigo, String mensagem) {
        return new FinalizacaoEmMassaException(Categoria.CONFLITO, codigo, mensagem);
    }

    public Categoria categoria() {
        return categoria;
    }

    public String codigo() {
        return codigo;
    }
}
