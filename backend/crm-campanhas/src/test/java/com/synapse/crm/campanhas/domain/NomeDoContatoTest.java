package com.synapse.crm.campanhas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class NomeDoContatoTest {

    @ParameterizedTest
    @ValueSource(strings = {"Maria", "Jo", "Ana Paula", "ze", "5561 Joao"})
    @DisplayName("nome com ao menos duas letras e utilizavel")
    void utilizavel(String nome) {
        assertThat(NomeDoContato.utilizavel(nome)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "1234", "5561999990000", "---", "A", "12 3", "+55 (61) 9999-0000"})
    @DisplayName("nome so com codigo, numero, pontuacao ou uma letra nao e utilizavel")
    void naoUtilizavel(String nome) {
        assertThat(NomeDoContato.utilizavel(nome)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "MARIA DA SILVA|Maria",
                "maria da silva|Maria",
                "Maria da Silva|Maria",
                "McDonald Lanches|McDonald",
                "  joao   pedro |Joao",
                "ANA-CLARA SOUZA|Ana-Clara",
                "\"Carlos\" Souza|Carlos",
                "5561 Joana|Joana",
                "d'avila neto|D'Avila"
            })
    @DisplayName("primeiro nome: primeiro termo com letras, capitalizado so quando o usuario digitou uniforme")
    void primeiroNome(String entrada, String esperado) {
        assertThat(NomeDoContato.primeiroNome(entrada)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "1234", "---"})
    @DisplayName("sem termo com letras o primeiro nome e vazio")
    void primeiroNomeVazio(String entrada) {
        assertThat(NomeDoContato.primeiroNome(entrada)).isEmpty();
    }
}
