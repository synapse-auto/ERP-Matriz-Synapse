package com.synapse.crm.automacaoconfig.interfaces.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.automacaoconfig.application.ConfiguracaoResumoIaRepositorio;
import com.synapse.crm.automacaoconfig.application.ListarConfiguracoesAutomacaoUseCase;
import com.synapse.crm.automacaoconfig.application.ObterConfiguracaoAutomacaoUseCase;
import com.synapse.crm.automacaoconfig.application.regras.ListarRegrasFidelizacaoUseCase;
import com.synapse.crm.automacaoconfig.application.regras.ListarRegrasFollowUpUseCase;
import com.synapse.crm.automacaoconfig.application.telemetria.RegistrarEventoDeAutomacaoUseCase;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.domain.TipoConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.domain.regras.RegraFidelizacao;
import com.synapse.crm.automacaoconfig.domain.regras.RegraFollowUp;
import com.synapse.crm.automacaoconfig.domain.telemetria.TipoEventoAutomacao;

/**
 * O contrato {@code /internal/v1} (E07): o que a Automacao de todo filho consome.
 *
 * <p>Autenticado por {@code X-Synapse-Token} (ver {@code SynapseTokenAuthenticationFilter}, em
 * crm-equipe), nao por JWT de usuario — o filtro ja recusou a requisicao antes de chegar aqui se o
 * token nao bater. Mudanca incompativel de forma exige {@code /internal/v2} com este mantido; e para
 * isso que existe o teste de contrato contra snapshot (E07 §2), nao para decorar o CI.
 */
@RestController
@RequestMapping("/internal/v1")
@Tag(name = "Contrato interno da automação", description = "Contrato versionado consumido pelos processos de automação de cada instância.")
@SecurityRequirement(name = "synapseToken")
class AutomationConfigInternalController {

    private final ListarConfiguracoesAutomacaoUseCase listarConfiguracoes;
    private final ObterConfiguracaoAutomacaoUseCase obterConfiguracao;
    private final ListarRegrasFollowUpUseCase listarFollowUp;
    private final ListarRegrasFidelizacaoUseCase listarFidelizacao;
    private final RegistrarEventoDeAutomacaoUseCase registrarEvento;
    private final CanalGateway canal;
    private final ConfiguracaoResumoIaRepositorio resumoIa;

    AutomationConfigInternalController(
            ListarConfiguracoesAutomacaoUseCase listarConfiguracoes,
            ObterConfiguracaoAutomacaoUseCase obterConfiguracao,
            ListarRegrasFollowUpUseCase listarFollowUp,
            ListarRegrasFidelizacaoUseCase listarFidelizacao,
            RegistrarEventoDeAutomacaoUseCase registrarEvento,
            CanalGateway canal,
            ConfiguracaoResumoIaRepositorio resumoIa) {
        this.listarConfiguracoes = listarConfiguracoes;
        this.obterConfiguracao = obterConfiguracao;
        this.listarFollowUp = listarFollowUp;
        this.listarFidelizacao = listarFidelizacao;
        this.registrarEvento = registrarEvento;
        this.canal = canal;
        this.resumoIa = resumoIa;
    }

    @Operation(
            summary = "Listar parâmetros da automação",
            description = "Retorna todas as configurações e a capacidade do canal que condiciona o uso de templates.",
            responses = @ApiResponse(responseCode = "200", description = "Configuração completa da automação."))
    @GetMapping("/automation-config")
    AutomationConfigResposta todosOsParametros() {
        List<ParametroResposta> parametros =
                listarConfiguracoes.executar().stream().map(ParametroResposta::de).toList();
        // E07 §5: a Automacao precisa saber disto antes de tentar enviar, nao depois
        // de um 400 traduzido da Meta na primeira campanha de reativacao.
        Ev05ConfiguracaoResposta ev05 = ev05();
        return new AutomationConfigResposta(
                parametros, canal.exigeTemplateForaDaJanela(), ev05.resumo(), ev05.preenchimentoAutomatico());
    }

