package com.synapse.crm.equipe.interfaces;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.synapse.crm.equipe.application.permissao.AlvoDePermissaoNaoEncontradoException;
import com.synapse.crm.equipe.application.permissao.RevisaoDesatualizadaException;
import com.synapse.crm.equipe.domain.permissao.ConcessaoNegadaException;
import com.synapse.crm.equipe.domain.permissao.PermissaoInvalidaException;

/**
 * RFC 7807 para as excecoes de Gestao. Global de proposito: a mesma recusa de alcada nasce tanto em
 * {@code /api/v1/gestao/**} quanto na gestao de usuarios existente ({@code /api/v1/usuarios}).
 * So trata os tipos deste pacote — nenhuma outra excecao do sistema muda de forma.
 */
@RestControllerAdvice
class ProblemasDeGestao {

    private static final String BASE = "https://synapse.crm/problemas/";

    @ExceptionHandler(PermissaoInvalidaException.class)
    ProblemDetail invalida(PermissaoInvalidaException e) {
        ProblemDetail p = problema(HttpStatus.UNPROCESSABLE_ENTITY, "permissao-invalida", "Permissões inválidas",
                "A configuração enviada não pode ser salva; nada foi alterado.");
        List<Map<String, String>> violacoes = e.violacoes().stream()
                .map(v -> Map.of("chave", v.chave(), "codigo", v.codigo().name()))
                .toList();
        p.setProperty("violacoes", violacoes);
        return p;
    }

    @ExceptionHandler(ConcessaoNegadaException.class)
    ProblemDetail concessao(ConcessaoNegadaException e) {
        ProblemDetail p = problema(HttpStatus.FORBIDDEN, "concessao-negada", "Concessão fora da alçada",
                "Você não pode conceder ou alterar isto para este usuário.");
        p.setProperty("codigo", e.codigo().name());
        if (e.chave() != null) {
            p.setProperty("chave", e.chave());
        }
        return p;
    }

    @ExceptionHandler(RevisaoDesatualizadaException.class)
    ProblemDetail conflito(RevisaoDesatualizadaException e) {
        ProblemDetail p = problema(HttpStatus.CONFLICT, "revisao-desatualizada", "Alterado por outra pessoa",
                "Outra pessoa salvou esta configuração depois que você a abriu. Recarregue antes de salvar.");
        p.setProperty("revisaoAtual", e.revisaoAtual());
        return p;
    }

    @ExceptionHandler(AlvoDePermissaoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(AlvoDePermissaoNaoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "usuario-nao-encontrado", "Usuário não encontrado", e.getMessage());
    }

    private static ProblemDetail problema(HttpStatus status, String tipo, String titulo, String detalhe) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detalhe);
        p.setType(URI.create(BASE + tipo));
        p.setTitle(titulo);
        return p;
    }
}
