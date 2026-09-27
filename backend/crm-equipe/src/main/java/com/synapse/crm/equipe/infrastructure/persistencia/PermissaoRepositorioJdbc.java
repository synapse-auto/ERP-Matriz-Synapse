package com.synapse.crm.equipe.infrastructure.persistencia;

import java.sql.Array;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.equipe.application.permissao.PermissaoRepositorio;
import com.synapse.crm.equipe.application.permissao.RevisaoDesatualizadaException;
import com.synapse.crm.equipe.domain.permissao.Capacidade;
import com.synapse.crm.equipe.domain.permissao.ConfiguracaoDePermissoes;
import com.synapse.crm.equipe.domain.permissao.Modulo;
import com.synapse.crm.equipe.domain.permissao.NivelDeAcesso;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Adaptador JDBC das tabelas {@code permissao_*} (V83).
 *
 * <p>Chave desconhecida no banco (capacidade removida do catalogo, ou gravada por versao mais nova
 * durante um rollback) e ignorada na leitura e preservada na escrita — nunca apagada por quem nao a
 * entende.
 */
@Repository
class PermissaoRepositorioJdbc implements PermissaoRepositorio {

    private static final Logger log = LoggerFactory.getLogger(PermissaoRepositorioJdbc.class);
    private static final String NIVEL = "NIVEL";
    private static final String ACAO = "ACAO";
    /** Nunca gravada pela V83 (comeca em 0): a revisao muda quando as tabelas aparecem. */
    private static final long REVISAO_SEM_TABELAS = -1;

    private final JdbcTemplate jdbc;
    /**
     * Leituras do cache de permissoes ({@link #contextoDe}, {@link #revisaoGlobal}) no pool do chat.
     * Elas acontecem no caminho de TODA requisicao autenticada (filtro de sessao, {@code @capacidades});
     * no pool geral, um relatorio pesado que o esgotasse faria o envio de mensagem esperar por
     * conexao — exatamente o que o Bulkhead existe para impedir. Sao consultas por chave primaria,
     * raras (so em revisao nova), e sempre de dado ja commitado.
     */
    private final JdbcTemplate leitura;
    private final ObjectMapper json;
    /**
     * Banco novo sobe pausado antes da V73 (runner controlado, docs/41) e, portanto, sem as tabelas
     * da V83. Nesse estado o cache usa o padrao de cada papel — o mesmo acesso de antes da Gestao; nao
     * ha revogacao salva sem as tabelas. Uma vez vistas, as tabelas nao somem: a checagem para.
     */
    private volatile boolean tabelasPresentes;
    private volatile boolean ausenciaAvisada;

    PermissaoRepositorioJdbc(JdbcTemplate jdbc, @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chat, ObjectMapper json) {
        this.jdbc = jdbc;
        this.leitura = new JdbcTemplate(chat);
        this.json = json;
    }

    @Override
    public Armazenado perfil(PapelUsuario papel) {
        Long revisao = jdbc.query("SELECT revisao FROM permissao_perfil WHERE papel = CAST(? AS papel_usuario)",
                (r, i) -> r.getLong(1), papel.name()).stream().findFirst().orElse(0L);
        List<Item> itens = jdbc.query(
                "SELECT tipo, alvo, valor FROM permissao_perfil_item WHERE papel = CAST(? AS papel_usuario)",
                (r, i) -> new Item(r.getString(1), r.getString(2), r.getString(3)), papel.name());
        return new Armazenado(revisao, montar(itens));
    }

    @Override
    public Armazenado excecoesDe(UUID usuarioId) {
        Long revisao = jdbc.query("SELECT revisao FROM permissao_usuario WHERE usuario_id = ?",
                (r, i) -> r.getLong(1), usuarioId).stream().findFirst().orElse(0L);
        List<Item> itens = jdbc.query(
                "SELECT tipo, alvo, valor FROM permissao_usuario_excecao WHERE usuario_id = ?",
                (r, i) -> new Item(r.getString(1), r.getString(2), r.getString(3)), usuarioId);
        return new Armazenado(revisao, montar(itens));
    }

