package com.synapse.crm.atendimento.infrastructure.persistencia.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.origem.OrigemDaMensagem;
import com.synapse.crm.atendimento.application.origem.OrigemDeMensagemAutomaticaRepositorio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Tabela lateral de origem (V85): {@code mensagem} particionada nao e alterada. */
@Repository
class OrigemDeMensagemAutomaticaRepositorioJdbc implements OrigemDeMensagemAutomaticaRepositorio {

    /*
     * A faixa vale nas duas tabelas pelo instante, o que deixa o Postgres podar as particoes de
     * mensagem. O ramo SEM_ORIGEM_REGISTRADA expoe mensagens da IA que nao passaram por nenhum
     * caminho que grava origem (historico anterior a V85 ou caminho novo esquecido).
     */
    private static final String TOTAIS_POR_DIA = """
            SELECT dia, origem, count(*) AS mensagens, count(DISTINCT lead_id) AS leads
              FROM (SELECT (o.enviado_em AT TIME ZONE ?)::date AS dia, o.tipo AS origem, o.lead_id
                      FROM mensagem_origem_automacao o
                     WHERE o.enviado_em >= ? AND o.enviado_em < ?
                    UNION ALL
                    SELECT (m.enviado_em AT TIME ZONE ?)::date, ?, a.lead_id
                      FROM mensagem m
                      JOIN atendimento a ON a.id = m.atendimento_id
                     WHERE m.remetente_tipo = 'IA'
                       AND m.enviado_em >= ? AND m.enviado_em < ?
                       AND NOT EXISTS (SELECT 1 FROM mensagem_origem_automacao o WHERE o.mensagem_id = m.id)) x
             GROUP BY dia, origem
             ORDER BY dia, origem
            """;

    private final JdbcTemplate chat;

    OrigemDeMensagemAutomaticaRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public void registrar(UUID mensagemId, UUID atendimentoId, UUID leadId, Instant enviadoEm, OrigemDaMensagem origem) {
        TransacaoObrigatoria.exigir("registrar origem de mensagem automatica");
        chat.update(
                "INSERT INTO mensagem_origem_automacao"
                        + " (mensagem_id, atendimento_id, lead_id, enviado_em, tipo, regra_id, execucao_id)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (mensagem_id) DO NOTHING",
                mensagemId,
                atendimentoId,
                leadId,
                Timestamp.from(enviadoEm),
                origem.tipo().name(),
                origem.regraId(),
                origem.execucaoId());
    }

    @Override
    public List<TotalPorOrigem> totaisPorDia(LocalDate de, LocalDate ate, ZoneId zona) {
        TransacaoObrigatoria.exigir("totalizar mensagens automaticas por origem");
        Timestamp inicio = Timestamp.from(de.atStartOfDay(zona).toInstant());
        Timestamp fim = Timestamp.from(ate.plusDays(1).atStartOfDay(zona).toInstant());
        String fuso = zona.getId();
        return chat.query(
                TOTAIS_POR_DIA,
                (linha, indice) -> new TotalPorOrigem(
                        linha.getObject("dia", LocalDate.class),
                        linha.getString("origem"),
                        linha.getLong("mensagens"),
                        linha.getLong("leads")),
                fuso,
                inicio,
                fim,
                fuso,
                SEM_ORIGEM_REGISTRADA,
                inicio,
                fim);
    }
}
