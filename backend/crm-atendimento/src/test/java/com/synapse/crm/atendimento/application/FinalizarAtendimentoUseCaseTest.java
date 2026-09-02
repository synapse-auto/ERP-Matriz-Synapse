package com.synapse.crm.atendimento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;

class FinalizarAtendimentoUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-09-02T16:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);

    @Test
    void potencial_elevaRlsAntesDeGravar() {
        UUID atendimentoId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID quem = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        SolicitacaoDeAvaliacao avaliacao = mock(SolicitacaoDeAvaliacao.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        Atendimento aberto = Atendimento.abrirComIa(
                atendimentoId, leadId, UUID.randomUUID(), UUID.randomUUID(), AGORA.minusSeconds(60));
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(aberto));
        when(atendimentos.porIdParaAlteracao(atendimentoId)).thenReturn(Optional.of(aberto));
        when(leads.bloquearParaAtendimento(leadId)).thenReturn(true);
        when(atendimentos.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        Atendimento depois = new FinalizarAtendimentoUseCase(
                        atendimentos, leads, eventos, RELOGIO, avaliacao)
                .executar(atendimentoId, quem);

        assertThat(depois.status()).isEqualTo(StatusAtendimento.FINALIZADO);
        InOrder ordem = inOrder(atendimentos);
        ordem.verify(atendimentos).elevarRlsParaEscritaDeNovoDono();
        ordem.verify(atendimentos).salvar(depois);
        verify(leads).marcarStatus(leadId, StatusBasicoLead.FINALIZADO);
        verify(avaliacao).preparar(depois);
    }

    @Test
    void emAtendimento_naoElevaRls() {
        UUID atendimentoId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID ana = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        SolicitacaoDeAvaliacao avaliacao = mock(SolicitacaoDeAvaliacao.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        Atendimento aberto = Atendimento.abrirComIa(
                        atendimentoId, leadId, UUID.randomUUID(), UUID.randomUUID(), AGORA.minusSeconds(60))
                .transferirPara(ana);
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(aberto));
        when(atendimentos.porIdParaAlteracao(atendimentoId)).thenReturn(Optional.of(aberto));
        when(leads.bloquearParaAtendimento(leadId)).thenReturn(true);
        when(atendimentos.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        new FinalizarAtendimentoUseCase(atendimentos, leads, eventos, RELOGIO, avaliacao)
                .executarEmLote(atendimentoId, ana);

        verify(atendimentos, never()).elevarRlsParaEscritaDeNovoDono();
        verify(avaliacao, never()).preparar(any());
    }
}
