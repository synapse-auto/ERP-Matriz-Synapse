package com.synapse.crm.app.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

import com.synapse.crm.app.PostgresIT;

/** Prova os dois mecanismos da Meta pelo ponto de entrada HTTP real. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=meta-cloud",
            "synapse.canal.whatsapp.webhook-verify-token=" + WebhookMetaIT.VERIFY_TOKEN,
            "synapse.canal.whatsapp.webhook-secret=" + WebhookMetaIT.APP_SECRET,
            "synapse.canal.outbox.intervalo-ms=3600000",
            "synapse.canal.webhook.intervalo-ms=3600000"
        })
class WebhookMetaIT extends PostgresIT {

    static final String VERIFY_TOKEN = "verify-token-de-integracao";
    static final String APP_SECRET = "app-secret-de-integracao-distinto";

    @Autowired
    private TestRestTemplate http;

    @Test
    @DisplayName("GET correto devolve exatamente o challenge enviado pela Meta")
    void verificar_tokenCorreto_devolveChallengeCru() {
        String challenge = "challenge-1234567890";

        ResponseEntity<String> resposta =
                http.getForEntity(uriDeVerificacao(VERIFY_TOKEN, challenge), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isEqualTo(challenge);
    }

    @Test
    @DisplayName("GET com verify token errado devolve 403")
    void verificar_tokenErrado_recusa() {
        ResponseEntity<String> resposta =
                http.getForEntity(uriDeVerificacao("token-incorreto", "nao-devolver"), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resposta.getBody()).isNull();
    }

    @Test
    @DisplayName("POST com HMAC correto e aceito")
    void receber_assinaturaCorreta_aceita() {
        String payload = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}";

        assertThat(postar(payload, assinatura(payload)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("POST com assinatura invalida devolve 403")
    void receber_assinaturaInvalida_recusa() {
        String payload = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}";

        assertThat(postar(payload, "sha256=00").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String uriDeVerificacao(String token, String challenge) {
        return UriComponentsBuilder.fromPath("/webhook/canal")
                .queryParam("hub.mode", "subscribe")
                .queryParam("hub.verify_token", token)
                .queryParam("hub.challenge", challenge)
                .encode()
                .toUriString();
    }

    private ResponseEntity<String> postar(String payload, String assinatura) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        cabecalhos.set("X-Hub-Signature-256", assinatura);
        return http.postForEntity(
                "/webhook/canal", new HttpEntity<>(payload, cabecalhos), String.class);
    }

    private static String assinatura(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256="
                    + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
