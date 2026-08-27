package com.synapse.crm.equipe.infrastructure.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.equipe.application.feedback.FeedbackRepositorio;
import com.synapse.crm.equipe.domain.feedback.Feedback;
import com.synapse.crm.equipe.domain.feedback.TipoFeedback;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

@Repository
class FeedbackRepositorioJdbc implements FeedbackRepositorio {
    private final JdbcTemplate jdbc;

    FeedbackRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Feedback salvar(Feedback feedback) {
        jdbc.update("""
                INSERT INTO feedback_usuario(id, autor_id, tipo, area_chave, descricao, criado_em)
                VALUES (?, ?, ?::tipo_feedback, ?, ?, ?)
                """, feedback.id(), feedback.autorId(), feedback.tipo().name(),
                feedback.area().name(), feedback.descricao(), Timestamp.from(feedback.criadoEm()));
        return feedback;
    }

    @Override
    public Pagina listar(TipoFeedback tipo, Instant antesDe, UUID antesDoId, int limite) {
        StringBuilder sql = new StringBuilder("""
                SELECT f.id, f.autor_id, u.nome AS autor_nome, u.papel::text AS autor_papel,
                       CASE WHEN u.foto_referencia IS NOT NULL THEN '/api/v1/me/foto/' || u.id END AS autor_foto_url,
                       f.tipo::text, f.area_chave, f.descricao, f.criado_em
                  FROM feedback_usuario f
                  JOIN usuario u ON u.id = f.autor_id
                 WHERE TRUE
                """);
        List<Object> parametros = new ArrayList<>();
        if (tipo != null) {
            sql.append(" AND f.tipo = ?::tipo_feedback");
            parametros.add(tipo.name());
        }
        if (antesDe != null) {
            sql.append(" AND (f.criado_em < ? OR (f.criado_em = ? AND f.id < ?))");
            parametros.add(Timestamp.from(antesDe));
            parametros.add(Timestamp.from(antesDe));
            parametros.add(antesDoId);
        }
        sql.append(" ORDER BY f.criado_em DESC, f.id DESC LIMIT ?");
        parametros.add(limite + 1);

        List<FeedbackResumo> encontrados = jdbc.query(sql.toString(),
                FeedbackRepositorioJdbc::mapear, parametros.toArray());
        boolean temProxima = encontrados.size() > limite;
        List<FeedbackResumo> itens = temProxima
                ? List.copyOf(encontrados.subList(0, limite))
                : List.copyOf(encontrados);
        FeedbackResumo ultimo = temProxima ? itens.getLast() : null;
        return new Pagina(itens, ultimo == null ? null : ultimo.criadoEm(),
                ultimo == null ? null : ultimo.id());
    }

    private static FeedbackResumo mapear(ResultSet resultado, int indice) throws SQLException {
        return new FeedbackResumo(
                resultado.getObject("id", UUID.class),
                resultado.getObject("autor_id", UUID.class),
                resultado.getString("autor_nome"),
                PapelUsuario.valueOf(resultado.getString("autor_papel")),
                resultado.getString("autor_foto_url"),
                TipoFeedback.valueOf(resultado.getString("tipo")),
                resultado.getString("area_chave"),
                resultado.getString("descricao"),
                resultado.getTimestamp("criado_em").toInstant());
    }
}
