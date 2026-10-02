package com.synapse.crm.campanhas.infrastructure;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.campanhas.application.PublicoRepositorio;
import com.synapse.crm.campanhas.domain.CampoDoLead;
import com.synapse.crm.campanhas.domain.FiltroDePublico;
import com.synapse.crm.campanhas.domain.MotivoDoDestinatario;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * O publico da campanha, decidido por conjunto no banco. Previa e excluidos rodam no pool geral (leitura
 * pesada); materializar e dadosDoLead, no pool do chat, porque fazem parte de uma transacao de campanha.
 *
 * <p>A validade do telefone e uma aproximacao em SQL de {@code TelefoneCanonico} (a coluna {@code lead.telefone}
 * ja guarda a forma canonica): DDI + numero, 12 a 15 digitos, sem zero inicial. O envio revalida em Java.
 */
@Repository
class PublicoRepositorioJdbc implements PublicoRepositorio {

    /** Dois ou mais caracteres alfabeticos: espelha {@code NomeDoContato.utilizavel}. */
    private static final String NOME_UTILIZAVEL = "[A-Za-zÀ-ÿ].*[A-Za-zÀ-ÿ]";

    private static final String TELEFONE_VALIDO = "^[1-9][0-9]{11,14}$";

    private static final String BASE =
            """
            WITH base AS (
                SELECT l.id AS lead_id, l.telefone, l.nome,
                       CASE
                           WHEN l.telefone IS NULL OR l.telefone !~ :telefoneValido THEN 'TELEFONE_INVALIDO'
                           WHEN l.nome !~ :nomeUtilizavel THEN 'SEM_NOME_UTILIZAVEL'
                           WHEN EXISTS (SELECT 1 FROM contato_optout o WHERE o.lead_id = l.id) THEN 'OPT_OUT'
                           WHEN CAST(:campanha AS uuid) IS NOT NULL AND EXISTS (
                                SELECT 1 FROM campanha_template_destinatario d
                                 WHERE d.campanha_id = CAST(:campanha AS uuid) AND d.lead_id = l.id) THEN 'JA_RECEBEU'
                           WHEN EXISTS (
                                SELECT 1 FROM atendimento a
                                 WHERE a.lead_id = l.id AND a.status <> 'FINALIZADO') THEN 'ATENDIMENTO_ATIVO'
                           WHEN :cooldown > 0 AND EXISTS (
                                SELECT 1 FROM envio_proativo_reserva r
                                 WHERE r.lead_id = l.id AND r.reservado_em > :corteDoCooldown)
                                THEN 'RECEBEU_PROATIVA_RECENTE'
                       END AS motivo
                  FROM lead l
                 WHERE TRUE
            """;

    private final NamedParameterJdbcTemplate geral;
    private final NamedParameterJdbcTemplate chat;

    PublicoRepositorioJdbc(
            @Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource geralDataSource,
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.geral = new NamedParameterJdbcTemplate(new JdbcTemplate(geralDataSource));
        this.chat = new NamedParameterJdbcTemplate(new JdbcTemplate(chatDataSource));
    }

    @Override
    public ContagemDoPublico prever(FiltroDePublico filtro, int cooldownProativoHoras, ZoneId fuso) {
        TransacaoObrigatoria.exigir("prever publico da campanha");
        MapSqlParameterSource parametros = parametros(null, filtro, cooldownProativoHoras, fuso);
        Map<MotivoDoDestinatario, Long> excluidos = new EnumMap<>(MotivoDoDestinatario.class);
        long[] totais = new long[2];
        geral.query(
                montar(filtro, "SELECT motivo, count(*) AS quantidade FROM base GROUP BY motivo"),
                parametros,
                rs -> {
                    String motivo = rs.getString("motivo");
                    long quantidade = rs.getLong("quantidade");
                    totais[0] += quantidade;
                    if (motivo == null) {
                        totais[1] += quantidade;
                    } else {
                        excluidos.merge(MotivoDoDestinatario.valueOf(motivo), quantidade, Long::sum);
                    }
                });
        return new ContagemDoPublico(totais[0], totais[1], excluidos);
    }

    @Override
    public List<Excluido> excluidos(
            FiltroDePublico filtro, int cooldownProativoHoras, ZoneId fuso, MotivoDoDestinatario motivo, int limite) {
        TransacaoObrigatoria.exigir("listar excluidos do publico");
        MapSqlParameterSource parametros = parametros(null, filtro, cooldownProativoHoras, fuso)
                .addValue("motivoFiltrado", motivo.name())
                .addValue("limite", limite);
        return geral.query(
                montar(
                        filtro,
                        "SELECT lead_id, nome, telefone FROM base WHERE motivo = :motivoFiltrado"
                                + " ORDER BY nome LIMIT :limite"),
                parametros,
                (rs, linha) -> new Excluido(
                        rs.getObject("lead_id", UUID.class), rs.getString("nome"), rs.getString("telefone"), motivo));
    }

