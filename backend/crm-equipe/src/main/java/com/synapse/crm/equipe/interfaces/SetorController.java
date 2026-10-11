package com.synapse.crm.equipe.interfaces;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.equipe.application.setor.AtribuirSetorAoUsuarioUseCase;
import com.synapse.crm.equipe.application.setor.AtualizarSetorUseCase;
import com.synapse.crm.equipe.application.setor.CriarSetorUseCase;
import com.synapse.crm.equipe.application.setor.ListarSetoresUseCase;
import com.synapse.crm.equipe.application.setor.SetoresDesligadosException;
import com.synapse.crm.equipe.application.setor.VisaoDeSetores;
import com.synapse.crm.equipe.domain.setor.AtribuicaoDeSetor;
import com.synapse.crm.equipe.domain.setor.NomeDeSetorEmUsoException;
import com.synapse.crm.equipe.domain.setor.NomeDeSetorInvalidoException;
import com.synapse.crm.equipe.domain.setor.Setor;
import com.synapse.crm.equipe.domain.setor.SetorInativoException;
import com.synapse.crm.equipe.domain.setor.SetorNaoEncontradoException;

/**
 * Setores da instância e a atribuição de integrantes (D2 da Disbrel). Tudo atrás da feature flag {@code setores}:
 * desligada, estas rotas respondem 404, como antes de existirem.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Setores", description = "Setores da instância e atribuição de integrantes (feature flag setores).")
@SecurityRequirement(name = "bearerAuth")
class SetorController {

    private static final String BASE = "https://synapse.crm/problemas/";

    private final ListarSetoresUseCase listar;
    private final CriarSetorUseCase criar;
    private final AtualizarSetorUseCase atualizar;
    private final AtribuirSetorAoUsuarioUseCase atribuir;

    SetorController(
            ListarSetoresUseCase listar,
            CriarSetorUseCase criar,
            AtualizarSetorUseCase atualizar,
            AtribuirSetorAoUsuarioUseCase atribuir) {
        this.listar = listar;
        this.criar = criar;
        this.atualizar = atualizar;
        this.atribuir = atribuir;
    }

    @Operation(
            summary = "Listar setores e atribuições",
            responses = {
                @ApiResponse(responseCode = "200", description = "Setores e quem está em qual."),
                @ApiResponse(responseCode = "403", description = "Sem permissão para ver a equipe."),
                @ApiResponse(responseCode = "404", description = "Setores desligados nesta instância.")
            })
    @GetMapping("/setores")
    VisaoDeSetores listar() {
        return listar.executar();
    }

    @Operation(
            summary = "Criar setor",
            responses = {
                @ApiResponse(responseCode = "201", description = "Setor criado."),
                @ApiResponse(responseCode = "403", description = "Só GESTOR e ADMINISTRADOR."),
                @ApiResponse(responseCode = "404", description = "Setores desligados nesta instância."),
                @ApiResponse(responseCode = "409", description = "Já existe um setor com este nome."),
                @ApiResponse(responseCode = "422", description = "Nome vazio ou longo demais.")
            })
    @PostMapping("/setores")
    ResponseEntity<Setor> criar(@Valid @RequestBody CriacaoDeSetor corpo) {
        Setor setor = criar.executar(corpo.nome(), corpo.recebeDaTriagem() == null || corpo.recebeDaTriagem());
        return ResponseEntity.created(URI.create("/api/v1/setores/" + setor.id())).body(setor);
    }

    @Operation(
            summary = "Atualizar setor",
            responses = {
                @ApiResponse(responseCode = "200", description = "Setor atualizado."),
                @ApiResponse(responseCode = "403", description = "Só GESTOR e ADMINISTRADOR."),
                @ApiResponse(responseCode = "404", description = "Setor inexistente ou setores desligados."),
                @ApiResponse(responseCode = "409", description = "Já existe um setor com este nome.")
            })
    @PutMapping("/setores/{id}")
    Setor atualizar(@PathVariable UUID id, @Valid @RequestBody AlteracaoDeSetor corpo) {
        return atualizar.executar(id, corpo.nome(), corpo.recebeDaTriagem(), corpo.ativo());
    }

    @Operation(
            summary = "Atribuir setor a um integrante",
            description = "setorId nulo tira o integrante de qualquer setor. Não muda papel, permissão nem acesso a lead.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Atribuição gravada."),
                @ApiResponse(responseCode = "403", description = "Fora da alçada sobre este integrante."),
                @ApiResponse(responseCode = "404", description = "Integrante ou setor inexistente, ou setores desligados."),
                @ApiResponse(responseCode = "422", description = "Setor inativo.")
            })
    @PutMapping("/usuarios/{id}/setor")
    AtribuicaoDeSetor atribuir(@PathVariable UUID id, @RequestBody AtribuicaoRequisicao corpo) {
        return atribuir.executar(id, corpo.setorId());
    }

    record CriacaoDeSetor(
            @Schema(description = "Nome do setor; único sem diferenciar maiúsculas.", example = "Vendas", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank @Size(max = Setor.TAMANHO_MAXIMO_DO_NOME) String nome,
            @Schema(description = "Se o setor recebe lead da triagem da IA. Ausente = verdadeiro.") Boolean recebeDaTriagem) {}

    record AlteracaoDeSetor(
            @NotBlank @Size(max = Setor.TAMANHO_MAXIMO_DO_NOME) String nome,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean recebeDaTriagem,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean ativo) {}

    record AtribuicaoRequisicao(@Schema(description = "Setor de destino; nulo remove o integrante do setor.") UUID setorId) {}

    @ExceptionHandler(SetoresDesligadosException.class)
    ProblemDetail desligados(SetoresDesligadosException erro) {
        return problema(HttpStatus.NOT_FOUND, "setores-desligados", "Recurso indisponível", erro.getMessage());
    }

    @ExceptionHandler(SetorNaoEncontradoException.class)
    ProblemDetail naoEncontrado(SetorNaoEncontradoException erro) {
        return problema(HttpStatus.NOT_FOUND, "setor-nao-encontrado", "Setor não encontrado", erro.getMessage());
    }

    @ExceptionHandler(NomeDeSetorEmUsoException.class)
    ProblemDetail nomeEmUso(NomeDeSetorEmUsoException erro) {
        return problema(HttpStatus.CONFLICT, "setor-nome-em-uso", "Nome de setor em uso", erro.getMessage());
    }

    @ExceptionHandler(NomeDeSetorInvalidoException.class)
    ProblemDetail nomeInvalido(NomeDeSetorInvalidoException erro) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "setor-nome-invalido", "Nome de setor inválido", erro.getMessage());
    }

    @ExceptionHandler(SetorInativoException.class)
    ProblemDetail inativo(SetorInativoException erro) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "setor-inativo", "Setor inativo", erro.getMessage());
    }

    private static ProblemDetail problema(HttpStatus status, String tipo, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setType(URI.create(BASE + tipo));
        problema.setTitle(titulo);
        return problema;
    }
}
