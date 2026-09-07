package com.synapse.crm.automacaoconfig.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.automacaoconfig.application.AtualizarLogoDaInstanciaUseCase;
import com.synapse.crm.automacaoconfig.application.AtualizarTemaDaInstanciaUseCase;
import com.synapse.crm.automacaoconfig.application.ObterLogoDaInstanciaUseCase;
import com.synapse.crm.automacaoconfig.application.ObterTemaDaInstanciaUseCase;
import com.synapse.crm.automacaoconfig.application.featureflag.FeatureService;
import com.synapse.crm.automacaoconfig.infrastructure.ConfiguracaoDeInstanciaResources;

/**
 * Unitario, sem Spring context: os casos de uso sao mockados para isolar o contrato HTTP das rotas
 * de configuracao.
 */
class ConfigInstanciaControllerTest {

    private final FeatureService features = mock(FeatureService.class);
    private final ConfiguracaoDeInstanciaResources recursos = mock(ConfiguracaoDeInstanciaResources.class);
    private final CanalGateway canal = mock(CanalGateway.class);
    private final ObterTemaDaInstanciaUseCase obterTema = mock(ObterTemaDaInstanciaUseCase.class);
    private final ObterLogoDaInstanciaUseCase obterLogo = mock(ObterLogoDaInstanciaUseCase.class);
    private final AtualizarTemaDaInstanciaUseCase atualizarTema = mock(AtualizarTemaDaInstanciaUseCase.class);
    private final AtualizarLogoDaInstanciaUseCase atualizarLogo = mock(AtualizarLogoDaInstanciaUseCase.class);
    private final ConfigInstanciaController controller = new ConfigInstanciaController(
            features, recursos, canal, obterTema, obterLogo, atualizarTema, atualizarLogo);

    @Test
    @DisplayName("canal devolve as capacidades do gateway")
    void canal_devolveCapacidadeDoGateway() {
        when(canal.exigeTemplateForaDaJanela()).thenReturn(true);
        when(canal.gerenciaTemplates()).thenReturn(true);

        var resposta = controller.canal();

        assertThat(resposta.exigeTemplateForaDaJanela()).isTrue();
        assertThat(resposta.gerenciaTemplates()).isTrue();
    }

    @Test
    @DisplayName("logo presente: 200, Content-Type image/png, corpo com os bytes do arquivo")
    void logo_presente_devolve200EOTipoCerto() {
        byte[] bytes = {1, 2, 3};
        when(obterLogo.executar()).thenReturn(bytes);

        var resposta = controller.logo();

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(resposta.getBody()).isEqualTo(bytes);
    }

    @Test
    @DisplayName("logo ausente: 404 — nunca deve derrubar a aplicacao")
    void logo_ausente_devolve404() {
        when(obterLogo.executar()).thenReturn(null);

        var resposta = controller.logo();

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
