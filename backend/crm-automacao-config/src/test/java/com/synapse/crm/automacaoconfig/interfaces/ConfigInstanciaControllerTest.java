package com.synapse.crm.automacaoconfig.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.synapse.crm.automacaoconfig.application.featureflag.FeatureService;
import com.synapse.crm.automacaoconfig.infrastructure.ConfiguracaoDeInstanciaResources;

/**
 * Unitario, sem Spring context: {@code ConfiguracaoDeInstanciaResources} real leria tema.json/
 * textos.json do classpath de {@code crm-app} (nao deste modulo), entao mock e o jeito certo de
 * isolar as duas ramificacoes da rota de logo (E31b bloco 1).
 */
class ConfigInstanciaControllerTest {

    private final FeatureService features = mock(FeatureService.class);
    private final ConfiguracaoDeInstanciaResources recursos = mock(ConfiguracaoDeInstanciaResources.class);
    private final ConfigInstanciaController controller = new ConfigInstanciaController(features, recursos);

    @Test
    @DisplayName("logo presente: 200, Content-Type image/png, corpo com os bytes do arquivo")
    void logo_presente_devolve200EOTipoCerto() {
        byte[] bytes = {1, 2, 3};
        when(recursos.logo()).thenReturn(bytes);

        var resposta = controller.logo();

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(resposta.getBody()).isEqualTo(bytes);
    }

    @Test
    @DisplayName("logo ausente: 404 — nunca deve derrubar a aplicacao")
    void logo_ausente_devolve404() {
        when(recursos.logo()).thenReturn(null);

        var resposta = controller.logo();

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
