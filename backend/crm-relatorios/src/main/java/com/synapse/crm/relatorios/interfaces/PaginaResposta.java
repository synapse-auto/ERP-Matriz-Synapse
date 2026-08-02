package com.synapse.crm.relatorios.interfaces;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Envelope de paginacao para respostas REST. Generico de proposito: {@code audit-log} (E09a) e o
 * primeiro endpoint do projeto com paginacao real, mas nao deveria ser o unico por muito tempo.
 */
public record PaginaResposta<T>(List<T> itens, int pagina, int tamanho, long totalDeItens, int totalDePaginas) {

    public static <O, T> PaginaResposta<T> de(Page<O> pagina, Function<O, T> mapeador) {
        return new PaginaResposta<>(
                pagina.getContent().stream().map(mapeador).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }
}
