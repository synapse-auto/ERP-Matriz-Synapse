package com.synapse.crm.atendimento.interfaces;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.synapse.crm.atendimento.application.canal.SaidaSuspensaParaTransicaoException;

@RestControllerAdvice
public class ProblemasDeTransicaoDeCanal {
    @ExceptionHandler(SaidaSuspensaParaTransicaoException.class)
    public ProblemDetail suspensa(SaidaSuspensaParaTransicaoException erro) {
        var problema=ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                java.util.Set.of("REFERENCIA_DE_OUTRO_CANAL","CITACAO_NAO_SUPORTADA_PELO_CANAL").contains(erro.getMessage())
                ? "Não é possível citar esta mensagem neste envio. Remova a citação e envie novamente; o texto, os anexos e o histórico são preservados."
                : "Envio temporariamente suspenso para transicao do canal. A mensagem nao foi enviada; preserve o rascunho e tente apos a liberacao.");
        problema.setTitle("Envio suspenso"); problema.setProperty("codigo",erro.getMessage());return problema;
    }
}
