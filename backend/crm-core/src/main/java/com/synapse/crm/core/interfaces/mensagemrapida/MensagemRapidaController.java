package com.synapse.crm.core.interfaces.mensagemrapida;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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

    @GetMapping
    List<Resposta> listar(@RequestParam(defaultValue = "false") boolean minhas) {
        return listar.executar(minhas).stream().map(Resposta::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Resposta criar(@Valid @RequestBody Requisicao requisicao) {
        return Resposta.de(criar.executar(
                requisicao.palavraChave(), requisicao.conteudo()));
    }

    @PutMapping("/{id}")
    Resposta atualizar(@PathVariable UUID id, @Valid @RequestBody Requisicao requisicao) {
        return atualizar
                .executar(id, requisicao.palavraChave(), requisicao.conteudo())
                .map(Resposta::de)
                .orElseThrow(MensagemRapidaController::naoEncontrada);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remover(@PathVariable UUID id) {
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
            @NotBlank @Size(max = 60) @Pattern(regexp = "[\\p{L}\\p{N}_-]+") String palavraChave,
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
