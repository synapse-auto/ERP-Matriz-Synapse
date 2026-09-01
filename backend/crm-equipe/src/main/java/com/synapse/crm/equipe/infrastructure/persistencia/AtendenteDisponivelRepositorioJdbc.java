package com.synapse.crm.equipe.infrastructure.persistencia;

import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.equipe.application.disponibilidade.AtendenteDisponivelRepositorio;
import com.synapse.crm.equipe.domain.disponibilidade.AtendenteDisponivelParaIa;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Elegibilidade e ordem do rodizio da IA em uma consulta agregada. As tabelas de disponibilidade e
 * usuario nao tem RLS; as agregacoes de {@code atendimento} e {@code evento_timeline} rodam no
 * contexto {@code SERVICO}, que e o unico que deve consultar a fila comercial inteira.
 */
@Repository
class AtendenteDisponivelRepositorioJdbc implements AtendenteDisponivelRepositorio {

    private static final String SQL =
            """
            WITH carga AS (
                SELECT a.atendente_id,
                       count(*) FILTER (WHERE a.status = 'EM_ATENDIMENTO') AS atendimentos_abertos
                  FROM atendimento a
                 WHERE a.atendente_id IS NOT NULL
                 GROUP BY a.atendente_id
            ), recebimentos AS (
                SELECT atendente_id, max(recebido_em) AS ultimo_recebido_em
                  FROM (
                        SELECT a.atendente_id, a.iniciado_em AS recebido_em
                          FROM atendimento a
                         WHERE a.atendente_id IS NOT NULL
                        UNION ALL
                        SELECT (e.dados ->> 'paraAtendenteId')::uuid, e.criado_em
                          FROM evento_timeline e
                         WHERE e.tipo = 'ATENDIMENTO_TRANSFERIDO'
                           AND e.dados ->> 'paraAtendenteId' IS NOT NULL
                        UNION ALL
                        SELECT e.ator_id, e.criado_em
                          FROM evento_timeline e
                         WHERE e.tipo = 'LEAD_TRANSFERIDO_POR_ENVIO'
                           AND e.ator_id IS NOT NULL
                  ) recebimento
                 GROUP BY atendente_id
            )
            SELECT u.id, u.nome, u.email
              FROM disponibilidade_atendente_ia d
              JOIN usuario u ON u.id = d.atendente_id
              LEFT JOIN carga c ON c.atendente_id = u.id
              LEFT JOIN recebimentos r ON r.atendente_id = u.id
             WHERE d.disponivel_para_ia = TRUE AND u.ativo = TRUE
               -- Espelha PapelUsuario.recebeAtendimento()
               AND u.papel IN ('ATENDENTE', 'SUBGESTOR') AND u.status_presenca = 'ONLINE'
             ORDER BY COALESCE(c.atendimentos_abertos, 0),
                      r.ultimo_recebido_em NULLS FIRST,
                      u.id
            """;

    private final JdbcTemplate jdbc;

    AtendenteDisponivelRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.jdbc = new JdbcTemplate(chatDataSource);
    }

    @Override
    public List<AtendenteDisponivelParaIa> listarDisponiveisParaIa() {
        return jdbc.query(SQL, (linha, indice) -> new AtendenteDisponivelParaIa(
                UUID.fromString(linha.getString("id")), linha.getString("nome"), linha.getString("email")));
    }
}
