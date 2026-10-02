package com.synapse.crm.campanhas.infrastructure;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.campanhas.application.OptOutRepositorio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** {@code contato_optout} no pool do chat: o envio consulta antes de cada destinatario. */
@Repository
class OptOutRepositorioJdbc implements OptOutRepositorio {

    private final JdbcTemplate chat;

    OptOutRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public void registrar(UUID leadId, Origem origem, String motivo, UUID registradoPor) {
        TransacaoObrigatoria.exigir("registrar opt-out");
        chat.update(
                "INSERT INTO contato_optout (lead_id, origem, motivo, registrado_por) VALUES (?, ?, ?, ?)"
                        + " ON CONFLICT (lead_id) DO NOTHING",
                leadId,
                origem.name(),
                motivo,
                registradoPor);
    }

    @Override
    public boolean remover(UUID leadId) {
        TransacaoObrigatoria.exigir("remover opt-out");
        return chat.update("DELETE FROM contato_optout WHERE lead_id = ?", leadId) == 1;
    }

    @Override
    public boolean existe(UUID leadId) {
        TransacaoObrigatoria.exigir("consultar opt-out");
        Boolean existe = chat.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM contato_optout WHERE lead_id = ?)", Boolean.class, leadId);
        return Boolean.TRUE.equals(existe);
    }

    @Override
    public long contar() {
        TransacaoObrigatoria.exigir("contar opt-outs");
        Long total = chat.queryForObject("SELECT count(*) FROM contato_optout", Long.class);
        return total == null ? 0L : total;
    }

    @Override
    public List<Registro> listar(int pagina, int tamanho) {
        TransacaoObrigatoria.exigir("listar opt-outs");
        return chat.query(
                """
                SELECT o.lead_id, l.nome, l.telefone, o.desde, o.origem, o.motivo
                  FROM contato_optout o JOIN lead l ON l.id = o.lead_id
                 ORDER BY o.desde DESC LIMIT ? OFFSET ?
                """,
                (rs, linha) -> {
                    Timestamp desde = rs.getTimestamp("desde");
                    return new Registro(
                            rs.getObject("lead_id", UUID.class),
                            rs.getString("nome"),
                            rs.getString("telefone"),
                            desde.toInstant(),
                            Origem.valueOf(rs.getString("origem")),
                            rs.getString("motivo"));
                },
                tamanho,
                pagina * tamanho);
    }
}
