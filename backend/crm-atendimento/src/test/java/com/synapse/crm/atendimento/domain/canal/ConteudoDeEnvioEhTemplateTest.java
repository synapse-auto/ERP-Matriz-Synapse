package com.synapse.crm.atendimento.domain.canal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;

/** E233: o marcador de template nasce do tipo do conteúdo de envio, nunca do texto do histórico. */
class ConteudoDeEnvioEhTemplateTest {

    @Test
    @DisplayName("template é template com ou sem corpo renderizado")
    void template_ehTemplate() {
        assertThat(new ConteudoDeEnvio.MensagemTemplate("boas_vindas", "pt_BR", List.of("Maria")).ehTemplate())
                .isTrue();
        assertThat(new ConteudoDeEnvio.MensagemTemplate("boas_vindas", "pt_BR", List.of("Maria"), "Olá, Maria")
                        .ehTemplate())
                .isTrue();
    }

    @Test
    @DisplayName("texto livre não é template, mesmo que o texto imite o marcador técnico")
    void textoLivre_naoEhTemplate() {
        assertThat(new ConteudoDeEnvio.MensagemLivre("oi, tudo bem?").ehTemplate()).isFalse();
        assertThat(new ConteudoDeEnvio.MensagemLivre("[template boas_vindas] Maria").ehTemplate()).isFalse();
    }

    @Test
    @DisplayName("mídia não é template")
    void midia_naoEhTemplate() {
        assertThat(new ConteudoDeEnvio.MensagemMidia(TipoMensagem.IMAGEM, "chave/arquivo.png", "{}", null)
                        .ehTemplate())
                .isFalse();
    }
}
