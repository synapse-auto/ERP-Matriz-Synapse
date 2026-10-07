package com.synapse.crm.app.observabilidade;

import java.util.Optional;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import com.synapse.crm.atendimento.application.painel.EstadoDoPool;

/**
 * Le o estado de um pool Hikari sem abrir conexao nem derrubar quem chama (E225, PR 5). So contadores do MXBean do
 * proprio pool; antes do primeiro uso o pool ainda nao existe e a leitura devolve vazio.
 */
final class LeituraDePoolHikari {

    private LeituraDePoolHikari() {}

    static Optional<EstadoDoPool> ler(DataSource dataSource) {
        if (!(dataSource instanceof HikariDataSource hikari)) {
            return Optional.empty();
        }
        try {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            if (pool == null) {
                return Optional.empty();
            }
            return Optional.of(new EstadoDoPool(
                    hikari.getPoolName(),
                    pool.getActiveConnections(),
                    pool.getIdleConnections(),
                    pool.getThreadsAwaitingConnection(),
                    pool.getTotalConnections(),
                    hikari.getMaximumPoolSize()));
        } catch (RuntimeException erro) {
            // Observabilidade nunca derruba a requisicao nem o agendador.
            return Optional.empty();
        }
    }
}
