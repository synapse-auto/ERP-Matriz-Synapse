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
 * Escreve a timeline do lead a partir dos eventos de atendimento.
 *
 * <p>Duas decisoes de caminho critico moram nesta classe.
 *
 * <p>A primeira e a fase: {@code AFTER_COMMIT}. A timeline e uma <em>reacao</em> ao registro da
 * mensagem, nao parte dele. Se rodasse dentro da transacao, cada linha escrita aqui somaria latencia
 * ao ato de receber mensagem do cliente — e a aba Atendimentos nao pode ficar indisponivel entre
 * 08:00 e 18:30.
 *
 * <p>A segunda e o pool: {@code generalDataSource}, nao o do chat. A reserva do caminho critico e
 * para gravar mensagem; gastar uma conexao dela para escrever historico anularia o bulkhead
 * exatamente quando ele mais importa, que e sob carga.
 *
 * <p>{@code REQUIRES_NEW} e explicito porque, depois do commit, nao ha mais transacao — e uma escrita
 * sem transacao rodaria sem o contexto RLS que o gerente publica no {@code doBegin}.
 */
@Component
class TimelineDeAtendimentoListener {

    private static final String SQL =
            """
            INSERT INTO evento_timeline (lead_id, atendimento_id, tipo, descricao, origem, criado_em)
                 VALUES (?, ?, ?, ?, ?::origem_evento, ?)
            """;

    private final JdbcTemplate geral;

    TimelineDeAtendimentoListener(@Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource generalDataSource) {
        this.geral = new JdbcTemplate(generalDataSource);
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
                Timestamp.from(evento.ocorridoEm()));
    }

    /**
     * Switch exaustivo sobre o tipo selado: um evento novo quebra o build aqui ate ganhar redacao. Um
     * {@code default} silencioso produziria evento sem rastro na timeline — e a timeline e o que o
     * atendente usa para saber o que aconteceu com o lead dele.
     */
    private static Anotacao descrever(EventoDeAtendimento evento) {
        return switch (evento) {
            case EventoDeAtendimento.MensagemRecebida recebida -> new Anotacao(
                    "MENSAGEM_RECEBIDA",
                    recebida.abriuAtendimento()
                            ? "Cliente iniciou uma conversa."
                            : "Cliente enviou uma mensagem.",
                    "SISTEMA");

            // A anotacao mais importante do modulo: e o registro de que a comissao
            // daquele lead mudou de mao, e de quem para quem.
            case EventoDeAtendimento.MensagemEnviada enviada -> new Anotacao(
                    enviada.transferiu() ? "LEAD_TRANSFERIDO_POR_ENVIO" : "MENSAGEM_ENVIADA",
                    enviada.transferiu()
                            ? "Atendente " + enviada.remetenteId() + " enviou mensagem e assumiu o lead"
                                    + enviada.donoAnterior()
                                            .map(anterior -> ", antes de " + anterior + ".")
                                            .orElse(", que estava sem responsavel.")
                            : "Atendente " + enviada.remetenteId() + " enviou uma mensagem.",
                    "USUARIO");

            case EventoDeAtendimento.AtendimentoTransferido transferido -> new Anotacao(
                    "ATENDIMENTO_TRANSFERIDO",
                    "Atendimento transferido de "
                            + rotulo(transferido.deAtendenteId())
                            + " para "
                            + rotulo(transferido.paraAtendenteId())
                            + " por "
                            + transferido.quemTransferiu()
                            + ".",
                    "USUARIO");

            case EventoDeAtendimento.AtendimentoFinalizado finalizado -> new Anotacao(
                    "ATENDIMENTO_FINALIZADO",
                    "Atendimento finalizado por " + finalizado.quemFinalizou() + ".",
                    "USUARIO");
        };
    }

    private static String rotulo(UUID atendenteId) {
        return atendenteId == null ? "IA" : atendenteId.toString();
    }

    private record Anotacao(String tipo, String descricao, String origem) {}
}
