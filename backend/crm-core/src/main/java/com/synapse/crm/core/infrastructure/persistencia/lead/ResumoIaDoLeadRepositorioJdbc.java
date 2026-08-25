package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.lead.ResumoIaDoLeadRepositorio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Escrita estreita que nao precisa carregar a entidade completa do lead. */
@Repository
class ResumoIaDoLeadRepositorioJdbc implements ResumoIaDoLeadRepositorio {

    private final JdbcTemplate chat;

    ResumoIaDoLeadRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public void sobrescrever(UUID leadId, String resumo) {
        TransacaoObrigatoria.exigir("sobrescrever resumo da IA");
        int alterados = chat.update("UPDATE lead SET resumo_ia = ? WHERE id = ?", resumo, leadId);
        if (alterados != 1) {
            throw new IllegalStateException("lead do atendimento deixou de existir durante a atualizacao");
        }
    }
}
