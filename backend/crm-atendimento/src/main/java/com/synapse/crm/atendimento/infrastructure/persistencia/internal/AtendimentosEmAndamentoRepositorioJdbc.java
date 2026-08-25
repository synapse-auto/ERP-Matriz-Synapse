package com.synapse.crm.atendimento.infrastructure.persistencia.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.internal.AtendimentosEmAndamentoRepositorio;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Consulta estreita: a lateral busca somente o instante da ultima mensagem, nunca seu conteudo. */
@Repository
class AtendimentosEmAndamentoRepositorioJdbc implements AtendimentosEmAndamentoRepositorio {

    private final JdbcTemplate chat;

    AtendimentosEmAndamentoRepositorioJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public Pagina listar(Filtro filtro) {
        TransacaoObrigatoria.exigir("listar atendimentos em andamento para a Automacao");
        StringBuilder sql = new StringBuilder(
                """
                SELECT a.id AS atendimento_id, a.lead_id, a.status::text,
                       u.id AS responsavel_id, u.nome AS responsavel_nome,
                       ultima.enviado_em AS ultima_mensagem_em
                  FROM atendimento a
                  LEFT JOIN usuario u ON u.id = a.atendente_id
                  LEFT JOIN LATERAL (
                       SELECT m.enviado_em
                         FROM mensagem m
                        WHERE m.atendimento_id = a.id
                        ORDER BY m.enviado_em DESC, m.id DESC
                        LIMIT 1
                  ) ultima ON TRUE
                 WHERE a.status <> 'FINALIZADO'
                """);
        List<Object> parametros = new ArrayList<>();
        if (filtro.atividadeDesde() != null) {
            sql.append(" AND COALESCE(ultima.enviado_em, a.iniciado_em) >= ?");
            parametros.add(Timestamp.from(filtro.atividadeDesde()));
        }
        if (filtro.atividadeAte() != null) {
            sql.append(" AND COALESCE(ultima.enviado_em, a.iniciado_em) <= ?");
            parametros.add(Timestamp.from(filtro.atividadeAte()));
        }
        sql.append(
                " ORDER BY COALESCE(ultima.enviado_em, a.iniciado_em) DESC, a.id LIMIT ? OFFSET ?");
        parametros.add(filtro.tamanho() + 1);
        parametros.add(Math.multiplyExact((long) filtro.pagina(), filtro.tamanho()));

        List<Item> itens = chat.query(sql.toString(), AtendimentosEmAndamentoRepositorioJdbc::mapear, parametros.toArray());
        boolean temMais = itens.size() > filtro.tamanho();
        if (temMais) {
            itens = new ArrayList<>(itens.subList(0, filtro.tamanho()));
        }
        return new Pagina(itens, filtro.pagina(), filtro.tamanho(), temMais);
    }

    private static Item mapear(ResultSet linha, int indice) throws SQLException {
        UUID responsavelId = linha.getObject("responsavel_id", UUID.class);
        Responsavel responsavel = responsavelId == null
                ? null
                : new Responsavel(responsavelId, linha.getString("responsavel_nome"));
        Timestamp ultimaMensagem = linha.getTimestamp("ultima_mensagem_em");
        return new Item(
                linha.getObject("atendimento_id", UUID.class),
                linha.getObject("lead_id", UUID.class),
                StatusAtendimento.valueOf(linha.getString("status")),
                responsavel,
                ultimaMensagem == null ? null : ultimaMensagem.toInstant());
    }
}
