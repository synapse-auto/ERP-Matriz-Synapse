package com.synapse.crm.campanhas.interfaces;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.synapse.crm.campanhas.application.CampanhaNaoEncontradaException;
import com.synapse.crm.campanhas.application.CampanhasIndisponiveisException;
import com.synapse.crm.campanhas.application.TemplatesDoCanal;
import com.synapse.crm.campanhas.domain.CampanhaInvalidaException;
import com.synapse.crm.campanhas.domain.TransicaoDeStatusInvalidaException;

/**
 * RFC 7807 para campanhas. Restrito ao controlador de campanhas: nenhuma outra rota muda de formato. O corpo
 * traz um {@code codigo} estavel; o texto que a pessoa le vem do catalogo de textos, nao daqui.
 */
@RestControllerAdvice(assignableTypes = CampanhaController.class)
class ProblemasDeCampanha {

    private static final String BASE = "https://synapse.crm/problemas/campanha/";

    @ExceptionHandler(CampanhasIndisponiveisException.class)
    ProblemDetail indisponivel(CampanhasIndisponiveisException erro) {
        return problema(HttpStatus.NOT_FOUND, "indisponivel", "Campanhas indisponíveis", "CAMPANHAS_INDISPONIVEIS");
    }

    @ExceptionHandler(CampanhaNaoEncontradaException.class)
    ProblemDetail naoEncontrada(CampanhaNaoEncontradaException erro) {
        return problema(HttpStatus.NOT_FOUND, "nao-encontrada", "Campanha não encontrada", "CAMPANHA_NAO_ENCONTRADA");
    }

    @ExceptionHandler(CampanhaInvalidaException.class)
    ProblemDetail invalida(CampanhaInvalidaException erro) {
        ProblemDetail problema = problema(HttpStatus.UNPROCESSABLE_ENTITY, "invalida", "Campanha inválida", erro.codigo());
        problema.setDetail(erro.getMessage());
        return problema;
    }

    @ExceptionHandler(TransicaoDeStatusInvalidaException.class)
    ProblemDetail transicao(TransicaoDeStatusInvalidaException erro) {
        ProblemDetail problema =
                problema(HttpStatus.CONFLICT, "status-invalido", "Ação não vale neste status", "STATUS_INVALIDO");
        problema.setProperty("statusAtual", erro.statusAtual().name());
        return problema;
    }

    @ExceptionHandler(TemplatesDoCanal.TemplatesIndisponiveisException.class)
    ProblemDetail templatesIndisponiveis(TemplatesDoCanal.TemplatesIndisponiveisException erro) {
        return problema(
                HttpStatus.SERVICE_UNAVAILABLE, "templates-indisponiveis", "Provedor indisponível", "TEMPLATES_INDISPONIVEIS");
    }

    private static ProblemDetail problema(HttpStatus status, String tipo, String titulo, String codigo) {
        ProblemDetail problema = ProblemDetail.forStatus(status);
        problema.setType(URI.create(BASE + tipo));
        problema.setTitle(titulo);
        problema.setProperty("codigo", codigo);
        return problema;
    }
}
