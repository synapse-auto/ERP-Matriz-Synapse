package com.synapse.crm.atendimento.infrastructure.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.atendimento.application.Outbox;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.core.infrastructure.persistencia.TransacaoObrigatoria;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * A outbox em cima de {@code outbox_evento}, no pool do chat.
 *
 * <p>Pool do chat e nao geral, apesar de o publisher ser um job de fundo: {@code enfileirarEnvio}
 * <b>tem</b> de cair na mesma conexao que grava a mensagem, senao nao ha atomicidade — e o publisher
 * atualiza {@code mensagem.status_entrega} junto com a propria linha da outbox, o que tambem precisa
 * de conexao unica. O bulkhead existe para proteger o chat de <em>relatorio</em>; o envio de mensagem
 * e o caminho de chat, nao um invasor dele.
 *
 * <p>A serializacao vive aqui e nao no dominio: {@link ConteudoDeEnvio} nao conhece Jackson, e o
 * teste de arquitetura reprova se conhecer.
 */
@Repository
class OutboxRepositorioJdbc implements Outbox {

    /** Tipo do evento na tabela. Fixo e greppavel — o publisher filtra por ele. */
    static final String TIPO_ENVIO = "canal.mensagem.enviar";

    private static final int LIMITE_DO_ERRO = 500;

    private static final String SQL_ENFILEIRAR =
            "INSERT INTO outbox_evento (id, tipo, payload, criado_em, proxima_tentativa_em)"
                    + " VALUES (?, ?, ?::jsonb, ?, ?)";

    /**
     * {@code SKIP LOCKED} e o que permite duas instancias rodarem o mesmo job sem coordenacao
     * externa: cada uma pega linhas que a outra nao travou. Sem ele, ou as instancias se bloqueiam em
     * fila, ou — pior — a mesma mensagem sai duas vezes para o cliente.
     */
    private static final String SQL_RESERVAR =
            """
            SELECT id, payload, tentativas
              FROM outbox_evento
             WHERE tipo = ?
               AND publicado_em IS NULL
               AND esgotado_em IS NULL
               AND proxima_tentativa_em <= ?
             ORDER BY proxima_tentativa_em
             LIMIT ?
               FOR UPDATE SKIP LOCKED
            """;

    private static final String SQL_PUBLICADO =
            "UPDATE outbox_evento SET publicado_em = ?, ultimo_erro = NULL WHERE id = ?";

    private static final String SQL_REAGENDAR =
            "UPDATE outbox_evento SET tentativas = tentativas + 1, proxima_tentativa_em = ?,"
                    + " ultimo_erro = ? WHERE id = ?";

    private static final String SQL_ESGOTAR =
            "UPDATE outbox_evento SET tentativas = tentativas + 1, esgotado_em = ?, ultimo_erro = ?"
                    + " WHERE id = ?";

    private static final String SQL_ESGOTADAS = "SELECT outbox_esgotadas()";

    private final JdbcTemplate chat;
    private final ObjectMapper json;

    OutboxRepositorioJdbc(
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chatDataSource, ObjectMapper json) {
        this.chat = new JdbcTemplate(chatDataSource);
        this.json = json;
    }

    @Override
    public void enfileirarEnvio(
            UUID mensagemId,
            Instant enviadoEm,
            UUID leadId,
            String telefoneDestino,
            UUID credencialId,
            ConteudoDeEnvio conteudo) {
        TransacaoObrigatoria.exigir("enfileirarEnvio");

        Instant agora = Instant.now();
        chat.update(
                SQL_ENFILEIRAR,
                UUID.randomUUID(),
                TIPO_ENVIO,
                serializar(mensagemId, enviadoEm, leadId, telefoneDestino, credencialId, conteudo),
                Timestamp.from(agora),
                Timestamp.from(agora));
    }

    @Override
    public List<EnvioPendente> reservarPendentes(int limite, Instant agora) {
        TransacaoObrigatoria.exigir("reservarPendentes");
        return chat.query(SQL_RESERVAR, this::desserializar, TIPO_ENVIO, Timestamp.from(agora), limite);
    }

    @Override
    public void marcarPublicado(UUID outboxId, Instant quando) {
        TransacaoObrigatoria.exigir("marcarPublicado");
        chat.update(SQL_PUBLICADO, Timestamp.from(quando), outboxId);
    }

