package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.tag.LeadTagRepositorio;
import com.synapse.crm.core.domain.tag.Tag;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;

/** Adaptador da associacao {@code lead_tag}; nunca consulta o lead, que chega ja autorizado. */
@Repository
class LeadTagRepositorioJdbc implements LeadTagRepositorio {

    private static final String SQL_LISTAR =
            """
            SELECT t.id, t.nome, t.cor, t.icone
              FROM tag t
              JOIN lead_tag lt ON lt.tag_id = t.id
             WHERE lt.lead_id = ?
             ORDER BY t.nome
            """;

    /** {@code = ANY(?)} com array do driver, e nao {@code IN (?,?,...)} montado na mao por linha. */
    private static final String SQL_LISTAR_POR_LEADS =
            """
            SELECT lt.lead_id, t.id, t.nome, t.cor, t.icone
              FROM tag t
              JOIN lead_tag lt ON lt.tag_id = t.id
             WHERE lt.lead_id = ANY(?)
             ORDER BY lt.lead_id, t.nome
            """;

    private final JdbcTemplate jdbc;

    LeadTagRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Tag> listar(UUID leadId) {
        TransacaoObrigatoria.exigir("listar tags do lead");
        return jdbc.query(SQL_LISTAR, LeadTagRepositorioJdbc::mapear, leadId);
    }

    @Override
    public Map<UUID, List<Tag>> listarPorLeads(List<UUID> leadIds) {
        TransacaoObrigatoria.exigir("listar tags de varios leads");
        if (leadIds == null || leadIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<Tag>> porLead = new LinkedHashMap<>();
        jdbc.query(
                SQL_LISTAR_POR_LEADS,
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("uuid", leadIds.toArray())),
                (linha, indice) -> {
                    UUID leadId = linha.getObject("lead_id", UUID.class);
                    porLead.computeIfAbsent(leadId, ignorado -> new ArrayList<>()).add(mapear(linha, indice));
                    return null;
                });
        return porLead;
    }

    @Override
    public void vincular(UUID leadId, UUID tagId) {
        TransacaoObrigatoria.exigir("vincular tag ao lead");
        jdbc.update(
                "INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                leadId,
                tagId);
    }

    @Override
    public void desvincular(UUID leadId, UUID tagId) {
        TransacaoObrigatoria.exigir("desvincular tag do lead");
        jdbc.update("DELETE FROM lead_tag WHERE lead_id = ? AND tag_id = ?", leadId, tagId);
    }

    private static Tag mapear(ResultSet linha, int indice) throws SQLException {
        return new Tag(
                linha.getObject("id", UUID.class),
                linha.getString("nome"),
                linha.getString("cor"),
                linha.getString("icone"));
    }
}
