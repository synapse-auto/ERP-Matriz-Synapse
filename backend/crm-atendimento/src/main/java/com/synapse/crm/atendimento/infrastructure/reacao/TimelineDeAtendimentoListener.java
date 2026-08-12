package com.synapse.crm.atendimento.infrastructure.reacao;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Persiste snapshot legivel e parametros estruturados depois do commit do atendimento. */
@Component
class TimelineDeAtendimentoListener {

    private static final String SQL =
            """
            INSERT INTO evento_timeline
                (lead_id, atendimento_id, tipo, descricao, origem, ator_id, dados, criado_em)
            VALUES (?, ?, ?, ?, ?::origem_evento, ?, ?::jsonb, ?)
            """;

    private final JdbcTemplate geral;
    private final ObjectMapper json;

    TimelineDeAtendimentoListener(
            @Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource generalDataSource, ObjectMapper json) {
        this.geral = new JdbcTemplate(generalDataSource);
        this.json = json;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAcontecer(EventoDeAtendimento evento) {
        Anotacao anotacao = descrever(evento);
        geral.update(
                SQL,
                evento.leadId(),
                evento.atendimentoId(),
                anotacao.tipo(),
                anotacao.descricao(),
                anotacao.origem(),
                anotacao.atorId(),
                serializar(anotacao.dados()),
                Timestamp.from(evento.ocorridoEm()));
    }

    /** Switch exaustivo: um fato novo nao pode nascer sem representacao na timeline. */
    private Anotacao descrever(EventoDeAtendimento evento) {
        return switch (evento) {
            case EventoDeAtendimento.MensagemRecebida recebida -> new Anotacao(
                    "MENSAGEM_RECEBIDA",
                    recebida.abriuAtendimento()
                            ? "Cliente iniciou uma conversa."
                            : "Cliente enviou uma mensagem.",
                    "SISTEMA",
                    null,
                    Map.of("abriuAtendimento", recebida.abriuAtendimento()));

            case EventoDeAtendimento.MensagemEnviada enviada -> {
                Map<String, Object> dados = new LinkedHashMap<>();
                dados.put("transferiu", enviada.transferiu());
                dados.put("tinhaDonoAnterior", enviada.donoAnterior().isPresent());
                dados.put("donoAnteriorId", enviada.donoAnterior().map(UUID::toString).orElse(null));
                String ator = nome(enviada.remetenteId());
                String descricao = enviada.transferiu()
                        ? "Atendente " + ator + " enviou mensagem e assumiu o lead"
                                + enviada.donoAnterior()
                                        .map(anterior -> ", antes de " + nome(anterior) + ".")
                                        .orElse(", que estava sem responsavel.")
                        : "Atendente " + ator + " enviou uma mensagem.";
                yield new Anotacao(
                        enviada.transferiu() ? "LEAD_TRANSFERIDO_POR_ENVIO" : "MENSAGEM_ENVIADA",
                        descricao,
                        "USUARIO",
                        enviada.remetenteId(),
                        dados);
            }

            case EventoDeAtendimento.AtendimentoTransferido transferido -> {
                Map<String, Object> dados = new LinkedHashMap<>();
                dados.put("deIa", transferido.deAtendenteId() == null);
                dados.put("deAtendenteId", idOuNulo(transferido.deAtendenteId()));
                dados.put("paraIa", transferido.paraAtendenteId() == null);
                dados.put("paraAtendenteId", idOuNulo(transferido.paraAtendenteId()));
                yield new Anotacao(
                        "ATENDIMENTO_TRANSFERIDO",
                        "Atendimento transferido de "
                                + rotulo(transferido.deAtendenteId())
                                + " para "
                                + rotulo(transferido.paraAtendenteId())
                                + " por "
                                + rotuloDoAtor(transferido)
                                + ".",
                        transferido.atorTipo().name(),
                        transferido.atorId(),
                        dados);
            }

            case EventoDeAtendimento.AtendimentoFinalizado finalizado -> new Anotacao(
                    "ATENDIMENTO_FINALIZADO",
                    "Atendimento finalizado por " + nome(finalizado.quemFinalizou()) + ".",
                    "USUARIO",
                    finalizado.quemFinalizou(),
                    Map.of());
        };
    }

    private String rotulo(UUID atendenteId) {
        return atendenteId == null ? "IA" : nome(atendenteId);
    }

    private String rotuloDoAtor(EventoDeAtendimento.AtendimentoTransferido evento) {
        return evento.atorId() == null ? "Automacao" : nome(evento.atorId());
    }

    private String nome(UUID usuarioId) {
        return geral.query(
                        "SELECT nome FROM usuario WHERE id = ?",
                        (linha, indice) -> linha.getString("nome"),
                        usuarioId)
                .stream()
                .findFirst()
                .orElse(usuarioId.toString());
    }

    private String serializar(Map<String, Object> dados) {
        try {
            return json.writeValueAsString(dados);
        } catch (JsonProcessingException erro) {
            throw new IllegalStateException("Falha ao serializar dados da timeline", erro);
        }
    }

    private static String idOuNulo(UUID id) {
        return id == null ? null : id.toString();
    }

    private record Anotacao(
            String tipo, String descricao, String origem, UUID atorId, Map<String, Object> dados) {}
}