    @Override
    public void reagendar(UUID outboxId, Instant proximaTentativa, String erro) {
        TransacaoObrigatoria.exigir("reagendar");
        chat.update(SQL_REAGENDAR, Timestamp.from(proximaTentativa), truncar(erro), outboxId);
    }

    @Override
    public void esgotar(UUID outboxId, Instant quando, String erro) {
        TransacaoObrigatoria.exigir("esgotar");
        chat.update(SQL_ESGOTAR, Timestamp.from(quando), truncar(erro), outboxId);
    }

    @Override
    public long quantidadeEsgotada() {
        TransacaoObrigatoria.exigir("quantidadeEsgotada");
        Long total = chat.queryForObject(SQL_ESGOTADAS, Long.class);
        return total == null ? 0L : total;
    }

    // --- serializacao ---------------------------------------------------------

    private String serializar(
            UUID mensagemId,
            Instant enviadoEm,
            UUID leadId,
            String telefoneDestino,
            UUID credencialId,
            ConteudoDeEnvio conteudo) {

        ObjectNode raiz = json.createObjectNode();
        raiz.put("mensagemId", mensagemId.toString());
        raiz.put("enviadoEm", enviadoEm.toString());
        raiz.put("leadId", leadId.toString());
        raiz.put("telefoneDestino", telefoneDestino);
        raiz.put("credencialId", credencialId == null ? null : credencialId.toString());

        ObjectNode conteudoNo = raiz.putObject("conteudo");
        switch (conteudo) {
            case ConteudoDeEnvio.MensagemLivre livre -> {
                conteudoNo.put("tipo", "LIVRE");
                conteudoNo.put("texto", livre.texto());
            }
            case ConteudoDeEnvio.MensagemTemplate template -> {
                conteudoNo.put("tipo", "TEMPLATE");
                conteudoNo.put("nome", template.nome());
                conteudoNo.put("idioma", template.idioma());
                var parametros = conteudoNo.putArray("parametros");
                template.parametros().forEach(parametros::add);
            }
        }
        return raiz.toString();
    }

    private EnvioPendente desserializar(ResultSet linha, int indice) throws SQLException {
        JsonNode payload = ler(linha.getString("payload"));
        JsonNode conteudo = payload.get("conteudo");

        return new EnvioPendente(
                linha.getObject("id", UUID.class),
                UUID.fromString(payload.get("mensagemId").asText()),
                Instant.parse(payload.get("enviadoEm").asText()),
                UUID.fromString(payload.get("leadId").asText()),
                payload.get("telefoneDestino").asText(),
                payload.get("credencialId").isNull()
                        ? null
                        : UUID.fromString(payload.get("credencialId").asText()),
                paraConteudo(conteudo),
                linha.getInt("tentativas"));
    }

    private static ConteudoDeEnvio paraConteudo(JsonNode conteudo) {
        if ("TEMPLATE".equals(conteudo.get("tipo").asText())) {
            List<String> parametros = new ArrayList<>();
            conteudo.get("parametros").forEach(parametro -> parametros.add(parametro.asText()));
            return new ConteudoDeEnvio.MensagemTemplate(
                    conteudo.get("nome").asText(), conteudo.get("idioma").asText(), parametros);
        }
        return new ConteudoDeEnvio.MensagemLivre(conteudo.get("texto").asText());
    }

    private JsonNode ler(String bruto) {
        try {
            return json.readTree(bruto);
        } catch (JsonProcessingException e) {
            // Payload corrompido: nao da para adivinhar o que enviar. Deixar estourar faz
            // a linha ser reagendada e, no limite, esgotar com o erro visivel — melhor que
            // enviar algo diferente do que o atendente escreveu.
            throw new IllegalStateException("payload de outbox ilegivel", e);
        }
    }

    /** A coluna e TEXT, mas erro de provedor as vezes vem com um stack inteiro dentro. */
    private static String truncar(String erro) {
        if (erro == null) {
            return null;
        }
        return erro.length() <= LIMITE_DO_ERRO ? erro : erro.substring(0, LIMITE_DO_ERRO) + "...";
    }
}
