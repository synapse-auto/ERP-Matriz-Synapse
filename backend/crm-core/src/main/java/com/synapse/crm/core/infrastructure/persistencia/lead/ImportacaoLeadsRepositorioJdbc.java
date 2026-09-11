package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.lead.importacao.ImportacaoLeadsRepositorio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;

/** Persistencia direta da importacao confirmada; a unicidade do telefone fica no banco. */
@Repository
class ImportacaoLeadsRepositorioJdbc implements ImportacaoLeadsRepositorio {

    private final JdbcTemplate jdbc;

    ImportacaoLeadsRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> telefonesExistentes(Collection<String> telefones) {
        TransacaoObrigatoria.exigir("consultar telefones da importacao");
        if (telefones == null || telefones.isEmpty()) return Set.of();
        List<String> valores = telefones.stream().distinct().toList();
        return jdbc.query(
                        "SELECT telefone FROM lead WHERE telefone = ANY(?)",
                        ps -> ps.setArray(1, ps.getConnection().createArrayOf("text", valores.toArray())),
                        (linha, indice) -> linha.getString("telefone"))
                .stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public int inserir(List<LeadParaInsercao> leads) {
        TransacaoObrigatoria.exigir("inserir leads da importacao");
        int inseridos = 0;
        for (LeadParaInsercao lead : leads) {
            int afetadas = jdbc.update(
                    conexao -> {
                        PreparedStatement ps = conexao.prepareStatement(
                                "INSERT INTO lead (nome, telefone, empresa, cpf, localizacao, etapa_atendimento_id, status_basico, atendente_responsavel_id) "
                                        + "VALUES (?, ?, ?, ?, ?, ?, 'IA'::status_basico_lead, NULL) "
                                        + "ON CONFLICT (telefone) WHERE telefone IS NOT NULL DO NOTHING");
                        ps.setString(1, lead.nome());
                        ps.setString(2, lead.telefone());
                        ps.setString(3, lead.empresa());
                        ps.setString(4, lead.cpf());
                        ps.setString(5, lead.localizacao());
                        if (lead.etapaId() == null) ps.setObject(6, null);
                        else ps.setObject(6, lead.etapaId());
                        return ps;
                    });
            if (afetadas == 0) continue;
            inseridos += afetadas;
            UUID leadId = jdbc.queryForObject(
                    "SELECT id FROM lead WHERE telefone = ?", UUID.class, lead.telefone());
            if (leadId != null) {
                for (UUID tagId : lead.tags()) {
                    jdbc.update(
                            "INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                            leadId,
                            tagId);
                }
            }
        }
        return inseridos;
    }
}
