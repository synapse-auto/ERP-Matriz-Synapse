package com.synapse.crm.atendimento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.atendimento.application.referencia.MensagemReferenciaRepositorio;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;

class RegistrarMensagemRecebidaUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-09-03T15:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);

    private AtendimentoRepositorio atendimentos;
    private MensagemRepositorio mensagens;
    private LeadNoCaminhoDeMensagem leads;
    private ApplicationEventPublisher eventos;
    private MensagemReferenciaRepositorio referencias;
    private RegistrarMensagemRecebidaUseCase useCase;

    @BeforeEach
    void preparar() {
        atendimentos = mock(AtendimentoRepositorio.class);
        mensagens = mock(MensagemRepositorio.class);
        leads = mock(LeadNoCaminhoDeMensagem.class);
        eventos = mock(ApplicationEventPublisher.class);
        referencias = mock(MensagemReferenciaRepositorio.class);
        when(leads.alcancavel(any())).thenReturn(true);
        when(atendimentos.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mensagens.registrar(any())).thenAnswer(inv -> inv.getArgument(0));
        useCase = new RegistrarMensagemRecebidaUseCase(
                atendimentos, mensagens, leads, eventos, RELOGIO, referencias);
    }

    @Test
    void semAberto_abreEmIaEMarcaOLeadComoPotencial() {
        UUID leadId = UUID.randomUUID();
        when(atendimentos.abertoDoLead(leadId)).thenReturn(Optional.empty());

        RegistrarMensagemRecebidaUseCase.Resultado resultado = useCase.executar(entrada(leadId));

        assertThat(resultado.abriuAtendimento()).isTrue();
        assertThat(resultado.atendimento().status()).isEqualTo(StatusAtendimento.EM_IA);
        verify(leads).marcarStatus(leadId, StatusBasicoLead.IA);
        verify(leads, never()).transferirPara(any(), any());
    }

    @Test
    void comAberto_naoMexeNoStatusDoLead() {
        UUID leadId = UUID.randomUUID();
        UUID ana = UUID.randomUUID();
        Atendimento aberto = Atendimento.abrirComIa(
                        UUID.randomUUID(), leadId, null, null, AGORA.minusSeconds(60))
                .transferirPara(ana);
        when(atendimentos.abertoDoLead(leadId)).thenReturn(Optional.of(aberto));

        RegistrarMensagemRecebidaUseCase.Resultado resultado = useCase.executar(entrada(leadId));

        assertThat(resultado.abriuAtendimento()).isFalse();
        assertThat(resultado.atendimento().id()).isEqualTo(aberto.id());
        verify(leads, never()).marcarStatus(any(), any());
        verify(atendimentos, never()).salvar(any());
        verify(leads).registrarInteracao(leadId, AGORA, 0, 1);
        verify(leads).registrarMensagemDoLead(leadId, AGORA);
    }

    private static RegistrarMensagemRecebidaUseCase.MensagemRecebida entrada(UUID leadId) {
        return new RegistrarMensagemRecebidaUseCase.MensagemRecebida(
                leadId, null, null, "cliente voltou");
    }
}
