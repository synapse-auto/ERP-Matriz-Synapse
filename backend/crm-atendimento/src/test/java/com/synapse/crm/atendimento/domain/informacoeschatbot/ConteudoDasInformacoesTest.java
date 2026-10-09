package com.synapse.crm.atendimento.domain.informacoeschatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConteudoDasInformacoesTest {

    private static final int LIMITE = 50;

    @Test
    @DisplayName("preserva as quebras de linha e apara so as pontas")
    void preservaQuebrasDeLinha() {
        var conteudo = ConteudoDasInformacoes.de("  Nome: Maria\nInteresse: avaliação  \n", LIMITE);

        assertThat(conteudo.texto()).isEqualTo("Nome: Maria\nInteresse: avaliação");
    }

    @Test
    @DisplayName("CRLF e CR viram LF: o mesmo texto reenviado e o mesmo pedido")
    void normalizaQuebrasDeLinha() {
        var unix = ConteudoDasInformacoes.de("a\nb\nc", LIMITE);
        var windows = ConteudoDasInformacoes.de("a\r\nb\r\nc", LIMITE);
        var macAntigo = ConteudoDasInformacoes.de("a\rb\rc", LIMITE);

        assertThat(windows).isEqualTo(unix);
        assertThat(macAntigo).isEqualTo(unix);
    }

    @Test
    @DisplayName("aceita exatamente o limite e recusa um caractere a mais")
    void respeitaOLimite() {
        assertThat(ConteudoDasInformacoes.de("x".repeat(LIMITE), LIMITE).texto()).hasSize(LIMITE);

        assertThatThrownBy(() -> ConteudoDasInformacoes.de("x".repeat(LIMITE + 1), LIMITE))
                .isInstanceOf(ConteudoDasInformacoesInvalidoException.class)
                .hasMessageContaining(String.valueOf(LIMITE));
    }

    @Test
    @DisplayName("o limite vale depois de aparar: espaco nas pontas nao conta")
    void limiteAposAparar() {
        String comEspacos = "  " + "x".repeat(LIMITE) + "  ";

        assertThat(ConteudoDasInformacoes.de(comEspacos, LIMITE).texto()).hasSize(LIMITE);
    }

    @Test
    @DisplayName("recusa nulo, vazio e so espacos")
    void recusaVazio() {
        assertThatThrownBy(() -> ConteudoDasInformacoes.de(null, LIMITE))
                .isInstanceOf(ConteudoDasInformacoesInvalidoException.class);
        assertThatThrownBy(() -> ConteudoDasInformacoes.de("", LIMITE))
                .isInstanceOf(ConteudoDasInformacoesInvalidoException.class);
        assertThatThrownBy(() -> ConteudoDasInformacoes.de(" \n\t \r\n ", LIMITE))
                .isInstanceOf(ConteudoDasInformacoesInvalidoException.class);
    }

    @Test
    @DisplayName("recusa caractere nulo, que o Postgres nao armazena em texto")
    void recusaCaractereNulo() {
        assertThatThrownBy(() -> ConteudoDasInformacoes.de("abc\u0000def", LIMITE))
                .isInstanceOf(ConteudoDasInformacoesInvalidoException.class)
                .hasMessageContaining("nulo");
    }

    @Test
    @DisplayName("a mensagem de erro nunca repete o conteudo recebido")
    void erroNaoVazaConteudo() {
        String sensivel = "CPF 123.456.789-00 " + "y".repeat(LIMITE);

        assertThatThrownBy(() -> ConteudoDasInformacoes.de(sensivel, LIMITE))
                .hasMessageNotContaining("123.456.789-00");
    }
}
