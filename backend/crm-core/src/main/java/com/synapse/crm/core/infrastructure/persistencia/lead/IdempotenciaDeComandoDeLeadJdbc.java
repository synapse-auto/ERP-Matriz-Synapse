package com.synapse.crm.core.infrastructure.persistencia.lead;

import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.core.application.lead.IdempotenciaDeComandoDeLead;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Adaptador JDBC da reserva de comandos sobre lead, no pool geral (fora do caminho de chat). */
@Repository
class IdempotenciaDeComandoDeLeadJdbc implements IdempotenciaDeComandoDeLead {

    private static final String INSERIR =
            "INSERT INTO comando_automacao_lead_idempotencia "
                    + "(idempotency_key, operacao, lead_id, requisicao_hash) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT (idempotency_key) DO NOTHING";

    private static final String BUSCAR =
            "SELECT idempotency_key, operacao, lead_id, requisicao_hash, resposta "
                    + "FROM comando_automacao_lead_idempotencia WHERE idempotency_key = ?";

    private static final String CONCLUIR =
            "UPDATE comando_automacao_lead_idempotencia SET resposta = ?::jsonb "
                    + "WHERE idempotency_key = ? AND resposta IS NULL";

    private final JdbcTemplate geral;

    IdempotenciaDeComandoDeLeadJdbc(@Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource dataSource) {
        this.geral = new JdbcTemplate(dataSource);
    }

    @Override
    public Optional<Reserva> buscar(String chave) {
        TransacaoObrigatoria.exigir("buscar reserva de comando sobre lead");
        return geral.query(BUSCAR, (linha, indice) -> new Reserva(
                        false,
                        linha.getString("idempotency_key"),
                        linha.getString("operacao"),
                        linha.getObject("lead_id", UUID.class),
                        linha.getString("requisicao_hash"),
                        linha.getString("resposta")), chave)
                .stream()
                .findFirst();
    }

    @Override
    public Reserva reservar(String chave, String operacao, UUID leadId, String hashDaRequisicao) {
        TransacaoObrigatoria.exigir("reservar comando sobre lead");
        int inserida = geral.update(INSERIR, chave, operacao, leadId, hashDaRequisicao);
        return geral.queryForObject(BUSCAR, (linha, indice) -> new Reserva(
                inserida == 1,
                linha.getString("idempotency_key"),
                linha.getString("operacao"),
                linha.getObject("lead_id", UUID.class),
                linha.getString("requisicao_hash"),
                linha.getString("resposta")), chave);
    }

    @Override
    public void concluir(String chave, String respostaJson) {
        TransacaoObrigatoria.exigir("concluir comando sobre lead");
        geral.update(CONCLUIR, respostaJson, chave);
    }
}
