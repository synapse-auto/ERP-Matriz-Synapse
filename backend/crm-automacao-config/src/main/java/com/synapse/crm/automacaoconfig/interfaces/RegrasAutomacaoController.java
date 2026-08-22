package com.synapse.crm.automacaoconfig.interfaces;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

import com.synapse.crm.automacaoconfig.application.regras.*;
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

    RegrasAutomacaoController(ListarRegrasFollowUpAdminUseCase listarFollowUp, SalvarRegraFollowUpUseCase salvarFollowUp,
            AlternarRegraFollowUpUseCase alternarFollowUp, ListarRegrasFidelizacaoAdminUseCase listarFidelizacao,
            SalvarRegraFidelizacaoUseCase salvarFidelizacao, AlternarRegraFidelizacaoUseCase alternarFidelizacao) {
        this.listarFollowUp = listarFollowUp; this.salvarFollowUp = salvarFollowUp; this.alternarFollowUp = alternarFollowUp;
        this.listarFidelizacao = listarFidelizacao; this.salvarFidelizacao = salvarFidelizacao; this.alternarFidelizacao = alternarFidelizacao;
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

    @ExceptionHandler(RegraAutomacaoInvalidaException.class)
    ProblemDetail regraInvalida(RegraAutomacaoInvalidaException e) { ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); p.setTitle("Regra de automacao invalida"); return p; }
    @ExceptionHandler(RegraAutomacaoNaoEncontradaException.class)
    ProblemDetail regraNaoEncontrada(RegraAutomacaoNaoEncontradaException e) { return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage()); }

    record FollowUpRequisicao(@Schema(description = "Tempo em minutos, convertido pela interface para horas ou dias.", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Integer tempoMinutos, @NotBlank String texto, boolean ativo) {}
    record FidelizacaoRequisicao(@NotNull Integer diasSemContato, @NotBlank String mensagem, boolean ativo) {}
    record AtivoRequisicao(boolean ativo) {}
    record FollowUpResposta(UUID id, String nome, int tempoMinutos, String texto, boolean ativo) { static FollowUpResposta de(RegraFollowUp r) { return new FollowUpResposta(r.id(), r.nome(), r.tempoMinutos(), r.texto(), r.ativo()); } }
    record FidelizacaoResposta(UUID id, int diasSemContato, String mensagem, boolean ativo) { static FidelizacaoResposta de(RegraFidelizacao r) { return new FidelizacaoResposta(r.id(), r.diasSemContato(), r.mensagem(), r.ativo()); } }
}
