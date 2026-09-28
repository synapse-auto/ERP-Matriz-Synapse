package com.synapse.crm.atendimento.interfaces;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import com.synapse.crm.atendimento.application.template.TemplateRestritoException;

/** Mesmo 403 para criar, enviar e abrir contato com template restrito, em qualquer controller. */
final class ProblemasDeTemplateRestrito {

    private ProblemasDeTemplateRestrito() {}

    static ProblemDetail restrito(TemplateRestritoException erro) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, erro.getMessage());
        problema.setTitle("Template restrito");
        return problema;
    }
}
