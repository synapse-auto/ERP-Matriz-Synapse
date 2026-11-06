package com.synapse.crm.atendimento.infrastructure.outbox;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Uma consulta ao outro pool pode vincular uma conexao passiva em autocommit ao mesmo thread. */
final class ConexaoDaTransacaoAtual {
    private ConexaoDaTransacaoAtual() {}

    static DataSource exigir(DataSource geral, DataSource chat) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("escrita da ficha exige transacao ativa");
        }
        boolean geralAtivo = transacional(geral);
        boolean chatAtivo = transacional(chat);
        if (geralAtivo == chatAtivo) {
            throw new IllegalStateException("escrita da ficha exige exatamente uma conexao transacional");
        }
        return geralAtivo ? geral : chat;
    }

    private static boolean transacional(DataSource fonte) {
        Object recurso = TransactionSynchronizationManager.getResource(fonte);
        if (!(recurso instanceof ConnectionHolder conexao) || conexao.getConnectionHandle() == null) return false;
        try {
            // Nao pede conexao ao pool nem altera autocommit/RLS: inspeciona somente o recurso vinculado.
            return !conexao.getConnection().getAutoCommit();
        } catch (SQLException falha) {
            throw new IllegalStateException("nao foi possivel identificar a transacao da ficha", falha);
        }
    }
}
