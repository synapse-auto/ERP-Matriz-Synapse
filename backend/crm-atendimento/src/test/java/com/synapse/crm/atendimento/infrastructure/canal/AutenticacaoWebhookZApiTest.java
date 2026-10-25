package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AutenticacaoWebhookZApiTest {
    private static final String SEGREDO = "segredo-sintetico-exclusivo-webhook-com-mais-de-43-caracteres";

    @Test void recusaAusenciaErroEDesativacaoSemConfundirComCredenciaisDaApi() {
        var auth = new AutenticacaoWebhookZApi(true, SEGREDO);
        assertThat(auth.confere(null)).isFalse();
        assertThat(auth.confere("")).isFalse();
        assertThat(auth.confere("client-token-da-api")).isFalse();
        assertThat(auth.confere(SEGREDO + "x")).isFalse();
        assertThat(auth.confere(SEGREDO)).isTrue();
        assertThat(new AutenticacaoWebhookZApi(false, SEGREDO).confere(SEGREDO)).isFalse();
        assertThat(auth.toString()).doesNotContain(SEGREDO);
    }

    @Test void segredoCurtoOuAusenteNaoHabilitaUmaEntrada() {
        assertThatThrownBy(() -> new AutenticacaoWebhookZApi(true, "curto"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Z_API_WEBHOOK_SEGREDO_INVALIDO");
        assertThatThrownBy(() -> new AutenticacaoWebhookZApi(true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
