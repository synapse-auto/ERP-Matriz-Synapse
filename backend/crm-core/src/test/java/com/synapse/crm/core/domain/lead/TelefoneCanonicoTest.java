package com.synapse.crm.core.domain.lead;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TelefoneCanonicoTest {

    private final TelefoneCanonico telefone = new TelefoneCanonico("55");

    @Test
    void formatosEquivalentesProduzemOMesmoTelefone() {
        assertThat(telefone.normalizar("+55 61 99999-9999"))
                .isEqualTo("5561999999999")
                .isEqualTo(telefone.normalizar("5561999999999"));
    }

    @Test
    void telefoneLocalRecebeODdiDaInstancia() {
        assertThat(telefone.normalizar("(61) 99999-9999")).isEqualTo("5561999999999");
    }

    @Test
    void numerosComDozeETrezeDigitosDeOutroPaisPassamIntactos() {
        assertThat(telefone.normalizar("123456789012")).isEqualTo("123456789012");
        assertThat(telefone.normalizar("1234567890123")).isEqualTo("1234567890123");
    }

    @Test
    void entradaAusenteContinuaValidaMasEntradaCurtaEhRecusada() {
        assertThat(telefone.normalizar(null)).isNull();
        assertThatThrownBy(() -> telefone.normalizar("1234"))
                .isInstanceOf(TelefoneInvalidoException.class)
                .hasMessageContaining("DDD e numero");
        assertThatThrownBy(() -> telefone.normalizar(" + - "))
                .isInstanceOf(TelefoneInvalidoException.class);
    }

    /**
     * E111. A Meta entrega o {@code wa_id} de boa parte dos numeros brasileiros sem o nono digito.
     * Enquanto o cadastro manual usa o formato de discagem, o mesmo cliente vira dois cadastros.
     */
    @Nested
    @DisplayName("nono digito de celular brasileiro")
    class NonoDigito {

        @Test
        @DisplayName("assinante de oito digitos comecando em 6, 7, 8 ou 9 ganha o nono")
        void dozeDigitos_assinanteDeCelular_ganhaONono() {
            assertThat(telefone.normalizar("556181536371")).isEqualTo("5561981536371");
            assertThat(telefone.normalizar("556192729612")).isEqualTo("5561992729612");
            assertThat(telefone.normalizar("556181111111")).isEqualTo("5561981111111");
            assertThat(telefone.normalizar("556171111111")).isEqualTo("5561971111111");
            assertThat(telefone.normalizar("556161111111")).isEqualTo("5561961111111");
        }

        /** No Brasil, fixo nunca comeca em 6, 7, 8 ou 9 — e o que torna a inferencia segura. */
        @Test
        @DisplayName("assinante de oito digitos comecando em 2, 3, 4 ou 5 e fixo e nao muda")
        void dozeDigitos_assinanteDeFixo_naoMuda() {
            assertThat(telefone.normalizar("556132241234")).isEqualTo("556132241234");
            assertThat(telefone.normalizar("556122241234")).isEqualTo("556122241234");
            assertThat(telefone.normalizar("556142241234")).isEqualTo("556142241234");
            assertThat(telefone.normalizar("556152241234")).isEqualTo("556152241234");
        }

        @Test
        @DisplayName("treze digitos ja e canonico e passa intacto")
        void trezeDigitos_naoMuda() {
            assertThat(telefone.normalizar("5561981536371")).isEqualTo("5561981536371");
            assertThat(telefone.normalizar("5561992729612")).isEqualTo("5561992729612");
        }

        /**
         * O caso que faz a fusao valer a pena: o numero que chega da Meta e o que o atendente digita
         * sao a mesma chave depois desta etapa.
         */
        @Test
        @DisplayName("com e sem o nono digito convergem para a mesma chave")
        void comESemONono_convergem() {
            assertThat(telefone.normalizar("556181536371"))
                    .isEqualTo(telefone.normalizar("5561981536371"))
                    .isEqualTo(telefone.normalizar("(61) 98153-6371"))
                    .isEqualTo(telefone.normalizar("61 8153-6371"));
        }

        /** A regra e do Brasil: contato de outro pais na base de um filho brasileiro nao e tocado. */
        @Test
        @DisplayName("numero de outro DDI com doze digitos nao ganha nada")
        void outroDdi_naoMuda() {
            assertThat(telefone.normalizar("351219999999")).isEqualTo("351219999999");
            assertThat(telefone.normalizar("541199999999")).isEqualTo("541199999999");
        }

        /**
         * Assinante comecando em 0 ou 1 nao existe no plano brasileiro. Aqui passa intacto de
         * proposito: no caminho de mensagem, adivinhar seria pior que nao mexer. Na migration o
         * mesmo caso aborta, porque la o erro e irreversivel.
         */
        @Test
        @DisplayName("assinante comecando em 0 ou 1 passa intacto, sem adivinhar")
        void assinanteImpossivel_passaIntacto() {
            assertThat(telefone.normalizar("556101111111")).isEqualTo("556101111111");
            assertThat(telefone.normalizar("556111111111")).isEqualTo("556111111111");
        }

        @Test
        @DisplayName("qualquer outro tamanho nao muda")
        void outroTamanho_naoMuda() {
            assertThat(telefone.normalizar("55619815363712")).isEqualTo("55619815363712");
            assertThat(telefone.normalizar("556198153637123")).isEqualTo("556198153637123");
        }

        /**
         * Celular local de dez digitos: o DDI entra primeiro (V26) e so entao a regra do nono digito
         * olha o resultado. A ordem importa — invertida, o numero teria onze digitos e a regra nao
         * reconheceria o formato.
         */
        @Test
        @DisplayName("celular local sem o nono digito ganha DDI e nono digito")
        void localSemONono_ganhaDdiENono() {
            assertThat(telefone.normalizar("6181536371")).isEqualTo("5561981536371");
            assertThat(telefone.normalizar("(61) 8153-6371")).isEqualTo("5561981536371");
        }

        @Test
        @DisplayName("fixo local de dez digitos ganha DDI e nada mais")
        void fixoLocal_ganhaSoODdi() {
            assertThat(telefone.normalizar("6132241234")).isEqualTo("556132241234");
        }
    }
}