    @Operation(
            summary = "Obter configuração do ciclo EV-05",
            description = "Retorna separadamente os intervalos e habilitações de resumo e preenchimento. "
                    + "O cron permanece no n8n; o CRM apenas expõe a configuração persistida.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Configuração do EV-05."),
                @ApiResponse(responseCode = "401", description = "X-Synapse-Token ausente ou inválido.")
            })
    @GetMapping("/automation-config/ev05")
    Ev05ConfiguracaoResposta configuracaoEv05() {
        return ev05();
    }

    @Operation(
            summary = "Obter parâmetro da automação",
            description = "Retorna um parâmetro por sua chave estável.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Parâmetro encontrado."),
                @ApiResponse(responseCode = "404", description = "Chave não encontrada.")
            })
    @GetMapping("/automation-config/{chave}")
    ParametroResposta umParametro(
            @Parameter(description = "Chave estável do parâmetro.", required = true) @PathVariable String chave) {
        return obterConfiguracao
                .executar(chave)
                .map(ParametroResposta::de)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "configuracao nao encontrada: '" + chave + "'"));
    }

    @Operation(
            summary = "Listar regras de follow-up",
            description = "Retorna as regras ativas de contato após períodos sem resposta.",
            responses = @ApiResponse(responseCode = "200", description = "Regras de follow-up."))
    @GetMapping("/regras/follow-up")
    List<RegraFollowUpResposta> regrasDeFollowUp() {
        return listarFollowUp.executar().stream().map(RegraFollowUpResposta::de).toList();
    }

    @Operation(
            summary = "Listar regras de fidelização",
            description = "Retorna as regras ativas de reativação por dias sem contato.",
            responses = @ApiResponse(responseCode = "200", description = "Regras de fidelização."))
    @GetMapping("/regras/fidelizacao")
    List<RegraFidelizacaoResposta> regrasDeFidelizacao() {
        return listarFidelizacao.executar().stream().map(RegraFidelizacaoResposta::de).toList();
    }

    @Operation(
            summary = "Registrar evento da automação",
            description = "Registra telemetria operacional sem expor detalhes internos no contrato.",
            responses = @ApiResponse(responseCode = "204", description = "Evento registrado."))
    @PostMapping("/eventos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void registrarEvento(@Valid @RequestBody EventoRequisicao requisicao) {
        registrarEvento.executar(requisicao.tipo());
    }

    @Operation(
            summary = "Consultar recursos de IA",
            description = "Retorna as capacidades configuradas para o resumo e preenchimento automático.",
            responses = @ApiResponse(responseCode = "200", description = "Recursos de IA."))
    @GetMapping("/automation-config/recursos-ia")
    RecursosIaResposta recursosIa() {
        boolean preenchimento = obterConfiguracao
                .executar("ia.preenchimento_automatico")
                .map(config -> "true".equalsIgnoreCase(config.valor()))
                .orElse(false);
        var resumo = resumoIa.obter();
        return new RecursosIaResposta(
                new ResumoIaResposta(resumo.ativo(), resumo.gatilho(), resumo.quantidadeMensagens()), preenchimento);
    }

    // --- DTOs -------------------------------------------------------------

    record AutomationConfigResposta(
            List<ParametroResposta> parametros,
            boolean exigeTemplateForaDaJanela,
            Ev05ResumoConfiguracao resumo,
            Ev05PreenchimentoConfiguracao preenchimentoAutomatico) {}

    record Ev05ConfiguracaoResposta(
            Ev05ResumoConfiguracao resumo, Ev05PreenchimentoConfiguracao preenchimentoAutomatico) {}

    record Ev05ResumoConfiguracao(boolean habilitado, int intervaloHoras) {}

    record Ev05PreenchimentoConfiguracao(boolean habilitado, int intervaloHoras) {}

    record RecursosIaResposta(ResumoIaResposta resumo, boolean preenchimentoAutomatico) {}

    record ResumoIaResposta(
            boolean ativo,
            com.synapse.crm.automacaoconfig.domain.GatilhoResumo gatilho,
            Integer quantidadeMensagens) {}

    record ParametroResposta(
            String chave,
            String valor,
            String unidade,
            TipoConfiguracaoAutomacao tipo,
            BigDecimal valorMin,
            BigDecimal valorMax,
            String descricao,
            Instant atualizadoEm) {

        static ParametroResposta de(ConfiguracaoAutomacao configuracao) {
            return new ParametroResposta(
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

    record RegraFollowUpResposta(UUID id, String nome, int tempoMinutos, String texto) {
        static RegraFollowUpResposta de(RegraFollowUp regra) {
            return new RegraFollowUpResposta(regra.id(), regra.nome(), regra.tempoMinutos(), regra.texto());
        }
    }

    record RegraFidelizacaoResposta(UUID id, int diasSemContato, String mensagem) {
        static RegraFidelizacaoResposta de(RegraFidelizacao regra) {
            return new RegraFidelizacaoResposta(regra.id(), regra.diasSemContato(), regra.mensagem());
        }
    }

    record EventoRequisicao(
            @Schema(description = "Tipo de evento aceito pelo contrato interno.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull TipoEventoAutomacao tipo) {}

    private Ev05ConfiguracaoResposta ev05() {
        boolean preenchimento = obterConfiguracao
                .executar("ia.preenchimento_automatico")
                .map(config -> "true".equalsIgnoreCase(config.valor()))
                .orElse(false);
        var resumo = resumoIa.obter();
        return new Ev05ConfiguracaoResposta(
                new Ev05ResumoConfiguracao(resumo.ativo(), intervaloHoras("ia.resumo.intervalo_horas")),
                new Ev05PreenchimentoConfiguracao(
                        preenchimento, intervaloHoras("ia.preenchimento_automatico.intervalo_horas")));
    }

    private int intervaloHoras(String chave) {
        var config = obterConfiguracao.executar(chave).orElseThrow(() ->
                new IllegalStateException("parametro EV-05 ausente: " + chave));
        if (config.tipo() != TipoConfiguracaoAutomacao.INT) {
            throw new IllegalStateException("parametro EV-05 nao e inteiro: " + chave);
        }
        int valor;
        try {
            valor = Integer.parseInt(config.valor().trim());
        } catch (NumberFormatException erro) {
            throw new IllegalStateException("parametro EV-05 invalido: " + chave, erro);
        }
        if ((config.valorMin() != null && valor < config.valorMin().intValue())
                || (config.valorMax() != null && valor > config.valorMax().intValue())) {
            throw new IllegalStateException("parametro EV-05 fora da faixa: " + chave);
        }
        return valor;
    }
}
