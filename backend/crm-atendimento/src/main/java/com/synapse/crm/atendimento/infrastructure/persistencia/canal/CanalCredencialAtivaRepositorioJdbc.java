package com.synapse.crm.atendimento.infrastructure.persistencia.canal;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.canal.CanalCredencialAtivaRepositorio;
import com.synapse.crm.atendimento.application.canal.CanalEntradaAtiva;
import com.synapse.crm.atendimento.application.canal.ConfiguracaoCanalAtivo;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Repository
class CanalCredencialAtivaRepositorioJdbc implements CanalCredencialAtivaRepositorio {

    private static final String SQL =
            """
            SELECT c.id, NULLIF(btrim(cc.identificador_externo), '') AS identificador_externo
              FROM canal c
              LEFT JOIN canal_credencial cc
                ON cc.canal_id = c.id
               AND cc.ativo
               AND cc.vigente_desde <= now()
               AND (cc.vigente_ate IS NULL OR cc.vigente_ate > now())
             WHERE c.ativo
            """;

    private static final String SQL_POR_IDENTIFICADOR =
            """
            SELECT c.id AS canal_id, cc.id AS canal_credencial_id
              FROM canal c
              JOIN canal_credencial cc ON cc.canal_id = c.id
             WHERE c.ativo
               AND cc.ativo
               AND cc.identificador_externo = ?
               AND cc.vigente_desde <= now()
               AND (cc.vigente_ate IS NULL OR cc.vigente_ate > now())
            """;

    private static final String SQL_PRIMEIRA_ATIVA =
            """
            SELECT c.id AS canal_id, cc.id AS canal_credencial_id
              FROM canal c
              LEFT JOIN canal_credencial cc
                ON cc.canal_id = c.id
               AND cc.ativo
               AND cc.vigente_desde <= now()
               AND (cc.vigente_ate IS NULL OR cc.vigente_ate > now())
             WHERE c.ativo
             ORDER BY c.nome, cc.vigente_desde
             LIMIT 1
            """;

    private final JdbcTemplate chat;

    CanalCredencialAtivaRepositorioJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public ConfiguracaoCanalAtivo carregarConfiguracao() {
        Set<String> identificadores = new HashSet<>();
        int[] canais = {0};
        int[] semIdentificador = {0};
        chat.query(SQL, resultado -> {
            canais[0]++;
            String identificador = resultado.getString("identificador_externo");
            if (identificador == null) {
                semIdentificador[0]++;
            } else {
                identificadores.add(identificador);
            }
        });
        return new ConfiguracaoCanalAtivo(canais[0], semIdentificador[0], identificadores);
    }

    @Override
    public Optional<CanalEntradaAtiva> porIdentificadorExterno(String identificadorExterno) {
        if (identificadorExterno == null || identificadorExterno.isBlank()) return Optional.empty();
        return chat.query(
                        SQL_POR_IDENTIFICADOR,
                        resultado -> resultado.next()
                                ? Optional.of(new CanalEntradaAtiva(
                                        resultado.getObject("canal_id", java.util.UUID.class),
                                        resultado.getObject("canal_credencial_id", java.util.UUID.class)))
                                : Optional.empty(),
                        identificadorExterno);
    }

    @Override
    public Optional<CanalEntradaAtiva> primeiraAtiva() {
        return chat.query(
                SQL_PRIMEIRA_ATIVA,
                resultado -> resultado.next()
                        ? Optional.of(new CanalEntradaAtiva(
                                resultado.getObject("canal_id", java.util.UUID.class),
                                resultado.getObject("canal_credencial_id", java.util.UUID.class)))
                        : Optional.empty());
    }
}
