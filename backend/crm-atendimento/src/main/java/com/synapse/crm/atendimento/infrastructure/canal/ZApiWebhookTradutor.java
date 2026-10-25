package com.synapse.crm.atendimento.infrastructure.canal;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.synapse.crm.atendimento.domain.canal.IdentidadeDeMensagemDoCanal;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;

/**
 * ACL registrada por opt-in em ZApiEntradaConfig. Autenticacao fica antes do MVC/fila,
 * no filtro dedicado, nunca nos verificadores HMAC do endpoint Meta/Uzapi.
 */
public final class ZApiWebhookTradutor implements TradutorDeCanal {
    private final ObjectMapper json;
    private final String instancia;
    private final String namespaceStatus;
    private final boolean repasseHabilitado;

    public ZApiWebhookTradutor(ObjectMapper json, String instancia) { this(json, instancia, null); }

    public ZApiWebhookTradutor(ObjectMapper json, String instancia, String namespaceStatus) {
        this(json, instancia, namespaceStatus, false);
    }

    public ZApiWebhookTradutor(ObjectMapper json, String instancia, String namespaceStatus, boolean repasseHabilitado) {
        this.json = json;
        if (instancia == null || instancia.isBlank()) throw new IllegalArgumentException("INSTANCIA_OBRIGATORIA");
        if (namespaceStatus != null && !List.of("WHATSAPP", "PROCESSAMENTO").contains(namespaceStatus)) {
            throw new IllegalArgumentException("NAMESPACE_NAO_CONFIRMADO");
        }
        this.instancia = instancia;
        this.namespaceStatus = namespaceStatus;
        this.repasseHabilitado = repasseHabilitado;
    }

    @Override public String provedor() { return "z-api"; }
    @Override public boolean tokenDeVerificacaoValido(String token) { return false; }
    @Override public boolean assinaturaValida(String payload, String assinatura, String segredo) { return false; }
    @Override public Optional<RepasseParaAutomacao> repasseSemGrupos(String payload, String assinatura) {
        // So mensagens suportadas de cliente. Nao encaminha status, eco, grupo ou LID-only.
        return repasseHabilitado && !traduzirComDescartes(payload).mensagens().isEmpty()
                ? Optional.of(new RepasseParaAutomacao(payload, null)) : Optional.empty();
    }
    @Override public List<StatusDeEntregaDoCanal> statusDeEntrega(String payload) { return List.of(); }

    @Override public DestinosDoWebhook destinos(String payload) {
        String destino = texto(raiz(payload), "instanceId");
        return new DestinosDoWebhook(1, destino == null ? List.of() : List.of(destino));
    }

    @Override public List<String> idsExternos(String payload) {
        JsonNode no = raiz(payload);
        String id = texto(no, "messageId");
        if (!daInstancia(no)) return List.of();
        // Status tambem entra na fila duravel; nao e aplicado pelo indice global do controller.
        String evento = id == null ? no.toString() : texto(no, "type") + ":" + id;
        if ("DeliveryCallback".equals(texto(no, "type"))) {
            evento += ":" + no.path("momment").asText() + ":" + temErro(no);
        }
        return List.of(new IdentidadeDeMensagemDoCanal(provedor(), instancia, "PROCESSAMENTO", evento).chaveInterna());
    }

    @Override public Optional<IdentidadeDeMensagemDoCanal> identidadeDaMensagem(String payload, String idInterno) {
        JsonNode no = raiz(payload);
        String id = texto(no, "messageId");
        if (!daInstancia(no) || id == null) return Optional.empty();
        var identidade = identidade("WHATSAPP", id);
        return identidade.chaveInterna().equals(idInterno) ? Optional.of(identidade) : Optional.empty();
    }

    @Override public List<StatusComIdentidade> statusComIdentidade(String payload) {
        JsonNode no = raiz(payload);
        Instant horario = horario(no);
        if (!daInstancia(no) || horario == null || !flagsValidas(no) || flag(no, "isGroup") || flag(no, "isNewsletter")) return List.of();
        if ("DeliveryCallback".equals(texto(no, "type"))) {
            String id = texto(no, "messageId");
            if (id == null || texto(no, "zaapId") == null || (no.hasNonNull("error") && !no.path("error").isTextual())
                    || (no.hasNonNull("errorCode") && !no.path("errorCode").isTextual())) return List.of();
            StatusEntrega estado = temErro(no) ? StatusEntrega.FALHOU : StatusEntrega.ENVIADO;
            return List.of(new StatusComIdentidade(identidade("WHATSAPP", id), estado, horario));
        }
        if (!"MessageStatusCallback".equals(texto(no, "type")) || namespaceStatus == null) return List.of();
        StatusEntrega estado = switch (no.path("status").asText()) {
            case "SENT" -> StatusEntrega.ENVIADO;
            case "RECEIVED" -> StatusEntrega.ENTREGUE;
            case "READ" -> StatusEntrega.LIDO;
            default -> null;
        };
        if (estado == null || !no.path("ids").isArray()) return List.of();
        List<StatusComIdentidade> saida = new ArrayList<>();
        for (JsonNode item : no.path("ids")) {
            if (!item.isTextual() || !valido(item.asText())) return List.of();
            saida.add(new StatusComIdentidade(identidade(namespaceStatus, item.asText()), estado, horario));
        }
        return List.copyOf(saida);
    }

