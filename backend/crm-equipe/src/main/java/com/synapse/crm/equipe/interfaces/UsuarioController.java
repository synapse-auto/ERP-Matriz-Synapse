package com.synapse.crm.equipe.interfaces;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.equipe.application.usuario.AtualizarMinhaPresencaUseCase;
import com.synapse.crm.equipe.application.usuario.AtualizarUsuarioUseCase;
import com.synapse.crm.equipe.application.usuario.CriarUsuarioUseCase;
import com.synapse.crm.equipe.application.usuario.DesativarUsuarioUseCase;
import com.synapse.crm.equipe.application.usuario.EmailDeUsuarioEmUsoException;
import com.synapse.crm.equipe.application.usuario.ListarUsuariosUseCase;
import com.synapse.crm.equipe.application.usuario.ObterMinhaPresencaUseCase;
import com.synapse.crm.equipe.domain.usuario.PapelGerenciavel;
import com.synapse.crm.equipe.domain.usuario.StatusPresenca;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/** Consulta da equipe. A restricao por papel esta no caso de uso, nao aqui. */
@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Usuários e presença", description = "Gestão da equipe e presença do usuário autenticado.")
@SecurityRequirement(name = "bearerAuth")
class UsuarioController {

    private final ListarUsuariosUseCase listar;
    private final CriarUsuarioUseCase criar;
    private final AtualizarUsuarioUseCase atualizar;
    private final DesativarUsuarioUseCase desativar;
    private final ObterMinhaPresencaUseCase obterPresenca;
    private final AtualizarMinhaPresencaUseCase atualizarPresenca;

    UsuarioController(ListarUsuariosUseCase listar, CriarUsuarioUseCase criar,
            AtualizarUsuarioUseCase atualizar, DesativarUsuarioUseCase desativar,
            ObterMinhaPresencaUseCase obterPresenca, AtualizarMinhaPresencaUseCase atualizarPresenca) {
        this.listar = listar;
        this.criar = criar;
        this.atualizar = atualizar;
        this.desativar = desativar;
        this.obterPresenca = obterPresenca;
        this.atualizarPresenca = atualizarPresenca;
    }

    @Operation(summary = "Listar usuários", description = "Lista a equipe sem expor hashes ou credenciais; restrito aos papéis de gestão.", responses = @ApiResponse(responseCode = "200", description = "Usuários da equipe."))
    @GetMapping
    List<UsuarioResposta> listar() {
        return listar.executar().stream().map(UsuarioResposta::de).toList();
    }

    @Operation(summary = "Criar usuário", description = "Cria um integrante com papel gerenciável; restrito aos papéis de gestão.", responses = {@ApiResponse(responseCode = "201", description = "Usuário criado."), @ApiResponse(responseCode = "409", description = "E-mail já está em uso.")})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UsuarioResposta criar(@Valid @RequestBody Criacao requisicao) {
        return UsuarioResposta.de(criar.executar(requisicao.nome(), requisicao.email(),
                requisicao.senha(), requisicao.papel()));
    }

    @Operation(summary = "Atualizar usuário", description = "Substitui nome, e-mail e papel gerenciável do integrante.", responses = {@ApiResponse(responseCode = "200", description = "Usuário atualizado."), @ApiResponse(responseCode = "404", description = "Usuário não encontrado."), @ApiResponse(responseCode = "409", description = "E-mail já está em uso.")})
    @PutMapping("/{id}")
    UsuarioResposta atualizar(@Parameter(description = "Identificador do usuário.", required = true) @PathVariable UUID id, @Valid @RequestBody Alteracao requisicao) {
        return atualizar.executar(id, requisicao.nome(), requisicao.email(), requisicao.papel())
                .map(UsuarioResposta::de).orElseThrow(UsuarioController::naoEncontrado);
    }

    @Operation(summary = "Desativar usuário", description = "Impede novas autenticações do integrante sem remover seu histórico.", responses = {@ApiResponse(responseCode = "204", description = "Usuário desativado."), @ApiResponse(responseCode = "404", description = "Usuário não encontrado.")})
    @PatchMapping("/{id}/desativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void desativar(@Parameter(description = "Identificador do usuário.", required = true) @PathVariable UUID id) {
        if (!desativar.executar(id)) throw naoEncontrado();
    }

    @Operation(summary = "Obter minha presença", description = "Retorna o estado de presença do usuário autenticado.", responses = {@ApiResponse(responseCode = "200", description = "Presença atual."), @ApiResponse(responseCode = "404", description = "Usuário autenticado não encontrado.")})
    @GetMapping("/me/presenca")
    PresencaResposta presenca() {
        return obterPresenca.executar().map(PresencaResposta::new).orElseThrow(UsuarioController::naoEncontrado);
    }

    @Operation(summary = "Atualizar minha presença", description = "Altera o estado de presença do próprio usuário.", responses = {@ApiResponse(responseCode = "200", description = "Presença atualizada."), @ApiResponse(responseCode = "404", description = "Usuário autenticado não encontrado.")})
    @PatchMapping("/me/presenca")
    PresencaResposta presenca(@Valid @RequestBody PresencaRequisicao requisicao) {
        return atualizarPresenca.executar(requisicao.status()).map(PresencaResposta::new)
                .orElseThrow(UsuarioController::naoEncontrado);
    }

    @ExceptionHandler(EmailDeUsuarioEmUsoException.class)
    ProblemDetail emailEmUso(EmailDeUsuarioEmUsoException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problema.setTitle("E-mail em uso");
        return problema;
    }

    private static ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario nao encontrado");
    }

    /** Sem senha_hash: nunca sai da camada de persistencia. */
    record UsuarioResposta(UUID id, String nome, String email, PapelUsuario papel,
            StatusPresenca statusPresenca, boolean ativo) {
        static UsuarioResposta de(Usuario usuario) {
            return new UsuarioResposta(
                    usuario.id(), usuario.nome(), usuario.email(), usuario.papel(),
                    usuario.statusPresenca(), usuario.ativo());
        }
    }

    record Criacao(
            @Schema(description = "Nome do integrante.", example = "Pessoa Exemplo", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 150) String nome,
            @Schema(description = "E-mail único.", example = "pessoa@example.invalid", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Email String email,
            @Schema(description = "Senha inicial entre 8 e 100 caracteres.", format = "password", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(min = 8, max = 100) String senha,
            @Schema(description = "Papel aceito na gestão de usuários.", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull PapelGerenciavel papel) {}
    record Alteracao(
            @Schema(description = "Nome do integrante.", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 150) String nome,
            @Schema(description = "E-mail único.", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Email String email,
            @Schema(description = "Papel aceito na gestão de usuários.", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull PapelGerenciavel papel) {}
    record PresencaRequisicao(
            @Schema(description = "Novo estado de presença.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull StatusPresenca status) {}
    record PresencaResposta(StatusPresenca status) {}
}
