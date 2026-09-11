package com.synapse.crm.atendimento.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.application.AgendarRepasseWebhookAutomacaoUseCase;
import com.synapse.crm.atendimento.application.AplicarStatusDeEntregaDoCanalUseCase;
import com.synapse.crm.atendimento.application.ValidarDestinoWebhookUseCase;
import com.synapse.crm.atendimento.application.WebhookEntrada;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;

class WebhookCanalControllerTest {

    @Test
    void segredoOpcionalDaQueryERepassadoSemAlterarOContratoDaMeta() {
        TradutorDeCanal tradutor = mock(TradutorDeCanal.class);
        WebhookEntrada entrada = mock(WebhookEntrada.class);
        AgendarRepasseWebhookAutomacaoUseCase repasse = mock(AgendarRepasseWebhookAutomacaoUseCase.class);
        ValidarDestinoWebhookUseCase validar = mock(ValidarDestinoWebhookUseCase.class);
        AplicarStatusDeEntregaDoCanalUseCase status = mock(AplicarStatusDeEntregaDoCanalUseCase.class);
        WebhookCanalController controller = new WebhookCanalController(
                tradutor,
                entrada,
                repasse,
                validar,
                status,
                Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC));

        String payload = "{\"entry\":[]}";
        when(tradutor.assinaturaValida(payload, "sha256=assinatura", null)).thenReturn(true);
        when(tradutor.destinos(payload)).thenReturn(new TradutorDeCanal.DestinosDoWebhook(0, List.of()));
        when(validar.executar(new TradutorDeCanal.DestinosDoWebhook(0, List.of())))
                .thenReturn(new ValidarDestinoWebhookUseCase.Decisao(
                        ValidarDestinoWebhookUseCase.Resultado.ACEITO,
                        0,
                        Set.of(),
                        1,
                        0));
        when(tradutor.statusDeEntrega(payload)).thenReturn(List.of());
        when(tradutor.idsExternos(payload)).thenReturn(List.of());

        var resposta = controller.receber(payload, "sha256=assinatura", null);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        verify(tradutor).assinaturaValida(payload, "sha256=assinatura", null);
    }
}
