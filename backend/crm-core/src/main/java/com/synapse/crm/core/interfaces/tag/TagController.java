package com.synapse.crm.core.interfaces.tag;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.core.application.tag.GestaoDeTagsUseCases;
import com.synapse.crm.core.application.tag.NomeDeTagEmUsoException;
import com.synapse.crm.core.domain.tag.Tag;

/** CRUD de tags. A restricao por papel esta nos casos de uso, nao aqui. */
@RestController
@RequestMapping("/api/v1/tags")
class TagController {

    private final GestaoDeTagsUseCases tags;

    TagController(GestaoDeTagsUseCases tags) {
        this.tags = tags;
    }

    @GetMapping
    List<TagResposta> listar() {
        return tags.listar().stream().map(TagResposta::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TagResposta criar(@Valid @RequestBody TagRequisicao requisicao) {
        return TagResposta.de(
                tags.criar(requisicao.nome(), requisicao.cor(), requisicao.icone()));
    }

    @PutMapping("/{id}")
    TagResposta atualizar(@PathVariable UUID id, @Valid @RequestBody TagRequisicao requisicao) {
        return tags.atualizar(id, requisicao.nome(), requisicao.cor(), requisicao.icone())
                .map(TagResposta::de)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag nao encontrada"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remover(@PathVariable UUID id) {
        if (!tags.remover(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag nao encontrada");
        }
    }

    @ExceptionHandler(NomeDeTagEmUsoException.class)
    ProblemDetail aoConflitarNome(NomeDeTagEmUsoException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problema.setTitle("Nome de tag em uso");
        return problema;
    }

    record TagRequisicao(
            @NotBlank @Size(max = 60) String nome,
            @NotBlank @Size(max = 20) String cor,
            @Size(max = 60) String icone) {}

    record TagResposta(UUID id, String nome, String cor, String icone) {
        static TagResposta de(Tag tag) {
            return new TagResposta(tag.id(), tag.nome(), tag.cor(), tag.icone());
        }
    }
}
