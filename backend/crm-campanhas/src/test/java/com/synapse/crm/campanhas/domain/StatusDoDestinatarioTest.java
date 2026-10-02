package com.synapse.crm.campanhas.domain;

import static com.synapse.crm.campanhas.domain.StatusDoDestinatario.ENFILEIRADO;
import static com.synapse.crm.campanhas.domain.StatusDoDestinatario.ENTREGUE;
import static com.synapse.crm.campanhas.domain.StatusDoDestinatario.ENVIADO;
import static com.synapse.crm.campanhas.domain.StatusDoDestinatario.FALHA;
import static com.synapse.crm.campanhas.domain.StatusDoDestinatario.IGNORADO;
import static com.synapse.crm.campanhas.domain.StatusDoDestinatario.LIDO;
import static com.synapse.crm.campanhas.domain.StatusDoDestinatario.PENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatusDoDestinatarioTest {

    @Test
    @DisplayName("enfileirar conta o primeiro degrau do funil")
    void enfileirar() {
        assertThat(StatusDoDestinatario.niveisCruzados(PENDENTE, ENFILEIRADO)).containsExactly(ENFILEIRADO);
    }

    @Test
    @DisplayName("quando a Meta pula degraus, todos os degraus cruzados contam")
    void pulaDegraus() {
        assertThat(StatusDoDestinatario.niveisCruzados(ENFILEIRADO, LIDO)).containsExactly(ENVIADO, ENTREGUE, LIDO);
        assertThat(StatusDoDestinatario.niveisCruzados(ENVIADO, LIDO)).containsExactly(ENTREGUE, LIDO);
    }

    @Test
    @DisplayName("repeticao e retrocesso nao contam nada: a entrega chega fora de ordem")
    void repeticaoERetrocesso() {
        assertThat(StatusDoDestinatario.niveisCruzados(ENVIADO, ENVIADO)).isEmpty();
        assertThat(StatusDoDestinatario.niveisCruzados(LIDO, ENTREGUE)).isEmpty();
        assertThat(StatusDoDestinatario.niveisCruzados(ENTREGUE, ENVIADO)).isEmpty();
    }

    @Test
    @DisplayName("FALHA e IGNORADO ficam fora do funil")
    void foraDoFunil() {
        assertThat(StatusDoDestinatario.niveisCruzados(ENVIADO, FALHA)).isEmpty();
        assertThat(StatusDoDestinatario.niveisCruzados(FALHA, ENVIADO)).isEmpty();
        assertThat(StatusDoDestinatario.niveisCruzados(PENDENTE, IGNORADO)).isEmpty();
        assertThat(FALHA.noFunil()).isFalse();
        assertThat(IGNORADO.noFunil()).isFalse();
    }

    @Test
    @DisplayName("falha so e aceita enquanto enfileirado ou enviado; entregue nao falha depois")
    void aceitaFalha() {
        assertThat(ENFILEIRADO.aceitaFalha()).isTrue();
        assertThat(ENVIADO.aceitaFalha()).isTrue();
        assertThat(ENTREGUE.aceitaFalha()).isFalse();
        assertThat(LIDO.aceitaFalha()).isFalse();
        assertThat(PENDENTE.aceitaFalha()).isFalse();
    }

    @Test
    @DisplayName("so PENDENTE ainda pode ser enviado")
    void soPendenteEnvia() {
        assertThat(PENDENTE.aindaPodeSerEnviado()).isTrue();
        for (StatusDoDestinatario status : StatusDoDestinatario.values()) {
            if (status != PENDENTE) {
                assertThat(status.aindaPodeSerEnviado()).as(status.name()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("as exclusoes do publico sao as seis primeiras regras")
    void exclusoesDoPublico() {
        assertThat(MotivoDoDestinatario.TELEFONE_INVALIDO.exclusaoDoPublico()).isTrue();
        assertThat(MotivoDoDestinatario.RECEBEU_PROATIVA_RECENTE.exclusaoDoPublico()).isTrue();
        assertThat(MotivoDoDestinatario.COOLDOWN.exclusaoDoPublico()).isFalse();
        assertThat(MotivoDoDestinatario.ENVIO_NAO_CONFIRMADO.exclusaoDoPublico()).isFalse();
    }
}