    @Override public Traducao traduzirComDescartes(String payload) {
        JsonNode no = raiz(payload);
        if (!daInstancia(no)) return descarte("outro", MotivoDeDescarte.OUTRO_CANAL);
        if (!flagsValidas(no)) return descarte("outro", MotivoDeDescarte.ITEM_MALFORMADO);
        String evento = texto(no, "type");
        if ("MessageStatusCallback".equals(evento)) {
            return namespaceStatus == null ? descarte("status", MotivoDeDescarte.NAMESPACE_NAO_CONFIRMADO)
                    : statusComIdentidade(payload).isEmpty() ? descarte("status", MotivoDeDescarte.TIPO_NAO_SUPORTADO)
                    : Traducao.semDescartes(List.of());
        }
        if ("DeliveryCallback".equals(evento) && statusComIdentidade(payload).isEmpty()) return descarte("delivery", MotivoDeDescarte.ITEM_MALFORMADO);
        if (!"ReceivedCallback".equals(evento)) return Traducao.semDescartes(List.of());
        if (!no.path("fromMe").isBoolean()) return descarte("outro", MotivoDeDescarte.ITEM_MALFORMADO);
        if (flag(no, "fromMe")) return Traducao.semDescartes(List.of());
        if (flag(no, "isGroup")) return descarte("outro", MotivoDeDescarte.GRUPO_NAO_SUPORTADO);
        if (flag(no, "isNewsletter") || flag(no, "isStatusReply") || flag(no, "waitingMessage") || flag(no, "isEdit")) {
            return descarte("outro", MotivoDeDescarte.TIPO_NAO_SUPORTADO);
        }
        String telefone = texto(no, "phone");
        if (telefone == null || !telefone.matches("[1-9][0-9]{7,14}")) return descarte("outro", MotivoDeDescarte.IDENTIDADE_NAO_SUPORTADA);
        String id = texto(no, "messageId");
        Instant horario = horario(no);
        if (id == null || horario == null) return descarte("outro", MotivoDeDescarte.SEM_IDENTIFICADOR);
        String nome = texto(no, "senderName");
        String mensagem = conteudo(no.path("text"), "message");
        if (mensagem != null) {
            return Traducao.semDescartes(List.of(MensagemRecebidaDoCanal.texto(
                    identidade("WHATSAPP", id).chaveInterna(), instancia, telefone, nome, mensagem, horario)));
        }
        for (String tipo : List.of("document", "image", "audio", "video")) {
            JsonNode midia = no.path(tipo);
            if (!midia.isObject()) continue;
            String url = texto(midia, tipo + "Url");
            if (!urlHttps(url) || midia.hasNonNull("downloadError")) return descarte(tipo, MotivoDeDescarte.CONTEUDO_INVALIDO);
            String tipoCrm = switch (tipo) { case "document" -> "DOCUMENTO"; case "image" -> "IMAGEM"; case "audio" -> "AUDIO"; default -> "VIDEO"; };
            return Traducao.semDescartes(List.of(new MensagemRecebidaDoCanal(
                    identidade("WHATSAPP", id).chaveInterna(), telefone, nome, null, tipoCrm, url,
                    texto(midia, "mimeType"), texto(midia, "fileName"), conteudo(midia, "caption"), horario, instancia)));
        }
        return descarte("outro", MotivoDeDescarte.TIPO_NAO_SUPORTADO);
    }

    private IdentidadeDeMensagemDoCanal identidade(String namespace, String id) {
        return new IdentidadeDeMensagemDoCanal(provedor(), instancia, namespace, id);
    }

    private boolean daInstancia(JsonNode no) { return instancia.equals(texto(no, "instanceId")); }
    private static boolean temErro(JsonNode no) {
        return (no.path("error").isTextual() && !no.path("error").asText().isBlank())
                || (no.path("errorCode").isTextual() && !no.path("errorCode").asText().isBlank());
    }
    private static boolean flagsValidas(JsonNode no) {
        return List.of("isGroup", "isNewsletter", "isStatusReply", "waitingMessage", "isEdit", "fromMe").stream()
                .allMatch(campo -> !no.has(campo) || no.path(campo).isBoolean());
    }
    private static boolean flag(JsonNode no, String campo) { return no.path(campo).isBoolean() && no.path(campo).booleanValue(); }
    private static boolean valido(String texto) { return texto != null && !texto.isBlank() && texto.chars().noneMatch(Character::isISOControl); }
    private static String texto(JsonNode no, String campo) { JsonNode valor = no.path(campo); return valor.isTextual() && valido(valor.asText()) ? valor.asText() : null; }
    private static String conteudo(JsonNode no, String campo) { JsonNode valor = no.path(campo); return valor.isTextual() && !valor.asText().isBlank() ? valor.asText() : null; }
    private static Instant horario(JsonNode no) {
        JsonNode valor = no.path("momment");
        if (!valor.isIntegralNumber() || !valor.canConvertToLong() || valor.longValue() <= 0) return null;
        try { return Instant.ofEpochMilli(valor.longValue()); } catch (RuntimeException e) { return null; }
    }
    private static boolean urlHttps(String url) {
        if (url == null) return false;
        try { URI uri = URI.create(url); return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null && uri.getFragment() == null; }
        catch (RuntimeException e) { return false; }
    }
    private Traducao descarte(String tipo, MotivoDeDescarte motivo) { return new Traducao(List.of(), List.of(new ItemDescartado(tipo, motivo))); }
    private JsonNode raiz(String payload) {
        try {
            JsonNode no = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(payload);
            return no != null && no.isObject() ? no : json.createObjectNode();
        } catch (Exception e) { return json.createObjectNode(); }
    }
}
