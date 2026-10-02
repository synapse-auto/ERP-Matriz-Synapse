package com.synapse.crm.campanhas.infrastructure;

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
import org.springframework.stereotype.Repository;

import com.synapse.crm.campanhas.application.DestinatarioRepositorio;
import com.synapse.crm.campanhas.domain.MotivoDoDestinatario;
import com.synapse.crm.campanhas.domain.PoliticaDePausa;
import com.synapse.crm.campanhas.domain.StatusDoDestinatario;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * {@code campanha_template_destinatario} no pool do chat. Todo UPDATE de transicao e condicional ao status
 * esperado (compare-and-set): duas execucoes do mesmo passo nunca contam duas vezes.
 */
@Repository
class DestinatarioRepositorioJdbc implements DestinatarioRepositorio {

    private static final String COLUNAS_DO_ALVO =
            "id, campanha_id, lead_id, status, mensagem_id, mensagem_enviada_em, conferencia_em IS NOT NULL AS em_conferencia";

    private final JdbcTemplate chat;

    DestinatarioRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public Optional<Pendente> proximoPendente(UUID campanhaId) {
        TransacaoObrigatoria.exigir("proximo destinatario pendente");
        List<Pendente> lista = chat.query(
                """
                SELECT d.id, d.campanha_id, d.lead_id, d.telefone, l.nome, l.empresa, l.localizacao
                  FROM campanha_template_destinatario d
                  JOIN lead l ON l.id = d.lead_id
                 WHERE d.campanha_id = ? AND d.status = 'PENDENTE'
                 ORDER BY d.id
                 LIMIT 1
                   FOR UPDATE OF d SKIP LOCKED
                """,
                (rs, linha) -> new Pendente(
                        rs.getObject("id", UUID.class),
                        rs.getObject("campanha_id", UUID.class),
                        rs.getObject("lead_id", UUID.class),
                        rs.getString("telefone"),
                        rs.getString("nome"),
                        rs.getString("empresa"),
                        rs.getString("localizacao")),
                campanhaId);
        return lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
    }

    @Override
    public boolean marcarEnfileirado(
            UUID destinatarioId, UUID mensagemId, Instant mensagemEnviadaEm, UUID atendimentoId, Instant agora) {
        TransacaoObrigatoria.exigir("marcar destinatario enfileirado");
        return chat.update(
                        """
                        UPDATE campanha_template_destinatario
                           SET status = 'ENFILEIRADO', mensagem_id = ?, mensagem_enviada_em = ?, atendimento_id = ?,
                               enfileirado_em = ?, atualizado_em = ?
                         WHERE id = ? AND status = 'PENDENTE'
                        """,
                        mensagemId,
                        Timestamp.from(mensagemEnviadaEm),
                        atendimentoId,
                        Timestamp.from(agora),
                        Timestamp.from(agora),
                        destinatarioId)
                == 1;
    }

    @Override
    public boolean marcarIgnorado(UUID destinatarioId, MotivoDoDestinatario motivo, Instant agora) {
        TransacaoObrigatoria.exigir("marcar destinatario ignorado");
        return chat.update(
                        "UPDATE campanha_template_destinatario SET status = 'IGNORADO', motivo = ?, atualizado_em = ?"
                                + " WHERE id = ? AND status = 'PENDENTE'",
                        motivo.name(),
                        Timestamp.from(agora),
                        destinatarioId)
                == 1;
    }

    @Override
    public Optional<Alvo> bloquearPorMensagem(UUID mensagemId) {
        TransacaoObrigatoria.exigir("travar destinatario pela mensagem");
        List<Alvo> lista = chat.query(
                "SELECT " + COLUNAS_DO_ALVO + " FROM campanha_template_destinatario WHERE mensagem_id = ? FOR UPDATE",
                this::alvo,
                mensagemId);
        return lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
    }

