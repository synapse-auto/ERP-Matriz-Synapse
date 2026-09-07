package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.automacaoconfig.application.MarcaDaInstanciaRepositorio;
import com.synapse.crm.automacaoconfig.domain.MarcaDaInstancia;

/** Adaptador JDBC da linha singleton de customizacao da marca. */
@Repository
class MarcaDaInstanciaRepositorioJdbc implements MarcaDaInstanciaRepositorio {

    private final JdbcTemplate jdbc;

    MarcaDaInstanciaRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MarcaDaInstancia obter() {
        return jdbc.queryForObject(
                "SELECT tema::text, logo_referencia_storage, atualizado_por_id, atualizado_em "
                        + ", nome_da_marca, subtitulo "
                        + "FROM marca_da_instancia WHERE id = 1",
                (rs, rowNum) -> new MarcaDaInstancia(
                        rs.getString("tema"),
                        rs.getString("logo_referencia_storage"),
                        rs.getString("nome_da_marca"),
                        rs.getString("subtitulo"),
                        rs.getObject("atualizado_por_id", UUID.class),
                        rs.getTimestamp("atualizado_em") == null
                                ? null
                                : rs.getTimestamp("atualizado_em").toInstant()));
    }

    @Override
    public MarcaDaInstancia salvarTema(String temaJson, UUID atualizadoPorId, Instant atualizadoEm) {
        jdbc.update(
                "UPDATE marca_da_instancia SET tema = CAST(? AS jsonb), "
                        + "atualizado_por_id = ?, atualizado_em = ? WHERE id = 1",
                temaJson, atualizadoPorId, timestamp(atualizadoEm));
        return obter();
    }

    @Override
    public MarcaDaInstancia salvarLogo(String logoReferenciaStorage, UUID atualizadoPorId, Instant atualizadoEm) {
        jdbc.update(
                "UPDATE marca_da_instancia SET logo_referencia_storage = ?, "
                        + "atualizado_por_id = ?, atualizado_em = ? WHERE id = 1",
                logoReferenciaStorage, atualizadoPorId, timestamp(atualizadoEm));
        return obter();
    }

    @Override
    public MarcaDaInstancia salvarIdentidade(
            String nomeDaMarca, String subtitulo, UUID atualizadoPorId, Instant atualizadoEm) {
        jdbc.update(
                "UPDATE marca_da_instancia SET nome_da_marca = ?, subtitulo = ?, "
                        + "atualizado_por_id = ?, atualizado_em = ? WHERE id = 1",
                nomeDaMarca, subtitulo, atualizadoPorId, timestamp(atualizadoEm));
        return obter();
    }

    private static Timestamp timestamp(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }
}
