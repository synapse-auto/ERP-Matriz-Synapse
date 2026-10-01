package com.synapse.crm.atendimento.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.application.AgendarRepasseWebhookAutomacaoUseCase;
import com.synapse.crm.atendimento.application.AplicarStatusDeEntregaDoCanalUseCase;
import com.synapse.crm.atendimento.application.ValidarDestinoWebhookUseCase;
import com.synapse.crm.atendimento.application.WebhookEntrada;
import com.synapse.crm.atendimento.application.precificacao.RegistrarPrecificacaoMetaUseCase;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

class WebhookCanalControllerTest {

    @BeforeEach
    void prepararAutoridade() {
        ContextoDeServico.instalarPonteDeAutoridade(nome -> () -> {});
    }

    @AfterEach
    void restaurarAutoridade() {
        ContextoDeServico.instalarPonteDeAutoridade(ContextoDeServico.PonteDeAutoridade.NAO_INSTALADA);
    }

    @Test
    void falhaNaFilaDePrecoNaoRecusaWebhookNemImpedeStatus() {
        TradutorDeCanal tradutor = mock(TradutorDeCanal.class);
        WebhookEntrada entrada = mock(WebhookEntrada.class);
        AgendarRepasseWebhookAutomacaoUseCase repasse = mock(AgendarRepasseWebhookAutomacaoUseCase.class);
        ValidarDestinoWebhookUseCase validar = mock(ValidarDestinoWebhookUseCase.class);
        AplicarStatusDeEntregaDoCanalUseCase status = mock(AplicarStatusDeEntregaDoCanalUseCase.class);
        RegistrarPrecificacaoMetaUseCase precificacao = mock(RegistrarPrecificacaoMetaUseCase.class);
        String payload = "{\"entry\":[]}";
        var observacao = new TradutorDeCanal.PrecificacaoObservada(
                "evento", "wamid.teste", Instant.parse("2026-10-01T00:00:00Z"), true, "service", "regular", "PMP");
        var entrega = new TradutorDeCanal.StatusDeEntregaDoCanal("wamid.teste", "ENTREGUE", null, null);
        when(tradutor.assinaturaValida(payload, "sha256=assinatura", null)).thenReturn(true);
        when(tradutor.destinos(payload)).thenReturn(new TradutorDeCanal.DestinosDoWebhook(0, List.of()));
        when(validar.executar(new TradutorDeCanal.DestinosDoWebhook(0, List.of())))
                .thenReturn(new ValidarDestinoWebhookUseCase.Decisao(
                        ValidarDestinoWebhookUseCase.Resultado.ACEITO, 0, Set.of(), 1, 0));
        when(tradutor.statusDeEntrega(payload)).thenReturn(List.of(entrega));
        when(tradutor.precificacoesObservadas(payload)).thenReturn(List.of(observacao));
        doThrow(new IllegalStateException("fila indisponivel"))
                .when(precificacao)
                .executar(List.of(observacao));

        var controller = new WebhookCanalController(
                tradutor,
                entrada,
                repasse,
                validar,
                status,
                precificacao,
                Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC));
        assertThat(controller.receber(payload, "sha256=assinatura", null).getStatusCode().value())
                .isEqualTo(200);
        verify(status).executar(List.of(entrega));
    }

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
                mock(RegistrarPrecificacaoMetaUseCase.class),
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
        when(tradutor.repasseSemGrupos(payload, "sha256=assinatura")).thenReturn(java.util.Optional.of(
                new TradutorDeCanal.RepasseParaAutomacao(payload, "sha256=assinatura")));

        var resposta = controller.receber(payload, "sha256=assinatura", null);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        verify(tradutor).assinaturaValida(payload, "sha256=assinatura", null);
        verify(repasse).executar(payload, "sha256=assinatura", Instant.parse("2026-09-09T12:00:00Z"));
    }
}
