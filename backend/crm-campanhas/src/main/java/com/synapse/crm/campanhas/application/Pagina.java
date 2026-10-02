package com.synapse.crm.campanhas.application;

import java.util.List;

/** Uma pagina de resultado, com o total para a tela montar a paginacao. */
public record Pagina<T>(List<T> itens, int pagina, int tamanho, long total) {

    public static final int TAMANHO_PADRAO = 25;
    public static final int TAMANHO_MAXIMO = 100;

    public Pagina {
        itens = List.copyOf(itens);
    }

    /** Tamanho pedido dentro de 1..TAMANHO_MAXIMO; fora disso vale o padrao, nunca uma consulta sem limite. */
    public static int tamanhoSeguro(int pedido) {
        return pedido < 1 || pedido > TAMANHO_MAXIMO ? TAMANHO_PADRAO : pedido;
    }

    public static int paginaSegura(int pedida) {
        return Math.max(0, pedida);
    }

    public int deslocamento() {
        return pagina * tamanho;
    }
}
