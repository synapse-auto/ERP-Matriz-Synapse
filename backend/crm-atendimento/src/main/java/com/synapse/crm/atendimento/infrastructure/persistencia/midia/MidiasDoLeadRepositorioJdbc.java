package com.synapse.crm.atendimento.infrastructure.persistencia.midia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.midia.MidiaDoLead;
import com.synapse.crm.atendimento.application.midia.MidiasDoLeadRepositorio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Read model de anexos; a política RLS de atendimento decide quais linhas são alcançáveis. */
@Repository
class MidiasDoLeadRepositorioJdbc implements MidiasDoLeadRepositorio {
    private static final String COLUNAS = "m.id, m.atendimento_id, m.tipo::text, m.midia_url, "
            + "m.midia_metadados, m.enviado_em, c.tipo AS origem";
    private static final String FROM = " FROM mensagem m JOIN atendimento a ON a.id=m.atendimento_id "
            + "LEFT JOIN canal c ON c.id=a.canal_id WHERE a.lead_id=? AND m.midia_url IS NOT NULL "
            + "AND m.tipo IN ('IMAGEM','AUDIO','DOCUMENTO','VIDEO')";
    private final JdbcTemplate chat;
    private final ObjectMapper json;

    MidiasDoLeadRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource dataSource, ObjectMapper json) {
        this.chat = new JdbcTemplate(dataSource);
        this.json = json;
    }

    @Override
    public List<MidiaDoLead> listar(UUID leadId, int limite, int deslocamento) {
        TransacaoObrigatoria.exigir("listar midias do lead");
        return chat.query("SELECT " + COLUNAS + FROM + " ORDER BY m.enviado_em DESC, m.id DESC LIMIT ? OFFSET ?",
                this::mapear, leadId, limite, deslocamento);
    }

    @Override
    public Optional<MidiaDoLead> porMensagem(UUID leadId, UUID mensagemId) {
        TransacaoObrigatoria.exigir("obter midia do lead");
        return chat.query("SELECT " + COLUNAS + FROM + " AND m.id=?", this::mapear, leadId, mensagemId)
                .stream().findFirst();
    }

    private MidiaDoLead mapear(ResultSet r, int ignored) throws SQLException {
        JsonNode metadados;
        try {
            metadados = r.getString("midia_metadados") == null
                    ? json.createObjectNode() : json.readTree(r.getString("midia_metadados"));
        } catch (Exception e) {
            metadados = json.createObjectNode();
        }
        return new MidiaDoLead(r.getObject("id", UUID.class), r.getObject("atendimento_id", UUID.class),
                r.getString("tipo"), texto(metadados, "nome"), texto(metadados, "mimetype"),
                metadados.path("tamanho").asLong(0), texto(metadados, "legenda"),
                r.getString("midia_url"), r.getTimestamp("enviado_em").toInstant(), r.getString("origem"));
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n.get(campo);
        return v == null || v.isNull() ? null : v.asText();
    }
}
