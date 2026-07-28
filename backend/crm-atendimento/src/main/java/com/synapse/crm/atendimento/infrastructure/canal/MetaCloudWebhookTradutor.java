package com.synapse.crm.atendimento.infrastructure.canal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;

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
    public Optional<String> idExterno(String payloadCru) {
        return primeiraMensagem(payloadCru).map(mensagem -> mensagem.path("id").asText(null));
    }

    @Override
    public Optional<MensagemRecebidaDoCanal> traduzir(String payloadCru) {
        Optional<JsonNode> mensagem = primeiraMensagem(payloadCru);
        if (mensagem.isEmpty()) {
            return Optional.empty();
        }
        JsonNode no = mensagem.get();

        // So texto nesta etapa. Midia entra depois; devolver vazio faz o processador
        // marcar como processado sem criar mensagem, em vez de estourar em loop.
        if (!"text".equals(no.path("type").asText())) {
            return Optional.empty();
        }

        return Optional.of(new MensagemRecebidaDoCanal(
                no.path("id").asText(),
                no.path("from").asText(),
                nomeDeExibicao(payloadCru),
                no.path("text").path("body").asText(),
                // A Meta manda epoch em segundos, como string.
                Instant.ofEpochSecond(no.path("timestamp").asLong(Instant.now().getEpochSecond()))));
    }

    // --- formato da Meta (nada abaixo daqui sai desta classe) ------------------

    private Optional<JsonNode> primeiraMensagem(String payloadCru) {
        return valor(payloadCru)
                .map(valor -> valor.path("messages"))
                .flatMap(mensagens -> mensagens.isArray() && !mensagens.isEmpty()
                        ? Optional.of(mensagens.get(0))
                        : Optional.empty());
    }

    private String nomeDeExibicao(String payloadCru) {
        return valor(payloadCru)
                .map(valor -> valor.path("contacts"))
                .filter(contatos -> contatos.isArray() && !contatos.isEmpty())
                .map(contatos -> contatos.get(0).path("profile").path("name").asText(null))
                .orElse(null);
    }

    private Optional<JsonNode> valor(String payloadCru) {
        try {
            JsonNode entradas = json.readTree(payloadCru).path("entry");
            if (!entradas.isArray() || entradas.isEmpty()) {
                return Optional.empty();
            }
            JsonNode mudancas = entradas.get(0).path("changes");
            if (!mudancas.isArray() || mudancas.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(mudancas.get(0).path("value"));
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Payload de webhook ilegivel.", e);
            return Optional.empty();
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
