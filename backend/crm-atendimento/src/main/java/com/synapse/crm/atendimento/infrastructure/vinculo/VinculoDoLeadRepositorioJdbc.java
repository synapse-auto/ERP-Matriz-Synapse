package com.synapse.crm.atendimento.infrastructure.vinculo;

import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.VinculoDoLeadRepositorio;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Leitura no pool do chat (mesma transação do rodízio, em contexto de serviço: o join com {@code atendimento} passa
 * pela RLS como serviço); escrita no pool geral, depois do commit, como os demais listeners de reação.
 */
@Repository
class VinculoDoLeadRepositorioJdbc implements VinculoDoLeadRepositorio {

    static final String CHAVE = "ia.distribuicao.atendente_vinculado";

    /** Chave desligada = nenhuma linha, sem tocar em lead_atendente_vinculado. */
    private static final String SQL_VINCULADO_DO_ATENDIMENTO =
            """
            SELECT v.atendente_id
              FROM configuracao_automacao c
              JOIN atendimento a ON a.id = ?
              JOIN lead_atendente_vinculado v ON v.lead_id = a.lead_id
             WHERE c.chave = ? AND lower(btrim(c.valor)) = 'true'
            """;

    private static final String SQL_HABILITADO =
            "SELECT lower(btrim(valor)) = 'true' FROM configuracao_automacao WHERE chave = ?";

    private static final String SQL_REGISTRAR =
            """
            INSERT INTO lead_atendente_vinculado (lead_id, atendente_id, origem)
                 VALUES (?, ?, ?)
            ON CONFLICT (lead_id) DO UPDATE
                    SET atendente_id = EXCLUDED.atendente_id,
                        origem       = EXCLUDED.origem,
                        vinculado_em = now()
            """;

    private final JdbcTemplate chat;
    private final JdbcTemplate geral;

    VinculoDoLeadRepositorioJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource,
            @Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource generalDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
        this.geral = new JdbcTemplate(generalDataSource);
    }

    @Override
    public Optional<UUID> vinculadoDoAtendimento(UUID atendimentoId) {
        return chat.query(
                        SQL_VINCULADO_DO_ATENDIMENTO,
                        (linha, indice) -> linha.getObject("atendente_id", UUID.class),
                        atendimentoId,
                        CHAVE)
                .stream()
                .findFirst();
    }

    @Override
    public boolean habilitado() {
        return Boolean.TRUE.equals(geral.query(
                SQL_HABILITADO, resultados -> resultados.next() ? resultados.getBoolean(1) : Boolean.FALSE, CHAVE));
    }

    @Override
    public void registrar(UUID leadId, UUID atendenteId, String origem) {
        geral.update(SQL_REGISTRAR, leadId, atendenteId, origem);
    }
}
