package com.synapse.crm.campanhas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CorpoDoTemplateTest {

    @Test
    @DisplayName("cada {{n}} vira o n-esimo parametro, inclusive repetido e com espacos")
    void renderiza() {
        assertThat(CorpoDoTemplate.renderizar(
                        "Ola {{1}}, seu pedido {{2}} saiu. Ate logo, {{ 1 }}!", List.of("Maria", "A-10")))
                .isEqualTo("Ola Maria, seu pedido A-10 saiu. Ate logo, Maria!");
    }

    @Test
    @DisplayName("variavel sem parametro fica como esta, para o defeito aparecer; $ e \\ no valor nao quebram")
    void valoresEspeciaisESemParametro() {
        assertThat(CorpoDoTemplate.renderizar("{{1}} {{2}}", List.of("US$ 5\\6"))).isEqualTo("US$ 5\\6 {{2}}");
        assertThat(CorpoDoTemplate.renderizar(null, List.of())).isEmpty();
    }

    @Test
    @DisplayName("quantidade de variaveis e a maior posicao usada")
    void contaVariaveis() {
        assertThat(CorpoDoTemplate.quantidadeDeVariaveis("sem variavel")).isZero();
        assertThat(CorpoDoTemplate.quantidadeDeVariaveis("{{1}} e {{3}}")).isEqualTo(3);
        assertThat(CorpoDoTemplate.quantidadeDeVariaveis(null)).isZero();
    }
}
