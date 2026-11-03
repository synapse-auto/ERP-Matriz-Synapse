package com.synapse.crm.core.domain.lead;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CpfTest {
    @ParameterizedTest
    @ValueSource(strings = {"52998224725", "529.982.247-25"})
    void validaDigitosSemInventarDocumento(String valor) { assertThat(Cpf.validoOuNulo(valor)).isEqualTo("52998224725"); }
    @ParameterizedTest @NullSource
    @ValueSource(strings = {"", "00000000000", "11111111111", "52998224724", "CPF 529.982.247-25", "5299822472", "529982247250", "abc", "529 982 247 25"})
    void recusaAusenteFalsoETextoLivre(String valor) { assertThat(Cpf.validoOuNulo(valor)).isNull(); }
}
