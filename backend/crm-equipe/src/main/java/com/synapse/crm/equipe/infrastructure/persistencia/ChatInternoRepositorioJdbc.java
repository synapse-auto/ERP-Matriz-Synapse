package com.synapse.crm.equipe.infrastructure.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.equipe.application.chat.ChatInternoRepositorio;
import com.synapse.crm.equipe.domain.chat.TipoConversaChat;
import com.synapse.crm.equipe.domain.usuario.StatusPresenca;

@Repository
class ChatInternoRepositorioJdbc implements ChatInternoRepositorio {
    private static final String SQL_LISTAR_CONVERSAS = """
            SELECT c.id, c.tipo::text,
                   CASE WHEN c.tipo = 'GRUPO' THEN c.nome
                        ELSE COALESCE(string_agg(DISTINCT u.nome, ', ' ORDER BY u.nome), '')
                   END AS participantes,
                   CASE WHEN ultima.removida_em IS NULL THEN ultima.conteudo END AS ultima_mensagem,
                   ultima.enviado_em AS ultima_mensagem_em,
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
              LEFT JOIN LATERAL (SELECT m.conteudo, m.enviado_em, m.removida_em FROM chat_interno_mensagem m
                WHERE m.conversa_id = c.id ORDER BY m.enviado_em DESC LIMIT 1) ultima ON TRUE
             GROUP BY c.id, c.tipo, c.nome, ultima.conteudo, ultima.enviado_em, ultima.removida_em, cp.lido_ate
            """;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    ChatInternoRepositorioJdbc(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
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
    public UUID criarConversaGrupo(String nome, List<UUID> participantes) {
        String literal = participantes.stream()
                .map(UUID::toString)
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        return jdbc.queryForObject(
                "SELECT app_criar_conversa_grupo(?, ?::uuid[])", UUID.class, nome, literal);
    }

    @Override
    public boolean usuarioExiste(UUID usuarioId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM usuario WHERE id=? AND ativo)", Boolean.class, usuarioId));
    }

