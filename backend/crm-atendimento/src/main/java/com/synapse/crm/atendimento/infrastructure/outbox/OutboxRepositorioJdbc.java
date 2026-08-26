package com.synapse.crm.atendimento.infrastructure.outbox;

import java.nio.charset.StandardCharsets;
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
    static final String TIPO_REPASSE_WEBHOOK = "automacao.webhook.repassar";

    private static final int LIMITE_DO_ERRO = 500;

    private static final String SQL_ENFILEIRAR =
            "INSERT INTO outbox_evento (id, tipo, payload, criado_em, proxima_tentativa_em)"
                    + " VALUES (?, ?, ?::jsonb, ?, ?)";

    private static final String SQL_ENFILEIRAR_IDEMPOTENTE = SQL_ENFILEIRAR + " ON CONFLICT (id) DO NOTHING";

    /**
     * Seleciona e persiste a reserva na mesma transacao curta. {@code proxima_tentativa_em} ja e a
     * marca duravel que governa a fila: enquanto aponta para o futuro, nenhuma outra instancia
     * seleciona a linha; depois da expiracao, uma reserva orfa volta a ser elegivel.
     */
    private static final String SQL_RESERVAR_ENVIO =
            """
            WITH candidatos AS (
                SELECT id
                  FROM outbox_evento
                 WHERE tipo = ?
                   AND publicado_em IS NULL
                   AND esgotado_em IS NULL
                   AND proxima_tentativa_em <= ?
                 ORDER BY proxima_tentativa_em
                 LIMIT ?
                   FOR UPDATE SKIP LOCKED
            )
            UPDATE outbox_evento o
               SET proxima_tentativa_em = ?
              FROM candidatos c
             WHERE o.id = c.id
            RETURNING o.id, o.payload, o.tentativas
            """;

    /** O repasse de webhook permanece no ciclo existente; esta etapa so muda o envio ao canal. */
    private static final String SQL_RESERVAR_REPASSE =
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

    private static final String SQL_ESGOTADAS =
            "SELECT count(*) FROM outbox_evento WHERE tipo = ? AND esgotado_em IS NOT NULL";

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
            UUID atendimentoId,
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
                serializar(
                        mensagemId, enviadoEm, atendimentoId, leadId, telefoneDestino, credencialId,
                        conteudo),
                Timestamp.from(agora),
                Timestamp.from(agora));
    }

    @Override
    public void enfileirarRepasseWebhook(
            String payloadCru, String assinatura, Instant recebidoEm) {
        TransacaoObrigatoria.exigir("enfileirarRepasseWebhook");
        UUID idempotencia = UUID.nameUUIDFromBytes(
                (TIPO_REPASSE_WEBHOOK + "\n" + assinatura + "\n" + payloadCru)
                        .getBytes(StandardCharsets.UTF_8));
        chat.update(
                SQL_ENFILEIRAR_IDEMPOTENTE,
                idempotencia,
                TIPO_REPASSE_WEBHOOK,
                serializarRepasseWebhook(payloadCru, assinatura),
                Timestamp.from(recebidoEm),
                Timestamp.from(recebidoEm));
    }

    @Override
    public List<EnvioPendente> reservarPendentes(int limite, Instant agora, Instant reservaAte) {
        TransacaoObrigatoria.exigir("reservarPendentes");
        return chat.query(
                SQL_RESERVAR_ENVIO,
                this::desserializar,
                TIPO_ENVIO,
                Timestamp.from(agora),
                limite,
                Timestamp.from(reservaAte));
    }

    @Override
    public List<RepasseWebhookPendente> reservarRepassesWebhookPendentes(
            int limite, Instant agora) {
        TransacaoObrigatoria.exigir("reservarRepassesWebhookPendentes");
        return chat.query(
                SQL_RESERVAR_REPASSE,
                this::desserializarRepasseWebhook,
                TIPO_REPASSE_WEBHOOK,
                Timestamp.from(agora),
                limite);
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
        Long total = chat.queryForObject(SQL_ESGOTADAS, Long.class, TIPO_ENVIO);
        return total == null ? 0L : total;
    }

    @Override
    public long quantidadeRepassesWebhookEsgotados() {
        TransacaoObrigatoria.exigir("quantidadeRepassesWebhookEsgotados");
        Long total = chat.queryForObject(SQL_ESGOTADAS, Long.class, TIPO_REPASSE_WEBHOOK);
        return total == null ? 0L : total;
    }

    // --- serializacao ---------------------------------------------------------

    private String serializar(
            UUID mensagemId,
            Instant enviadoEm,
            UUID atendimentoId,
            UUID leadId,
            String telefoneDestino,
            UUID credencialId,
            ConteudoDeEnvio conteudo) {

        ObjectNode raiz = json.createObjectNode();
        raiz.put("mensagemId", mensagemId.toString());
        raiz.put("enviadoEm", enviadoEm.toString());
        raiz.put("atendimentoId", atendimentoId.toString());
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
            case ConteudoDeEnvio.MensagemMidia midia -> {
                conteudoNo.put("tipo", "MIDIA");
                conteudoNo.put("midiaTipo", midia.tipo().name());
                conteudoNo.put("referenciaStorage", midia.referenciaStorage());
                conteudoNo.put("metadados", midia.metadados());
                conteudoNo.put("legenda", midia.legenda());
            }
        }
        return raiz.toString();
    }

    private String serializarRepasseWebhook(String payloadCru, String assinatura) {
        ObjectNode raiz = json.createObjectNode();
        raiz.put("payloadCru", payloadCru);
        raiz.put("assinatura", assinatura);
        return raiz.toString();
    }

    private EnvioPendente desserializar(ResultSet linha, int indice) throws SQLException {
        JsonNode payload = ler(linha.getString("payload"));
        JsonNode conteudo = payload.get("conteudo");

        return new EnvioPendente(
                linha.getObject("id", UUID.class),
                UUID.fromString(payload.get("mensagemId").asText()),
                Instant.parse(payload.get("enviadoEm").asText()),
                UUID.fromString(payload.get("atendimentoId").asText()),
                UUID.fromString(payload.get("leadId").asText()),
                payload.get("telefoneDestino").asText(),
                payload.get("credencialId").isNull()
                        ? null
                        : UUID.fromString(payload.get("credencialId").asText()),
                paraConteudo(conteudo),
                linha.getInt("tentativas"));
    }

    private RepasseWebhookPendente desserializarRepasseWebhook(ResultSet linha, int indice)
            throws SQLException {
        JsonNode payload = ler(linha.getString("payload"));
        return new RepasseWebhookPendente(
                linha.getObject("id", UUID.class),
                payload.get("payloadCru").asText(),
                payload.get("assinatura").asText(),
                linha.getInt("tentativas"));
    }

    private static ConteudoDeEnvio paraConteudo(JsonNode conteudo) {
        String tipo = conteudo.get("tipo").asText();
        if ("TEMPLATE".equals(tipo)) {
            List<String> parametros = new ArrayList<>();
            conteudo.get("parametros").forEach(parametro -> parametros.add(parametro.asText()));
            return new ConteudoDeEnvio.MensagemTemplate(
                    conteudo.get("nome").asText(), conteudo.get("idioma").asText(), parametros);
        }
        if ("MIDIA".equals(tipo)) {
            return new ConteudoDeEnvio.MensagemMidia(
                    com.synapse.crm.atendimento.domain.mensagem.TipoMensagem.valueOf(
                            conteudo.get("midiaTipo").asText()),
                    conteudo.get("referenciaStorage").asText(),
                    conteudo.get("metadados").asText(null),
                    conteudo.get("legenda").asText(null));
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
