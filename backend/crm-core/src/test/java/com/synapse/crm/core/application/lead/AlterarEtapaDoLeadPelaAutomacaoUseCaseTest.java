package com.synapse.crm.core.application.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.synapse.crm.core.application.etapa.EtapaNaoEncontradaException;
import com.synapse.crm.core.application.etapa.EtapaRepositorio;
import com.synapse.crm.core.application.tag.LeadDaAutomacaoNaoEncontradoException;
import com.synapse.crm.core.domain.etapa.EtapaAtendimento;
import com.synapse.crm.core.domain.etapa.ResultadoEtapa;
import com.synapse.crm.core.domain.evento.EtapaDoLeadAlterada;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;
import com.synapse.crm.core.domain.timeline.OrigemEvento;

class AlterarEtapaDoLeadPelaAutomacaoUseCaseTest {

    private final LeadRepositorio leads = mock(LeadRepositorio.class);
    private final EtapaRepositorio etapas = mock(EtapaRepositorio.class);
    private final IdempotenciaDeComandoDeLead idempotencia = mock(IdempotenciaDeComandoDeLead.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final Clock relogio = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);

    private AlterarEtapaDoLeadPelaAutomacaoUseCase usecase;

    private final UUID leadId = UUID.randomUUID();
    private final UUID etapaAnteriorId = UUID.randomUUID();
    private final UUID etapaNovaId = UUID.randomUUID();
    private final EtapaAtendimento etapaAnterior =
            new EtapaAtendimento(etapaAnteriorId, "Novo contato", (short) 1, "#000000", ResultadoEtapa.EM_ANDAMENTO);
    private final EtapaAtendimento etapaNova =
            new EtapaAtendimento(etapaNovaId, "Qualificacao", (short) 2, "#000000", ResultadoEtapa.EM_ANDAMENTO);

    @BeforeEach
    void configurar() {
        usecase = new AlterarEtapaDoLeadPelaAutomacaoUseCase(
                leads, etapas, idempotencia, eventos, new ObjectMapper(), relogio);
        when(idempotencia.buscar(any())).thenReturn(Optional.empty());
        when(idempotencia.reservar(any(), any(), any(), any()))
                .thenAnswer(chamada -> new IdempotenciaDeComandoDeLead.Reserva(
                        true, chamada.getArgument(0), chamada.getArgument(1), chamada.getArgument(2),
                        chamada.getArgument(3), null));
    }

    @Test
    void etapaValidaMudaOLeadEPublicaEventoComOrigemAutomacao() {
        Lead atual = leadCom(etapaAnteriorId);
        when(etapas.porId(etapaNovaId)).thenReturn(Optional.of(etapaNova));
        when(etapas.porId(etapaAnteriorId)).thenReturn(Optional.of(etapaAnterior));
        when(leads.porId(leadId)).thenReturn(Optional.of(atual));
        when(leads.salvar(any())).thenAnswer(chamada -> Optional.of(chamada.getArgument(0)));

        var resultado = usecase.executar(leadId, etapaNovaId, "chave-1");

        assertThat(resultado.alterado()).isTrue();
        assertThat(resultado.etapaId()).isEqualTo(etapaNovaId);

        ArgumentCaptor<EtapaDoLeadAlterada> captor = ArgumentCaptor.forClass(EtapaDoLeadAlterada.class);
        verify(eventos).publishEvent(captor.capture());
        EtapaDoLeadAlterada evento = captor.getValue();
        assertThat(evento.atorId()).isNull();
        assertThat(evento.atorTipo()).isEqualTo(OrigemEvento.AUTOMACAO);
        assertThat(evento.etapaAnterior()).isEqualTo(etapaAnterior);
        assertThat(evento.etapaNova()).isEqualTo(etapaNova);
        assertThat(evento.leadId()).isEqualTo(leadId);
    }

    @Test
    void etapaIgualAAtualNaoAlteraNemPublicaEvento() {
        Lead atual = leadCom(etapaNovaId);
        when(etapas.porId(etapaNovaId)).thenReturn(Optional.of(etapaNova));
        when(leads.porId(leadId)).thenReturn(Optional.of(atual));

        var resultado = usecase.executar(leadId, etapaNovaId, "chave-2");

        assertThat(resultado.alterado()).isFalse();
        verify(eventos, never()).publishEvent(any());
        verify(leads, never()).salvar(any());
    }

    @Test
    void etapaInexistenteLancaExcecaoSemReservarChave() {
        when(etapas.porId(etapaNovaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usecase.executar(leadId, etapaNovaId, "chave-3"))
                .isInstanceOf(EtapaNaoEncontradaException.class);

        verify(idempotencia, never()).reservar(any(), any(), any(), any());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void leadInexistenteLancaExcecao() {
        when(etapas.porId(etapaNovaId)).thenReturn(Optional.of(etapaNova));
        when(leads.porId(leadId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usecase.executar(leadId, etapaNovaId, "chave-4"))
                .isInstanceOf(LeadDaAutomacaoNaoEncontradoException.class);

        verify(idempotencia, never()).reservar(any(), any(), any(), any());
    }

    @Test
    void chaveVaziaLancaExcecao() {
        assertThatThrownBy(() -> usecase.executar(leadId, etapaNovaId, " "))
                .isInstanceOf(IdempotencyKeyInvalidaException.class);
    }

    @Test
    void replayComMesmaChaveDevolveResultadoSemChamarLeadOuEtapa() throws Exception {
        String respostaSalva =
                new ObjectMapper().writeValueAsString(
                        new AlterarEtapaDoLeadPelaAutomacaoUseCase.ResultadoEtapaLead(leadId, etapaNovaId, true));
        when(idempotencia.buscar("chave-5"))
                .thenReturn(Optional.of(new IdempotenciaDeComandoDeLead.Reserva(
                        false, "chave-5", "ETAPA_LEAD", leadId,
                        sha256("ETAPA_LEAD\n" + leadId + "\n" + etapaNovaId), respostaSalva)));

        var resultado = usecase.executar(leadId, etapaNovaId, "chave-5");

        assertThat(resultado.alterado()).isTrue();
        verify(leads, never()).porId(any());
        verify(etapas, never()).porId(any());
        verify(eventos, never()).publishEvent(any());
    }

    private Lead leadCom(UUID etapaId) {
        return new Lead(
                leadId, "Lead de teste", null, null, "5561999999999", null, null, null, null, null,
                null, StatusBasicoLead.IA, etapaId, null, null, null, null, 0, 0, Instant.now(), null);
    }

    private static String sha256(String valor) throws Exception {
        var digest = java.security.MessageDigest.getInstance("SHA-256").digest(valor.getBytes());
        StringBuilder out = new StringBuilder();
        for (byte b : digest) out.append(String.format("%02x", b));
        return out.toString();
    }
}
