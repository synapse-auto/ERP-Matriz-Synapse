package com.synapse.crm.equipe.infrastructure.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.equipe.application.chat.ChatInternoRepositorio;
import com.synapse.crm.equipe.domain.chat.TipoConversaChat;
import com.synapse.crm.equipe.domain.usuario.StatusPresenca;

@Repository
class ChatInternoRepositorioJdbc implements ChatInternoRepositorio {
    private static final String SQL_LISTAR_CONVERSAS = """
            SELECT c.id, c.tipo::text,
                   COALESCE(string_agg(DISTINCT u.nome, ', ' ORDER BY u.nome), '') AS participantes,
                   ultima.conteudo AS ultima_mensagem, ultima.enviado_em AS ultima_mensagem_em,
                   COALESCE((SELECT count(*) FROM chat_interno_mensagem nova
                       WHERE nova.conversa_id = c.id AND nova.remetente_id <> ?
                         AND nova.enviado_em > COALESCE(cp.lido_ate, TIMESTAMPTZ 'epoch')), 0) AS nao_lidas,
                   CASE WHEN c.tipo = 'DIRETA' AND MAX(u.foto_referencia) IS NOT NULL
                        THEN '/api/v1/me/foto/' || MAX(u.id::text) END AS foto_url
              FROM chat_interno_conversa c
              JOIN chat_interno_participante cp ON cp.conversa_id = c.id AND cp.usuario_id = ?
              LEFT JOIN chat_interno_participante outros ON outros.conversa_id = c.id
                AND outros.usuario_id <> ?
              LEFT JOIN usuario u ON u.id = outros.usuario_id
              LEFT JOIN LATERAL (SELECT m.conteudo, m.enviado_em FROM chat_interno_mensagem m
                WHERE m.conversa_id = c.id ORDER BY m.enviado_em DESC LIMIT 1) ultima ON TRUE
             GROUP BY c.id, c.tipo, ultima.conteudo, ultima.enviado_em, cp.lido_ate
            """;
    private final JdbcTemplate jdbc;

    ChatInternoRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ConversaResumo> listarConversas(UUID usuarioId) {
        String sql = SQL_LISTAR_CONVERSAS
                + " ORDER BY COALESCE(ultima.enviado_em, c.criado_em) DESC";
        return jdbc.query(sql, ChatInternoRepositorioJdbc::mapearConversa, usuarioId, usuarioId, usuarioId);
    }

    @Override
    public List<ConversaResumo> listarConversasPaginado(UUID usuarioId, Instant depoisDe,
            UUID depoisDoId, int limite) {
        String base = SQL_LISTAR_CONVERSAS;
        String filtro = "";
        List<Object> parametros = new java.util.ArrayList<>(List.of(usuarioId, usuarioId, usuarioId));
        if (depoisDoId != null && depoisDe == null) {
            filtro = " WHERE ultima_mensagem_em IS NULL AND id < ?";
            parametros.add(depoisDoId);
        } else if (depoisDoId != null) {
            filtro = " WHERE (ultima_mensagem_em < ? OR (ultima_mensagem_em = ? AND id < ?)"
                    + " OR ultima_mensagem_em IS NULL)";
            parametros.add(Timestamp.from(depoisDe));
            parametros.add(Timestamp.from(depoisDe));
            parametros.add(depoisDoId);
        }
        String sql = "SELECT id,tipo,participantes,ultima_mensagem,ultima_mensagem_em,nao_lidas,foto_url FROM ("
                + base + ") itens" + filtro
                + " ORDER BY ultima_mensagem_em DESC NULLS LAST, id DESC LIMIT ?";
        parametros.add(Math.min(101, Math.max(1, limite)));
        return jdbc.query(sql, ChatInternoRepositorioJdbc::mapearConversa, parametros.toArray());
    }

    @Override
    public List<ContatoResumo> listarContatos(UUID usuarioId) {
        return jdbc.query("""
                SELECT id, nome, status_presenca::text,
                       CASE WHEN foto_referencia IS NOT NULL THEN '/api/v1/me/foto/' || id::text END AS foto_url
                  FROM usuario WHERE ativo AND id<>? ORDER BY nome
                """,
                (r, i) -> new ContatoResumo(r.getObject("id", UUID.class), r.getString("nome"),
                        r.getString("foto_url"), StatusPresenca.valueOf(r.getString("status_presenca"))), usuarioId);
    }

    @Override
    public Optional<UUID> conversaDireta(UUID primeiroUsuario, UUID segundoUsuario) {
        return jdbc.query("""
                SELECT c.id FROM chat_interno_conversa c
                JOIN chat_interno_participante p1 ON p1.conversa_id = c.id AND p1.usuario_id = ?
                JOIN chat_interno_participante p2 ON p2.conversa_id = c.id AND p2.usuario_id = ?
                WHERE c.tipo = 'DIRETA'
                LIMIT 1
                """, (r, i) -> r.getObject(1, UUID.class), primeiroUsuario, segundoUsuario)
                .stream().findFirst();
    }

    @Override
    public UUID criarConversaDireta(UUID primeiroUsuario, UUID segundoUsuario) {
        return jdbc.queryForObject("SELECT app_criar_conversa_direta(?, ?)", UUID.class,
                primeiroUsuario, segundoUsuario);
    }

