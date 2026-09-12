package com.synapse.crm.atendimento.infrastructure.persistencia.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.internal.CandidatosEv05Repositorio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Repository
class CandidatosEv05RepositorioJdbc implements CandidatosEv05Repositorio {

    private final JdbcTemplate chat;

    CandidatosEv05RepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource dataSource) {
        this.chat = new JdbcTemplate(dataSource);
    }

    @Override
    public Pagina listar(int pagina, int tamanho, Instant atualizadoDesde) {
        TransacaoObrigatoria.exigir("listar candidatos EV-05");
        StringBuilder sql = new StringBuilder(
                "SELECT a.id AS atendimento_id, a.lead_id, a.status::text AS status, "
                        + "COALESCE(l.ultima_interacao_em, a.iniciado_em) AS atualizado_em "
                        + "FROM atendimento a JOIN lead l ON l.id = a.lead_id "
                        + "WHERE a.status = 'EM_ATENDIMENTO'");
        List<Object> args = new ArrayList<>();
        if (atualizadoDesde != null) {
            sql.append(" AND COALESCE(l.ultima_interacao_em, a.iniciado_em) >= ?");
            args.add(Timestamp.from(atualizadoDesde));
        }
        sql.append(" ORDER BY COALESCE(l.ultima_interacao_em, a.iniciado_em) DESC, a.id LIMIT ? OFFSET ?");
        args.add(tamanho + 1);
        args.add(Math.multiplyExact((long) pagina, tamanho));
        List<Item> itens = chat.query(sql.toString(), (rs, row) -> new Item(
                rs.getObject("atendimento_id", UUID.class),
                rs.getObject("lead_id", UUID.class),
                rs.getString("status"),
                rs.getTimestamp("atualizado_em").toInstant()), args.toArray());
        boolean temMais = itens.size() > tamanho;
        if (temMais) {
            itens = new ArrayList<>(itens.subList(0, tamanho));
        }
        return new Pagina(itens, pagina, tamanho, temMais);
    }
}