    /**
     * Uma ida ao banco: papel/situacao atuais, itens do perfil do papel e excecoes do usuario. E o
     * caminho frio do cache; o quente nao consulta nada.
     */
    @Override
    public Optional<ContextoDeAcesso> contextoDe(UUID usuarioId) {
        if (!tabelasPresentes()) {
            return leitura.query("SELECT papel::text, ativo FROM usuario WHERE id = ?",
                    (r, i) -> new ContextoDeAcesso(usuarioId, PapelUsuario.valueOf(r.getString(1)), r.getBoolean(2),
                            montar(List.of()), montar(List.of())),
                    usuarioId).stream().findFirst();
        }
        List<String[]> linhas = leitura.query("""
                SELECT u.papel::text, u.ativo::text, 'P', i.tipo, i.alvo, i.valor
                  FROM usuario u LEFT JOIN permissao_perfil_item i ON i.papel = u.papel
                 WHERE u.id = ?
                UNION ALL
                SELECT u.papel::text, u.ativo::text, 'U', e.tipo, e.alvo, e.valor
                  FROM usuario u JOIN permissao_usuario_excecao e ON e.usuario_id = u.id
                 WHERE u.id = ?
                """, (r, i) -> new String[] {
                    r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6)},
                usuarioId, usuarioId);
        if (linhas.isEmpty()) {
            return Optional.empty();
        }
        List<Item> perfil = new ArrayList<>();
        List<Item> excecoes = new ArrayList<>();
        for (String[] l : linhas) {
            if (l[3] == null) continue;
            ("P".equals(l[2]) ? perfil : excecoes).add(new Item(l[3], l[4], l[5]));
        }
        String[] primeira = linhas.get(0);
        return Optional.of(new ContextoDeAcesso(usuarioId, PapelUsuario.valueOf(primeira[0]),
                Boolean.parseBoolean(primeira[1]), montar(perfil), montar(excecoes)));
    }

    @Override
    public Map<UUID, ConfiguracaoDePermissoes> todasAsExcecoes() {
        Map<UUID, List<Item>> porUsuario = new HashMap<>();
        jdbc.query("SELECT usuario_id, tipo, alvo, valor FROM permissao_usuario_excecao", r -> {
            porUsuario.computeIfAbsent(r.getObject(1, UUID.class), k -> new ArrayList<>())
                    .add(new Item(r.getString(2), r.getString(3), r.getString(4)));
        });
        Map<UUID, ConfiguracaoDePermissoes> resultado = new HashMap<>();
        porUsuario.forEach((id, itens) -> resultado.put(id, montar(itens)));
        return resultado;
    }

    @Override
    public Set<UUID> usuariosAtivosComPapel(PapelUsuario papel) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT id FROM usuario WHERE ativo = TRUE AND papel = CAST(? AS papel_usuario)", UUID.class, papel.name()));
    }

    @Override
    public long revisaoGlobal() {
        if (!tabelasPresentes()) {
            return REVISAO_SEM_TABELAS;
        }
        Long revisao = leitura.queryForObject("SELECT revisao FROM permissao_politica WHERE id = 1", Long.class);
        return revisao == null ? 0 : revisao;
    }

    @Override
    public Optional<AlvoTravado> travarAlvo(UUID usuarioId) {
        return jdbc.query("SELECT id, papel::text, ativo FROM usuario WHERE id = ? FOR SHARE",
                (r, i) -> new AlvoTravado(r.getObject(1, UUID.class), PapelUsuario.valueOf(r.getString(2)), r.getBoolean(3)),
                usuarioId).stream().findFirst();
    }

    @Override
    public Optional<AlvoTravado> alvo(UUID usuarioId) {
        return jdbc.query("SELECT id, papel::text, ativo FROM usuario WHERE id = ?",
                (r, i) -> new AlvoTravado(r.getObject(1, UUID.class), PapelUsuario.valueOf(r.getString(2)), r.getBoolean(3)),
                usuarioId).stream().findFirst();
    }

    @Override
    public long substituirPerfil(PapelUsuario papel, long revisaoEsperada, ConfiguracaoDePermissoes nova,
            Set<Modulo> modulosGerenciados, UUID autorId) {
        int atualizadas = jdbc.update("""
                UPDATE permissao_perfil SET revisao = revisao + 1, atualizado_em = now(), atualizado_por = ?
                 WHERE papel = CAST(? AS papel_usuario) AND revisao = ?
                """, autorId, papel.name(), revisaoEsperada);
        if (atualizadas == 0) {
            throw new RevisaoDesatualizadaException(perfil(papel).revisao());
        }
        apagarChavesGerenciadas("DELETE FROM permissao_perfil_item WHERE papel = CAST(? AS papel_usuario)",
                papel.name(), modulosGerenciados);
        inserirItens("INSERT INTO permissao_perfil_item (papel, tipo, alvo, valor) VALUES (CAST(? AS papel_usuario), ?, ?, ?)",
                papel.name(), nova);
        return revisaoEsperada + 1;
    }

    @Override
    public long substituirExcecoes(UUID usuarioId, long revisaoEsperada, ConfiguracaoDePermissoes novas,
            Set<Modulo> modulosGerenciados, UUID autorId) {
        jdbc.update("INSERT INTO permissao_usuario (usuario_id) VALUES (?) ON CONFLICT (usuario_id) DO NOTHING", usuarioId);
        int atualizadas = jdbc.update("""
                UPDATE permissao_usuario SET revisao = revisao + 1, atualizado_em = now(), atualizado_por = ?
                 WHERE usuario_id = ? AND revisao = ?
                """, autorId, usuarioId, revisaoEsperada);
        if (atualizadas == 0) {
            throw new RevisaoDesatualizadaException(excecoesDe(usuarioId).revisao());
        }
        apagarChavesGerenciadas("DELETE FROM permissao_usuario_excecao WHERE usuario_id = ?", usuarioId, modulosGerenciados);
        inserirItens("INSERT INTO permissao_usuario_excecao (usuario_id, tipo, alvo, valor) VALUES (?, ?, ?, ?)",
                usuarioId, novas);
        return revisaoEsperada + 1;
    }

    @Override
    public Descartadas descartarExcecoesPorMudancaDePapel(UUID usuarioId, UUID autorId) {
        Armazenado antes = excecoesDe(usuarioId);
        jdbc.update("INSERT INTO permissao_usuario (usuario_id) VALUES (?) ON CONFLICT (usuario_id) DO NOTHING", usuarioId);
        jdbc.update("UPDATE permissao_usuario SET revisao = revisao + 1, atualizado_em = now(), atualizado_por = ? WHERE usuario_id = ?",
                autorId, usuarioId);
        // Todas, inclusive as de chave desconhecida: nenhum privilegio do papel anterior sobrevive.
        jdbc.update("DELETE FROM permissao_usuario_excecao WHERE usuario_id = ?", usuarioId);
        return new Descartadas(antes.revisao(), antes.revisao() + 1, antes.configuracao());
    }

    @Override
    public long incrementarRevisaoGlobal() {
        Long revisao = jdbc.queryForObject(
                "UPDATE permissao_politica SET revisao = revisao + 1, atualizado_em = now() WHERE id = 1 RETURNING revisao",
                Long.class);
        return revisao == null ? 0 : revisao;
    }

    @Override
    public void registrarHistorico(RegistroDeHistorico r) {
        jdbc.update("""
                INSERT INTO permissao_historico (escopo, papel, usuario_id, operacao, revisao_anterior, revisao_nova,
                    antes, depois, origem_papel, origem_usuario_id, autor_id, criado_em)
                VALUES (?, CAST(? AS papel_usuario), ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb),
                    CAST(? AS papel_usuario), ?, ?, ?)
                """,
                r.escopo().name(),
                r.papel() == null ? null : r.papel().name(),
                r.escopo() == Escopo.USUARIO ? r.usuarioId() : null,
                r.operacao().name(), r.revisaoAnterior(), r.revisaoNova(),
                paraJson(r.antes()), paraJson(r.depois()),
                r.origemPapel() == null ? null : r.origemPapel().name(),
                r.origemUsuarioId(), r.autorId(), Timestamp.from(r.criadoEm()));
    }

    @Override
    public List<RegistroDeHistorico> historicoDoUsuario(UUID usuarioId, int limite) {
        return jdbc.query("""
                SELECT escopo, papel::text, usuario_id, operacao, revisao_anterior, revisao_nova, antes::text, depois::text,
                       origem_papel::text, origem_usuario_id, autor_id, criado_em
                  FROM permissao_historico WHERE usuario_id = ? ORDER BY criado_em DESC LIMIT ?
                """, (r, i) -> new RegistroDeHistorico(
                        Escopo.valueOf(r.getString(1)),
                        r.getString(2) == null ? null : PapelUsuario.valueOf(r.getString(2)),
                        r.getObject(3, UUID.class), Operacao.valueOf(r.getString(4)), r.getLong(5), r.getLong(6),
                        deJson(r.getString(7)), deJson(r.getString(8)),
                        r.getString(9) == null ? null : PapelUsuario.valueOf(r.getString(9)),
                        r.getObject(10, UUID.class), r.getObject(11, UUID.class), r.getTimestamp(12).toInstant()),
                usuarioId, limite);
    }

    // --- detalhes -------------------------------------------------------------------------------

    private void apagarChavesGerenciadas(String deletePorEscopo, Object escopo, Set<Modulo> modulosGerenciados) {
        List<String> niveis = modulosGerenciados.stream().map(Modulo::id).toList();
        List<String> acoes = java.util.Arrays.stream(Capacidade.values())
                .filter(c -> modulosGerenciados.contains(c.modulo()))
                .map(Capacidade::id)
                .toList();
        jdbc.execute((ConnectionCallback<Void>) conexao -> {
            Array arrayNiveis = conexao.createArrayOf("varchar", niveis.toArray());
            Array arrayAcoes = conexao.createArrayOf("varchar", acoes.toArray());
            try (var comando = conexao.prepareStatement(deletePorEscopo
                    + " AND ((tipo = 'NIVEL' AND alvo = ANY (?)) OR (tipo = 'ACAO' AND alvo = ANY (?)))")) {
                comando.setObject(1, escopo);
                comando.setArray(2, arrayNiveis);
                comando.setArray(3, arrayAcoes);
                comando.executeUpdate();
            }
            return null;
        });
    }

    private void inserirItens(String insert, Object escopo, ConfiguracaoDePermissoes configuracao) {
        List<Object[]> linhas = new ArrayList<>();
        configuracao.niveis().forEach((m, n) -> linhas.add(new Object[] {escopo, NIVEL, m.id(), n.name()}));
        configuracao.acoes().forEach((c, v) -> linhas.add(new Object[] {escopo, ACAO, c.id(), v ? "PERMITIR" : "NEGAR"}));
        if (!linhas.isEmpty()) {
            jdbc.batchUpdate(insert, linhas);
        }
    }

    private boolean tabelasPresentes() {
        if (tabelasPresentes) {
            return true;
        }
        Boolean presentes = leitura.queryForObject("SELECT to_regclass('permissao_politica') IS NOT NULL", Boolean.class);
        if (Boolean.TRUE.equals(presentes)) {
            tabelasPresentes = true;
            return true;
        }
        if (!ausenciaAvisada) {
            ausenciaAvisada = true;
            log.warn("[PERMISSOES_SEM_V83] tabelas de permissao ausentes (migrations pendentes); "
                    + "usando o padrao de cada papel ate a V83 ser aplicada");
        }
        return false;
    }

    private static ConfiguracaoDePermissoes montar(List<Item> itens) {
        Map<Modulo, NivelDeAcesso> niveis = new EnumMap<>(Modulo.class);
        Map<Capacidade, Boolean> acoes = new EnumMap<>(Capacidade.class);
        for (Item item : itens) {
            if (NIVEL.equals(item.tipo)) {
                Optional<Modulo> modulo = Modulo.porId(item.alvo);
                if (modulo.isPresent()) {
                    niveis.put(modulo.get(), NivelDeAcesso.valueOf(item.valor));
                } else {
                    log.debug("Nivel de modulo desconhecido ignorado: {}", item.alvo);
                }
            } else {
                Optional<Capacidade> capacidade = Capacidade.porId(item.alvo);
                if (capacidade.isPresent()) {
                    acoes.put(capacidade.get(), "PERMITIR".equals(item.valor));
                } else {
                    log.debug("Capacidade desconhecida ignorada: {}", item.alvo);
                }
            }
        }
        return new ConfiguracaoDePermissoes(niveis, acoes);
    }

    private String paraJson(ConfiguracaoDePermissoes configuracao) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        Map<String, String> niveis = new LinkedHashMap<>();
        configuracao.niveis().forEach((m, n) -> niveis.put(m.id(), n.name()));
        Map<String, Boolean> acoes = new LinkedHashMap<>();
        configuracao.acoes().forEach((c, v) -> acoes.put(c.id(), v));
        corpo.put("niveis", niveis);
        corpo.put("acoes", acoes);
        try {
            return json.writeValueAsString(corpo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("falha ao serializar historico de permissao", e);
        }
    }

    @SuppressWarnings("unchecked")
    private ConfiguracaoDePermissoes deJson(String texto) {
        try {
            Map<String, Object> corpo = json.readValue(texto, Map.class);
            return ConfiguracaoDePermissoes.interpretar(
                    (Map<String, String>) corpo.getOrDefault("niveis", Map.of()),
                    (Map<String, Boolean>) corpo.getOrDefault("acoes", Map.of()));
        } catch (JsonProcessingException | RuntimeException e) {
            return ConfiguracaoDePermissoes.vazia();
        }
    }

    private record Item(String tipo, String alvo, String valor) {}
}
