package com.synapse.crm.equipe.interfaces.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.crm.equipe.application.disponibilidade.ListarAtendentesDisponiveisUseCase;
import com.synapse.crm.equipe.domain.disponibilidade.AtendenteDisponivelParaIa;

/** {@code /internal/v1/atendentes/disponiveis} — parte do contrato da Automacao (E07 §1). */
@RestController
@RequestMapping("/internal/v1/atendentes")
class AtendentesDisponiveisInternalController {

    private final ListarAtendentesDisponiveisUseCase listar;

    AtendentesDisponiveisInternalController(ListarAtendentesDisponiveisUseCase listar) {
        this.listar = listar;
    }

    @GetMapping("/disponiveis")
    List<AtendenteResposta> disponiveis() {
        return listar.executar().stream().map(AtendenteResposta::de).toList();
    }

    record AtendenteResposta(UUID usuarioId, String nome, String email) {
        static AtendenteResposta de(AtendenteDisponivelParaIa atendente) {
            return new AtendenteResposta(atendente.usuarioId(), atendente.nome(), atendente.email());
        }
    }
}
