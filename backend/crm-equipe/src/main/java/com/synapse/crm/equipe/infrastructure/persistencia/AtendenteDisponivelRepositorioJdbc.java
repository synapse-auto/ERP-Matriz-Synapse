package com.synapse.crm.equipe.infrastructure.persistencia;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.equipe.application.disponibilidade.AtendenteDisponivelRepositorio;
import com.synapse.crm.equipe.domain.disponibilidade.AtendenteDisponivelParaIa;

/**
 * {@code disponibilidade_atendente_ia} JOIN {@code usuario}. Nenhuma das duas tem RLS (V12 so cobre
 * {@code lead}/{@code atendimento}/{@code lembrete}/{@code mensagem_programada}) — roteiro da IA e
 * dado operacional, nao carteira de lead, entao nao ha recorte de visibilidade aqui.
 */
@Repository
class AtendenteDisponivelRepositorioJdbc implements AtendenteDisponivelRepositorio {

    private static final String SQL =
            """
            SELECT u.id, u.nome, u.email
              FROM disponibilidade_atendente_ia d
              JOIN usuario u ON u.id = d.atendente_id
             WHERE d.disponivel_para_ia = TRUE AND u.ativo = TRUE
             ORDER BY u.nome
            """;

    private final JdbcTemplate jdbc;

    AtendenteDisponivelRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AtendenteDisponivelParaIa> listarDisponiveisParaIa() {
        return jdbc.query(SQL, (linha, indice) -> new AtendenteDisponivelParaIa(
                UUID.fromString(linha.getString("id")), linha.getString("nome"), linha.getString("email")));
    }
}