    @Override
    public boolean usuarioExiste(UUID usuarioId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM usuario WHERE id=? AND ativo)", Boolean.class, usuarioId));
    }

    @Override
    public boolean participante(UUID conversaId, UUID usuarioId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM chat_interno_participante WHERE conversa_id=? AND usuario_id=?)",
                Boolean.class, conversaId, usuarioId));
    }

    @Override
    public List<UUID> participantes(UUID conversaId) {
        return jdbc.query("SELECT usuario_id FROM chat_interno_participante WHERE conversa_id=?",
                (r, i) -> r.getObject(1, UUID.class), conversaId);
    }

    @Override
    public PaginaMensagens listarMensagens(UUID conversaId, UUID usuarioId, Instant antesDe, int limite) {
        String cursor = antesDe == null ? "" : " AND m.enviado_em < ? ";
        Object[] args = antesDe == null
                ? new Object[] {conversaId, limite}
                : new Object[] {conversaId, antesDe, limite};
        List<MensagemResumo> mensagens = jdbc.query("""
                SELECT m.id,m.conversa_id,m.remetente_id,u.nome,m.tipo,m.conteudo,m.midia_url,m.midia_metadados,m.enviado_em
                  FROM chat_interno_mensagem m JOIN usuario u ON u.id=m.remetente_id
                 WHERE m.conversa_id=? %s ORDER BY m.enviado_em DESC LIMIT ?
                """.formatted(cursor), (r, i) -> new MensagemResumo(
                r.getObject("id", UUID.class), r.getObject("conversa_id", UUID.class),
                r.getObject("remetente_id", UUID.class), r.getString("nome"),
                r.getString("tipo"), r.getString("conteudo"),
                r.getString("midia_url"), r.getString("midia_metadados"),
                instant(r, "enviado_em")), args);
        Instant proximo = mensagens.size() == limite && !mensagens.isEmpty()
                ? mensagens.get(mensagens.size() - 1).enviadoEm() : null;
        return new PaginaMensagens(mensagens.reversed(), proximo);
    }

    @Override
    public MensagemResumo salvarMensagem(UUID conversaId, UUID remetenteId, String conteudo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO chat_interno_mensagem(id,conversa_id,remetente_id,tipo,conteudo) VALUES (?, ?, ?, 'TEXTO', ?)",
                id, conversaId, remetenteId, conteudo);
        return jdbc.queryForObject("SELECT m.id,m.conversa_id,m.remetente_id,u.nome,m.tipo,m.conteudo,m.midia_url,m.midia_metadados,m.enviado_em FROM chat_interno_mensagem m JOIN usuario u ON u.id=m.remetente_id WHERE m.id=?",
                (r, i) -> new MensagemResumo(r.getObject("id", UUID.class), r.getObject("conversa_id", UUID.class),
                        r.getObject("remetente_id", UUID.class), r.getString("nome"),
                        r.getString("tipo"), r.getString("conteudo"),
                        r.getString("midia_url"), r.getString("midia_metadados"),
                        instant(r, "enviado_em")), id);
    }

    @Override
    public MensagemResumo salvarMensagemDeMidia(UUID conversaId, UUID remetenteId, String tipo, String conteudo, String midiaUrl, String midiaMetadados) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO chat_interno_mensagem(id,conversa_id,remetente_id,tipo,conteudo,midia_url,midia_metadados) VALUES (?, ?, ?, ?::tipo_mensagem, ?, ?, ?::jsonb)",
                id, conversaId, remetenteId, tipo, conteudo, midiaUrl, midiaMetadados);
        return jdbc.queryForObject("SELECT m.id,m.conversa_id,m.remetente_id,u.nome,m.tipo,m.conteudo,m.midia_url,m.midia_metadados,m.enviado_em FROM chat_interno_mensagem m JOIN usuario u ON u.id=m.remetente_id WHERE m.id=?",
                (r, i) -> new MensagemResumo(r.getObject("id", UUID.class), r.getObject("conversa_id", UUID.class),
                        r.getObject("remetente_id", UUID.class), r.getString("nome"),
                        r.getString("tipo"), r.getString("conteudo"),
                        r.getString("midia_url"), r.getString("midia_metadados"),
                        instant(r, "enviado_em")), id);
    }

    @Override
    public void marcarComoLida(UUID conversaId, UUID usuarioId, Instant quando) {
        jdbc.update("UPDATE chat_interno_participante SET lido_ate=? WHERE conversa_id=? AND usuario_id=?", Timestamp.from(quando), conversaId, usuarioId);
    }

    private static ConversaResumo mapearConversa(ResultSet r, int ignored) throws SQLException {
        return new ConversaResumo(
                r.getObject("id", UUID.class), TipoConversaChat.valueOf(r.getString("tipo")),
                r.getString("participantes"), r.getString("ultima_mensagem"),
                instant(r, "ultima_mensagem_em"), r.getLong("nao_lidas"), r.getString("foto_url"));
    }

    private static Instant instant(ResultSet r, String coluna) throws SQLException {
        Timestamp valor = r.getTimestamp(coluna);
        return valor == null ? null : valor.toInstant();
    }
}
