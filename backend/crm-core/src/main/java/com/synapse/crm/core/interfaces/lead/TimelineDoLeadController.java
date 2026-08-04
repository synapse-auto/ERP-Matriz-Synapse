package com.synapse.crm.core.interfaces.lead;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.synapse.crm.core.application.timeline.ListarTimelineDoLeadUseCase;
import com.synapse.crm.core.application.timeline.PaginaTimeline;
import com.synapse.crm.core.domain.timeline.EventoTimeline;
import com.synapse.crm.core.domain.timeline.OrigemEvento;

/** Linha do tempo do lead, paginada e protegida pela mesma visibilidade da ficha. */
@RestController
@RequestMapping("/api/v1/leads")
class TimelineDoLeadController {

    private final ListarTimelineDoLeadUseCase listar;
    private final int tamanhoPagina;

    TimelineDoLeadController(
            ListarTimelineDoLeadUseCase listar,
            @Value("${synapse.timeline.tamanho-pagina}") int tamanhoPagina) {
        this.listar = listar;
        this.tamanhoPagina = tamanhoPagina;
    }

    @GetMapping("/{id}/timeline")
    PaginaTimelineResposta listar(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @PositiveOrZero int pagina) {
        return listar.executar(id, pagina, tamanhoPagina)
                .map(PaginaTimelineResposta::de)
                .orElseThrow(TimelineDoLeadController::naoEncontrado);
    }

    private static ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead nao encontrado");
    }

    record PaginaTimelineResposta(List<EventoTimelineResposta> eventos, int pagina, boolean temMais) {
        static PaginaTimelineResposta de(PaginaTimeline pagina) {
            return new PaginaTimelineResposta(
                    pagina.eventos().stream().map(EventoTimelineResposta::de).toList(),
                    pagina.pagina(),
                    pagina.temMais());
        }
    }

    record EventoTimelineResposta(
            UUID id,
            UUID atendimentoId,
            String tipo,
            String descricao,
            OrigemEvento origem,
            Instant criadoEm) {
        static EventoTimelineResposta de(EventoTimeline evento) {
            return new EventoTimelineResposta(
                    evento.id(), evento.atendimentoId(), evento.tipo(), evento.descricao(),
                    evento.origem(), evento.criadoEm());
        }
    }
}
