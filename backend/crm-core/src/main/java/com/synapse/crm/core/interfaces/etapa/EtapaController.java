package com.synapse.crm.core.interfaces.etapa;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.core.application.etapa.GestaoDeEtapasUseCases;
import com.synapse.crm.core.domain.etapa.EtapaAtendimento;

/** Etapas do funil. Leitura para todos; escrita so para gestor (checado no caso de uso). */
@RestController
@RequestMapping("/api/v1/etapas")
class EtapaController {

    private final GestaoDeEtapasUseCases etapas;

    EtapaController(GestaoDeEtapasUseCases etapas) {
        this.etapas = etapas;
    }

    @GetMapping
    List<EtapaResposta> listar() {
        return etapas.listar().stream().map(EtapaResposta::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EtapaResposta criar(@Valid @RequestBody EtapaRequisicao requisicao) {
        return EtapaResposta.de(
                etapas.criar(requisicao.nome(), requisicao.ordem(), requisicao.corVisual()));
    }

    @PutMapping("/{id}")
    EtapaResposta atualizar(@PathVariable UUID id, @Valid @RequestBody EtapaRequisicao requisicao) {
        return etapas.atualizar(id, requisicao.nome(), requisicao.ordem(), requisicao.corVisual())
                .map(EtapaResposta::de)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Etapa nao encontrada"));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remover(@PathVariable UUID id) {
        if (!etapas.remover(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Etapa nao encontrada");
        }
    }

    record EtapaRequisicao(
            @NotBlank @Size(max = 80) String nome,
            @Positive short ordem,
            @Size(max = 20) String corVisual) {}

    record EtapaResposta(UUID id, String nome, short ordem, String corVisual) {
        static EtapaResposta de(EtapaAtendimento etapa) {
            return new EtapaResposta(etapa.id(), etapa.nome(), etapa.ordem(), etapa.corVisual());
        }
    }
}
