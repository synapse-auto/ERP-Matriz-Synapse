package com.synapse.crm.atendimento.infrastructure.canal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.StatusDeEntregaDoCanal;

/**
 * Tradutor de entrada da Uzapi/Autotic ({@code uzapi.com.br}).
 *
 * <p>A Uzapi documenta um envelope compativel com o da Meta, mas a classe mantem o parsing separado:
 * o ACL deve absorver aliases e eventos especificos do fornecedor sem vazar JSON para o dominio.
 * A URL de callback e unica (configurada no campo {@code webhook} de {@code instance/update}); os
 * paths {@code /webhook/message/*} do Swagger sao tipos de evento do fornecedor, nao sufixos que o
 * CRM precise expor.
 */
@Component
class UzapiAutoticWebhookTradutor implements TradutorDeCanal {

    private static final Logger log = LoggerFactory.getLogger(UzapiAutoticWebhookTradutor.class);

    private static final Map<String, String> STATUS_PARA_CRM =
            Map.of("sent", "ENVIADO", "delivered", "ENTREGUE", "read", "LIDO", "failed", "FALHOU");

    private static final Map<String, String> TIPO_PARA_CRM =
            Map.of(
                    "image", "IMAGEM",
                    "audio", "AUDIO",
                    "document", "DOCUMENTO",
                    "video", "VIDEO",
                    "sticker", "IMAGEM");

    private static final List<String> CHAVES_DE_IDENTIFICADOR_DE_STATUS =
            List.of(
                    "chatid",
                    "chatId",
                    "remotejid",
                    "remoteJid",
                    "from",
                    "sender",
                    "participant",
                    "jid",
                    // A Uzapi identifica Status/Story em `group_id: status@broadcast`.
                    "groupid");

    private final CanalProperties propriedades;
    private final ObjectMapper json;

    UzapiAutoticWebhookTradutor(CanalProperties propriedades, ObjectMapper json) {
        this.propriedades = propriedades;
        this.json = json;
    }

    @Override
    public String provedor() {
        return UzapiAutoticAdapter.PROVEDOR;
    }

    /** A documentação não prevê desafio GET; falha fechada para não inventar um protocolo. */
    @Override
    public boolean tokenDeVerificacaoValido(String tokenRecebido) {
        return false;
    }

    /**
     * A Uzapi não documenta assinatura nativa. O precedente de produção usa o segredo na query da
     * URL de callback; comparamos em tempo constante e recusamos se a configuração estiver ausente.
     */
    @Override
    public boolean assinaturaValida(
            String payloadCru, String assinaturaCabecalho, String segredoConsulta) {
        if (!propriedades.temSegredoDeWebhook() || segredoConsulta == null) {
            return false;
        }
        byte[] esperado = propriedades.webhookSecret().getBytes(StandardCharsets.UTF_8);
        byte[] recebido = segredoConsulta.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(esperado, recebido);
    }

    @Override
    public DestinosDoWebhook destinos(String payloadCru) {
        List<String> identificadores = new ArrayList<>();
        int quantidadeEventos = 0;
        for (JsonNode entrada : entradas(payloadCru)) {
            JsonNode mudancas = entrada.path("changes");
            if (!mudancas.isArray()) {
                continue;
            }
            for (JsonNode mudanca : mudancas) {
                quantidadeEventos++;
                String identificador = mudanca.path("value")
                        .path("metadata")
                        .path("phone_number_id")
                        .asText(null);
                if (identificador != null && !identificador.isBlank()) {
                    identificadores.add(identificador);
                }
            }
        }
        return new DestinosDoWebhook(quantidadeEventos, identificadores);
    }

    @Override
    public List<String> idsExternos(String payloadCru) {
        return mensagens(payloadCru).stream()
                .map(MensagemDoPayload::mensagem)
                .filter(mensagem -> !ehStatusOuStory(mensagem))
                .map(mensagem -> mensagem.path("id").asText(null))
                .filter(id -> id != null && !id.isBlank())
                .toList();
    }

    @Override
    public List<StatusDeEntregaDoCanal> statusDeEntrega(String payloadCru) {
        List<StatusDeEntregaDoCanal> resultado = new ArrayList<>();
        for (JsonNode entrada : entradas(payloadCru)) {
            JsonNode mudancas = entrada.path("changes");
            if (!mudancas.isArray()) {
                continue;
            }
            for (JsonNode mudanca : mudancas) {
                JsonNode statuses = mudanca.path("value").path("statuses");
                if (!statuses.isArray()) {
                    continue;
                }
                for (JsonNode status : statuses) {
                    StatusDeEntregaDoCanal traduzido = traduzirStatus(status);
                    if (traduzido != null) {
                        resultado.add(traduzido);
                    }
                }
            }
        }
        return List.copyOf(resultado);
    }

