package com.synapse.crm.atendimento.domain.mensagem;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatusEntregaTest {

    @Test
    void sentDeliveredReadAvanca() {
        assertThat(StatusEntrega.ENVIADO.ehPosteriorA(StatusEntrega.PENDENTE)).isTrue();
        assertThat(StatusEntrega.ENTREGUE.ehPosteriorA(StatusEntrega.ENVIADO)).isTrue();
        assertThat(StatusEntrega.LIDO.ehPosteriorA(StatusEntrega.ENTREGUE)).isTrue();
    }

    @Test
    void readAntesDeDeliveredNaoRebaixa() {
        assertThat(StatusEntrega.LIDO.ehPosteriorA(StatusEntrega.ENVIADO)).isTrue();
        assertThat(StatusEntrega.ENTREGUE.ehPosteriorA(StatusEntrega.LIDO)).isFalse();
    }

    @Test
    void mesmoStatusNaoEPosterior() {
        assertThat(StatusEntrega.ENTREGUE.ehPosteriorA(StatusEntrega.ENTREGUE)).isFalse();
        assertThat(StatusEntrega.LIDO.ehPosteriorA(StatusEntrega.LIDO)).isFalse();
    }

    @Test
    void falhouCabeDepoisDeEnviadoMasNaoDepoisDeLido() {
        assertThat(StatusEntrega.FALHOU.ehPosteriorA(StatusEntrega.PENDENTE)).isTrue();
        assertThat(StatusEntrega.FALHOU.ehPosteriorA(StatusEntrega.ENVIADO)).isTrue();
        assertThat(StatusEntrega.FALHOU.ehPosteriorA(StatusEntrega.ENTREGUE)).isFalse();
        assertThat(StatusEntrega.FALHOU.ehPosteriorA(StatusEntrega.LIDO)).isFalse();
        assertThat(StatusEntrega.ENVIADO.ehPosteriorA(StatusEntrega.FALHOU)).isFalse();
    }
}
