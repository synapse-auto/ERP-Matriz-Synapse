package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.sharedkernel.permissao.ConsultaDeFuncionalidades;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Flags habilitadas para o cache de permissoes (Gestao, docs/47), que as rele a cada revalidacao.
 *
 * <p>No pool do chat pelo mesmo motivo das leituras de permissao: e caminho de toda requisicao
 * autenticada e nao pode esperar conexao atras de um relatorio pesado no pool geral. A porta HTTP
 * das flags continua sendo {@code FeatureService}.
 */
@Repository
class FuncionalidadesHabilitadasJdbc implements ConsultaDeFuncionalidades {

    private final JdbcTemplate chat;

    FuncionalidadesHabilitadasJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public Set<String> habilitadas() {
        return Set.copyOf(new HashSet<>(chat.queryForList(
                "SELECT chave FROM feature_flag WHERE habilitado = TRUE", String.class)));
    }
}