    @Override
    public List<MensagemRecebidaDoCanal> traduzir(String payloadCru) {
        List<MensagemRecebidaDoCanal> resultado = new ArrayList<>();
        for (MensagemDoPayload mensagemDoPayload : mensagens(payloadCru)) {
            JsonNode mensagem = mensagemDoPayload.mensagem();
            JsonNode valor = mensagemDoPayload.valor();
            String tipo = mensagem.path("type").asText("").toLowerCase(Locale.ROOT);
            try {
                if (ehStatusOuStory(mensagem)) {
                    log.debug("Evento Status/Story da Uzapi descartado.");
                    continue;
                }

                String idExterno = texto(mensagem, "id");
                String telefone = primeiroTexto(mensagem, "from", "sender", "participant");
                if (idExterno == null || idExterno.isBlank() || telefone == null || telefone.isBlank()) {
                    log.warn("Mensagem Uzapi sem identificador obrigatorio; item descartado. type={}", tipo);
                    continue;
                }
                String destino = valor.path("metadata").path("phone_number_id").asText(null);
                String nome = nomeDeExibicao(valor, mensagem, telefone);
                Instant enviadoEm = timestamp(mensagem.path("timestamp"));
                String contexto = contextoWamid(mensagem);

                if ("text".equals(tipo)) {
                    resultado.add(MensagemRecebidaDoCanal.texto(
                            idExterno,
                            destino,
                            telefone,
                            nome,
                            mensagem.path("text").path("body").asText(""),
                            enviadoEm,
                            contexto));
                    continue;
                }

                if ("interactive".equals(tipo) || "button_reply".equals(tipo) || "list_reply".equals(tipo)) {
                    String titulo = tituloDaResposta(mensagem);
                    if (titulo == null || titulo.isBlank()) {
                        log.warn("Resposta interativa Uzapi sem titulo reconhecido; item descartado. type={}", tipo);
                        continue;
                    }
                    resultado.add(MensagemRecebidaDoCanal.texto(
                            idExterno, destino, telefone, nome, titulo, enviadoEm, contexto));
                    continue;
                }

                if ("location".equals(tipo)) {
                    MensagemRecebidaDoCanal localizacao = traduzirLocalizacao(
                            mensagem, idExterno, destino, telefone, nome, enviadoEm, contexto);
                    if (localizacao != null) {
                        resultado.add(localizacao);
                    }
                    continue;
                }

                String tipoCrm = TIPO_PARA_CRM.get(tipo);
                if (tipoCrm == null) {
                    log.warn("Tipo de mensagem Uzapi desconhecido; item descartado. type={}", tipo);
                    continue;
                }

                JsonNode midia = mensagem.path(tipo);
                String midiaId = primeiroTexto(midia, "media_id", "mediaId", "id");
                if (midiaId == null || midiaId.isBlank()) {
                    log.warn("Midia Uzapi sem id; item descartado. type={}", tipo);
                    continue;
                }
                resultado.add(new MensagemRecebidaDoCanal(
                        idExterno,
                        telefone,
                        nome,
                        null,
                        tipoCrm,
                        midiaId,
                        primeiroTexto(midia, "mime_type", "mimeType", "mimetype"),
                        primeiroTexto(midia, "filename", "fileName", "name"),
                        primeiroTexto(midia, "caption"),
                        enviadoEm,
                        destino,
                        contexto));
            } catch (RuntimeException e) {
                // Nunca deixar um item malformado perder os demais itens do mesmo POST. O log não
                // inclui a exceção nem o JSON, pois ambos podem carregar telefone ou conteúdo.
                log.warn("Item de webhook Uzapi malformado; item descartado. type={}", tipo);
            }
        }
        return List.copyOf(resultado);
    }

    private static StatusDeEntregaDoCanal traduzirStatus(JsonNode status) {
        String id = texto(status, "id");
        String estado = STATUS_PARA_CRM.get(status.path("status").asText("").toLowerCase(Locale.ROOT));
        if (id == null || id.isBlank() || estado == null) {
            return null;
        }
        Integer codigo = null;
        String titulo = null;
        JsonNode erros = status.path("errors");
        if (erros.isArray() && !erros.isEmpty()) {
            JsonNode primeiro = erros.get(0);
            if (primeiro.path("code").canConvertToInt()) {
                codigo = primeiro.path("code").asInt();
            }
            titulo = texto(primeiro, "title");
        }
        return new StatusDeEntregaDoCanal(id, estado, codigo, titulo);
    }

