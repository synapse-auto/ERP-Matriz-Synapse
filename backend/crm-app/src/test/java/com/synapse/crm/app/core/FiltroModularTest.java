package com.synapse.crm.app.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.synapse.crm.core.domain.filtro.CampoFiltravel;
import com.synapse.crm.core.domain.filtro.Criterio;
import com.synapse.crm.core.domain.filtro.CriterioComposto;
import com.synapse.crm.core.domain.filtro.CriterioSimples;
import com.synapse.crm.core.domain.filtro.FiltroDeLeads;
import com.synapse.crm.core.domain.filtro.FiltroInvalidoException;

/**
 * As recusas do filtro modular, sem banco e sem Spring.
 *
 * <p>Toda protecao desta etapa nasce com um teste que a viola de proposito. Uma allowlist que nunca
 * reprovou nada e decoracao — foi assim que a E00 descobriu regras de arquitetura que nao rodavam.
 */
class FiltroModularTest {

    private static final String UM_ID = "e1000000-0000-4000-8000-000000000002";

    @Nested
    @DisplayName("allowlist de campos e operadores")
    class Allowlist {

        @Test
        @DisplayName("campo fora da lista e rejeitado, nao saneado")
        void campo_foraDaAllowlist_rejeita() {
            assertThatThrownBy(() -> CriterioSimples.deTextos("senha_hash", "IGUAL", List.of("x")))
                    .isInstanceOf(FiltroInvalidoException.class)
                    .hasMessageContaining("campo nao permitido");
        }

        /**
         * O vetor que a etapa existe para fechar: identificador vindo do cliente nunca vira SQL. Ele
         * nem chega a virar objeto — morre na traducao para {@link CampoFiltravel}.
         */
        @Test
        @DisplayName("campo com SQL embutido nao vira identificador")
        void campo_comSqlEmbutido_rejeita() {
            String ataque = "nome); DROP TABLE lead; --";

            assertThatThrownBy(() -> CriterioSimples.deTextos(ataque, "CONTEM", List.of("a")))
                    .isInstanceOf(FiltroInvalidoException.class)
                    .hasMessageContaining("campo nao permitido");
        }

        @Test
        @DisplayName("operador fora da lista e rejeitado")
        void operador_foraDaAllowlist_rejeita() {
            assertThatThrownBy(() -> CriterioSimples.deTextos("nome", "REGEX", List.of("a")))
                    .isInstanceOf(FiltroInvalidoException.class)
                    .hasMessageContaining("operador nao permitido");
        }

        @Test
        @DisplayName("operador que existe mas nao se aplica ao campo e rejeitado")
        void operador_incompativelComOCampo_rejeita() {
            assertThatThrownBy(() -> CriterioSimples.deTextos("numAtendimentos", "CONTEM", List.of("3")))
                    .isInstanceOf(FiltroInvalidoException.class)
                    .hasMessageContaining("nao se aplica ao campo");
        }

        @Test
        @DisplayName("notas e resumoIa nao sao filtraveis")
        void camposLongos_naoEstaoNaAllowlist() {
            assertThat(CampoFiltravel.apelidos()).doesNotContain("notas", "resumoia");
        }
    }

    @Nested
    @DisplayName("conversao de valores")
    class Valores {

        @Test
        @DisplayName("valor que nao e identificador e rejeitado em campo de referencia")
        void valor_naoUuidEmCampoDeReferencia_rejeita() {
            assertThatThrownBy(() -> CriterioSimples.deTextos("etapa", "IGUAL", List.of("' OR 1=1--")))
                    .isInstanceOf(FiltroInvalidoException.class)
                    .hasMessageContaining("nao e um identificador");
        }

        @Test
        @DisplayName("operador de dois valores com um valor so e rejeitado")
        void aridade_entreComUmValor_rejeita() {
            assertThatThrownBy(() -> CriterioSimples.deTextos("numAtendimentos", "ENTRE", List.of("1")))
                    .isInstanceOf(FiltroInvalidoException.class)
                    .hasMessageContaining("exige exatamente dois valores");
        }

        /** Sem teto, {@code agora.minus(n dias)} estoura a aritmetica e devolve 500. */
        @Test
        @DisplayName("janela de dias absurda e rejeitada antes de virar aritmetica")
        void semRetornoDias_alemDoTeto_rejeita() {
            assertThatThrownBy(() -> CriterioSimples.deTextos(
                            "semRetornoDias", "MAIOR_QUE", List.of(String.valueOf(Long.MAX_VALUE))))
                    .isInstanceOf(FiltroInvalidoException.class)
                    .hasMessageContaining("semRetornoDias deve estar entre");
        }
    }

    @Nested
    @DisplayName("limites da arvore")
    class Limites {

        @Test
        @DisplayName("aninhamento dentro do limite e aceito")
        void profundidade_noLimite_aceita() {
            assertThatCode(() -> new FiltroDeLeads(aninhar(FiltroDeLeads.PROFUNDIDADE_MAXIMA)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("aninhamento alem do limite e rejeitado")
        void profundidade_alemDoLimite_rejeita() {
            assertThatThrownBy(() -> new FiltroDeLeads(aninhar(FiltroDeLeads.PROFUNDIDADE_MAXIMA + 1)))
                    .isInstanceOf(FiltroInvalidoException.class)
                    .hasMessageContaining("aninhado alem de");
        }

        /**
         * O outro formato do mesmo ataque: arvore rasa e larguissima. A verificacao e iterativa
         * justamente para nao estourar a propria pilha no cenario que deveria barrar.
         */
        @Test
        @DisplayName("arvore com nos demais e rejeitada")
        void quantidadeDeNos_alemDoLimite_rejeita() {
            List<Criterio> muitos = IntStream.range(0, CriterioComposto.MAXIMO_DE_FILHOS)
                    .mapToObj(i -> (Criterio) CriterioComposto.ou(List.of(folha(), folha(), folha())))
                    .toList();

            assertThatThrownBy(() -> new FiltroDeLeads(CriterioComposto.e(muitos)))
                    .isInstanceOf(FiltroInvalidoException.class)
                    .hasMessageContaining("mais de " + FiltroDeLeads.NOS_MAXIMOS);
        }

        @Test
        @DisplayName("criterio composto sem filhos e rejeitado")
        void composto_semFilhos_rejeita() {
            assertThatThrownBy(() -> CriterioComposto.e(List.of()))
                    .isInstanceOf(FiltroInvalidoException.class)
                    .hasMessageContaining("ao menos um filho");
        }
    }

    // --- apoio ---------------------------------------------------------------

    private static Criterio aninhar(int niveis) {
        Criterio atual = folha();
        for (int nivel = 1; nivel < niveis; nivel++) {
            atual = CriterioComposto.e(List.of(atual));
        }
        return atual;
    }

    private static Criterio folha() {
        return CriterioSimples.deTextos("etapa", "IGUAL", List.of(UM_ID));
    }
}
