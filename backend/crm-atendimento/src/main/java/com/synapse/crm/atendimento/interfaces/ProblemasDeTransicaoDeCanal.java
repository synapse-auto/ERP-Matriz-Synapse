package com.synapse.crm.atendimento.interfaces;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.synapse.crm.atendimento.application.canal.SaidaSuspensaParaTransicaoException;

@RestControllerAdvice
public class ProblemasDeTransicaoDeCanal {
    @ExceptionHandler(SaidaSuspensaParaTransicaoException.class)
    public ProblemDetail suspensa(SaidaSuspensaParaTransicaoException erro) {
        var problema=ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "REFERENCIA_DE_OUTRO_CANAL".equals(erro.getMessage())
                ? "A citacao pertence ao canal anterior. Envie sem essa referencia; o historico continua preservado."
                : "Envio temporariamente suspenso para transicao do canal. A mensagem nao foi enviada; preserve o rascunho e tente apos a liberacao.");
        problema.setTitle("Envio suspenso"); problema.setProperty("codigo",erro.getMessage());return problema;
    }
}
