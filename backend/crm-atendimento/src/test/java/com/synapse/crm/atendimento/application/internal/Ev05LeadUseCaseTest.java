package com.synapse.crm.atendimento.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.synapse.crm.atendimento.application.IdempotenciaDeComandoAutomacao;
import com.synapse.crm.core.application.lead.AutomacaoEv05LeadRepositorio;

@ExtendWith(MockitoExtension.class)
class Ev05LeadUseCaseTest {
    private static final UUID LEAD = UUID.randomUUID();
    private static final UUID ATENDIMENTO = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-09-12T10:00:00Z");

    @Mock
    private AutomacaoEv05LeadRepositorio leads;

    @Mock
    private AtendimentosEmAndamentoRepositorio atendimentos;

    @Mock
    private IdempotenciaDeComandoAutomacao idempotencia;

    private Ev05LeadUseCase caso;

    @BeforeEach
    void criarCaso() {
        caso = new Ev05LeadUseCase(
                leads,
                atendimentos,
                idempotencia,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                Clock.fixed(AGORA, ZoneOffset.UTC),
                8000);
    }

    @Test
    void resumoGravaComChaveERespostaSegura() {
        when(idempotencia.buscar("k")).thenReturn(Optional.empty());
        when(atendimentos.porLeadEmAtendimento(LEAD))
                .thenReturn(Optional.of(new AtendimentosEmAndamentoRepositorio.Item(
                        ATENDIMENTO,
                        LEAD,
                        com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento.EM_ATENDIMENTO,
                        null,
                        null)));
        when(idempotencia.reservar(eq("k"), eq("EV05_RESUMO"), eq(ATENDIMENTO), any()))
                .thenReturn(new IdempotenciaDeComandoAutomacao.Reserva(
                        true, "k", "EV05_RESUMO", ATENDIMENTO, "hash", null));
        when(leads.gravarResumo(eq(LEAD), eq("cliente aguarda orçamento"), isNull(), eq(AGORA)))
                .thenReturn(new AutomacaoEv05LeadRepositorio.EscritaResumo(LEAD, AGORA));

        var resultado = caso.gravarResumo(LEAD, " cliente aguarda orçamento ", null, "k");

        assertThat(resultado.aplicado()).isTrue();
        verify(leads).gravarResumo(eq(LEAD), eq("cliente aguarda orçamento"), isNull(), eq(AGORA));
    }

    @Test
    void chaveAusenteERecusadaAntesDaEscrita() {
        assertThatThrownBy(() -> caso.gravarResumo(LEAD, "resumo", null, " "))
                .isInstanceOf(com.synapse.crm.atendimento.application.IdempotencyKeyInvalidaException.class);
        verify(leads, never()).gravarResumo(any(), any(), any(), any());
    }

    @Test
    void preenchimentoInvalidoNaoAplicaCampoParcialmente() {
        when(idempotencia.buscar("k")).thenReturn(Optional.empty());
        when(atendimentos.porLeadEmAtendimento(LEAD))
                .thenReturn(Optional.of(new AtendimentosEmAndamentoRepositorio.Item(
                        ATENDIMENTO,
                        LEAD,
                        com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento.EM_ATENDIMENTO,
                        null,
                        null)));
        when(idempotencia.reservar(eq("k"), eq("EV05_PREENCHIMENTO"), eq(ATENDIMENTO), any()))
                .thenReturn(new IdempotenciaDeComandoAutomacao.Reserva(
                        true, "k", "EV05_PREENCHIMENTO", ATENDIMENTO, "hash", null));
        when(leads.aplicarPreenchimento(
                        eq(LEAD), any(), any(), any(), any(), eq(AGORA), any()))
                .thenReturn(new AutomacaoEv05LeadRepositorio.EscritaPreenchimento(
                        LEAD,
                        AutomacaoEv05LeadRepositorio.ResultadoCampo.IGNORADO_INVALIDO,
                        AutomacaoEv05LeadRepositorio.ResultadoCampo.IGNORADO_INVALIDO,
                        AutomacaoEv05LeadRepositorio.ResultadoCampo.AUSENTE,
                        AutomacaoEv05LeadRepositorio.ResultadoCampo.AUSENTE,
                        AGORA));

        var resultado = caso.preencher(LEAD, "email-invalido", "11111111111", null, null, "k");

        assertThat(resultado.email()).isEqualTo(AutomacaoEv05LeadRepositorio.ResultadoCampo.IGNORADO_INVALIDO);
        verify(leads).aplicarPreenchimento(
                eq(LEAD), eq("email-invalido"), eq("11111111111"), isNull(), isNull(), eq(AGORA),
                eq(Set.of("email", "cpf")));
    }
}
