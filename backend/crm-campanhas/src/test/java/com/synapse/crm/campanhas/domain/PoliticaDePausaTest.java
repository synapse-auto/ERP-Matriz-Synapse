package com.synapse.crm.campanhas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.synapse.crm.campanhas.domain.PoliticaDePausa.Desfechos;

class PoliticaDePausaTest {

    private static final PoliticaDePausa POLITICA = new PoliticaDePausa(20, 50, 20);

    @Test
    @DisplayName("amostra menor que o minimo nunca pausa, por pior que seja a taxa")
    void amostraCurta() {
        assertThat(POLITICA.avaliarTaxa(new Desfechos(19, 19))).isEmpty();
        assertThat(POLITICA.avaliarTaxa(new Desfechos(0, 0))).isEmpty();
    }

    @Test
    @DisplayName("taxa abaixo do limiar nao pausa; no limiar ou acima pausa e informa o percentual")
    void limiar() {
        assertThat(POLITICA.avaliarTaxa(new Desfechos(50, 9))).isEmpty();
        assertThat(POLITICA.avaliarTaxa(new Desfechos(50, 10))).contains("TAXA_DE_FALHA:20");
        assertThat(POLITICA.avaliarTaxa(new Desfechos(50, 25))).contains("TAXA_DE_FALHA:50");
    }

    @ParameterizedTest
    @ValueSource(ints = {130429, 131048, 131049, 80007, 132015, 132016, 132000, 132012})
    @DisplayName("erros da Meta que indicam limite, saude do numero ou template invalido pausam de imediato")
    void codigosDeParada(int codigo) {
        assertThat(PoliticaDePausa.paraImediatamente(codigo)).isTrue();
        assertThat(PoliticaDePausa.motivoPorCodigo(codigo)).isEqualTo("ERRO_DA_META:" + codigo);
    }

    @ParameterizedTest
    @ValueSource(ints = {131026, 131056, 131000, 1})
    @DisplayName("erros por destinatario (nao entregavel, par de contatos) so entram na taxa")
    void codigosPorDestinatario(int codigo) {
        assertThat(PoliticaDePausa.paraImediatamente(codigo)).isFalse();
    }

    @Test
    @DisplayName("sem codigo de erro nao ha parada imediata")
    void semCodigo() {
        assertThat(PoliticaDePausa.paraImediatamente(null)).isFalse();
    }
}