    @Override
    public Optional<String> nomeDoUsuario(UUID usuarioId) {
        List<String> nomes = jdbc.query(
                "SELECT nome FROM usuario WHERE id=?",
                (r, i) -> r.getString(1),
                usuarioId);
        return nomes.stream().findFirst();
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
    public Optional<TipoConversaChat> tipoDaConversa(UUID conversaId) {
        List<TipoConversaChat> tipos = jdbc.query(
                "SELECT tipo::text FROM chat_interno_conversa WHERE id=?",
                (r, i) -> TipoConversaChat.valueOf(r.getString(1)),
                conversaId);
        return tipos.stream().findFirst();
    }

    @Override
    public Optional<String> nomeDoGrupo(UUID conversaId) {
        List<String> nomes = jdbc.query(
                "SELECT nome FROM chat_interno_conversa WHERE id=?",
                (r, i) -> r.getString(1),
                conversaId);
        return nomes.stream().findFirst();
    }

    @Override
    public void adicionarParticipante(UUID conversaId, UUID usuarioId) {
        jdbc.update(
                "INSERT INTO chat_interno_participante(conversa_id, usuario_id) VALUES (?, ?)",
                conversaId,
                usuarioId);
    }

    @Override
    public void removerParticipante(UUID conversaId, UUID usuarioId) {
        jdbc.update(
                "DELETE FROM chat_interno_participante WHERE conversa_id=? AND usuario_id=?",
                conversaId,
                usuarioId);
    }

    @Override
    public void renomearGrupo(UUID conversaId, String nome) {
        jdbc.update("UPDATE chat_interno_conversa SET nome=? WHERE id=? AND tipo='GRUPO'", nome, conversaId);
    }

    @Override
    public boolean apagarSeSemParticipantes(UUID conversaId) {
        Boolean apagou = jdbc.queryForObject(
                "SELECT app_apagar_conversa_chat_se_vazia(?)", Boolean.class, conversaId);
        return Boolean.TRUE.equals(apagou);
    }

    @Override
    public PaginaMensagens listarMensagens(UUID conversaId, UUID usuarioId, Instant antesDe, int limite) {
        String cursor = antesDe == null ? "" : " AND m.enviado_em < ? ";
        Object[] args = antesDe == null
                ? new Object[] {conversaId, limite}
                : new Object[] {conversaId, antesDe, limite};
        List<MensagemResumo> mensagens = jdbc.query("""
                SELECT m.id,m.conversa_id,m.remetente_id,u.nome,m.tipo,
                       CASE WHEN m.removida_em IS NULL THEN m.conteudo END AS conteudo,
                       CASE WHEN m.removida_em IS NULL THEN m.midia_url END AS midia_url,
                       CASE WHEN m.removida_em IS NULL THEN m.midia_metadados END AS midia_metadados,
                       m.enviado_em,m.removida_em IS NOT NULL AS removida,
                       m.referencia_origem_id,m.referencia_tipo,m.referencia_autor,
                       m.referencia_tipo_conteudo,m.referencia_previa,m.referencia_origem_removida
                  FROM chat_interno_mensagem m JOIN usuario u ON u.id=m.remetente_id
                 WHERE m.conversa_id=? %s ORDER BY m.enviado_em DESC LIMIT ?
                """.formatted(cursor), (r, i) -> new MensagemResumo(
                r.getObject("id", UUID.class), r.getObject("conversa_id", UUID.class),
                r.getObject("remetente_id", UUID.class), r.getString("nome"),
                r.getString("tipo"), r.getString("conteudo"),
                r.getString("midia_url"), r.getString("midia_metadados"),
                instant(r, "enviado_em"), List.of(), r.getBoolean("removida"),
                referencia(r)), args);
        Instant proximo = mensagens.size() == limite && !mensagens.isEmpty()
                ? mensagens.get(mensagens.size() - 1).enviadoEm() : null;
        return new PaginaMensagens(mensagens.reversed(), proximo);
    }

    @Override
    public List<MidiaResumo> listarMidias(UUID conversaId, int limite, int deslocamento) {
        return jdbc.query("""
                SELECT m.id, m.tipo::text, m.midia_url, m.midia_metadados, m.enviado_em
                  FROM chat_interno_mensagem m
                 WHERE m.conversa_id=?
                   AND m.removida_em IS NULL
                   AND m.midia_url IS NOT NULL
                   AND m.tipo IN ('IMAGEM','AUDIO','DOCUMENTO','VIDEO')
                 ORDER BY m.enviado_em DESC, m.id DESC
                 LIMIT ? OFFSET ?
                """, this::mapearMidia, conversaId, limite, deslocamento);
    }

    @Override
    public Optional<MidiaResumo> midia(UUID conversaId, UUID mensagemId) {
        return jdbc.query("""
                SELECT m.id, m.tipo::text, m.midia_url, m.midia_metadados, m.enviado_em
                  FROM chat_interno_mensagem m
                 WHERE m.conversa_id=? AND m.id=?
                   AND m.removida_em IS NULL
                   AND m.midia_url IS NOT NULL
                   AND m.tipo IN ('IMAGEM','AUDIO','DOCUMENTO','VIDEO')
                """, this::mapearMidia, conversaId, mensagemId).stream().findFirst();
    }

    @Override
    public MensagemResumo salvarMensagem(UUID conversaId, UUID remetenteId, String conteudo) {
        return inserirMensagem(conversaId, remetenteId, "TEXTO", conteudo);
    }

    @Override
    public MensagemResumo salvarMensagemSistema(UUID conversaId, UUID atorId, String conteudoJson) {
        return inserirMensagem(conversaId, atorId, "SISTEMA", conteudoJson);
    }

    private MensagemResumo inserirMensagem(UUID conversaId, UUID remetenteId, String tipo, String conteudo) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO chat_interno_mensagem(id,conversa_id,remetente_id,tipo,conteudo)"
                        + " VALUES (?, ?, ?, ?::tipo_mensagem, ?)",
                id,
                conversaId,
                remetenteId,
                tipo,
                conteudo);
        return jdbc.queryForObject(
                "SELECT m.id,m.conversa_id,m.remetente_id,u.nome,m.tipo,m.conteudo,m.midia_url,m.midia_metadados,m.enviado_em"
                        + " FROM chat_interno_mensagem m JOIN usuario u ON u.id=m.remetente_id WHERE m.id=?",
                (r, i) -> new MensagemResumo(
                        r.getObject("id", UUID.class),
                        r.getObject("conversa_id", UUID.class),
                        r.getObject("remetente_id", UUID.class),
                        r.getString("nome"),
                        r.getString("tipo"),
                        r.getString("conteudo"),
                        r.getString("midia_url"),
                        r.getString("midia_metadados"),
                        instant(r, "enviado_em")),
                id);
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
    public Optional<MensagemResumo> mensagem(UUID conversaId, UUID mensagemId) {
        return jdbc.query("""
                SELECT m.id,m.conversa_id,m.remetente_id,u.nome,m.tipo,
                       CASE WHEN m.removida_em IS NULL THEN m.conteudo END AS conteudo,
                       CASE WHEN m.removida_em IS NULL THEN m.midia_url END AS midia_url,
                       CASE WHEN m.removida_em IS NULL THEN m.midia_metadados END AS midia_metadados,
                       m.enviado_em,m.removida_em IS NOT NULL AS removida,
                       m.referencia_origem_id,m.referencia_tipo,m.referencia_autor,
                       m.referencia_tipo_conteudo,m.referencia_previa,m.referencia_origem_removida
                  FROM chat_interno_mensagem m JOIN usuario u ON u.id=m.remetente_id
                 WHERE m.conversa_id=? AND m.id=?
                """, (r, i) -> mapearMensagem(r), conversaId, mensagemId).stream().findFirst();
    }

