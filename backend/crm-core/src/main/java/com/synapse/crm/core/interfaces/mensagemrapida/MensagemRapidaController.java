package com.synapse.crm.core.interfaces.mensagemrapida;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.core.application.mensagemrapida.AtualizarMensagemRapidaUseCase;
import com.synapse.crm.core.application.mensagemrapida.CriarMensagemRapidaUseCase;
import com.synapse.crm.core.application.mensagemrapida.ListarMensagensRapidasUseCase;
import com.synapse.crm.core.application.mensagemrapida.PalavraChaveEmUsoException;
import com.synapse.crm.core.application.mensagemrapida.RemoverMensagemRapidaUseCase;
import com.synapse.crm.core.domain.mensagemrapida.MensagemRapida;

@RestController
@RequestMapping("/api/v1/mensagens-rapidas")
@Tag(name = "Mensagens rápidas", description = "Atalhos de texto reutilizáveis no atendimento.")
@SecurityRequirement(name = "bearerAuth")
class MensagemRapidaController {
    private final ListarMensagensRapidasUseCase listar;
    private final CriarMensagemRapidaUseCase criar;
    private final AtualizarMensagemRapidaUseCase atualizar;
    private final RemoverMensagemRapidaUseCase remover;

    MensagemRapidaController(
            ListarMensagensRapidasUseCase listar,
            CriarMensagemRapidaUseCase criar,
            AtualizarMensagemRapidaUseCase atualizar,
            RemoverMensagemRapidaUseCase remover) {
        this.listar = listar;
        this.criar = criar;
        this.atualizar = atualizar;
        this.remover = remover;
    }

    @Operation(
            summary = "Listar mensagens rápidas",
            description = "Lista mensagens globais e, opcionalmente, limita o resultado às mensagens do usuário atual.",
            responses = @ApiResponse(responseCode = "200", description = "Mensagens rápidas visíveis."))
    @GetMapping
    List<Resposta> listar(
            @Parameter(description = "Quando true, retorna somente atalhos do usuário atual.", example = "false")
                    @RequestParam(defaultValue = "false") boolean minhas) {
        return listar.executar(minhas).stream().map(Resposta::de).toList();
    }

    @Operation(
            summary = "Criar mensagem rápida",
            description = "Cria um atalho de texto para o usuário autenticado.",
            responses = {
                @ApiResponse(responseCode = "201", description = "Mensagem rápida criada."),
                @ApiResponse(responseCode = "409", description = "Palavra-chave já está em uso.")
            })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Resposta criar(@Valid @RequestBody Requisicao requisicao) {
        return Resposta.de(criar.executar(
                requisicao.palavraChave(), requisicao.conteudo()));
    }

    @Operation(
            summary = "Atualizar mensagem rápida",
            description = "Substitui palavra-chave e conteúdo de um atalho acessível ao usuário.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Mensagem rápida atualizada."),
                @ApiResponse(responseCode = "404", description = "Mensagem rápida não encontrada."),
                @ApiResponse(responseCode = "409", description = "Palavra-chave já está em uso.")
            })
    @PutMapping("/{id}")
    Resposta atualizar(
            @Parameter(description = "Identificador da mensagem rápida.", required = true) @PathVariable UUID id,
            @Valid @RequestBody Requisicao requisicao) {
        return atualizar
                .executar(id, requisicao.palavraChave(), requisicao.conteudo())
                .map(Resposta::de)
                .orElseThrow(MensagemRapidaController::naoEncontrada);
    }

    @Operation(
            summary = "Remover mensagem rápida",
            description = "Remove um atalho acessível ao usuário autenticado.",
            responses = {
                @ApiResponse(responseCode = "204", description = "Mensagem rápida removida."),
                @ApiResponse(responseCode = "404", description = "Mensagem rápida não encontrada.")
            })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remover(
            @Parameter(description = "Identificador da mensagem rápida.", required = true) @PathVariable UUID id) {
        if (!remover.executar(id)) throw naoEncontrada();
    }

    @ExceptionHandler(PalavraChaveEmUsoException.class)
    ProblemDetail conflito(PalavraChaveEmUsoException erro) {
        ProblemDetail problema =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, erro.getMessage());
        problema.setTitle("Palavra-chave em uso");
        return problema;
    }

    private static ResponseStatusException naoEncontrada() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Mensagem rapida nao encontrada");
    }

    record Requisicao(
            @Schema(description = "Atalho sem espaços; aceita letras, números, _ e -.", example = "boas_vindas", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank @Size(max = 60) @Pattern(regexp = "[\\p{L}\\p{N}_-]+") String palavraChave,
            @Schema(description = "Texto inserido no composer.", example = "Olá! Como posso ajudar?", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotBlank String conteudo) {}

    record Resposta(
            UUID id,
            UUID atendenteId,
            String atendenteNome,
            String palavraChave,
            String conteudo,
            String tipoMidia) {
        static Resposta de(MensagemRapida mensagem) {
            return new Resposta(
                    mensagem.id(),
                    mensagem.atendenteId(),
                    mensagem.atendenteNome(),
                    mensagem.palavraChave(),
                    mensagem.conteudo(),
                    mensagem.tipoMidia());
        }
    }
}
