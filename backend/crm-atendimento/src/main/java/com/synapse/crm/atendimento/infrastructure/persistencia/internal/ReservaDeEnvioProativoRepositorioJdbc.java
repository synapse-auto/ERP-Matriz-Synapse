package com.synapse.crm.atendimento.infrastructure.persistencia.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.origem.TipoDeOrigem;
import com.synapse.crm.atendimento.application.proativo.ReservaDeEnvioProativoRepositorio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Reserva proativa (V85). PK e indice unico garantem a atomicidade; a trava por lead, a contagem. */
@Repository
class ReservaDeEnvioProativoRepositorioJdbc implements ReservaDeEnvioProativoRepositorio {

    private static final String COLUNAS = "chave, lead_id, tipo, regra_id, ocorrencia, execucao_id, requisicao_hash,"
            + " estado, mensagem_id, wamid_saida, reservado_em, enviado_em";

    /** Lista SQL fixa, montada do enum: nenhum valor externo entra na consulta. */
    private static final String TIPOS_PROATIVOS = Arrays.stream(TipoDeOrigem.values())
            .filter(TipoDeOrigem::proativa)
            .map(tipo -> "'" + tipo.name() + "'")
            .collect(Collectors.joining(", "));

    private final JdbcTemplate chat;

    ReservaDeEnvioProativoRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public void serializarDecisoesDoLead(UUID leadId) {
        TransacaoObrigatoria.exigir("serializar reservas proativas do lead");
        // Trava transacional: liberada no commit/rollback, nunca fica presa a conexao do pool.
        chat.query(
                "SELECT pg_advisory_xact_lock(hashtextextended('envio_proativo:' || ?, 0))",
                linha -> {},
                leadId.toString());
    }

    @Override
    public Optional<ReservaProativa> porChave(String chave) {
        TransacaoObrigatoria.exigir("buscar reserva proativa");
        return chat.query("SELECT " + COLUNAS + " FROM envio_proativo_reserva WHERE chave = ?",
                        ReservaDeEnvioProativoRepositorioJdbc::mapear, chave)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ReservaProativa> porOcorrencia(UUID leadId, TipoDeOrigem tipo, String regraId, String ocorrencia) {
        TransacaoObrigatoria.exigir("buscar reserva proativa por ocorrencia");
        return chat.query(
                        "SELECT " + COLUNAS + " FROM envio_proativo_reserva"
                                + " WHERE lead_id = ? AND tipo = ? AND COALESCE(regra_id, '') = ? AND ocorrencia = ?",
                        ReservaDeEnvioProativoRepositorioJdbc::mapear,
                        leadId,
                        tipo.name(),
                        regraId == null ? "" : regraId,
                        ocorrencia)
                .stream()
                .findFirst();
    }

    @Override
    public boolean inserir(ReservaProativa reserva) {
        TransacaoObrigatoria.exigir("inserir reserva proativa");
        return chat.update(
                        "INSERT INTO envio_proativo_reserva"
                                + " (chave, lead_id, tipo, regra_id, ocorrencia, execucao_id, requisicao_hash, estado, reservado_em)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, 'RESERVADO', ?) ON CONFLICT DO NOTHING",
                        reserva.chave(),
                        reserva.leadId(),
                        reserva.tipo().name(),
                        reserva.regraId(),
                        reserva.ocorrencia(),
                        reserva.execucaoId(),
                        reserva.requisicaoHash(),
                        Timestamp.from(reserva.reservadoEm()))
                == 1;
    }

    @Override
    public Optional<Instant> ultimoEnvioDoTipo(UUID leadId, TipoDeOrigem tipo) {
        TransacaoObrigatoria.exigir("consultar ultimo envio proativo");
        Timestamp ultimo = chat.queryForObject(
                "SELECT GREATEST("
                        + " (SELECT max(reservado_em) FROM envio_proativo_reserva WHERE lead_id = ? AND tipo = ?),"
                        + " (SELECT max(enviado_em) FROM mensagem_origem_automacao WHERE lead_id = ? AND tipo = ?))",
                Timestamp.class,
                leadId,
                tipo.name(),
                leadId,
                tipo.name());
        return Optional.ofNullable(ultimo).map(Timestamp::toInstant);
    }

    /**
     * Reservas (com ou sem resultado) mais proativas registradas sem reserva — o fluxo que ainda envia
     * sem reservar, mas declara a origem, tambem conta. Uma reserva concluida nao conta duas vezes.
     */
    @Override
    public int enviosProativosDesde(UUID leadId, Instant desde) {
        TransacaoObrigatoria.exigir("contar envios proativos do lead");
        Timestamp inicio = Timestamp.from(desde);
        Integer total = chat.queryForObject(
                "SELECT (SELECT count(*) FROM envio_proativo_reserva r WHERE r.lead_id = ? AND r.reservado_em >= ?)"
                        + " + (SELECT count(*) FROM mensagem_origem_automacao o"
                        + "     WHERE o.lead_id = ? AND o.enviado_em >= ? AND o.tipo IN (" + TIPOS_PROATIVOS + ")"
                        + "       AND NOT EXISTS (SELECT 1 FROM envio_proativo_reserva r"
                        + "                        WHERE r.lead_id = o.lead_id AND r.mensagem_id = o.mensagem_id))",
                Integer.class,
                leadId,
                inicio,
                leadId,
                inicio);
        return total == null ? 0 : total;
    }

    @Override
    public boolean concluir(String chave, UUID mensagemId, String wamidSaida, Instant agora) {
        TransacaoObrigatoria.exigir("concluir reserva proativa");
        return chat.update(
                        "UPDATE envio_proativo_reserva SET estado = 'ENVIADO', mensagem_id = ?, wamid_saida = ?, enviado_em = ?"
                                + " WHERE chave = ? AND estado = 'RESERVADO'",
                        mensagemId,
                        wamidSaida,
                        Timestamp.from(agora),
                        chave)
                == 1;
    }

    @Override
    public List<ReservaProativa> pendentesAntesDe(Instant limite, int maximo) {
        TransacaoObrigatoria.exigir("listar reservas proativas pendentes");
        return chat.query(
                "SELECT " + COLUNAS + " FROM envio_proativo_reserva"
                        + " WHERE estado = 'RESERVADO' AND reservado_em < ?"
                        + " ORDER BY reservado_em, chave LIMIT ?",
                ReservaDeEnvioProativoRepositorioJdbc::mapear,
                Timestamp.from(limite),
                maximo);
    }

    private static ReservaProativa mapear(ResultSet linha, int indice) throws SQLException {
        Timestamp enviadoEm = linha.getTimestamp("enviado_em");
        return new ReservaProativa(
                linha.getString("chave"),
                linha.getObject("lead_id", UUID.class),
                TipoDeOrigem.valueOf(linha.getString("tipo")),
                linha.getString("regra_id"),
                linha.getString("ocorrencia"),
                linha.getString("execucao_id"),
                linha.getString("requisicao_hash"),
                Estado.valueOf(linha.getString("estado")),
                linha.getObject("mensagem_id", UUID.class),
                linha.getString("wamid_saida"),
                linha.getTimestamp("reservado_em").toInstant(),
                enviadoEm == null ? null : enviadoEm.toInstant());
    }
}
