package com.synapse.crm.atendimento.domain.canal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

class RegraDeTemplateRestritoTest {

    private final RegraDeTemplateRestrito regra = new RegraDeTemplateRestrito("interno");

    @ParameterizedTest
    @ValueSource(strings = {
        "aviso_interno_cliente", "interno", "INTERNO_boas_vindas", "Aviso_Interno", "subinterno", "uso_iNtErNo"
    })
    void nomeQueContemOTermoEmQualquerCaixaERestrito(String nome) {
        assertThat(regra.restringe(nome)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"aviso_cliente", "internacional", "inter_no", "externo", "retorno_orcamento"})
    void nomeSemOTermoContiguoNaoERestrito(String nome) {
        assertThat(regra.restringe(nome)).isFalse();
    }

    @Test
    void nomeNuloNaoERestrito() {
        assertThat(regra.restringe(null)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PapelUsuario.class, names = {"ATENDENTE", "SUBGESTOR", "GESTOR"})
    void soAdministradorAlcancaTemplateRestrito_gestorNaoEExcecao(PapelUsuario papel) {
        assertThat(regra.permite(papel, "aviso_interno_cliente")).isFalse();
        assertThat(regra.permite(papel, "aviso_cliente")).isTrue();
        assertThat(regra.permite(PapelUsuario.ADMINISTRADOR, "aviso_interno_cliente")).isTrue();
    }

    @Test
    void termoConfiguradoEComparadoSemCaixaESemEspacos() {
        assertThat(new RegraDeTemplateRestrito("  Reservado ").restringe("aviso_reservado")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void termoVazioNaoDesligaARegraEmSilencio(String termo) {
        assertThatThrownBy(() -> new RegraDeTemplateRestrito(termo))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
