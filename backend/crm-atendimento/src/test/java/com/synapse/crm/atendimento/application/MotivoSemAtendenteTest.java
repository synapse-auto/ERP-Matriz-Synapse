package com.synapse.crm.atendimento.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.synapse.crm.equipe.domain.disponibilidade.DiagnosticoDoRodizio;

/** O motivo e o primeiro filtro do funil que zera, na mesma ordem em que a consulta de elegibilidade os aplica. */
class MotivoSemAtendenteTest {

    private static DiagnosticoDoRodizio funil(long comPapel, long disponiveis, long online) {
        return new DiagnosticoDoRodizio(DiagnosticoDoRodizio.ESTRATEGIA_MENOR_CARGA, 9, comPapel, disponiveis, online, 0);
    }

    @Test
    void semPapelQueRecebeAtendimentoEhElegivel() {
        assertThat(MotivoSemAtendente.de(funil(0, 0, 0))).isEqualTo(MotivoSemAtendente.SEM_ATENDENTE_ELEGIVEL);
    }

    @Test
    void comPapelMasNenhumMarcadoParaIaEhDisponivelParaIa() {
        assertThat(MotivoSemAtendente.de(funil(3, 0, 0))).isEqualTo(MotivoSemAtendente.SEM_ATENDENTE_DISPONIVEL_PARA_IA);
    }

    @Test
    void marcadosMasNenhumOnlineEhOnline() {
        assertThat(MotivoSemAtendente.de(funil(3, 2, 0))).isEqualTo(MotivoSemAtendente.SEM_ATENDENTE_ONLINE);
    }

    @Test
    void funilQueNaoZeraEmNenhumPassoEhNaoDeterminado() {
        // A consulta devolveu vazio mas o diagnostico ainda enxerga candidato: corrida entre as duas leituras.
        assertThat(MotivoSemAtendente.de(funil(3, 2, 1))).isEqualTo(MotivoSemAtendente.NAO_DETERMINADO);
    }

    @Test
    void oPrimeiroFiltroQueZeraVenceMesmoQueOsSeguintesTambemZerem() {
        assertThat(MotivoSemAtendente.de(funil(0, 5, 5))).isEqualTo(MotivoSemAtendente.SEM_ATENDENTE_ELEGIVEL);
        assertThat(MotivoSemAtendente.de(funil(3, 0, 5))).isEqualTo(MotivoSemAtendente.SEM_ATENDENTE_DISPONIVEL_PARA_IA);
    }
}