    @Override
    public void aplicarStatus(
            UUID destinatarioId,
            StatusDoDestinatario novo,
            MotivoDoDestinatario motivo,
            Integer codigoDeErro,
            Instant quando,
            AcaoDeConferencia conferencia) {
        TransacaoObrigatoria.exigir("aplicar status ao destinatario");
        Timestamp instante = Timestamp.from(quando);
        boolean marcaEnviado = novo == StatusDoDestinatario.ENVIADO
                || novo == StatusDoDestinatario.ENTREGUE
                || novo == StatusDoDestinatario.LIDO;
        boolean marcaEntregue = novo == StatusDoDestinatario.ENTREGUE || novo == StatusDoDestinatario.LIDO;
        boolean marcaLido = novo == StatusDoDestinatario.LIDO;
        chat.update(
                """
                UPDATE campanha_template_destinatario SET
                    status = ?, motivo = ?, erro_codigo = ?, atualizado_em = ?,
                    enviado_em = CASE WHEN ? THEN COALESCE(enviado_em, ?) ELSE enviado_em END,
                    entregue_em = CASE WHEN ? THEN COALESCE(entregue_em, ?) ELSE entregue_em END,
                    lido_em = CASE WHEN ? THEN COALESCE(lido_em, ?) ELSE lido_em END,
                    conferencia_em = CASE
                        WHEN ? = 'SINALIZAR' THEN COALESCE(conferencia_em, ?)
                        WHEN ? = 'LIMPAR' THEN NULL
                        ELSE conferencia_em END
                WHERE id = ?
                """,
                novo.name(),
                motivo == null ? null : motivo.name(),
                codigoDeErro,
                instante,
                marcaEnviado,
                instante,
                marcaEntregue,
                instante,
                marcaLido,
                instante,
                conferencia.name(),
                instante,
                conferencia.name(),
                destinatarioId);
    }

    @Override
    public Optional<ErroDaMensagem> erroDaMensagem(UUID mensagemId, Instant enviadoEm) {
        TransacaoObrigatoria.exigir("ler erro da mensagem");
        List<ErroDaMensagem> lista = chat.query(
                "SELECT erro_entrega ->> 'codigo' AS codigo, erro_entrega ->> 'titulo' AS titulo"
                        + " FROM mensagem WHERE id = ? AND enviado_em = ?",
                (rs, linha) -> {
                    String codigo = rs.getString("codigo");
                    return new ErroDaMensagem(codigo == null ? null : Integer.valueOf(codigo), rs.getString("titulo"));
                },
                mensagemId,
                Timestamp.from(enviadoEm));
        return lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
    }

    @Override
    public Optional<UUID> registrarResposta(UUID leadId, Instant quando) {
        TransacaoObrigatoria.exigir("registrar resposta de campanha");
        List<UUID> campanhas = chat.queryForList(
                """
                UPDATE campanha_template_destinatario d SET respondeu_em = ?
                 WHERE d.id = (
                        SELECT x.id FROM campanha_template_destinatario x
                         WHERE x.lead_id = ? AND x.enviado_em IS NOT NULL AND x.respondeu_em IS NULL
                           AND x.enviado_em >= ?::timestamptz - make_interval(days => (
                                SELECT valor::int FROM configuracao_automacao
                                 WHERE chave = 'campanhas.respondeu_janela_dias'))
                         ORDER BY x.enviado_em DESC
                         LIMIT 1)
                RETURNING d.campanha_id
                """,
                UUID.class,
                Timestamp.from(quando),
                leadId,
                Timestamp.from(quando));
        return campanhas.isEmpty() ? Optional.empty() : Optional.of(campanhas.get(0));
    }

    @Override
    public int sinalizarParaConferencia(UUID campanhaId, Instant corte) {
        TransacaoObrigatoria.exigir("sinalizar conferencia");
        Timestamp agora = Timestamp.from(Instant.now());
        return chat.update(
                "UPDATE campanha_template_destinatario SET conferencia_em = ?"
                        + " WHERE campanha_id = ? AND status = 'ENFILEIRADO' AND conferencia_em IS NULL"
                        + "   AND enfileirado_em < ?",
                agora,
                campanhaId,
                Timestamp.from(corte));
    }

