package com.synapse.crm.equipe.interfaces;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.equipe.application.autenticacao.AutenticarUsuarioUseCase;
import com.synapse.crm.equipe.application.autenticacao.EncerrarSessaoUseCase;
import com.synapse.crm.equipe.application.autenticacao.RenovarSessaoUseCase;
import com.synapse.crm.equipe.application.autenticacao.Sessao;
import com.synapse.crm.equipe.domain.usuario.AutenticacaoInvalidaException;

/** Login, renovacao e logout. Unicas rotas abertas da API. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação", description = "Emissão, rotação e revogação da sessão stateless.")
class AutenticacaoController {

    private final AutenticarUsuarioUseCase autenticar;
    private final RenovarSessaoUseCase renovar;
    private final EncerrarSessaoUseCase encerrar;

    AutenticacaoController(
            AutenticarUsuarioUseCase autenticar,
            RenovarSessaoUseCase renovar,
            EncerrarSessaoUseCase encerrar) {
        this.autenticar = autenticar;
        this.renovar = renovar;
        this.encerrar = encerrar;
    }

    @Operation(
            summary = "Autenticar usuário",
            description = "Valida credenciais sem distinguir senha incorreta de usuário inativo e emite access/refresh tokens.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Sessão emitida."),
                @ApiResponse(responseCode = "400", description = "Corpo inválido."),
                @ApiResponse(responseCode = "401", description = "Credenciais inválidas.")
            })
    @PostMapping("/login")
    SessaoResposta login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            description = "Credenciais do usuário.",
                            required = true,
                            content = @Content(examples = @ExampleObject(value = "{\"email\":\"usuario@example.invalid\",\"senha\":\"senha-nao-real\"}")))
                    @Valid @RequestBody LoginRequisicao requisicao) {
        return SessaoResposta.de(autenticar.executar(requisicao.email(), requisicao.senha()));
    }

    @Operation(
            summary = "Renovar sessão",
            description = "Consome o refresh token uma única vez, rotaciona-o e emite novo access token.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Sessão renovada."),
                @ApiResponse(responseCode = "400", description = "Corpo inválido."),
                @ApiResponse(responseCode = "401", description = "Refresh token inválido, expirado, revogado ou reutilizado.")
            })
    @PostMapping("/refresh")
    SessaoResposta refresh(@Valid @RequestBody RefreshRequisicao requisicao) {
        return SessaoResposta.de(renovar.executar(requisicao.refreshToken()));
    }

    @Operation(
            summary = "Encerrar sessão",
            description = "Revoga a família do refresh token informado.",
            responses = {
                @ApiResponse(responseCode = "204", description = "Sessão encerrada ou já inexistente."),
                @ApiResponse(responseCode = "400", description = "Corpo inválido.")
            })
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequisicao requisicao) {
        encerrar.executar(requisicao.refreshToken());
    }

    /**
     * Toda falha de autenticacao vira 401 com a mesma mensagem (RFC 7807). O motivo real fica no log
     * do servidor: distinguir "senha errada" de "usuario inativo" na resposta entrega informacao a
     * quem esta tentando adivinhar contas.
     */
    @ExceptionHandler(AutenticacaoInvalidaException.class)
    ProblemDetail aoFalharAutenticacao(AutenticacaoInvalidaException e) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        problema.setTitle("Falha de autenticacao");
        return problema;
    }

    record LoginRequisicao(
            @Schema(description = "E-mail do usuário.", example = "usuario@example.invalid", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank String email,
            @Schema(description = "Senha em texto; nunca é devolvida ou registrada.", format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank String senha) {}

    record RefreshRequisicao(
            @Schema(description = "Token opaco de renovação.", format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank String refreshToken) {}

    record SessaoResposta(
            @Schema(description = "JWT usado como Bearer token.", format = "password") String accessToken,
            @Schema(description = "Token opaco rotativo; armazenar em local seguro.", format = "password") String refreshToken,
            @Schema(description = "Validade do access token em segundos.", example = "900") long expiraEmSegundos) {
        static SessaoResposta de(Sessao sessao) {
            return new SessaoResposta(
                    sessao.accessToken(), sessao.refreshToken(), sessao.expiraEmSegundos());
        }
    }
}
