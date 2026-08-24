package com.synapse.crm.equipe.interfaces;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.equipe.application.usuario.AtualizarMeuUsuarioUseCase;
import com.synapse.crm.equipe.application.usuario.ObterMeuUsuarioUseCase;
import com.synapse.crm.equipe.domain.usuario.StatusPresenca;
import com.synapse.crm.equipe.domain.usuario.Usuario;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * {@code GET /api/v1/me} (E17): nome, papel e presença de quem está autenticado.
 *
 * <p>Existe porque {@code GET /api/v1/usuarios} — que teria o nome — é restrito a
 * GESTOR/SUBGESTOR/ADMINISTRADOR, e o JWT só carrega {@code papel}. O rodapé da Sidebar usava
 * e-mail no lugar do nome até esta rota existir.
 */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Meu usuário", description = "Dados do próprio usuário autenticado.")
@SecurityRequirement(name = "bearerAuth")
class MeuUsuarioController {

    private final ObterMeuUsuarioUseCase obterMeuUsuario;
    private final AtualizarMeuUsuarioUseCase atualizarMeuUsuario;

    MeuUsuarioController(ObterMeuUsuarioUseCase obterMeuUsuario, AtualizarMeuUsuarioUseCase atualizarMeuUsuario) {
        this.obterMeuUsuario = obterMeuUsuario;
        this.atualizarMeuUsuario = atualizarMeuUsuario;
    }

    @Operation(
            summary = "Meu usuário",
            description = "Nome, papel e presença do usuário autenticado.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Dados do usuário autenticado."),
                @ApiResponse(responseCode = "404", description = "Usuário do token não existe mais (desativado ou removido).")
            })
    @GetMapping
    MeuUsuarioResposta obter() {
        return obterMeuUsuario.executar().map(MeuUsuarioResposta::de).orElseThrow(MeuUsuarioController::naoEncontrado);
    }

    @Operation(
            summary = "Atualizar meu perfil",
            description = "Altera somente o nome do usuário autenticado. E-mail e papel permanecem sob gestão.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Perfil atualizado."),
                @ApiResponse(responseCode = "404", description = "Usuário autenticado não encontrado.")
            })
    @org.springframework.web.bind.annotation.PatchMapping
    MeuUsuarioResposta atualizar(@Valid @org.springframework.web.bind.annotation.RequestBody AtualizacaoDoMeuPerfil requisicao) {
        return atualizarMeuUsuario.executar(requisicao.nome())
                .map(MeuUsuarioResposta::de).orElseThrow(MeuUsuarioController::naoEncontrado);
    }

    @ExceptionHandler(UsuarioAutenticadoNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void aoNaoEncontrar() {}

    private static UsuarioAutenticadoNaoEncontradoException naoEncontrado() {
        return new UsuarioAutenticadoNaoEncontradoException();
    }

    private static final class UsuarioAutenticadoNaoEncontradoException extends RuntimeException {}

    record AtualizacaoDoMeuPerfil(
            @Schema(description = "Nome de exibição do usuário autenticado.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank @Size(max = 150) String nome) {}

    record MeuUsuarioResposta(
            @Schema(description = "Identificador do usuário autenticado.") java.util.UUID id,
            @Schema(description = "Nome do usuário.") String nome,
            @Schema(description = "E-mail de login do usuário.") String email,
            @Schema(description = "Papel do usuário.") PapelUsuario papel,
            @Schema(description = "Status de presença atual.") StatusPresenca presenca,
            @Schema(description = "Telefone de exibição, quando cadastrado.") String telefone,
            @Schema(description = "Cargo de exibição, quando cadastrado.") String cargo,
            @Schema(description = "Instante da última troca de senha; nulo para senha provisória.")
                    java.time.Instant senhaAlteradaEm) {
        static MeuUsuarioResposta de(Usuario usuario) {
            return new MeuUsuarioResposta(usuario.id(), usuario.nome(), usuario.email(), usuario.papel(),
                    usuario.statusPresenca(), usuario.telefone(), usuario.cargo(), usuario.senhaAlteradaEm());
        }
    }
}
