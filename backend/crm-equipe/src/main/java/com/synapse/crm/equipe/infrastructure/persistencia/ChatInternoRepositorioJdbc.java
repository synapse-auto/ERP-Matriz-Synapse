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

@Repository
class ChatInternoRepositorioJdbc implements ChatInternoRepositorio {
    private final JdbcTemplate jdbc;

    ChatInternoRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ConversaResumo> listarConversas(UUID usuarioId) {
        String sql = """
                SELECT c.id, c.tipo::text,
                       COALESCE(string_agg(DISTINCT u.nome, ', ' ORDER BY u.nome), '') AS participantes,
                       ultima.conteudo AS ultima_mensagem, ultima.enviado_em AS ultima_mensagem_em,
                       COALESCE((SELECT count(*) FROM chat_interno_mensagem nova
                           WHERE nova.conversa_id = c.id AND nova.remetente_id <> ?
                             AND nova.enviado_em > COALESCE(cp.lido_ate, TIMESTAMPTZ 'epoch')), 0) AS nao_lidas
                  FROM chat_interno_conversa c
                  JOIN chat_interno_participante cp ON cp.conversa_id = c.id AND cp.usuario_id = ?
                  LEFT JOIN chat_interno_participante outros ON outros.conversa_id = c.id
                    AND outros.usuario_id <> ?
                  LEFT JOIN usuario u ON u.id = outros.usuario_id
                  LEFT JOIN LATERAL (SELECT m.conteudo, m.enviado_em FROM chat_interno_mensagem m
                    WHERE m.conversa_id = c.id ORDER BY m.enviado_em DESC LIMIT 1) ultima ON TRUE
                 GROUP BY c.id, c.tipo, ultima.conteudo, ultima.enviado_em, cp.lido_ate
                 ORDER BY COALESCE(ultima.enviado_em, c.criado_em) DESC
                """;
        return jdbc.query(sql, (r, i) -> new ConversaResumo(
                r.getObject("id", UUID.class), TipoConversaChat.valueOf(r.getString("tipo")),
                r.getString("participantes"), r.getString("ultima_mensagem"),
                instant(r, "ultima_mensagem_em"), r.getLong("nao_lidas")), usuarioId, usuarioId, usuarioId);
    }

    @Override
    public List<ContatoResumo> listarContatos(UUID usuarioId) {
        return jdbc.query("SELECT id,nome FROM usuario WHERE ativo AND id<>? ORDER BY nome",
                (r, i) -> new ContatoResumo(r.getObject("id", UUID.class), r.getString("nome")), usuarioId);
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
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext('synapse:chat-interno:direta'))", Object.class);
        Optional<UUID> existente = conversaDireta(primeiroUsuario, segundoUsuario);
        if (existente.isPresent()) return existente.get();
        UUID conversa = UUID.randomUUID();
        jdbc.update("INSERT INTO chat_interno_conversa(id,tipo) VALUES (?, 'DIRETA')", conversa);
        jdbc.update("INSERT INTO chat_interno_participante(conversa_id,usuario_id) VALUES (?,?), (?,?)",
                conversa, primeiroUsuario, conversa, segundoUsuario);
        return conversa;
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
                SELECT m.id,m.conversa_id,m.remetente_id,u.nome,m.conteudo,m.enviado_em
                  FROM chat_interno_mensagem m JOIN usuario u ON u.id=m.remetente_id
                 WHERE m.conversa_id=? %s ORDER BY m.enviado_em DESC LIMIT ?
                """.formatted(cursor), (r, i) -> new MensagemResumo(
                r.getObject("id", UUID.class), r.getObject("conversa_id", UUID.class),
                r.getObject("remetente_id", UUID.class), r.getString("nome"),
                r.getString("conteudo"), instant(r, "enviado_em")), args);
        Instant proximo = mensagens.size() == limite && !mensagens.isEmpty()
                ? mensagens.get(mensagens.size() - 1).enviadoEm() : null;
        return new PaginaMensagens(mensagens.reversed(), proximo);
    }

    @Override
    public MensagemResumo salvarMensagem(UUID conversaId, UUID remetenteId, String conteudo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO chat_interno_mensagem(id,conversa_id,remetente_id,tipo,conteudo) VALUES (?, ?, ?, 'TEXTO', ?)",
                id, conversaId, remetenteId, conteudo);
        return jdbc.queryForObject("SELECT m.id,m.conversa_id,m.remetente_id,u.nome,m.conteudo,m.enviado_em FROM chat_interno_mensagem m JOIN usuario u ON u.id=m.remetente_id WHERE m.id=?",
                (r, i) -> new MensagemResumo(r.getObject("id", UUID.class), r.getObject("conversa_id", UUID.class),
                        r.getObject("remetente_id", UUID.class), r.getString("nome"), r.getString("conteudo"), instant(r, "enviado_em")), id);
    }

    @Override
    public void marcarComoLida(UUID conversaId, UUID usuarioId, Instant quando) {
        jdbc.update("UPDATE chat_interno_participante SET lido_ate=? WHERE conversa_id=? AND usuario_id=?", Timestamp.from(quando), conversaId, usuarioId);
    }

    private static Instant instant(ResultSet r, String coluna) throws SQLException {
        Timestamp valor = r.getTimestamp(coluna);
        return valor == null ? null : valor.toInstant();
    }
}
