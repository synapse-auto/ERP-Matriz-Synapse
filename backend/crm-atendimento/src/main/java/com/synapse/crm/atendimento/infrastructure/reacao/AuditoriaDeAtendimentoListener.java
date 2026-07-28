package com.synapse.crm.atendimento.infrastructure.reacao;

import java.sql.Timestamp;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Registra em {@code audit_log} o que aconteceu no modulo de atendimento.
 *
 * <p>Distinta da timeline de proposito, como diz o comentario da propria tabela: a timeline e a visao
 * do atendente sobre <em>um lead</em>; a auditoria e transversal, para manutencao e diagnostico. Sao
 * dois leitores diferentes com duas retencoes diferentes, e junta-las tornaria impossivel apagar uma
 * sem perder a outra.
 *
 * <p>Mesma fase e mesmo pool do listener de timeline, pelos mesmos motivos: {@code AFTER_COMMIT} para
 * nao somar latencia ao caminho critico, e pool geral para nao gastar a reserva do chat.
 */
@Component
class AuditoriaDeAtendimentoListener {

    private static final String SQL =
            """
            INSERT INTO audit_log (ator_id, ator_tipo, acao, entidade_tipo, entidade_id, lead_id, criado_em)
                 VALUES (?, ?::origem_evento, ?, 'atendimento', ?, ?, ?)
            """;

    private final JdbcTemplate geral;

    AuditoriaDeAtendimentoListener(
            @Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource generalDataSource) {
        this.geral = new JdbcTemplate(generalDataSource);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAcontecer(EventoDeAtendimento evento) {
        Registro registro = registrar(evento);
        geral.update(
                SQL,
                registro.atorId(),
                registro.atorTipo(),
                registro.acao(),
                evento.atendimentoId(),
                evento.leadId(),
                Timestamp.from(evento.ocorridoEm()));
    }

    /** Exaustivo pelo mesmo motivo do listener de timeline: evento novo sem auditoria e cego. */
    private static Registro registrar(EventoDeAtendimento evento) {
        return switch (evento) {
            // Sem ator humano: quem "agiu" foi o cliente, que nao e usuario do CRM.
            case EventoDeAtendimento.MensagemRecebida ignorado ->
                new Registro(null, "SISTEMA", "MENSAGEM_RECEBIDA");

            case EventoDeAtendimento.MensagemEnviada enviada -> new Registro(
                    enviada.remetenteId(),
                    "USUARIO",
                    enviada.transferiu() ? "ENVIO_COM_TRANSFERENCIA_DE_LEAD" : "MENSAGEM_ENVIADA");

            case EventoDeAtendimento.AtendimentoTransferido transferido ->
                new Registro(transferido.quemTransferiu(), "USUARIO", "ATENDIMENTO_TRANSFERIDO");

            case EventoDeAtendimento.AtendimentoFinalizado finalizado ->
                new Registro(finalizado.quemFinalizou(), "USUARIO", "ATENDIMENTO_FINALIZADO");
        };
    }

    private record Registro(UUID atorId, String atorTipo, String acao) {}
}