    @Override
    public MensagemResumo salvarMensagemComReferencia(UUID conversaId, UUID remetenteId, String conteudo,
            String tipo, String midiaUrl, String midiaMetadados, UUID origemConversaId, UUID origemId,
            String referenciaTipo) {
        MensagemResumo origem = mensagem(origemConversaId, origemId)
                .orElseThrow(() -> new IllegalArgumentException("Mensagem de origem nao encontrada."));
        UUID id = UUID.randomUUID();
        String previa = previa(origem);
        jdbc.update("""
                INSERT INTO chat_interno_mensagem(
                    id,conversa_id,remetente_id,tipo,conteudo,midia_url,midia_metadados,
                    referencia_origem_id,referencia_tipo,referencia_autor,referencia_tipo_conteudo,
                    referencia_previa,referencia_origem_removida)
                VALUES (?, ?, ?, ?::tipo_mensagem, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                """, id, conversaId, remetenteId, tipo, conteudo, midiaUrl, midiaMetadados,
                origem.id(), referenciaTipo, origem.remetenteNome(), origem.tipo(), previa, origem.removida());
        return mensagem(conversaId, id).orElseThrow();
    }

    @Override
    public MensagemResumo removerMensagem(UUID conversaId, UUID mensagemId, UUID remetenteId, Instant removidaEm) {
        int alteradas = jdbc.update("""
                UPDATE chat_interno_mensagem
                   SET removida_em=?, midia_url=NULL, midia_metadados=NULL, conteudo=NULL
                 WHERE conversa_id=? AND id=? AND remetente_id=? AND removida_em IS NULL
                """, Timestamp.from(removidaEm), conversaId, mensagemId, remetenteId);
        if (alteradas == 0) {
            throw new IllegalArgumentException("Mensagem inexistente ou ja removida.");
        }
        return mensagem(conversaId, mensagemId).orElseThrow();
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

    private MensagemResumo mapearMensagem(ResultSet r) throws SQLException {
        return new MensagemResumo(
                r.getObject("id", UUID.class), r.getObject("conversa_id", UUID.class),
                r.getObject("remetente_id", UUID.class), r.getString("nome"), r.getString("tipo"),
                r.getString("conteudo"), r.getString("midia_url"), r.getString("midia_metadados"),
                instant(r, "enviado_em"), List.of(), r.getBoolean("removida"), referencia(r));
    }

    private static ReferenciaResumo referencia(ResultSet r) throws SQLException {
        UUID origemId = r.getObject("referencia_origem_id", UUID.class);
        String tipo = r.getString("referencia_tipo");
        boolean origemRemovida = r.getBoolean("referencia_origem_removida");
        return origemId == null && tipo == null && !origemRemovida ? null
                : new ReferenciaResumo(origemId, tipo, r.getString("referencia_autor"),
                        r.getString("referencia_tipo_conteudo"), r.getString("referencia_previa"), origemRemovida);
    }

    private String previa(MensagemResumo origem) {
        if (origem.removida()) {
            return "";
        }
        if (origem.conteudo() != null && !origem.conteudo().isBlank()) {
            return resumir(origem.conteudo());
        }
        try {
            JsonNode metadados = origem.midiaMetadados() == null
                    ? json.createObjectNode() : json.readTree(origem.midiaMetadados());
            String legenda = texto(metadados, "legenda", "nome_original", "nome");
            return legenda == null ? "" : resumir(legenda);
        } catch (Exception e) {
            return "";
        }
    }

    private static String resumir(String valor) {
        String compacto = valor.replaceAll("[\\n\\r\\t]+", " ").replaceAll(" +", " ").trim();
        return compacto.length() <= 120 ? compacto : compacto.substring(0, 120);
    }

    private MidiaResumo mapearMidia(ResultSet r, int ignored) throws SQLException {
        JsonNode metadados;
        try {
            metadados = r.getString("midia_metadados") == null
                    ? json.createObjectNode() : json.readTree(r.getString("midia_metadados"));
        } catch (Exception e) {
            metadados = json.createObjectNode();
        }
        return new MidiaResumo(
                r.getObject("id", UUID.class),
                r.getString("tipo"),
                texto(metadados, "nome_original", "nome"),
                texto(metadados, "mimetype"),
                numero(metadados, "tamanho_bytes", "tamanho"),
                texto(metadados, "legenda"),
                r.getString("midia_url"),
                instant(r, "enviado_em"));
    }

    private static String texto(JsonNode metadados, String... campos) {
        for (String campo : campos) {
            JsonNode valor = metadados.get(campo);
            if (valor != null && !valor.isNull() && !valor.asText().isBlank()) {
                return valor.asText();
            }
        }
        return null;
    }

    private static long numero(JsonNode metadados, String... campos) {
        for (String campo : campos) {
            JsonNode valor = metadados.get(campo);
            if (valor != null && valor.isNumber()) {
                return valor.asLong();
            }
        }
        return 0;
    }
}
