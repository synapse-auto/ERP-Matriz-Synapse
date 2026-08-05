package com.synapse.crm.automacaoconfig.interfaces;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Configuração da automação", description = "Edição administrativa dos parâmetros consumidos pela automação.")
@SecurityRequirement(name = "bearerAuth")
class ConfiguracaoAutomacaoController {

    private final AtualizarConfiguracaoAutomacaoUseCase atualizar;

    ConfiguracaoAutomacaoController(AtualizarConfiguracaoAutomacaoUseCase atualizar) {
        this.atualizar = atualizar;
    }

    @Operation(
            summary = "Atualizar parâmetro de automação",
            description = "Atualiza somente o valor de uma chave pré-existente; tipo, unidade e limites vêm do cadastro.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Parâmetro atualizado."),
                @ApiResponse(responseCode = "400", description = "Valor incompatível com tipo ou limites."),
                @ApiResponse(responseCode = "404", description = "Chave não encontrada.")
            })
    @PutMapping("/{chave}")
    ParametroAutomacaoResposta atualizar(
            @Parameter(description = "Chave estável do parâmetro.", required = true, example = "followup.tempo_inicial")
                    @PathVariable String chave,
            @Valid @RequestBody AtualizacaoRequisicao requisicao) {
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

    record AtualizacaoRequisicao(
            @Schema(description = "Valor serializado conforme o tipo do parâmetro.", example = "30", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank String valor) {}

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
