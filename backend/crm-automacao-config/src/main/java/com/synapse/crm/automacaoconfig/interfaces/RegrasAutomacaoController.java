package com.synapse.crm.automacaoconfig.interfaces;

import java.time.LocalDate;
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
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.automacaoconfig.application.ConfiguracaoFidelizacaoUseCase;
import com.synapse.crm.automacaoconfig.application.festivas.GerenciarMensagensFestivasUseCase;
import com.synapse.crm.automacaoconfig.application.regras.*;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacaoInvalidaException;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacaoNaoEncontradaException;
import com.synapse.crm.automacaoconfig.domain.festivas.MensagemFestiva;
import com.synapse.crm.automacaoconfig.domain.festivas.MensagemFestivaInvalidaException;
import com.synapse.crm.automacaoconfig.domain.festivas.MensagemFestivaNaoEncontradaException;
import com.synapse.crm.automacaoconfig.domain.regras.*;

@RestController
@RequestMapping("/api/v1/automacao")
@Tag(name = "Regras da automação", description = "Cadastro administrativo das regras que o n8n executa.")
@SecurityRequirement(name = "bearerAuth")
class RegrasAutomacaoController {
    private final ListarRegrasFollowUpAdminUseCase listarFollowUp;
    private final SalvarRegraFollowUpUseCase salvarFollowUp;
    private final AlternarRegraFollowUpUseCase alternarFollowUp;
    private final ListarRegrasFidelizacaoAdminUseCase listarFidelizacao;
    private final SalvarRegraFidelizacaoUseCase salvarFidelizacao;
    private final AlternarRegraFidelizacaoUseCase alternarFidelizacao;
    private final ConfiguracaoFidelizacaoUseCase configuracaoFidelizacao;
    private final GerenciarMensagensFestivasUseCase mensagensFestivas;

    RegrasAutomacaoController(ListarRegrasFollowUpAdminUseCase listarFollowUp, SalvarRegraFollowUpUseCase salvarFollowUp,
            AlternarRegraFollowUpUseCase alternarFollowUp, ListarRegrasFidelizacaoAdminUseCase listarFidelizacao,
            SalvarRegraFidelizacaoUseCase salvarFidelizacao, AlternarRegraFidelizacaoUseCase alternarFidelizacao,
            ConfiguracaoFidelizacaoUseCase configuracaoFidelizacao, GerenciarMensagensFestivasUseCase mensagensFestivas) {
        this.listarFollowUp = listarFollowUp; this.salvarFollowUp = salvarFollowUp; this.alternarFollowUp = alternarFollowUp;
        this.listarFidelizacao = listarFidelizacao; this.salvarFidelizacao = salvarFidelizacao; this.alternarFidelizacao = alternarFidelizacao;
        this.configuracaoFidelizacao = configuracaoFidelizacao;
        this.mensagensFestivas = mensagensFestivas;
    }

    @GetMapping("/follow-ups")
    @Operation(summary = "Listar regras de follow-up", description = "Lista todas as regras de follow-up, inclusive inativas.", responses = @ApiResponse(responseCode = "200", description = "Todas as regras, inclusive inativas."))
    List<FollowUpResposta> followUps() { return listarFollowUp.executar().stream().map(FollowUpResposta::de).toList(); }

    @PostMapping("/follow-ups")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar regra de follow-up", description = "Cria uma regra de contato após ausência de resposta.", responses = @ApiResponse(responseCode = "201", description = "Regra criada."))
    FollowUpResposta criarFollowUp(@Valid @RequestBody FollowUpRequisicao r) { return FollowUpResposta.de(salvarFollowUp.criar(r.tempoMinutos(), r.texto(), r.ativo())); }

    @PutMapping("/follow-ups/{id}")
    @Operation(summary = "Atualizar regra de follow-up", description = "Atualiza tempo e mensagem da regra.")
    FollowUpResposta atualizarFollowUp(@Parameter @PathVariable UUID id, @Valid @RequestBody FollowUpRequisicao r) { return FollowUpResposta.de(salvarFollowUp.atualizar(id, r.tempoMinutos(), r.texto(), r.ativo())); }

    @PatchMapping("/follow-ups/{id}/ativo")
    @Operation(summary = "Ativar ou desativar regra de follow-up", description = "Altera somente o estado ativo da regra.")
    FollowUpResposta alternarFollowUp(@Parameter @PathVariable UUID id, @Valid @RequestBody AtivoRequisicao r) { return FollowUpResposta.de(alternarFollowUp.executar(id, r.ativo())); }

