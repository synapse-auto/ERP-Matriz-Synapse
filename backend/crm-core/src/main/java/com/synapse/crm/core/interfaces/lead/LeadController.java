package com.synapse.crm.core.interfaces.lead;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.core.application.lead.FiltroLead;
import com.synapse.crm.core.application.lead.ListarLeadsUseCase;
import com.synapse.crm.core.application.lead.ObterLeadUseCase;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;

/** Leitura de leads. O recorte por papel acontece no repositorio, nunca aqui nem no frontend. */
@RestController
@RequestMapping("/api/v1/leads")
class LeadController {

    private final ListarLeadsUseCase listar;
    private final ObterLeadUseCase obter;

    LeadController(ListarLeadsUseCase listar, ObterLeadUseCase obter) {
        this.listar = listar;
        this.obter = obter;
    }

    @GetMapping
    List<LeadResposta> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) StatusBasicoLead status) {
        return listar.executar(new FiltroLead(busca, status)).stream()
                .map(LeadResposta::de)
                .toList();
    }

    @GetMapping("/{id}")
    LeadResposta porId(@PathVariable UUID id) {
        return obter.executar(id)
                .map(LeadResposta::de)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead nao encontrado"));
    }

    record LeadResposta(UUID id, String nome, StatusBasicoLead status, UUID atendenteResponsavelId) {
        static LeadResposta de(Lead lead) {
            return new LeadResposta(
                    lead.id(), lead.nome(), lead.statusBasico(), lead.atendenteResponsavelId());
        }
    }
}
