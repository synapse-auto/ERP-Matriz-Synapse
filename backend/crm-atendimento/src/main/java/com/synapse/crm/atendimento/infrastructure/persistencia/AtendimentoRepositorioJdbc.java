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
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
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

    private static final String SQL_ABERTOS_VISIVEIS = "SELECT " + COLUNAS
            + " FROM atendimento WHERE status <> 'FINALIZADO' ORDER BY iniciado_em, id";

    private static final String SQL_MARCAR_COMO_LIDO =
            """
            INSERT INTO atendimento_leitura (atendimento_id, usuario_id, lido_ate)
            SELECT a.id, ?, ?
              FROM atendimento a
             WHERE a.lead_id = (SELECT origem.lead_id FROM atendimento origem WHERE origem.id = ?)
            ON CONFLICT (atendimento_id, usuario_id) DO UPDATE
                    SET lido_ate = GREATEST(atendimento_leitura.lido_ate, EXCLUDED.lido_ate)
            """;

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
        TransacaoObrigatoria.exigir("abertoDoLead");
        return primeiro(chat.query(SQL_ABERTO_DO_LEAD, MAPEADOR, leadId));
    }

    @Override
    public Optional<Atendimento> porId(UUID atendimentoId) {
        TransacaoObrigatoria.exigir("porId");
        return primeiro(chat.query(SQL_POR_ID, MAPEADOR, atendimentoId));
    }

    @Override
    public List<Atendimento> abertosVisiveis() {
        TransacaoObrigatoria.exigir("abertosVisiveis");
        return chat.query(SQL_ABERTOS_VISIVEIS, MAPEADOR);
    }

    @Override
    public void bloquearDistribuicaoDaAutomacao() {
        TransacaoObrigatoria.exigir("bloquearDistribuicaoDaAutomacao");
        chat.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtext('synapse:distribuicao-automacao'))",
                Object.class);
    }

    @Override
    public long contarAbertosDoAtendente(UUID atendenteId) {
        TransacaoObrigatoria.exigir("contarAbertosDoAtendente");
        Long quantidade = chat.queryForObject(
                "SELECT count(*) FROM atendimento WHERE atendente_id = ? AND status = 'EM_ATENDIMENTO'",
                Long.class,
                atendenteId);
        return quantidade == null ? 0 : quantidade;
    }

    @Override
    public void marcarComoLido(UUID atendimentoId, UUID usuarioId, Instant quando) {
        TransacaoObrigatoria.exigir("marcarComoLido");
        int linhas = chat.update(
                SQL_MARCAR_COMO_LIDO, usuarioId, Timestamp.from(quando), atendimentoId);
        // #region agent log
        try {
            String linha = "{\"sessionId\":\"ec4265\",\"hypothesisId\":\"A\",\"runId\":\"post-fix\",\"location\":\"AtendimentoRepositorioJdbc.marcarComoLido\",\"message\":\"leitura gravada\",\"data\":{\"atendimentoId\":\""
                    + atendimentoId
                    + "\",\"linhas\":"
                    + linhas
                    + "},\"timestamp\":"
                    + System.currentTimeMillis()
                    + "}\n";
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of(
                            "C:/Users/marcondes/Desktop/projeto_matriz/debug-ec4265.log"),
                    linha,
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
        // #endregion
    }

    @Override
    public Atendimento salvar(Atendimento atendimento) {
        TransacaoObrigatoria.exigir("salvar");
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
