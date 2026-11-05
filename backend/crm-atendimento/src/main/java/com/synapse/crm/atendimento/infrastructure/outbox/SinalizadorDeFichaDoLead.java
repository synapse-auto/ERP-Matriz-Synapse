package com.synapse.crm.atendimento.infrastructure.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.synapse.crm.atendimento.domain.evento.EventoCanonicoDeAtendimento;
import com.synapse.crm.core.domain.evento.DadosCadastraisDoLeadAlterados;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Mesmo commit do cadastro, mesmo banco e mesma outbox; nenhum HTTP/Redis nesta escrita. */
@Component
class SinalizadorDeFichaDoLead {
    private final DataSource geral;
    private final DataSource chat;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    SinalizadorDeFichaDoLead(@Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource geral,
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chat, ApplicationEventPublisher eventos, Clock relogio) {
        this.geral = geral; this.chat = chat; this.eventos = eventos; this.relogio = relogio;
    }

    @EventListener
    void sinalizar(DadosCadastraisDoLeadAlterados alteracao) {
        if (TransactionSynchronizationManager.hasResource(chat) && TransactionSynchronizationManager.hasResource(geral)) {
            throw new IllegalStateException("sinal de ficha com pools concorrentes nao possui transacao inequivoca");
        }
        DataSource ativa = TransactionSynchronizationManager.hasResource(chat) ? chat : geral;
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(ativa)) {
            throw new IllegalStateException("alteracao de ficha exige transacao do cadastro");
        }
        new JdbcTemplate(ativa).query("UPDATE atendimento SET versao_evento = versao_evento + 1 "
                        + "WHERE lead_id = ? AND finalizado_em IS NULL RETURNING id, versao_evento",
                (rs, indice) -> EventoCanonicoDeAtendimento.criar(EventoCanonicoDeAtendimento.Tipo.DADOS_CLIENTE_ATUALIZADOS,
                        rs.getObject("id", UUID.class), alteracao.leadId(), rs.getLong("versao_evento"), Instant.now(relogio)),
                alteracao.leadId()).forEach(eventos::publishEvent);
    }
}
