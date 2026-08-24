package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.lead.LeadParaEntrada;
import com.synapse.crm.core.application.lead.LeadParaEntradaRepositorio;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Repository
class LeadParaEntradaRepositorioJdbc implements LeadParaEntradaRepositorio {
    private final JdbcTemplate jdbc;

    LeadParaEntradaRepositorioJdbc(@Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public List<LeadParaEntrada> buscar(String termo, UUID usuarioId) {
        return jdbc.query(
                "SELECT * FROM app_buscar_lead_para_entrada(?, ?)",
                (linha, indice) -> new LeadParaEntrada(
                        linha.getObject("id", UUID.class),
                        linha.getString("nome"),
                        linha.getString("empresa"),
                        linha.getObject("responsavel_id", UUID.class),
                        linha.getString("responsavel_nome")),
                termo,
                usuarioId);
    }
}
