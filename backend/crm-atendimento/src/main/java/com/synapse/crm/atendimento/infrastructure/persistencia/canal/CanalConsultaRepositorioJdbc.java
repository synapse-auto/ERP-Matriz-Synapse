package com.synapse.crm.atendimento.infrastructure.persistencia.canal;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.canal.CanalConsultaRepositorio;
import com.synapse.crm.atendimento.application.canal.CanalResumo;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;

/** Projecao explicitamente sem {@code canal_credencial}: segredo nao chega a esta consulta. */
@Repository
class CanalConsultaRepositorioJdbc implements CanalConsultaRepositorio {

    private static final String SQL = "SELECT id, nome, tipo, ativo FROM canal ORDER BY nome";

    private final JdbcTemplate jdbc;

    CanalConsultaRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CanalResumo> listar() {
        TransacaoObrigatoria.exigir("listar canais");
        return jdbc.query(SQL, (linha, indice) -> new CanalResumo(
                linha.getObject("id", java.util.UUID.class),
                linha.getString("nome"),
                linha.getString("tipo"),
                linha.getBoolean("ativo")));
    }
}
