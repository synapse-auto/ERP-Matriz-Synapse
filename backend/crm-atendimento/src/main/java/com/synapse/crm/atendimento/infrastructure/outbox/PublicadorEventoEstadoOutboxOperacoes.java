package com.synapse.crm.atendimento.infrastructure.outbox;

import java.time.Clock;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.evento.EventoCanonicoDeAtendimento;
import com.synapse.crm.atendimento.infrastructure.tempo_real.CanaisRedis;

/** Drena sinais canônicos da outbox e os publica no backplane Redis fora da transação SQL. */
@Component
class PublicadorEventoEstadoOutboxOperacoes {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventoEstadoOutboxOperacoes.class);
    private final PublicadorEventoEstadoOutboxTransacoes transacoes;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Clock relogio;

    PublicadorEventoEstadoOutboxOperacoes(
            PublicadorEventoEstadoOutboxTransacoes transacoes,
            StringRedisTemplate redis,
            ObjectMapper json,
            Clock relogio) {
        this.transacoes = transacoes;
        this.redis = redis;
        this.json = json;
        this.relogio = relogio;
    }

    int rodada() {
        var pendentes = transacoes.reservar(Instant.now(relogio));
        for (var pendente : pendentes) publicar(pendente);
        return pendentes.size();
    }

    private void publicar(OutboxEventoEstadoRepositorioJdbc.Pendente pendente) {
        EventoCanonicoDeAtendimento evento = pendente.evento();
        try {
            redis.convertAndSend(CanaisRedis.doAtendimento(evento.atendimentoId()), serializar(evento));
            transacoes.registrarSucesso(pendente, Instant.now(relogio));
            log.debug(
                    "Evento canonico publicado: atendimentoId={}, leadId={}, eventId={}, versao={}, tipo={}",
                    evento.atendimentoId(), evento.leadId(), evento.eventoId(), evento.versao(), evento.tipo());
        } catch (RuntimeException erro) {
            boolean esgotou = transacoes.registrarFalha(
                    pendente, Instant.now(relogio), erro.getClass().getSimpleName() + ": " + erro.getMessage());
            log.atWarn()
                    .setCause(erro)
                    .log("Falha ao publicar evento canonico: atendimentoId={}, leadId={}, eventId={}, versao={}, tipo={}, esgotou={}",
                            evento.atendimentoId(), evento.leadId(), evento.eventoId(), evento.versao(), evento.tipo(), esgotou);
        }
    }

    private String serializar(EventoCanonicoDeAtendimento evento) {
        ObjectNode envelope = json.createObjectNode();
        envelope.put("tipo", "ATENDIMENTO_ESTADO");
        envelope.put("contrato", "atendimento.estado.v1");
        envelope.put("eventoId", evento.eventoId().toString());
        envelope.put("versaoContrato", evento.versaoContrato());
        ObjectNode dados = envelope.putObject("dados");
        dados.put("atendimentoId", evento.atendimentoId().toString());
        dados.put("leadId", evento.leadId().toString());
        dados.put("eventoTipo", evento.tipo().name());
        dados.put("versao", evento.versao());
        dados.put("ocorridoEm", evento.ocorridoEm().toString());
        return envelope.toString();
    }
}
