package com.synapse.crm.core.application.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.synapse.crm.core.application.campocustomizado.CampoCustomizadoRepositorio;
import com.synapse.crm.core.application.tag.LeadDaAutomacaoNaoEncontradoException;
import com.synapse.crm.core.domain.campocustomizado.CampoCustomizado;
import com.synapse.crm.core.domain.campocustomizado.DadosCustomizadosInvalidosException;
import com.synapse.crm.core.domain.campocustomizado.TipoCampoCustomizado;
import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;

class DefinirDataNascimentoDoLeadPelaAutomacaoUseCaseTest {

    private static final String CHAVE = "data_nascimento";

    private final LeadRepositorio leads = mock(LeadRepositorio.class);
    private final CampoCustomizadoRepositorio camposCustomizados = mock(CampoCustomizadoRepositorio.class);
    private final IdempotenciaDeComandoDeLead idempotencia = mock(IdempotenciaDeComandoDeLead.class);

    private DefinirDataNascimentoDoLeadPelaAutomacaoUseCase usecase;

    private final UUID leadId = UUID.randomUUID();
    private final CampoCustomizado campoData =
            new CampoCustomizado(CHAVE, "Data de nascimento", TipoCampoCustomizado.DATA, null, false, false, (short) 0);

    @BeforeEach
    void configurar() {
        usecase = new DefinirDataNascimentoDoLeadPelaAutomacaoUseCase(
                leads, camposCustomizados, idempotencia, new ObjectMapper());
        when(idempotencia.buscar(any())).thenReturn(Optional.empty());
        when(idempotencia.reservar(any(), any(), any(), any()))
                .thenAnswer(chamada -> new IdempotenciaDeComandoDeLead.Reserva(
                        true, chamada.getArgument(0), chamada.getArgument(1), chamada.getArgument(2),
                        chamada.getArgument(3), null));
    }

    @Test
    void leadSemValorPrevioGravaOValorCanonicalizado() {
        when(camposCustomizados.porChave(CHAVE)).thenReturn(Optional.of(campoData));
        when(leads.porId(leadId)).thenReturn(Optional.of(leadComDados(Map.of())));
        when(leads.salvar(any())).thenAnswer(chamada -> Optional.of(chamada.getArgument(0)));

        var resultado = usecase.executar(leadId, "1990-05-21", "chave-1");

        assertThat(resultado.situacao())
                .isEqualTo(DefinirDataNascimentoDoLeadPelaAutomacaoUseCase.Situacao.APLICADO);
        assertThat(resultado.dataNascimento()).startsWith("1990-05-21");

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leads).salvar(captor.capture());
        assertThat(captor.getValue().dadosCustomizados().get(CHAVE)).isEqualTo(resultado.dataNascimento());
    }

    @Test
    void leadComValorJaPreenchidoNaoESobrescrito() {
        when(camposCustomizados.porChave(CHAVE)).thenReturn(Optional.of(campoData));
        when(leads.porId(leadId))
                .thenReturn(Optional.of(leadComDados(Map.of(CHAVE, "1985-01-02T00:00:00Z"))));

        var resultado = usecase.executar(leadId, "1999-12-31", "chave-2");

        assertThat(resultado.situacao())
                .isEqualTo(DefinirDataNascimentoDoLeadPelaAutomacaoUseCase.Situacao.IGNORADO_JA_PREENCHIDO);
        assertThat(resultado.dataNascimento()).isEqualTo("1985-01-02T00:00:00Z");
        verify(leads, never()).salvar(any());
    }

    @Test
    void campoNaoCadastradoLancaExcecaoSemLerOLead() {
        when(camposCustomizados.porChave(CHAVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usecase.executar(leadId, "1990-05-21", "chave-3"))
                .isInstanceOf(CampoDataNascimentoNaoCadastradoException.class);

        verify(leads, never()).porId(any());
        verify(idempotencia, never()).reservar(any(), any(), any(), any());
    }

    @Test
    void campoCadastradoComTipoDiferenteDeDataLancaExcecao() {
        CampoCustomizado campoTexto = new CampoCustomizado(
                CHAVE, "Data de nascimento", TipoCampoCustomizado.TEXTO, null, false, false, (short) 0);
        when(camposCustomizados.porChave(CHAVE)).thenReturn(Optional.of(campoTexto));

        assertThatThrownBy(() -> usecase.executar(leadId, "1990-05-21", "chave-4"))
                .isInstanceOf(CampoDataNascimentoNaoCadastradoException.class);
    }

    @Test
    void formatoInvalidoERecusadoAntesDeLerOLead() {
        when(camposCustomizados.porChave(CHAVE)).thenReturn(Optional.of(campoData));

        assertThatThrownBy(() -> usecase.executar(leadId, "31 de dezembro", "chave-5"))
                .isInstanceOf(DadosCustomizadosInvalidosException.class);

        verify(leads, never()).porId(any());
    }

    @Test
    void leadInexistenteLancaExcecao() {
        when(camposCustomizados.porChave(CHAVE)).thenReturn(Optional.of(campoData));
        when(leads.porId(leadId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usecase.executar(leadId, "1990-05-21", "chave-6"))
                .isInstanceOf(LeadDaAutomacaoNaoEncontradoException.class);
    }

    @Test
    void chaveVaziaLancaExcecao() {
        assertThatThrownBy(() -> usecase.executar(leadId, "1990-05-21", ""))
                .isInstanceOf(IdempotencyKeyInvalidaException.class);
    }

    private Lead leadComDados(Map<String, Object> dadosCustomizados) {
        return new Lead(
                leadId, "Lead de teste", null, null, "5561999999999", null, null, null, null, null,
                null, StatusBasicoLead.IA, null, null, null, null, null, 0, 0, Instant.now(),
                dadosCustomizados);
    }
}
