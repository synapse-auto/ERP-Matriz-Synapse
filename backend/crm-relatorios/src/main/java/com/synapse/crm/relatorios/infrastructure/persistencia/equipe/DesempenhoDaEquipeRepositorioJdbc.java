package com.synapse.crm.relatorios.infrastructure.persistencia.equipe;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.relatorios.application.equipe.DesempenhoDaEquipeRepositorio;

/** Read model de atendimentos por integrante; vendas ficam na agregacao compartilhada. */
@Repository
class DesempenhoDaEquipeRepositorioJdbc implements DesempenhoDaEquipeRepositorio {

    private final JdbcTemplate jdbc;

    DesempenhoDaEquipeRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AtendimentosPorAtendente> contarAtendimentos() {
        return jdbc.query(
                """
                SELECT u.id, u.nome, count(a.id) AS atendimentos
                  FROM usuario u
                  LEFT JOIN atendimento a ON a.atendente_id = u.id
                 WHERE u.papel IN ('ATENDENTE', 'SUBGESTOR')
                 GROUP BY u.id, u.nome
                 ORDER BY u.nome
                """,
                (linha, indice) -> new AtendimentosPorAtendente(
                        linha.getObject("id", UUID.class),
                        linha.getString("nome"),
                        linha.getLong("atendimentos")));
    }
}