    @DeleteMapping("/follow-ups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Excluir regra de follow-up", description = "Remove uma regra de follow-up cadastrada.")
    void excluirFollowUp(@Parameter @PathVariable UUID id) { salvarFollowUp.excluir(id); }

    @GetMapping("/fidelizacao")
    @Operation(summary = "Listar regras de fidelização", description = "Lista todas as regras de fidelização, inclusive inativas.", responses = @ApiResponse(responseCode = "200", description = "Todas as regras, inclusive inativas."))
    List<FidelizacaoResposta> fidelizacao() { return listarFidelizacao.executar().stream().map(FidelizacaoResposta::de).toList(); }

    @PostMapping("/fidelizacao")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar regra de fidelização", description = "Cria uma regra de reativação por dias sem contato.")
    FidelizacaoResposta criarFidelizacao(@Valid @RequestBody FidelizacaoRequisicao r) { return FidelizacaoResposta.de(salvarFidelizacao.criar(r.diasSemContato(), r.mensagem(), r.ativo())); }

    @PutMapping("/fidelizacao/{id}")
    @Operation(summary = "Atualizar regra de fidelização", description = "Atualiza dias e mensagem da regra.")
    FidelizacaoResposta atualizarFidelizacao(@Parameter @PathVariable UUID id, @Valid @RequestBody FidelizacaoRequisicao r) { return FidelizacaoResposta.de(salvarFidelizacao.atualizar(id, r.diasSemContato(), r.mensagem(), r.ativo())); }

    @PatchMapping("/fidelizacao/{id}/ativo")
    @Operation(summary = "Ativar ou desativar regra de fidelização", description = "Altera somente o estado ativo da regra.")
    FidelizacaoResposta alternarFidelizacao(@Parameter @PathVariable UUID id, @Valid @RequestBody AtivoRequisicao r) { return FidelizacaoResposta.de(alternarFidelizacao.executar(id, r.ativo())); }

    @DeleteMapping("/fidelizacao/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Excluir regra de fidelização", description = "Remove uma regra de fidelização cadastrada.")
    void excluirFidelizacao(@Parameter @PathVariable UUID id) { salvarFidelizacao.excluir(id); }

