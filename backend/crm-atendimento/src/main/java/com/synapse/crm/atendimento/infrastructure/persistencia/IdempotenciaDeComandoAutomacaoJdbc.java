package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.IdempotenciaDeComandoAutomacao;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Adaptador JDBC da reserva de comandos, no mesmo pool transacional do atendimento. */
@Repository
class IdempotenciaDeComandoAutomacaoJdbc implements IdempotenciaDeComandoAutomacao {

    private static final String INSERIR =
            "INSERT INTO comando_automacao_idempotencia "
                    + "(idempotency_key, operacao, atendimento_id, requisicao_hash) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT (idempotency_key) DO NOTHING";

    private static final String BUSCAR =
            "SELECT idempotency_key, operacao, atendimento_id, requisicao_hash, resposta "
                    + "FROM comando_automacao_idempotencia WHERE idempotency_key = ?";

    private static final String CONCLUIR =
            "UPDATE comando_automacao_idempotencia SET resposta = ?::jsonb "
                    + "WHERE idempotency_key = ? AND resposta IS NULL";

    private final JdbcTemplate chat;

    IdempotenciaDeComandoAutomacaoJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public Reserva reservar(String chave, String operacao, UUID atendimentoId, String hashDaRequisicao) {
        TransacaoObrigatoria.exigir("reservar comando da Automacao");
        int inserida = chat.update(INSERIR, chave, operacao, atendimentoId, hashDaRequisicao);
        return chat.queryForObject(BUSCAR, (linha, indice) -> new Reserva(
                inserida == 1,
                linha.getString("idempotency_key"),
                linha.getString("operacao"),
                linha.getObject("atendimento_id", UUID.class),
                linha.getString("requisicao_hash"),
                linha.getString("resposta")), chave);
    }

    @Override
    public void concluir(String chave, String respostaJson) {
        TransacaoObrigatoria.exigir("concluir comando da Automacao");
        chat.update(CONCLUIR, respostaJson, chave);
    }
}
