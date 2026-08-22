package com.synapse.crm.automacaoconfig.interfaces;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.automacaoconfig.application.AtualizarConfiguracaoAutomacaoUseCase;
import com.synapse.crm.automacaoconfig.application.AtualizarConfiguracaoResumoIaUseCase;
import com.synapse.crm.automacaoconfig.application.ListarConfiguracoesAutomacaoAdminUseCase;
import com.synapse.crm.automacaoconfig.application.ObterConfiguracaoAutomacaoUseCase;
import com.synapse.crm.automacaoconfig.application.ObterConfiguracaoResumoIaUseCase;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacaoInvalidaException;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacaoNaoEncontradaException;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoResumoIa;
import com.synapse.crm.automacaoconfig.domain.GatilhoResumo;
import com.synapse.crm.automacaoconfig.domain.TipoConfiguracaoAutomacao;

/**
 * Leitura e edicao de parametro de automacao (E07 §3, E15b §1). Sem criacao pela API: as linhas ja
 * existem, populadas por migration/seed (docs/09).
 */
@RestController
@RequestMapping("/api/v1/automacao/config")
@Tag(name = "Configuração da automação", description = "Leitura e edição administrativa dos parâmetros consumidos pela automação.")
@SecurityRequirement(name = "bearerAuth")
class ConfiguracaoAutomacaoController {

    private final ListarConfiguracoesAutomacaoAdminUseCase listar;
    private final AtualizarConfiguracaoAutomacaoUseCase atualizar;
    private final ObterConfiguracaoResumoIaUseCase obterResumo;
    private final AtualizarConfiguracaoResumoIaUseCase atualizarResumo;
    private final ObterConfiguracaoAutomacaoUseCase obterParametro;

    ConfiguracaoAutomacaoController(
            ListarConfiguracoesAutomacaoAdminUseCase listar,
            AtualizarConfiguracaoAutomacaoUseCase atualizar,
            ObterConfiguracaoResumoIaUseCase obterResumo,
            AtualizarConfiguracaoResumoIaUseCase atualizarResumo,
            ObterConfiguracaoAutomacaoUseCase obterParametro) {
        this.listar = listar;
        this.atualizar = atualizar;
        this.obterResumo = obterResumo;
        this.atualizarResumo = atualizarResumo;
        this.obterParametro = obterParametro;
    }

    @Operation(
            summary = "Listar parâmetros de automação",
            description = "Retorna todos os parâmetros com valor atual, faixa válida e unidade — a tela não conhece limite nenhum por conta própria.",
            responses = @ApiResponse(responseCode = "200", description = "Parâmetros da automação."))
    @GetMapping
    List<ParametroAutomacaoResposta> listar() {
        return listar.executar().stream().map(ParametroAutomacaoResposta::de).toList();
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

    @Operation(summary = "Obter configuração do resumo por IA")
    @GetMapping("/resumo-ia")
    ConfiguracaoResumoIaResposta resumoIa() {
        return ConfiguracaoResumoIaResposta.de(obterResumo.executar());
    }

    @Operation(summary = "Atualizar configuração do resumo por IA")
    @PutMapping("/resumo-ia")
    ConfiguracaoResumoIaResposta atualizarResumoIa(@Valid @RequestBody AtualizacaoResumoIaRequisicao requisicao) {
        return ConfiguracaoResumoIaResposta.de(atualizarResumo.executar(
                new ConfiguracaoResumoIa(requisicao.ativo(), requisicao.gatilho(), requisicao.quantidadeMensagens())));
    }

    @Operation(summary = "Obter recursos de IA")
    @GetMapping("/recursos-ia")
    RecursosIaResposta recursosIa() {
        boolean preenchimento = obterParametro.executar("ia.preenchimento_automatico")
                .map(config -> "true".equalsIgnoreCase(config.valor())).orElse(false);
        ConfiguracaoResumoIa resumo = obterResumo.executar();
        return new RecursosIaResposta(ConfiguracaoResumoIaResposta.de(resumo), preenchimento);
    }

    @ExceptionHandler(ConfiguracaoAutomacaoNaoEncontradaException.class)
    ProblemDetail aoNaoEncontrar(ConfiguracaoAutomacaoNaoEncontradaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConfiguracaoAutomacaoInvalidaException.class)
    ProblemDetail aoReceberValorInvalido(ConfiguracaoAutomacaoInvalidaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problema.setTitle("Valor de configuracao invalido");
        return problema;
    }

    record AtualizacaoRequisicao(
            @Schema(description = "Valor serializado conforme o tipo do parâmetro.", example = "30", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank String valor) {}

    record AtualizacaoResumoIaRequisicao(boolean ativo, GatilhoResumo gatilho, Integer quantidadeMensagens) {}

    record ConfiguracaoResumoIaResposta(boolean ativo, GatilhoResumo gatilho, Integer quantidadeMensagens) {
        static ConfiguracaoResumoIaResposta de(ConfiguracaoResumoIa configuracao) {
            return new ConfiguracaoResumoIaResposta(
                    configuracao.ativo(), configuracao.gatilho(), configuracao.quantidadeMensagens());
        }
    }

    record RecursosIaResposta(ConfiguracaoResumoIaResposta resumo, boolean preenchimentoAutomatico) {}

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
