package com.synapse.crm.automacaoconfig.interfaces.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

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
class AutomationConfigInternalController {

    private final ListarConfiguracoesAutomacaoUseCase listarConfiguracoes;
    private final ObterConfiguracaoAutomacaoUseCase obterConfiguracao;
    private final ListarRegrasFollowUpUseCase listarFollowUp;
    private final ListarRegrasFidelizacaoUseCase listarFidelizacao;
    private final RegistrarEventoDeAutomacaoUseCase registrarEvento;
    private final CanalGateway canal;

    AutomationConfigInternalController(
            ListarConfiguracoesAutomacaoUseCase listarConfiguracoes,
            ObterConfiguracaoAutomacaoUseCase obterConfiguracao,
            ListarRegrasFollowUpUseCase listarFollowUp,
            ListarRegrasFidelizacaoUseCase listarFidelizacao,
            RegistrarEventoDeAutomacaoUseCase registrarEvento,
            CanalGateway canal) {
        this.listarConfiguracoes = listarConfiguracoes;
        this.obterConfiguracao = obterConfiguracao;
        this.listarFollowUp = listarFollowUp;
        this.listarFidelizacao = listarFidelizacao;
        this.registrarEvento = registrarEvento;
        this.canal = canal;
    }

    @GetMapping("/automation-config")
    AutomationConfigResposta todosOsParametros() {
        List<ParametroResposta> parametros =
                listarConfiguracoes.executar().stream().map(ParametroResposta::de).toList();
        // E07 §5: a Automacao precisa saber disto antes de tentar enviar, nao depois
        // de um 400 traduzido da Meta na primeira campanha de reativacao.
        return new AutomationConfigResposta(parametros, canal.exigeTemplateForaDaJanela());
    }

    @GetMapping("/automation-config/{chave}")
    ParametroResposta umParametro(@PathVariable String chave) {
        return obterConfiguracao
                .executar(chave)
                .map(ParametroResposta::de)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "configuracao nao encontrada: '" + chave + "'"));
    }

    @GetMapping("/regras/follow-up")
    List<RegraFollowUpResposta> regrasDeFollowUp() {
        return listarFollowUp.executar().stream().map(RegraFollowUpResposta::de).toList();
    }

    @GetMapping("/regras/fidelizacao")
    List<RegraFidelizacaoResposta> regrasDeFidelizacao() {
        return listarFidelizacao.executar().stream().map(RegraFidelizacaoResposta::de).toList();
    }

    @PostMapping("/eventos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void registrarEvento(@Valid @RequestBody EventoRequisicao requisicao) {
        registrarEvento.executar(requisicao.tipo());
    }

    // --- DTOs -------------------------------------------------------------

    record AutomationConfigResposta(List<ParametroResposta> parametros, boolean exigeTemplateForaDaJanela) {}

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

    record EventoRequisicao(@NotNull TipoEventoAutomacao tipo) {}
}
