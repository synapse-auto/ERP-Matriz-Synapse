package com.synapse.crm.app.config.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.infrastructure.midia.MidiaProperties;

/**
 * O prefixo e o que isola o alcance de uma referencia. Aponta para uma porta fechada de proposito:
 * se o filtro deixasse passar, a chamada tentaria a rede e o teste falharia por excecao, em vez de
 * devolver vazio ou nao fazer nada.
 */
class MinioArmazenamentoDeFotoDeGrupoTest {

    private final MinioArmazenamentoDeFotoDeGrupo armazenamento = new MinioArmazenamentoDeFotoDeGrupo(
            new BucketDeAvatares(new MidiaProperties("http://127.0.0.1:1", null, "teste", "k", "s", null)));

    @Test
    @DisplayName("referencia de avatar de usuario, de foto de lead ou arbitraria nao e lida pelo adaptador de grupo")
    void naoLeObjetoDeOutroDono() {
        assertThat(armazenamento.buscar("avatar/" + java.util.UUID.randomUUID() + ".png")).isEmpty();
        assertThat(armazenamento.buscar("lead/" + java.util.UUID.randomUUID() + ".png")).isEmpty();
        assertThat(armazenamento.buscar("../avatar/x.png")).isEmpty();
        assertThat(armazenamento.buscar(null)).isEmpty();
    }

    @Test
    @DisplayName("referencia de outro dono ou nula nao e apagada pelo adaptador de grupo")
    void naoApagaObjetoDeOutroDono() {
        assertThatCode(() -> armazenamento.remover("avatar/" + java.util.UUID.randomUUID() + ".png")).doesNotThrowAnyException();
        assertThatCode(() -> armazenamento.remover("lead/" + java.util.UUID.randomUUID() + ".png")).doesNotThrowAnyException();
        assertThatCode(() -> armazenamento.remover(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("o prefixo de grupo e distinto dos de usuario e de lead")
    void prefixosDistintos() {
        assertThat(MinioArmazenamentoDeFotoDeGrupo.PREFIXO).isEqualTo("grupo/")
                .isNotEqualTo(MinioArmazenamentoDeAvatar.PREFIXO)
                .isNotEqualTo(MinioArmazenamentoDeFotoDeLead.PREFIXO);
    }
}
