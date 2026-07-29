package com.synapse.crm.automacaoconfig.infrastructure.reacao;

import java.sql.Timestamp;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.automacaoconfig.domain.evento.ConfiguracaoAutomacaoAtualizada;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Registra em {@code audit_log} toda alteracao de parametro de automacao, com o valor antes e
 * depois — mesmo padrao de {@code AuditoriaDeAtendimentoListener} (crm-atendimento).
 *
 * <p>{@code entidade_id} fica nulo: a chave primaria de {@code configuracao_automacao} e texto
 * ({@code chave}), nao {@code UUID}, e a coluna nao muda de tipo por causa disto — a chave vai dentro
 * de {@code dados_antes}/{@code dados_depois}, que ja sao JSONB.
 */
@Component
class AuditoriaDeConfiguracaoAutomacaoListener {

    private static final String SQL =
            """
            INSERT INTO audit_log (ator_id, ator_tipo, acao, entidade_tipo, dados_antes, dados_depois, criado_em)
                 VALUES (?, 'USUARIO'::origem_evento, 'ATUALIZAR_CONFIGURACAO_AUTOMACAO',
                         'configuracao_automacao', ?::jsonb, ?::jsonb, ?)
            """;

    private final JdbcTemplate geral;

    AuditoriaDeConfiguracaoAutomacaoListener(@Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource generalDataSource) {
        this.geral = new JdbcTemplate(generalDataSource);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void aoAtualizar(ConfiguracaoAutomacaoAtualizada evento) {
        geral.update(
                SQL,
                evento.atualizadoPorId(),
                dadosJson(evento.chave(), evento.valorAntes()),
                dadosJson(evento.chave(), evento.valorDepois()),
                Timestamp.from(evento.ocorridoEm()));
    }

    private static String dadosJson(String chave, String valor) {
        return "{\"chave\":\"" + escapar(chave) + "\",\"valor\":\"" + escapar(valor) + "\"}";
    }

    private static String escapar(String texto) {
        return texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
