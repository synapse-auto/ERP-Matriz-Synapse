package com.synapse.crm.atendimento.infrastructure.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Adaptador de atendimento sobre o pool do chat.
 *
 * <p>JDBC e nao JPA. O motivo nao e preferencia: para mensagem, atendimento e contadores do lead
 * caberem numa transacao so, todos precisam da mesma conexao — e a conexao do caminho critico vem do
 * {@code chatDataSource}, que nao tem {@code EntityManagerFactory} proprio (isso e a E09).
 *
 * <p>Nenhum {@code WHERE} aqui filtra por atendente. Quem recorta e a politica RLS de
 * {@code atendimento}, com o contexto que o gerente de transacao publica no inicio da transacao —
 * inclusive neste pool. Um atendente que peca o atendimento de um colega recebe zero linhas, e o caso
 * de uso responde "nao encontrado".
 */
@Repository
class AtendimentoRepositorioJdbc implements AtendimentoRepositorio {

    private static final String COLUNAS =
            "id, lead_id, canal_id, canal_credencial_id, atendente_id, status, iniciado_em, finalizado_em";

    /**
     * O aberto mais recente. {@code LIMIT 1} e nao "exatamente um": se por qualquer motivo houver
     * dois abertos, atender pelo mais recente e melhor que estourar excecao no caminho critico.
     */
    private static final String SQL_ABERTO_DO_LEAD = "SELECT " + COLUNAS + " FROM atendimento"
            + " WHERE lead_id = ? AND status <> 'FINALIZADO'"
            + " ORDER BY iniciado_em DESC LIMIT 1";

    private static final String SQL_POR_ID = "SELECT " + COLUNAS + " FROM atendimento WHERE id = ?";

    // Upsert: o caso de uso nao precisa saber se esta abrindo ou atualizando.
    private static final String SQL_SALVAR =
            """
            INSERT INTO atendimento (id, lead_id, canal_id, canal_credencial_id, atendente_id,
                                     status, iniciado_em, finalizado_em)
                 VALUES (?, ?, ?, ?, ?, ?::status_atendimento, ?, ?)
            ON CONFLICT (id) DO UPDATE
                    SET atendente_id  = EXCLUDED.atendente_id,
                        status        = EXCLUDED.status,
                        finalizado_em = EXCLUDED.finalizado_em
            """;

    private static final RowMapper<Atendimento> MAPEADOR = AtendimentoRepositorioJdbc::paraDominio;

    private final JdbcTemplate chat;

    AtendimentoRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public Optional<Atendimento> abertoDoLead(UUID leadId) {
        return primeiro(chat.query(SQL_ABERTO_DO_LEAD, MAPEADOR, leadId));
    }

    @Override
    public Optional<Atendimento> porId(UUID atendimentoId) {
        return primeiro(chat.query(SQL_POR_ID, MAPEADOR, atendimentoId));
    }

    @Override
    public Atendimento salvar(Atendimento atendimento) {
        chat.update(
                SQL_SALVAR,
                atendimento.id(),
                atendimento.leadId(),
                atendimento.canalId(),
                atendimento.canalCredencialId(),
                atendimento.atendenteId(),
                atendimento.status().name(),
                Timestamp.from(atendimento.iniciadoEm()),
                atendimento.finalizadoEm() == null ? null : Timestamp.from(atendimento.finalizadoEm()));
        return atendimento;
    }

    private static Optional<Atendimento> primeiro(List<Atendimento> encontrados) {
        return encontrados.isEmpty() ? Optional.empty() : Optional.of(encontrados.get(0));
    }

    private static Atendimento paraDominio(ResultSet linha, int indice) throws SQLException {
        return new Atendimento(
                linha.getObject("id", UUID.class),
                linha.getObject("lead_id", UUID.class),
                linha.getObject("canal_id", UUID.class),
                linha.getObject("canal_credencial_id", UUID.class),
                linha.getObject("atendente_id", UUID.class),
                StatusAtendimento.valueOf(linha.getString("status")),
                instante(linha, "iniciado_em"),
                instante(linha, "finalizado_em"));
    }

    private static Instant instante(ResultSet linha, String coluna) throws SQLException {
        Timestamp valor = linha.getTimestamp(coluna);
        return valor == null ? null : valor.toInstant();
    }
}
