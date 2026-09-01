package com.synapse.crm.atendimento.infrastructure.canal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.StatusDeEntregaDoCanal;

/**
 * Traducao do webhook da Meta Cloud API. A outra metade do ACL.
 *
 * <p>O formato da Meta e profundamente aninhado — {@code entry[].changes[].value.messages[]} — e nada
 * disso sai daqui. Quem chama recebe {@link TradutorDeCanal.MensagemRecebidaDoCanal} e nao tem como
 * saber de onde veio.
 */
@Component
class MetaCloudWebhookTradutor implements TradutorDeCanal {

    private static final Logger log = LoggerFactory.getLogger(MetaCloudWebhookTradutor.class);

    private static final String ALGORITMO = "HmacSHA256";
    private static final String PREFIXO_ASSINATURA = "sha256=";

    private final CanalProperties propriedades;
    private final ObjectMapper json;

    MetaCloudWebhookTradutor(CanalProperties propriedades, ObjectMapper json) {
        this.propriedades = propriedades;
        this.json = json;
    }

    @Override
    public String provedor() {
        return MetaCloudApiAdapter.PROVEDOR;
    }

    /** Token do desafio de cadastro, distinto do App Secret que assina o {@code POST}. */
    @Override
    public boolean tokenDeVerificacaoValido(String tokenRecebido) {
        if (!propriedades.temTokenDeVerificacao()) {
            log.error(
                    "synapse.canal.whatsapp.webhook-verify-token ausente: recusando desafio de webhook.");
            return false;
        }
        if (tokenRecebido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                propriedades.webhookVerifyToken().getBytes(StandardCharsets.UTF_8),
                tokenRecebido.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * HMAC-SHA256 do corpo cru com o segredo do app, como a Meta manda em
     * {@code X-Hub-Signature-256}.
     *
     * <p>Duas decisoes deliberadas de falhar fechado:
     *
     * <ul>
     *   <li>sem segredo configurado, <b>nada</b> e aceito. A alternativa — pular a verificacao quando
     *       falta configuracao — significa que esquecer uma variavel de ambiente abre a rota para a
     *       internet inteira, sem nenhum sinal;
     *   <li>a comparacao usa {@link MessageDigest#isEqual}, que nao retorna cedo. Um {@code equals()}
     *       comum vaza, pelo tempo de resposta, quantos bytes iniciais bateram — e assinatura se
     *       descobre byte a byte assim.
     * </ul>
     */
    @Override
    public boolean assinaturaValida(String payloadCru, String assinaturaRecebida) {
        if (!propriedades.temSegredoDeWebhook()) {
            log.error("synapse.canal.whatsapp.webhook-secret ausente: recusando todo webhook.");
            return false;
        }
        if (assinaturaRecebida == null || !assinaturaRecebida.startsWith(PREFIXO_ASSINATURA)) {
            return false;
        }

        byte[] esperada = calcular(payloadCru);
        byte[] recebida = decodificar(assinaturaRecebida.substring(PREFIXO_ASSINATURA.length()));
        return recebida.length > 0 && MessageDigest.isEqual(esperada, recebida);
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

    private static final Map<String, String> STATUS_META_PARA_CRM =
            Map.of("sent", "ENVIADO", "delivered", "ENTREGUE", "read", "LIDO", "failed", "FALHOU");

    private static StatusDeEntregaDoCanal traduzirStatus(JsonNode status) {
        String wamid = status.path("id").asText(null);
        if (wamid == null || wamid.isBlank()) {
            return null;
        }
        String crm = STATUS_META_PARA_CRM.get(status.path("status").asText());
        if (crm == null) {
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
            String lido = primeiro.path("title").asText(null);
            titulo = (lido == null || lido.isBlank()) ? null : lido;
        }
        return new StatusDeEntregaDoCanal(wamid, crm, codigo, titulo);
    }

    /** {@code type} da Meta -> {@code TipoMensagem} do CRM. {@code null} para tipo desconhecido. */
    private static final Map<String, String> TIPO_META_PARA_CRM =
            Map.of("image", "IMAGEM", "audio", "AUDIO", "document", "DOCUMENTO");

    @Override
    public List<MensagemRecebidaDoCanal> traduzir(String payloadCru) {
        List<MensagemRecebidaDoCanal> traduzidas = new ArrayList<>();
        for (MensagemDoPayload mensagemDoPayload : mensagens(payloadCru)) {
            JsonNode no = mensagemDoPayload.mensagem();
            String tipoMeta = no.path("type").asText();

            // A Meta manda epoch em segundos, como string.
            Instant enviadoEm =
                    Instant.ofEpochSecond(no.path("timestamp").asLong(Instant.now().getEpochSecond()));
            String idExterno = no.path("id").asText();
            String identificadorDestino = mensagemDoPayload.valor().path("metadata").path("phone_number_id").asText(null);
            String telefoneRemetente = no.path("from").asText();
            String nomeExibicao = nomeDeExibicao(mensagemDoPayload.valor(), telefoneRemetente);
            String contextoWamid = no.path("context").path("id").asText(null);

            if ("interactive".equals(tipoMeta)) {
                String titulo = tituloDaResposta(no.path("interactive"));
                if (titulo == null || titulo.isBlank()) {
                    continue;
                }
                // A resposta do cliente é texto do ponto de vista do histórico. O id interno da
                // opção é controle do provedor; o atendente precisa ver o título que o cliente leu.
                traduzidas.add(MensagemRecebidaDoCanal.texto(
                        idExterno, identificadorDestino, telefoneRemetente, nomeExibicao, titulo, enviadoEm,
                        contextoWamid));
                continue;
            }

            if ("text".equals(tipoMeta)) {
                traduzidas.add(MensagemRecebidaDoCanal.texto(
                        idExterno, identificadorDestino, telefoneRemetente, nomeExibicao,
                        no.path("text").path("body").asText(), enviadoEm, contextoWamid));
                continue;
            }

            String tipoCrm = TIPO_META_PARA_CRM.get(tipoMeta);
            if (tipoCrm == null) {
                // Nem texto, nem midia suportada (status, reacao, sticker, etc.). Ignorar
                // somente este item preserva as mensagens boas que vierem no mesmo POST.
                continue;
            }

            JsonNode midiaNo = no.path(tipoMeta);
            traduzidas.add(new MensagemRecebidaDoCanal(
                    idExterno,
                    telefoneRemetente,
                    nomeExibicao,
                    null,
                    tipoCrm,
                    midiaNo.path("id").asText(null),
                    midiaNo.path("mime_type").asText(null),
                    midiaNo.path("filename").asText(null),
                    midiaNo.path("caption").asText(null),
                    enviadoEm,
                    identificadorDestino,
                    contextoWamid));
        }
        return traduzidas;
    }

    // --- formato da Meta (nada abaixo daqui sai desta classe) ------------------

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
                if (!mensagens.isArray()) {
                    continue;
                }
                for (JsonNode mensagem : mensagens) {
                    resultado.add(new MensagemDoPayload(valor, mensagem));
                }
            }
        }
        return resultado;
    }

    private String nomeDeExibicao(JsonNode valor, String telefoneRemetente) {
        JsonNode contatos = valor.path("contacts");
        if (!contatos.isArray()) {
            return null;
        }
        for (JsonNode contato : contatos) {
            if (telefoneRemetente.equals(contato.path("wa_id").asText(null))) {
                return contato.path("profile").path("name").asText(null);
            }
        }
        return null;
    }

    private static String tituloDaResposta(JsonNode interativa) {
        String tipo = interativa.path("type").asText();
        if ("button".equals(tipo)) {
            return interativa.path("button_reply").path("title").asText(null);
        }
        if ("list".equals(tipo)) {
            return interativa.path("list_reply").path("title").asText(null);
        }
        return null;
    }

    private record MensagemDoPayload(JsonNode valor, JsonNode mensagem) {}

    private JsonNode entradas(String payloadCru) {
        try {
            JsonNode entradas = json.readTree(payloadCru).path("entry");
            if (!entradas.isArray()) {
                return json.createArrayNode();
            }
            return entradas;
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Payload de webhook ilegivel.", e);
            return json.createArrayNode();
        }
    }

    private byte[] calcular(String payloadCru) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(
                    propriedades.webhookSecret().getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return mac.doFinal(payloadCru.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("falha ao calcular a assinatura do webhook", e);
        }
    }

    private static byte[] decodificar(String hexadecimal) {
        try {
            return HexFormat.of().parseHex(hexadecimal);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }
}
