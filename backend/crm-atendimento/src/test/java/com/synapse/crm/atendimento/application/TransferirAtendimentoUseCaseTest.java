package com.synapse.crm.atendimento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.synapse.crm.core.domain.lead.StatusBasicoLead;
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
        when(atendimentos.avancarVersaoDoEvento(atendimentoId)).thenReturn(1L);
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

        EventoDeAtendimento.AtendimentoTransferido transferencia = transferenciaPublicada(eventos);
        assertThat(transferencia.paraAtendenteId()).isEqualTo(atendenteId);
        assertThat(transferencia.atorId()).isNull();
        assertThat(transferencia.atorTipo()).isEqualTo(OrigemEvento.AUTOMACAO);
        verify(destinos).exigirAtendenteAtivo(atendenteId);
    }

    @Test
    void reatribuicao_explicita_da_automacao_permite_atendimento_humano_aberto() {
        UUID atendimentoId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID micheleId = UUID.randomUUID();
        UUID daianeId = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        AtendenteParaTransferenciaRepositorio destinos = mock(AtendenteParaTransferenciaRepositorio.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        Atendimento antes = Atendimento.abrirComIa(
                        atendimentoId,
                        leadId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.parse("2026-08-23T11:00:00Z"))
                .transferirPara(micheleId);
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(antes));
        when(atendimentos.porIdParaAlteracao(atendimentoId)).thenReturn(Optional.of(antes));
        when(leads.bloquearParaAtendimento(leadId)).thenReturn(true);
        when(leads.nomeParaTempoReal(leadId)).thenReturn(Optional.of("Lead"));
        when(leads.transferirPara(leadId, daianeId))
                .thenReturn(LeadNoCaminhoDeMensagem.Transferencia.de(micheleId));
        when(atendimentos.avancarVersaoDoEvento(atendimentoId)).thenReturn(1L);
        doReturn(new AtendenteParaTransferenciaRepositorio.Destino(daianeId, "Daiane"))
                .when(destinos)
                .exigirAtendenteAtivo(daianeId);

        TransferirAtendimentoUseCase useCase = new TransferirAtendimentoUseCase(
                atendimentos,
                leads,
                destinos,
                eventos,
                Clock.fixed(Instant.parse("2026-08-23T12:00:00Z"), ZoneOffset.UTC),
                mock(UsuarioContext.class));

        Atendimento depois = useCase.reatribuirPelaAutomacao(atendimentoId, daianeId);

        assertThat(depois.atendenteId()).isEqualTo(daianeId);
        assertThat(depois.status()).isEqualTo(com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento.EM_ATENDIMENTO);
        verify(leads).transferirPara(leadId, daianeId);
        verify(atendimentos).salvar(depois);
        EventoDeAtendimento.AtendimentoTransferido transferencia = transferenciaPublicada(eventos);
        assertThat(transferencia.deAtendenteId()).isEqualTo(micheleId);
        assertThat(transferencia.paraAtendenteId()).isEqualTo(daianeId);
        assertThat(transferencia.atorTipo()).isEqualTo(OrigemEvento.AUTOMACAO);
    }

    @Test
    void reatribuicao_explicita_da_automacao_bloqueia_atendimento_finalizado() {
        UUID atendimentoId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID micheleId = UUID.randomUUID();
        UUID daianeId = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        AtendenteParaTransferenciaRepositorio destinos = mock(AtendenteParaTransferenciaRepositorio.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        Atendimento finalizado = Atendimento.abrirComIa(
                        atendimentoId,
                        leadId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.parse("2026-08-23T11:00:00Z"))
                .transferirPara(micheleId)
                .finalizar(Instant.parse("2026-08-23T12:00:00Z"));
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(finalizado));
        when(atendimentos.porIdParaAlteracao(atendimentoId)).thenReturn(Optional.of(finalizado));
        when(leads.bloquearParaAtendimento(leadId)).thenReturn(true);

        TransferirAtendimentoUseCase useCase = new TransferirAtendimentoUseCase(
                atendimentos,
                leads,
                destinos,
                eventos,
                Clock.fixed(Instant.parse("2026-08-23T13:00:00Z"), ZoneOffset.UTC),
                mock(UsuarioContext.class));

        assertThatThrownBy(() -> useCase.reatribuirPelaAutomacao(atendimentoId, daianeId))
                .isInstanceOf(com.synapse.crm.atendimento.domain.atendimento.AtendimentoJaFinalizadoException.class);
        verify(destinos, never()).exigirAtendenteAtivo(daianeId);
        verify(atendimentos, never()).salvar(finalizado);
        verify(eventos, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rodizio_da_automacao_continua_bloqueado_para_atendimento_humano() {
        UUID atendimentoId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID micheleId = UUID.randomUUID();
        UUID daianeId = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        AtendenteParaTransferenciaRepositorio destinos = mock(AtendenteParaTransferenciaRepositorio.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        Atendimento humano = Atendimento.abrirComIa(
                        atendimentoId,
                        leadId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.parse("2026-08-23T11:00:00Z"))
                .transferirPara(micheleId);
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(humano));
        when(atendimentos.porIdParaAlteracao(atendimentoId)).thenReturn(Optional.of(humano));
        when(leads.bloquearParaAtendimento(leadId)).thenReturn(true);

        TransferirAtendimentoUseCase useCase = new TransferirAtendimentoUseCase(
                atendimentos,
                leads,
                destinos,
                eventos,
                Clock.fixed(Instant.parse("2026-08-23T13:00:00Z"), ZoneOffset.UTC),
                mock(UsuarioContext.class));

        assertThatThrownBy(() -> useCase.executarPelaAutomacao(atendimentoId, daianeId))
                .isInstanceOf(TransferenciaDaAutomacaoInvalidaException.class);
        verify(destinos, never()).exigirAtendenteAtivo(daianeId);
        verify(atendimentos, never()).salvar(humano);
        verify(eventos, never()).publishEvent(org.mockito.ArgumentMatchers.any());
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
        when(atendimentos.avancarVersaoDoEvento(atendimentoId)).thenReturn(1L);
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

    @Test
    void devolverParaIaPelaAutomacao_publicaEventoComAtendimentoELeadSemResponsavel() {
        UUID atendimentoId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID humanoId = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        Atendimento antes = Atendimento.abrirComIa(
                        atendimentoId,
                        leadId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.parse("2026-08-23T11:00:00Z"))
                .transferirPara(humanoId);
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(antes));
        when(atendimentos.porIdParaAlteracao(atendimentoId)).thenReturn(Optional.of(antes));
        when(leads.bloquearParaAtendimento(leadId)).thenReturn(true);
        when(leads.nomeParaTempoReal(leadId)).thenReturn(Optional.of("Lead"));
        when(atendimentos.avancarVersaoDoEvento(atendimentoId)).thenReturn(1L);

        TransferirAtendimentoUseCase useCase = new TransferirAtendimentoUseCase(
                atendimentos,
                leads,
                mock(AtendenteParaTransferenciaRepositorio.class),
                eventos,
                Clock.fixed(Instant.parse("2026-09-04T15:00:00Z"), ZoneOffset.UTC),
                mock(UsuarioContext.class));

        Atendimento depois = useCase.devolverParaIaPelaAutomacao(atendimentoId);

        assertThat(depois.atendenteId()).isNull();
        assertThat(depois.status().name()).isEqualTo("EM_IA");
        verify(atendimentos).salvar(depois);
        verify(leads).marcarStatus(leadId, StatusBasicoLead.IA);

        EventoDeAtendimento.AtendimentoTransferido transferencia = transferenciaPublicada(eventos);
        assertThat(transferencia.atendimentoId()).isEqualTo(atendimentoId);
        assertThat(transferencia.leadId()).isEqualTo(leadId);
        assertThat(transferencia.deAtendenteId()).isEqualTo(humanoId);
        assertThat(transferencia.paraAtendenteId()).isNull();
        assertThat(transferencia.atorTipo()).isEqualTo(OrigemEvento.AUTOMACAO);
    }

    @Test
    void devolverParaIaPelaAutomacao_duasVezes_eIdempotenteSemSegundoEvento() {
        UUID atendimentoId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        AtendimentoRepositorio atendimentos = mock(AtendimentoRepositorio.class);
        LeadNoCaminhoDeMensagem leads = mock(LeadNoCaminhoDeMensagem.class);
        ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
        Atendimento jaNaIa = Atendimento.abrirComIa(
                atendimentoId,
                leadId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-08-23T11:00:00Z"));
        when(atendimentos.porId(atendimentoId)).thenReturn(Optional.of(jaNaIa));
        when(atendimentos.porIdParaAlteracao(atendimentoId)).thenReturn(Optional.of(jaNaIa));
        when(leads.bloquearParaAtendimento(leadId)).thenReturn(true);

        TransferirAtendimentoUseCase useCase = new TransferirAtendimentoUseCase(
                atendimentos,
                leads,
                mock(AtendenteParaTransferenciaRepositorio.class),
                eventos,
                Clock.fixed(Instant.parse("2026-09-04T15:00:00Z"), ZoneOffset.UTC),
                mock(UsuarioContext.class));

        Atendimento depois = useCase.devolverParaIaPelaAutomacao(atendimentoId);

        assertThat(depois.atendenteId()).isNull();
        verify(atendimentos, never()).salvar(jaNaIa);
        verify(eventos, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    private static EventoDeAtendimento.AtendimentoTransferido transferenciaPublicada(
            ApplicationEventPublisher eventos) {
        ArgumentCaptor<Object> publicados = ArgumentCaptor.forClass(Object.class);
        verify(eventos, times(2)).publishEvent(publicados.capture());
        return publicados.getAllValues().stream()
                .filter(EventoDeAtendimento.AtendimentoTransferido.class::isInstance)
                .map(EventoDeAtendimento.AtendimentoTransferido.class::cast)
                .findFirst()
                .orElseThrow();
    }
}
