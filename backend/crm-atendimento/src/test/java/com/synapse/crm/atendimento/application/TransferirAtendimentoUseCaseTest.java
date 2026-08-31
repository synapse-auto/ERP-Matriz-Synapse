package com.synapse.crm.atendimento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
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
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.core.domain.timeline.OrigemEvento;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

class TransferirAtendimentoUseCaseTest {

    @Test
    void transferencia_da_automacao_publica_evento_para_o_destinatario() {
        UUID atendimentoId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID atendenteId = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        AtendenteParaTransferenciaRepositorio destinos = mock(AtendenteParaTransferenciaRepositorio.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        Atendimento antes = Atendimento.abrirComIa(
                atendimentoId,
                leadId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-08-23T11:00:00Z"));
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(antes));
        when(atendimentos.porIdParaAlteracao(atendimentoId)).thenReturn(Optional.of(antes));
        when(leads.bloquearParaAtendimento(leadId)).thenReturn(true);
        doReturn(new AtendenteParaTransferenciaRepositorio.Destino(atendenteId, "Ana"))
                .when(destinos)
                .exigirAtendenteAtivo(atendenteId);

        TransferirAtendimentoUseCase useCase = new TransferirAtendimentoUseCase(
                atendimentos,
                leads,
                destinos,
                eventos,
                Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneOffset.UTC),
                mock(UsuarioContext.class));

        useCase.executarPelaAutomacao(atendimentoId, atendenteId);

        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(evento.capture());
        assertThat(evento.getValue()).isInstanceOf(EventoDeAtendimento.AtendimentoTransferido.class);
        EventoDeAtendimento.AtendimentoTransferido transferencia =
                (EventoDeAtendimento.AtendimentoTransferido) evento.getValue();
        assertThat(transferencia.paraAtendenteId()).isEqualTo(atendenteId);
        assertThat(transferencia.atorId()).isNull();
        assertThat(transferencia.atorTipo()).isEqualTo(OrigemEvento.AUTOMACAO);
        verify(destinos).exigirAtendenteAtivo(atendenteId);
    }

    @Test
    void atendente_transfereProprioAtendimentoParaColegaAtivo() {
        UUID atendimentoId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID anaId = UUID.randomUUID();
        UUID brunoId = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        AtendenteParaTransferenciaRepositorio destinos = mock(AtendenteParaTransferenciaRepositorio.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        UsuarioContext usuarios = mock(UsuarioContext.class);
        Atendimento antes = Atendimento.abrirComIa(
                        atendimentoId,
                        leadId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.parse("2026-08-23T11:00:00Z"))
                .transferirPara(anaId);
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(antes));
        when(atendimentos.porIdParaAlteracao(atendimentoId)).thenReturn(Optional.of(antes));
        when(leads.bloquearParaAtendimento(leadId)).thenReturn(true);
        when(leads.nomeParaTempoReal(leadId)).thenReturn(Optional.of("Lead"));
        when(leads.transferirPara(leadId, brunoId)).thenReturn(LeadNoCaminhoDeMensagem.Transferencia.de(anaId));
        when(usuarios.atual()).thenReturn(new UsuarioAutenticado(anaId, PapelUsuario.ATENDENTE, false));
        doReturn(new AtendenteParaTransferenciaRepositorio.Destino(brunoId, "Bruno"))
                .when(destinos)
                .exigirAtendenteAtivo(brunoId);

        TransferirAtendimentoUseCase useCase = new TransferirAtendimentoUseCase(
                atendimentos,
                leads,
                destinos,
                eventos,
                Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneOffset.UTC),
                usuarios);

        Atendimento depois = useCase.executar(atendimentoId, brunoId, anaId);

        assertThat(depois.atendenteId()).isEqualTo(brunoId);
        verify(destinos).exigirAtendenteAtivo(brunoId);
        verify(atendimentos).elevarRlsParaEscritaDeNovoDono();
        verify(atendimentos).salvar(depois);
        verify(leads).transferirPara(leadId, brunoId);
    }

    @Test
    void atendente_naoDistribuiPotencialParaColega() {
        UUID atendimentoId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID anaId = UUID.randomUUID();
        UUID brunoId = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        AtendenteParaTransferenciaRepositorio destinos = mock(AtendenteParaTransferenciaRepositorio.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        UsuarioContext usuarios = mock(UsuarioContext.class);
        Atendimento potencial = Atendimento.abrirComIa(
                atendimentoId,
                leadId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-08-23T11:00:00Z"));
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(potencial));
        when(atendimentos.porIdParaAlteracao(atendimentoId)).thenReturn(Optional.of(potencial));
        when(leads.bloquearParaAtendimento(leadId)).thenReturn(true);
        when(usuarios.atual()).thenReturn(new UsuarioAutenticado(anaId, PapelUsuario.ATENDENTE, false));

        TransferirAtendimentoUseCase useCase = new TransferirAtendimentoUseCase(
                atendimentos,
                leads,
                destinos,
                eventos,
                Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneOffset.UTC),
                usuarios);

        assertThatThrownBy(() -> useCase.executar(atendimentoId, brunoId, anaId))
                .isInstanceOf(TransferenciaDePotencialProibidaException.class);
        verify(destinos, never()).exigirAtendenteAtivo(brunoId);
        verify(atendimentos, never()).elevarRlsParaEscritaDeNovoDono();
        verify(atendimentos, never()).salvar(potencial);
    }
}
