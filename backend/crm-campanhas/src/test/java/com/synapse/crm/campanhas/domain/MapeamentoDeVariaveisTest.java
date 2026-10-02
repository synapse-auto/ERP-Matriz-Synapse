package com.synapse.crm.campanhas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.campanhas.domain.MapeamentoDeVariaveis.Variavel;

class MapeamentoDeVariaveisTest {

    private static final CampoDoLead.Dados MARIA = new CampoDoLead.Dados("MARIA da Silva", "Vidracaria Sol", "Brasilia");

    @Test
    @DisplayName("resolve na ordem do template, mesmo com o mapeamento fora de ordem")
    void resolveNaOrdemDoTemplate() {
        MapeamentoDeVariaveis mapeamento = new MapeamentoDeVariaveis(List.of(
                new Variavel(2, CampoDoLead.EMPRESA, "sua empresa"),
                new Variavel(1, CampoDoLead.PRIMEIRO_NOME, "cliente")));

        assertThat(mapeamento.resolver(MARIA)).containsExactly("Maria", "Vidracaria Sol");
    }

    @Test
    @DisplayName("campo vazio usa o valor de reserva: o resultado nunca tem parametro vazio")
    void campoVazioUsaReserva() {
        MapeamentoDeVariaveis mapeamento = new MapeamentoDeVariaveis(List.of(
                new Variavel(1, CampoDoLead.PRIMEIRO_NOME, "cliente"),
                new Variavel(2, CampoDoLead.EMPRESA, "sua empresa")));

        List<String> parametros = mapeamento.resolver(new CampoDoLead.Dados("---", "   ", null));

        assertThat(parametros).containsExactly("cliente", "sua empresa");
    }

    @Test
    @DisplayName("quebra de linha, tabulacao e espacos repetidos nao chegam ao provedor")
    void sanitizaOValor() {
        MapeamentoDeVariaveis mapeamento =
                new MapeamentoDeVariaveis(List.of(new Variavel(1, CampoDoLead.NOME_COMPLETO, "cliente")));

        assertThat(mapeamento.resolver(new CampoDoLead.Dados("Ana\n\tPaula    Souza", null, null)))
                .containsExactly("Ana Paula Souza");
    }

    @Test
    @DisplayName("valida a quantidade exata de variaveis do template")
    void quantidadeDeVariaveis() {
        MapeamentoDeVariaveis uma = new MapeamentoDeVariaveis(List.of(new Variavel(1, CampoDoLead.PRIMEIRO_NOME, "x")));

        uma.validarPara(1);
        assertThatThrownBy(() -> uma.validarPara(2)).isInstanceOf(CampanhaInvalidaException.class);
        assertThatThrownBy(() -> uma.validarPara(0)).isInstanceOf(CampanhaInvalidaException.class);
        MapeamentoDeVariaveis.vazio().validarPara(0);
    }

    @Test
    @DisplayName("recusa posicao repetida, fora da faixa e reserva em branco ou longa demais")
    void recusaMapeamentosInvalidos() {
        assertThatThrownBy(() -> new MapeamentoDeVariaveis(List.of(
                                new Variavel(1, CampoDoLead.PRIMEIRO_NOME, "a"),
                                new Variavel(1, CampoDoLead.EMPRESA, "b")))
                        .validarPara(2))
                .isInstanceOf(CampanhaInvalidaException.class);
        assertThatThrownBy(() -> new MapeamentoDeVariaveis(List.of(new Variavel(3, CampoDoLead.PRIMEIRO_NOME, "a")))
                        .validarPara(1))
                .isInstanceOf(CampanhaInvalidaException.class);
        assertThatThrownBy(() -> new MapeamentoDeVariaveis(List.of(new Variavel(1, CampoDoLead.PRIMEIRO_NOME, "  ")))
                        .validarPara(1))
                .isInstanceOf(CampanhaInvalidaException.class);
        assertThatThrownBy(() -> new MapeamentoDeVariaveis(
                                List.of(new Variavel(1, CampoDoLead.PRIMEIRO_NOME, "x".repeat(61))))
                        .validarPara(1))
                .isInstanceOf(CampanhaInvalidaException.class);
        assertThatThrownBy(() -> new MapeamentoDeVariaveis(List.of(new Variavel(1, null, "a"))).validarPara(1))
                .isInstanceOf(CampanhaInvalidaException.class);
    }
}
