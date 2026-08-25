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
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.equipe.application.usuario.AtualizarMeuUsuarioUseCase;
import com.synapse.crm.equipe.application.usuario.AtualizarMinhaFotoUseCase;
import com.synapse.crm.equipe.application.usuario.EmailDeUsuarioEmUsoException;
import com.synapse.crm.equipe.application.usuario.FotoDeUsuarioExcedeuLimiteException;
import com.synapse.crm.equipe.application.usuario.FotoDeUsuarioInvalidaException;
import com.synapse.crm.equipe.application.usuario.ObterFotoDeUsuarioUseCase;
import com.synapse.crm.equipe.application.usuario.ObterMeuUsuarioUseCase;
import com.synapse.crm.equipe.domain.usuario.SenhaInvalidaException;
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
    private final AtualizarMinhaFotoUseCase atualizarMinhaFoto;
    private final ObterFotoDeUsuarioUseCase obterFoto;

    MeuUsuarioController(
            ObterMeuUsuarioUseCase obterMeuUsuario,
            AtualizarMeuUsuarioUseCase atualizarMeuUsuario,
            AtualizarMinhaFotoUseCase atualizarMinhaFoto,
            ObterFotoDeUsuarioUseCase obterFoto) {
        this.obterMeuUsuario = obterMeuUsuario;
        this.atualizarMeuUsuario = atualizarMeuUsuario;
        this.atualizarMinhaFoto = atualizarMinhaFoto;
        this.obterFoto = obterFoto;
    }

    @Operation(
            summary = "Meu usuário",
            description = "Nome, e-mail, telefone, cargo, papel, presença e foto do usuário autenticado.",
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
            description = "Altera nome, e-mail, telefone e cargo do usuário autenticado. A troca de e-mail exige a senha atual; o papel permanece sob gestão.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Perfil atualizado."),
                @ApiResponse(responseCode = "404", description = "Usuário autenticado não encontrado.")
            })
    @org.springframework.web.bind.annotation.PatchMapping
    MeuUsuarioResposta atualizar(@Valid @org.springframework.web.bind.annotation.RequestBody AtualizacaoDoMeuPerfil requisicao) {
        return atualizarMeuUsuario.executar(
                        requisicao.nome(), requisicao.email(), requisicao.telefone(), requisicao.cargo(),
                        requisicao.senhaAtual())
                .map(MeuUsuarioResposta::de).orElseThrow(MeuUsuarioController::naoEncontrado);
    }

    @Operation(
            summary = "Atualizar minha foto",
            description = "Recebe JPEG, PNG ou WebP, valida o conteúdo e grava uma versão quadrada reprocessada no storage de avatares.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Foto atualizada."),
                @ApiResponse(responseCode = "413", description = "Arquivo excede o limite configurado."),
                @ApiResponse(responseCode = "422", description = "Formato ou conteúdo de imagem não permitido.")
            })
    @org.springframework.web.bind.annotation.PostMapping(value = "/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    MeuUsuarioResposta atualizarFoto(
            @org.springframework.web.bind.annotation.RequestPart("arquivo") org.springframework.web.multipart.MultipartFile arquivo) {
        try {
            return atualizarMinhaFoto.executar(arquivo.getBytes())
                    .map(MeuUsuarioResposta::de).orElseThrow(MeuUsuarioController::naoEncontrado);
        } catch (java.io.IOException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "falha ao ler a foto");
        }
    }

    @Operation(summary = "Remover minha foto", description = "Remove a foto e volta a exibir as iniciais coloridas.", responses = @ApiResponse(responseCode = "200", description = "Foto removida."))
    @org.springframework.web.bind.annotation.DeleteMapping("/foto")
    MeuUsuarioResposta removerFoto() {
        return atualizarMinhaFoto.executar(null)
                .map(MeuUsuarioResposta::de).orElseThrow(MeuUsuarioController::naoEncontrado);
    }

    @Operation(summary = "Entregar foto de usuário", description = "Entrega a foto processada pelo backend para os avatares autenticados.", responses = {
        @ApiResponse(responseCode = "200", description = "Imagem do avatar."),
        @ApiResponse(responseCode = "404", description = "Usuário sem foto.")
    })
    @GetMapping("/foto/{id}")
    ResponseEntity<byte[]> foto(@org.springframework.web.bind.annotation.PathVariable java.util.UUID id) {
        return obterFoto.executar(id)
                .map(arquivo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(arquivo.mimetype()))
                        .cacheControl(org.springframework.http.CacheControl.noCache())
                        .body(arquivo.conteudo()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(UsuarioAutenticadoNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void aoNaoEncontrar() {}

    @ExceptionHandler(EmailDeUsuarioEmUsoException.class)
    ProblemDetail emailEmUso(EmailDeUsuarioEmUsoException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problema.setTitle("E-mail em uso");
        return problema;
    }

    @ExceptionHandler(SenhaInvalidaException.class)
    ProblemDetail senhaInvalida(SenhaInvalidaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problema.setTitle("Senha invalida");
        return problema;
    }

    @ExceptionHandler(FotoDeUsuarioInvalidaException.class)
    ProblemDetail fotoInvalida(FotoDeUsuarioInvalidaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problema.setTitle("Foto invalida");
        return problema;
    }

    @ExceptionHandler(FotoDeUsuarioExcedeuLimiteException.class)
    ProblemDetail fotoGrande(FotoDeUsuarioExcedeuLimiteException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, e.getMessage());
        problema.setTitle("Foto excede o limite");
        return problema;
    }

    private static UsuarioAutenticadoNaoEncontradoException naoEncontrado() {
        return new UsuarioAutenticadoNaoEncontradoException();
    }

    private static final class UsuarioAutenticadoNaoEncontradoException extends RuntimeException {}

    record AtualizacaoDoMeuPerfil(
            @Schema(description = "Nome de exibição do usuário autenticado.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank @Size(max = 150) String nome,
            @Schema(description = "E-mail de login. Se for alterado, senhaAtual é obrigatória.", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank @jakarta.validation.constraints.Email @Size(max = 200) String email,
            @Schema(description = "Telefone de exibição, sem normalização de operadora.")
                    @Size(max = 30) String telefone,
            @Schema(description = "Cargo de exibição na equipe.") @Size(max = 120) String cargo,
            @Schema(description = "Senha atual, obrigatória somente quando o e-mail mudar.", format = "password")
                    String senhaAtual) {}

    record MeuUsuarioResposta(
            @Schema(description = "Identificador do usuário autenticado.") java.util.UUID id,
            @Schema(description = "Nome do usuário.") String nome,
            @Schema(description = "E-mail de login do usuário.") String email,
            @Schema(description = "Papel do usuário.") PapelUsuario papel,
            @Schema(description = "Status de presença atual.") StatusPresenca presenca,
            @Schema(description = "Telefone de exibição, quando cadastrado.") String telefone,
            @Schema(description = "Cargo de exibição, quando cadastrado.") String cargo,
            @Schema(description = "URL relativa autenticada para a foto processada, quando cadastrada.") String fotoUrl,
            @Schema(description = "Instante da última troca de senha; nulo para senha provisória.")
                    java.time.Instant senhaAlteradaEm) {
        static MeuUsuarioResposta de(Usuario usuario) {
            String fotoUrl = usuario.fotoReferencia() == null ? null : "/api/v1/me/foto/" + usuario.id();
            return new MeuUsuarioResposta(usuario.id(), usuario.nome(), usuario.email(), usuario.papel(),
                    usuario.statusPresenca(), usuario.telefone(), usuario.cargo(), fotoUrl, usuario.senhaAlteradaEm());
        }
    }
}
