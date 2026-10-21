package com.synapse.crm.app.cancelamentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.synapse.crm.core.application.lead.LeadRepositorio;
import com.synapse.crm.core.domain.lead.IdentificacaoLead;
import com.synapse.crm.sharedkernel.permissao.VerificadorDeCapacidades;

class IdentificacaoDoLeadCancelamentoAdapterTest {
    private final LeadRepositorio leads=mock(LeadRepositorio.class);
    private final VerificadorDeCapacidades capacidades=mock(VerificadorDeCapacidades.class);
    private final IdentificacaoDoLeadCancelamentoAdapter adapter=new IdentificacaoDoLeadCancelamentoAdapter(leads,capacidades);

    @Test void semVinculoOuCapacidadeNaoConsultaIdentificacao() {
        assertThat(adapter.obter(null)).isEmpty();
        assertThat(adapter.obter(UUID.randomUUID())).isEmpty();
        verifyNoInteractions(leads);
    }

    @Test void consultaSomenteProjecaoDoIdAutorizadoSemCarregarFichaCompleta() {
        UUID id=UUID.randomUUID();
        when(capacidades.permite("atendimentos.ver")).thenReturn(true);
        when(leads.identificacao(id)).thenReturn(Optional.of(new IdentificacaoLead("00000000000","556100000001")));
        var resultado=adapter.obter(id).orElseThrow();
        assertThat(resultado.cpf()).isEqualTo("00000000000");
        assertThat(resultado.telefone()).isEqualTo("556100000001");
        assertThat(resultado.toString()).doesNotContain("00000000000","556100000001");
        verify(leads).identificacao(id);
        verifyNoMoreInteractions(leads);
    }

    @Test void cadastroNaoVisivelNaoDevolveIdentificacao() {
        UUID id=UUID.randomUUID();
        when(capacidades.permite("atendimentos.ver")).thenReturn(true);
        when(leads.identificacao(id)).thenReturn(Optional.empty());
        assertThat(adapter.obter(id)).isEmpty();
    }
}
