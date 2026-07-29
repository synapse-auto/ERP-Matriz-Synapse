package com.synapse.crm.automacaoconfig.interfaces;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.automacaoconfig.application.AtualizarConfiguracaoAutomacaoUseCase;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacaoInvalidaException;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacaoNaoEncontradaException;
import com.synapse.crm.automacaoconfig.domain.TipoConfiguracaoAutomacao;

/**
 * Edicao de parametro de automacao (E07 §3). So PUT: nao ha criacao pela API nesta fase — as linhas
 * ja existem, populadas por migration/seed (docs/09).
 */
@RestController
@RequestMapping("/api/v1/automacao/config")
class ConfiguracaoAutomacaoController {

    private final AtualizarConfiguracaoAutomacaoUseCase atualizar;

    ConfiguracaoAutomacaoController(AtualizarConfiguracaoAutomacaoUseCase atualizar) {
        this.atualizar = atualizar;
    }

    @PutMapping("/{chave}")
    ParametroAutomacaoResposta atualizar(
            @PathVariable String chave, @Valid @RequestBody AtualizacaoRequisicao requisicao) {
        return ParametroAutomacaoResposta.de(atualizar.executar(chave, requisicao.valor()));
    }

    @ExceptionHandler(ConfiguracaoAutomacaoNaoEncontradaException.class)
    ProblemDetail aoNaoEncontrar(ConfiguracaoAutomacaoNaoEncontradaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConfiguracaoAutomacaoInvalidaException.class)
    ProblemDetail aoReceberValorInvalido(ConfiguracaoAutomacaoInvalidaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problema.setTitle("Valor de configuracao invalido");
        return problema;
    }

    record AtualizacaoRequisicao(@NotBlank String valor) {}

    record ParametroAutomacaoResposta(
            String chave,
            String valor,
            String unidade,
            TipoConfiguracaoAutomacao tipo,
            BigDecimal valorMin,
            BigDecimal valorMax,
            String descricao,
            Instant atualizadoEm) {

        static ParametroAutomacaoResposta de(ConfiguracaoAutomacao configuracao) {
            return new ParametroAutomacaoResposta(
                    configuracao.chave(),
                    configuracao.valor(),
                    configuracao.unidade(),
                    configuracao.tipo(),
                    configuracao.valorMin(),
                    configuracao.valorMax(),
                    configuracao.descricao(),
                    configuracao.atualizadoEm());
        }
    }
}
