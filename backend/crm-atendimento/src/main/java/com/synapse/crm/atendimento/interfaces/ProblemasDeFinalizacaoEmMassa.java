package com.synapse.crm.atendimento.interfaces;

import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.synapse.crm.atendimento.domain.finalizacaomassa.FinalizacaoEmMassaException;

/**
 * RFC 7807 da finalizacao em massa, restrito ao seu controlador. O corpo traz {@code codigo} estavel; o texto que a
 * pessoa le vem do catalogo de textos do frontend, nao daqui.
 */
@RestControllerAdvice(assignableTypes = FinalizacaoEmMassaController.class)
class ProblemasDeFinalizacaoEmMassa {

    private static final String BASE = "https://synapse.crm/problemas/finalizacao-em-massa/";

    @ExceptionHandler(FinalizacaoEmMassaException.class)
    ProblemDetail recusa(FinalizacaoEmMassaException erro) {
        HttpStatus status = switch (erro.categoria()) {
            case INVALIDA -> HttpStatus.UNPROCESSABLE_ENTITY;
            case CONFLITO -> HttpStatus.CONFLICT;
            case NAO_ENCONTRADA -> HttpStatus.NOT_FOUND;
            case PROIBIDA -> HttpStatus.FORBIDDEN;
        };
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, erro.getMessage());
        problema.setType(URI.create(BASE + erro.codigo().toLowerCase(Locale.ROOT).replace('_', '-')));
        problema.setTitle(titulo(erro.categoria()));
        problema.setProperty("codigo", erro.codigo());
        return problema;
    }

    private static String titulo(FinalizacaoEmMassaException.Categoria categoria) {
        return switch (categoria) {
            case INVALIDA -> "Filtros inválidos";
            case CONFLITO -> "Conflito na finalização em massa";
            case NAO_ENCONTRADA -> "Finalização em massa não encontrada";
            case PROIBIDA -> "Fora do escopo autorizado";
        };
    }
}
