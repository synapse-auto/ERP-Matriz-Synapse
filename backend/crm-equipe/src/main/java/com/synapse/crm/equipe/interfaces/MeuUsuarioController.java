package com.synapse.crm.equipe.interfaces;

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

    MeuUsuarioController(ObterMeuUsuarioUseCase obterMeuUsuario) {
        this.obterMeuUsuario = obterMeuUsuario;
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

    @ExceptionHandler(UsuarioAutenticadoNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void aoNaoEncontrar() {}

    private static UsuarioAutenticadoNaoEncontradoException naoEncontrado() {
        return new UsuarioAutenticadoNaoEncontradoException();
    }

    private static final class UsuarioAutenticadoNaoEncontradoException extends RuntimeException {}

    record MeuUsuarioResposta(
            @Schema(description = "Nome do usuário.") String nome,
            @Schema(description = "Papel do usuário.") PapelUsuario papel,
            @Schema(description = "Status de presença atual.") StatusPresenca presenca) {
        static MeuUsuarioResposta de(Usuario usuario) {
            return new MeuUsuarioResposta(usuario.nome(), usuario.papel(), usuario.statusPresenca());
        }
    }
}