    @Override
    public int materializar(UUID campanhaId, FiltroDePublico filtro, int cooldownProativoHoras, ZoneId fuso) {
        TransacaoObrigatoria.exigir("materializar destinatarios da campanha");
        MapSqlParameterSource parametros = parametros(campanhaId, filtro, cooldownProativoHoras, fuso);
        int novos = chat.update(
                montar(
                        filtro,
                        """
                        INSERT INTO campanha_template_destinatario (campanha_id, lead_id, telefone, status, motivo)
                        SELECT CAST(:campanha AS uuid), lead_id, telefone,
                               CASE WHEN motivo IS NULL THEN 'PENDENTE' ELSE 'IGNORADO' END, motivo
                          FROM base
                        ON CONFLICT (campanha_id, lead_id) DO NOTHING
                        """),
                parametros);
        // Unica contagem do ciclo de vida: acerta os contadores incrementais uma vez, ao iniciar.
        chat.update(
                """
                UPDATE campanha_template c
                   SET qtd_total = s.total, qtd_pendentes = s.pendentes, qtd_ignorados = s.ignorados
                  FROM (SELECT count(*) AS total,
                               count(*) FILTER (WHERE status = 'PENDENTE') AS pendentes,
                               count(*) FILTER (WHERE status = 'IGNORADO') AS ignorados
                          FROM campanha_template_destinatario WHERE campanha_id = :campanha) s
                 WHERE c.id = :campanha
                """,
                new MapSqlParameterSource().addValue("campanha", campanhaId));
        return novos;
    }

    @Override
    public Optional<CampoDoLead.Dados> dadosDoLead(UUID leadId) {
        TransacaoObrigatoria.exigir("ler dados do lead para campanha");
        List<CampoDoLead.Dados> lista = chat.query(
                "SELECT nome, empresa, localizacao FROM lead WHERE id = :id",
                new MapSqlParameterSource().addValue("id", leadId),
                (rs, linha) -> new CampoDoLead.Dados(
                        rs.getString("nome"), rs.getString("empresa"), rs.getString("localizacao")));
        return lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
    }

    // --- montagem -------------------------------------------------------------------------------------

    /** O CTE base com os filtros opcionais, fechado e seguido do SQL final. Todo valor entra como parametro. */
    private static String montar(FiltroDePublico filtro, String sqlFinal) {
        StringBuilder sql = new StringBuilder(BASE);
        if (!filtro.tagIds().isEmpty()) {
            sql.append(" AND EXISTS (SELECT 1 FROM lead_tag lt WHERE lt.lead_id = l.id AND lt.tag_id IN (:tags))\n");
        }
        if (filtro.etapaId() != null) {
            sql.append(" AND l.etapa_atendimento_id = :etapa\n");
        }
        if (filtro.atendenteId() != null) {
            sql.append(" AND l.atendente_responsavel_id = :atendente\n");
        }
        if (filtro.cadastroDesde() != null) {
            sql.append(" AND l.criado_em >= :cadastroDesde\n");
        }
        if (filtro.cadastroAte() != null) {
            sql.append(" AND l.criado_em < :cadastroAte\n");
        }
        if (filtro.nuncaConversou()) {
            sql.append(" AND l.num_mensagens = 0\n");
        }
        if (filtro.busca() != null) {
            sql.append(" AND (l.nome ILIKE :busca ESCAPE '\\' OR l.telefone LIKE :busca ESCAPE '\\')\n");
        }
        sql.append(")\n").append(sqlFinal);
        return sql.toString();
    }

    private static MapSqlParameterSource parametros(
            UUID campanhaId, FiltroDePublico filtro, int cooldownHoras, ZoneId fuso) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("telefoneValido", TELEFONE_VALIDO)
                .addValue("nomeUtilizavel", NOME_UTILIZAVEL)
                .addValue("campanha", campanhaId, Types.OTHER)
                .addValue("cooldown", cooldownHoras)
                .addValue(
                        "corteDoCooldown",
                        Timestamp.from(Instant.now().minus(java.time.Duration.ofHours(Math.max(0, cooldownHoras)))));
        if (!filtro.tagIds().isEmpty()) {
            parametros.addValue("tags", filtro.tagIds());
        }
        if (filtro.etapaId() != null) {
            parametros.addValue("etapa", filtro.etapaId());
        }
        if (filtro.atendenteId() != null) {
            parametros.addValue("atendente", filtro.atendenteId());
        }
        if (filtro.cadastroDesde() != null) {
            parametros.addValue("cadastroDesde", Timestamp.from(filtro.cadastroDesde().atStartOfDay(fuso).toInstant()));
        }
        if (filtro.cadastroAte() != null) {
            parametros.addValue(
                    "cadastroAte", Timestamp.from(filtro.cadastroAte().plusDays(1).atStartOfDay(fuso).toInstant()));
        }
        if (filtro.busca() != null) {
            parametros.addValue("busca", "%" + escaparCuringa(filtro.busca()) + "%");
        }
        return parametros;
    }

    /** {@code %} e {@code _} digitados pela pessoa sao texto, nao curinga. */
    private static String escaparCuringa(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