    private MensagemRecebidaDoCanal traduzirLocalizacao(
            JsonNode mensagem,
            String idExterno,
            String destino,
            String telefone,
            String nome,
            Instant enviadoEm,
            String contexto) {
        JsonNode localizacao = mensagem.path("location");
        Double latitude = coordenada(localizacao, "latitude");
        Double longitude = coordenada(localizacao, "longitude");
        if (latitude == null
                || longitude == null
                || latitude < -90
                || latitude > 90
                || longitude < -180
                || longitude > 180) {
            log.warn("Coordenadas de localizacao Uzapi invalidas; item descartado.");
            return null;
        }

        ObjectNode metadados = json.createObjectNode();
        metadados.put("latitude", latitude);
        metadados.put("longitude", longitude);
        String local = primeiroTexto(localizacao, "name");
        if (local != null && !local.isBlank()) {
            metadados.put("nome", local);
        }
        String endereco = primeiroTexto(localizacao, "address");
        if (endereco != null && !endereco.isBlank()) {
            metadados.put("endereco", endereco);
        }
        return new MensagemRecebidaDoCanal(
                idExterno,
                telefone,
                nome,
                metadados.toString(),
                "LOCALIZACAO",
                null,
                null,
                null,
                null,
                enviadoEm,
                destino,
                contexto);
    }

    private String nomeDeExibicao(JsonNode valor, JsonNode mensagem, String telefone) {
        if (valor.path("contacts").isArray()) {
            for (JsonNode contato : valor.path("contacts")) {
                if (telefone.equals(contato.path("wa_id").asText(null))) {
                    String nome = contato.path("profile").path("name").asText(null);
                    if (nome != null && !nome.isBlank()) {
                        return nome;
                    }
                }
            }
        }
        return primeiroTexto(mensagem, "pushName", "push_name", "notifyName");
    }

    private String contextoWamid(JsonNode mensagem) {
        String contexto = primeiroTexto(mensagem.path("context"), "id", "message_id", "messageId");
        return contexto == null || contexto.isBlank() ? null : contexto;
    }

    private static String tituloDaResposta(JsonNode mensagem) {
        for (String chave : List.of("button_reply", "list_reply", "reply")) {
            String titulo = texto(mensagem.path("interactive").path(chave), "title");
            if (titulo != null && !titulo.isBlank()) {
                return titulo;
            }
            titulo = mensagem.path(chave).path("title").asText(null);
            if (titulo != null && !titulo.isBlank()) {
                return titulo;
            }
        }
        return null;
    }

    private List<MensagemDoPayload> mensagens(String payloadCru) {
        List<MensagemDoPayload> resultado = new ArrayList<>();
        for (JsonNode entrada : entradas(payloadCru)) {
            JsonNode mudancas = entrada.path("changes");
            if (!mudancas.isArray()) {
                continue;
            }
            for (JsonNode mudanca : mudancas) {
                JsonNode valor = mudanca.path("value");
                JsonNode mensagens = valor.path("messages");
                if (mensagens.isArray()) {
                    for (JsonNode mensagem : mensagens) {
                        resultado.add(new MensagemDoPayload(valor, mensagem));
                    }
                }
            }
        }
        return resultado;
    }

    private record MensagemDoPayload(JsonNode valor, JsonNode mensagem) {}

    private JsonNode entradas(String payloadCru) {
        try {
            JsonNode entradas = json.readTree(payloadCru).path("entry");
            return entradas.isArray() ? entradas : json.createArrayNode();
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Payload de webhook Uzapi ilegivel.");
            return json.createArrayNode();
        }
    }

    private static boolean ehStatusOuStory(JsonNode mensagem) {
        return contemStatusBroadcast(mensagem, null);
    }

    private static boolean contemStatusBroadcast(JsonNode no, String nomeCampo) {
        if (no == null) {
            return false;
        }
        if (no.isTextual()
                && nomeCampo != null
                && CHAVES_DE_IDENTIFICADOR_DE_STATUS.stream()
                        .anyMatch(chave -> chave.equalsIgnoreCase(nomeCampo.replace("_", "")))
                && no.asText().toLowerCase(Locale.ROOT).contains("status@broadcast")) {
            return true;
        }
        if (no.isObject()) {
            var campos = no.fields();
            while (campos.hasNext()) {
                var campo = campos.next();
                if (contemStatusBroadcast(campo.getValue(), campo.getKey())) {
                    return true;
                }
            }
        } else if (no.isArray()) {
            for (JsonNode item : no) {
                if (contemStatusBroadcast(item, nomeCampo)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String texto(JsonNode no, String campo) {
        if (no == null) {
            return null;
        }
        JsonNode valor = no.path(campo);
        return valor.isMissingNode() || valor.isNull() ? null : valor.asText(null);
    }

    private static String primeiroTexto(JsonNode no, String... campos) {
        for (String campo : campos) {
            String valor = texto(no, campo);
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return null;
    }

    private static Double coordenada(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        if (valor.isNumber()) {
            double numero = valor.asDouble();
            return Double.isFinite(numero) ? numero : null;
        }
        if (valor.isTextual()) {
            try {
                double numero = Double.parseDouble(valor.asText());
                return Double.isFinite(numero) ? numero : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Instant timestamp(JsonNode no) {
        long epoch = no.isNumber() || no.isTextual()
                ? no.asLong(Instant.now().getEpochSecond())
                : Instant.now().getEpochSecond();
        return Instant.ofEpochSecond(epoch);
    }
}
