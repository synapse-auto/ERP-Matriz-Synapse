package com.synapse.crm.atendimento.domain.mensagem;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CitacaoDeMensagemTest {

    @Test
    void previaDeTextoRemoveControleECorta() {
        String previa = CitacaoDeMensagem.previaDe(
                TipoMensagem.TEXTO, "ola\u0000 mundo  ".repeat(40), null);
        assertThat(previa).doesNotContain("\u0000");
        assertThat(previa.length()).isLessThanOrEqualTo(CitacaoDeMensagem.LIMITE_PREVIA);
    }

    @Test
    void previaDeMidiaUsaLegendaSemUrl() {
        String previa = CitacaoDeMensagem.previaDe(
                TipoMensagem.IMAGEM, null, "{\"nome\":\"x.png\",\"legenda\":\"fachada\"}");
        assertThat(previa).isEqualTo("fachada");
        assertThat(previa).doesNotContain("http");
    }

    @Test
    void autorDoLeadUsaNome() {
        assertThat(CitacaoDeMensagem.autorDe(RemetenteTipo.LEAD, "Maria", null)).isEqualTo("Maria");
        assertThat(CitacaoDeMensagem.autorDe(RemetenteTipo.IA, "x", "y")).isEqualTo("IA");
    }
}