    @GetMapping("/fidelizacao/configuracao")
    @Operation(
            summary = "Listar configuracao de aniversario e datas festivas",
            description = "Retorna somente os parametros da secao Fidelizacao. Apenas GESTOR e ADMINISTRADOR podem ler os textos.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Configuracao atual."),
                @ApiResponse(responseCode = "403", description = "Papel sem permissao para a configuracao.")
            })
    List<ConfiguracaoFidelizacaoResposta> configuracaoFidelizacao() {
        return configuracaoFidelizacao.listar().stream().map(ConfiguracaoFidelizacaoResposta::de).toList();
    }

    @PutMapping("/fidelizacao/configuracao/{chave}")
    @Operation(
            summary = "Atualizar parametro de fidelizacao",
            description = "Atualiza uma chave previamente cadastrada de aniversario ou data festiva. Nao dispara mensagens.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Parametro atualizado."),
                @ApiResponse(responseCode = "403", description = "Papel sem permissao para a configuracao."),
                @ApiResponse(responseCode = "404", description = "Chave desconhecida."),
                @ApiResponse(responseCode = "422", description = "Valor invalido.")
            })
    ConfiguracaoFidelizacaoResposta atualizarConfiguracaoFidelizacao(
            @Parameter(description = "Chave estavel do parametro.", required = true) @PathVariable String chave,
            @Valid @RequestBody ConfiguracaoFidelizacaoRequisicao requisicao) {
        return ConfiguracaoFidelizacaoResposta.de(configuracaoFidelizacao.atualizar(chave, requisicao.valor()));
    }

    @GetMapping("/fidelizacao/datas-festivas")
    @Operation(summary = "Listar datas festivas", description = "Lista as datas festivas cadastradas pela gestao. Nao dispara mensagens.")
    List<MensagemFestivaResposta> datasFestivas() {
        return mensagensFestivas.listar().stream().map(MensagemFestivaResposta::de).toList();
    }

    @PostMapping("/fidelizacao/datas-festivas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastrar data festiva", description = "Cadastra titulo, icone, data e mensagem para uso futuro pela Automacao.")
    MensagemFestivaResposta criarDataFestiva(@Valid @RequestBody MensagemFestivaRequisicao requisicao) {
        return MensagemFestivaResposta.de(mensagensFestivas.criar(
                requisicao.titulo(), requisicao.icone(), requisicao.data(), requisicao.mensagem(), requisicao.ativo()));
    }

    @PutMapping("/fidelizacao/datas-festivas/{id}")
    @Operation(summary = "Atualizar data festiva", description = "Atualiza um registro de data festiva cadastrado.")
    MensagemFestivaResposta atualizarDataFestiva(@PathVariable UUID id, @Valid @RequestBody MensagemFestivaRequisicao requisicao) {
        return MensagemFestivaResposta.de(mensagensFestivas.atualizar(
                id, requisicao.titulo(), requisicao.icone(), requisicao.data(), requisicao.mensagem(), requisicao.ativo()));
    }

    @PatchMapping("/fidelizacao/datas-festivas/{id}/ativo")
    @Operation(summary = "Ativar ou desativar data festiva", description = "Altera somente o estado de ativacao.")
    MensagemFestivaResposta alternarDataFestiva(@PathVariable UUID id, @Valid @RequestBody AtivoRequisicao requisicao) {
        return MensagemFestivaResposta.de(mensagensFestivas.alternar(id, requisicao.ativo()));
    }

    @DeleteMapping("/fidelizacao/datas-festivas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Excluir data festiva", description = "Exclui uma data festiva cadastrada.")
    void excluirDataFestiva(@PathVariable UUID id) {
        mensagensFestivas.excluir(id);
    }

    @ExceptionHandler(RegraAutomacaoInvalidaException.class)
    ProblemDetail regraInvalida(RegraAutomacaoInvalidaException e) { ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); p.setTitle("Regra de automacao invalida"); return p; }
    @ExceptionHandler(RegraAutomacaoNaoEncontradaException.class)
    ProblemDetail regraNaoEncontrada(RegraAutomacaoNaoEncontradaException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()); }
    @ExceptionHandler(ConfiguracaoAutomacaoNaoEncontradaException.class)
    ProblemDetail configuracaoNaoEncontrada(ConfiguracaoAutomacaoNaoEncontradaException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()); }
    @ExceptionHandler(ConfiguracaoAutomacaoInvalidaException.class)
    ProblemDetail configuracaoInvalida(ConfiguracaoAutomacaoInvalidaException e) { ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); p.setTitle("Configuracao de fidelizacao invalida"); return p; }
    @ExceptionHandler(MensagemFestivaNaoEncontradaException.class)
    ProblemDetail dataFestivaNaoEncontrada(MensagemFestivaNaoEncontradaException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()); }
    @ExceptionHandler(MensagemFestivaInvalidaException.class)
    ProblemDetail dataFestivaInvalida(MensagemFestivaInvalidaException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); }

    record FollowUpRequisicao(@Schema(description = "Tempo em minutos, convertido pela interface para horas ou dias.", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Integer tempoMinutos, String texto, boolean ativo) {}
    record FidelizacaoRequisicao(@NotNull Integer diasSemContato, String mensagem, boolean ativo) {}
    record AtivoRequisicao(boolean ativo) {}
    record ConfiguracaoFidelizacaoRequisicao(@NotNull String valor) {}
    record MensagemFestivaRequisicao(@NotNull String titulo, @NotNull String icone, @NotNull LocalDate data, @NotNull String mensagem, boolean ativo) {}
    record FollowUpResposta(UUID id, String nome, int tempoMinutos, String texto, boolean ativo) { static FollowUpResposta de(RegraFollowUp r) { return new FollowUpResposta(r.id(), r.nome(), r.tempoMinutos(), r.texto(), r.ativo()); } }
    record FidelizacaoResposta(UUID id, int diasSemContato, String mensagem, boolean ativo) { static FidelizacaoResposta de(RegraFidelizacao r) { return new FidelizacaoResposta(r.id(), r.diasSemContato(), r.mensagem(), r.ativo()); } }
    record ConfiguracaoFidelizacaoResposta(String chave, String valor, String unidade, String tipo, String descricao) {
        static ConfiguracaoFidelizacaoResposta de(ConfiguracaoAutomacao configuracao) {
            return new ConfiguracaoFidelizacaoResposta(
                    configuracao.chave(),
                    configuracao.valor(),
                    configuracao.unidade(),
                    configuracao.tipo().name(),
                    configuracao.descricao());
        }
    }
    record MensagemFestivaResposta(UUID id, String titulo, String icone, LocalDate data, String mensagem, boolean ativo) {
        static MensagemFestivaResposta de(MensagemFestiva mensagem) {
            return new MensagemFestivaResposta(mensagem.id(), mensagem.titulo(), mensagem.icone(), mensagem.data(), mensagem.mensagem(), mensagem.ativo());
        }
    }
}
