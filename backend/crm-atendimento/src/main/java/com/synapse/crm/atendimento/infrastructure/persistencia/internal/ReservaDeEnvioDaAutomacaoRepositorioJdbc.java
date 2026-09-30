package com.synapse.crm.atendimento.infrastructure.persistencia.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.ReservaDeEnvioDaAutomacaoRepositorio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** A PK de envio_automacao_reserva e a unica fonte de verdade da atomicidade (V84). */
@Repository
class ReservaDeEnvioDaAutomacaoRepositorioJdbc implements ReservaDeEnvioDaAutomacaoRepositorio {

    private static final String COLUNAS =
            "chave, atendimento_id, estado, wamid_saida, reservado_em, enviado_em";

    private final JdbcTemplate chat;

    @Autowired
    ReservaDeEnvioDaAutomacaoRepositorioJdbc(@Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource) {
        this.chat = new JdbcTemplate(chatDataSource);
    }

    @Override
    public boolean reservar(String chave, UUID atendimentoId, Instant agora) {
        TransacaoObrigatoria.exigir("reservar envio da Automacao");
        return chat.update(
                        "INSERT INTO envio_automacao_reserva (chave, atendimento_id, estado, reservado_em)"
                                + " VALUES (?, ?, 'RESERVADO', ?) ON CONFLICT (chave) DO NOTHING",
                        chave,
                        atendimentoId,
                        Timestamp.from(agora))
                == 1;
    }

    @Override
    public Optional<Reserva> buscar(String chave) {
        TransacaoObrigatoria.exigir("buscar reserva de envio da Automacao");
        return chat.query(
                        "SELECT " + COLUNAS + " FROM envio_automacao_reserva WHERE chave = ?",
                        ReservaDeEnvioDaAutomacaoRepositorioJdbc::mapear,
                        chave)
                .stream()
                .findFirst();
    }

    @Override
    public boolean concluir(String chave, UUID atendimentoId, String wamidSaida, Instant agora) {
        TransacaoObrigatoria.exigir("concluir reserva de envio da Automacao");
        return chat.update(
                        "UPDATE envio_automacao_reserva SET estado = 'ENVIADO', wamid_saida = ?, enviado_em = ?"
                                + " WHERE chave = ? AND atendimento_id = ? AND estado = 'RESERVADO'",
                        wamidSaida,
                        Timestamp.from(agora),
                        chave,
                        atendimentoId)
                == 1;
    }

    @Override
    public List<Reserva> pendentesAntesDe(Instant limite, int maximo) {
        TransacaoObrigatoria.exigir("listar reservas de envio pendentes");
        return chat.query(
                "SELECT " + COLUNAS + " FROM envio_automacao_reserva"
                        + " WHERE estado = 'RESERVADO' AND reservado_em < ?"
                        + " ORDER BY reservado_em, chave LIMIT ?",
                ReservaDeEnvioDaAutomacaoRepositorioJdbc::mapear,
                Timestamp.from(limite),
                maximo);
    }

    private static Reserva mapear(ResultSet linha, int indice) throws SQLException {
        Timestamp enviadoEm = linha.getTimestamp("enviado_em");
        return new Reserva(
                linha.getString("chave"),
                linha.getObject("atendimento_id", UUID.class),
                Estado.valueOf(linha.getString("estado")),
                linha.getString("wamid_saida"),
                linha.getTimestamp("reservado_em").toInstant(),
                enviadoEm == null ? null : enviadoEm.toInstant());
    }
}
