package com.synapse.crm.atendimento.infrastructure.tempo_real;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

class ObservabilidadeDeSessoesWebSocketTest {

    @Test
    void distingueSessoesAtivasDeUsuariosAutenticadosUnicos() {
        SimpUserRegistry registro = mock(SimpUserRegistry.class);
        SimpUser ana = usuarioComSessoes(2);
        SimpUser bruno = usuarioComSessoes(1);
        when(registro.getUsers()).thenReturn(Set.of(ana, bruno));

        MetricasDeSessoesWebSocket metricas = new ObservabilidadeDeSessoesWebSocket(registro).medir();

        assertThat(metricas.sessoesAtivas()).isEqualTo(3);
        assertThat(metricas.usuariosAutenticadosUnicos()).isEqualTo(2);
    }

    @Test
    void retornaZeroQuandoNenhumaSessaoEstaConectadaNestaInstancia() {
        SimpUserRegistry registro = mock(SimpUserRegistry.class);
        when(registro.getUsers()).thenReturn(Set.of());

        MetricasDeSessoesWebSocket metricas = new ObservabilidadeDeSessoesWebSocket(registro).medir();

        assertThat(metricas.sessoesAtivas()).isZero();
        assertThat(metricas.usuariosAutenticadosUnicos()).isZero();
    }

    private SimpUser usuarioComSessoes(int quantidade) {
        SimpUser usuario = mock(SimpUser.class);
        Set<SimpSession> sessoes = java.util.stream.IntStream.range(0, quantidade)
                .mapToObj(indice -> mock(SimpSession.class))
                .collect(java.util.stream.Collectors.toSet());
        when(usuario.getSessions()).thenReturn(sessoes);
        return usuario;
    }
}