    @Override
    public boolean resolverConferencia(UUID campanhaId, UUID destinatarioId) {
        TransacaoObrigatoria.exigir("resolver conferencia");
        return chat.update(
                        "UPDATE campanha_template_destinatario SET conferencia_em = NULL"
                                + " WHERE id = ? AND campanha_id = ? AND conferencia_em IS NOT NULL",
                        destinatarioId,
                        campanhaId)
                == 1;
    }

    @Override
    public PoliticaDePausa.Desfechos desfechosRecentes(UUID campanhaId, int janela) {
        TransacaoObrigatoria.exigir("ler desfechos recentes");
        return chat.queryForObject(
                """
                SELECT count(*), count(*) FILTER (WHERE status = 'FALHA')
                  FROM (SELECT status FROM campanha_template_destinatario
                         WHERE campanha_id = ? AND enfileirado_em IS NOT NULL AND status <> 'ENFILEIRADO'
                         ORDER BY enfileirado_em DESC
                         LIMIT ?) recentes
                """,
                (rs, linha) -> new PoliticaDePausa.Desfechos(rs.getInt(1), rs.getInt(2)),
                campanhaId,
                janela);
    }

    @Override
    public List<UUID> campanhasComEnfileiradoAntesDe(Instant corte, int limite) {
        TransacaoObrigatoria.exigir("campanhas com enfileirado parado");
        return chat.queryForList(
                "SELECT DISTINCT campanha_id FROM campanha_template_destinatario"
                        + " WHERE status = 'ENFILEIRADO' AND enfileirado_em < ? LIMIT ?",
                UUID.class,
                Timestamp.from(corte),
                limite);
    }

    @Override
    public List<Alvo> aReconciliar(UUID campanhaId, Instant enfileiradosAntesDe, int limite) {
        TransacaoObrigatoria.exigir("destinatarios a reconciliar");
        return chat.query(
                "SELECT " + COLUNAS_DO_ALVO + " FROM campanha_template_destinatario"
                        + " WHERE campanha_id = ? AND status = 'ENFILEIRADO' AND enfileirado_em < ?"
                        + " ORDER BY enfileirado_em LIMIT ?",
                this::alvo,
                campanhaId,
                Timestamp.from(enfileiradosAntesDe),
                limite);
    }

    @Override
    public Optional<StatusDoDestinatario> statusDaMensagem(UUID mensagemId, Instant enviadoEm) {
        TransacaoObrigatoria.exigir("ler status da mensagem");
        List<String> status = chat.queryForList(
                "SELECT status_entrega::text FROM mensagem WHERE id = ? AND enviado_em = ?",
                String.class,
                mensagemId,
                Timestamp.from(enviadoEm));
        if (status.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (status.get(0)) {
            case "PENDENTE" -> StatusDoDestinatario.ENFILEIRADO;
            case "ENVIADO" -> StatusDoDestinatario.ENVIADO;
            case "ENTREGUE" -> StatusDoDestinatario.ENTREGUE;
            case "LIDO" -> StatusDoDestinatario.LIDO;
            case "FALHOU" -> StatusDoDestinatario.FALHA;
            default -> null;
        });
    }

    private Alvo alvo(ResultSet rs, int linha) throws SQLException {
        Timestamp enviadaEm = rs.getTimestamp("mensagem_enviada_em");
        return new Alvo(
                rs.getObject("id", UUID.class),
                rs.getObject("campanha_id", UUID.class),
                rs.getObject("lead_id", UUID.class),
                StatusDoDestinatario.valueOf(rs.getString("status")),
                rs.getObject("mensagem_id", UUID.class),
                enviadaEm == null ? null : enviadaEm.toInstant(),
                rs.getBoolean("em_conferencia"));
    }
}
