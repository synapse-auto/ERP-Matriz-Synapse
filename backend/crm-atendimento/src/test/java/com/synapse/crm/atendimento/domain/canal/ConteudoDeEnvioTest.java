package com.synapse.crm.atendimento.domain.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ConteudoDeEnvioTest {

    @Test
    void templateComCorpoRenderizadoUsaOTextoQueOClienteRecebe() {
        var template = new ConteudoDeEnvio.MensagemTemplate(
                "boas_vindas", "pt_BR", List.of("Maria"), "Olá Maria, seja bem-vinda!");

        assertThat(template.paraHistorico()).isEqualTo("Olá Maria, seja bem-vinda!");
    }

    @Test
    void templateSemCorpoRenderizadoMantemFallbackLegado() {
        var template = new ConteudoDeEnvio.MensagemTemplate("boas_vindas", "pt_BR", List.of("Maria"));

        assertThat(template.corpoRenderizado()).isNull();
        assertThat(template.paraHistorico()).isEqualTo("[template boas_vindas] Maria");
    }

    @Test
    void corpoRenderizadoEmBrancoTambemUsaFallback() {
        var template = new ConteudoDeEnvio.MensagemTemplate("boas_vindas", "pt_BR", List.of(), "  ");

        assertThat(template.paraHistorico()).isEqualTo("[template boas_vindas] ");
    }
}
