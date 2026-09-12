package com.synapse.crm.atendimento.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.atendimento.domain.evento.EventoCanonicoDeAtendimento;

/** Publica o sinal canonico com uma versao reservada na mesma transacao da acao de negocio. */
public final class EventosCanonicosDeAtendimento {

    private EventosCanonicosDeAtendimento() {}

    public static EventoCanonicoDeAtendimento publicar(
            AtendimentoRepositorio atendimentos,
            ApplicationEventPublisher eventos,
            EventoCanonicoDeAtendimento.Tipo tipo,
            UUID atendimentoId,
            UUID leadId,
            Instant ocorridoEm) {
        long versao = atendimentos.avancarVersaoDoEvento(atendimentoId);
        EventoCanonicoDeAtendimento evento =
                EventoCanonicoDeAtendimento.criar(tipo, atendimentoId, leadId, versao, ocorridoEm);
        eventos.publishEvent(evento);
        return evento;
    }
}
