package com.synapse.crm.equipe.application.permissao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.synapse.crm.equipe.domain.permissao.Capacidade;
import com.synapse.crm.equipe.domain.permissao.ConfiguracaoDePermissoes;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.permissao.AcessoDeUsuariosAlterado;
import com.synapse.crm.sharedkernel.permissao.ConsultaDeFuncionalidades;

class ResolvedorDePermissoesEfetivasTest {

    private final PermissaoRepositorio repositorio = mock(PermissaoRepositorio.class);
    private final ConsultaDeFuncionalidades flags = () -> Set.of("dashboard");
    private final AtomicReference<Instant> agora = new AtomicReference<>(Instant.parse("2026-09-26T10:00:00Z"));
    private final Clock relogio = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return agora.get(); }
    };
    private final UUID ana = UUID.randomUUID();
    private ResolvedorDePermissoesEfetivas resolvedor;

    @BeforeEach
    void preparar() {
        resolvedor = new ResolvedorDePermissoesEfetivas(repositorio, flags, () -> Duration.ofSeconds(2), relogio);
        when(repositorio.revisaoGlobal()).thenReturn(1L);
        when(repositorio.contextoDe(ana)).thenReturn(Optional.of(contexto(true)));
    }

    private PermissaoRepositorio.ContextoDeAcesso contexto(boolean tagsLiberadas) {
        return new PermissaoRepositorio.ContextoDeAcesso(ana, PapelUsuario.ATENDENTE, true,
                new ConfiguracaoDePermissoes(Map.of(), Map.of(Capacidade.TAGS_APLICAR, tagsLiberadas)),
                ConfiguracaoDePermissoes.vazia());
    }

    @Test
    @DisplayName("quente: mil consultas do mesmo usuario = uma leitura de contexto e uma de revisao")
    void quenteNaoConsultaBanco() {
        for (int i = 0; i < 1000; i++) {
            assertThat(resolvedor.de(ana).orElseThrow().efetivas().permite(Capacidade.TAGS_APLICAR)).isTrue();
        }
        verify(repositorio, times(1)).contextoDe(ana);
        verify(repositorio, times(1)).revisaoGlobal();
        assertThat(resolvedor.metricas().acertos()).isEqualTo(999);
    }

    @Test
    @DisplayName("outro no salvou: dentro do intervalo ainda vale o antigo; depois dele, a revogacao aplica")
    void limiteEntreNos() {
        resolvedor.de(ana);
        when(repositorio.revisaoGlobal()).thenReturn(2L);
        when(repositorio.contextoDe(ana)).thenReturn(Optional.of(contexto(false)));

        agora.set(agora.get().plusMillis(1999));
        assertThat(resolvedor.de(ana).orElseThrow().efetivas().permite(Capacidade.TAGS_APLICAR)).isTrue();

        agora.set(agora.get().plusMillis(1));
        assertThat(resolvedor.de(ana).orElseThrow().efetivas().permite(Capacidade.TAGS_APLICAR)).isFalse();
    }

    @Test
    @DisplayName("mesmo no: depois do commit a revogacao vale na proxima chamada, sem esperar o intervalo")
    void mesmoNoImediato() {
        resolvedor.de(ana);
        when(repositorio.revisaoGlobal()).thenReturn(2L);
        when(repositorio.contextoDe(ana)).thenReturn(Optional.of(contexto(false)));

        resolvedor.aoAlterarAcesso(new AcessoDeUsuariosAlterado(Set.of(ana), false, 2L));

        assertThat(resolvedor.de(ana).orElseThrow().efetivas().permite(Capacidade.TAGS_APLICAR)).isFalse();
    }

    @Test
    @DisplayName("banco indisponivel: falha propaga, nunca vira permissao presumida")
    void falhaFecha() {
        when(repositorio.revisaoGlobal()).thenThrow(new DataAccessResourceFailureException("fora"));
        assertThatThrownBy(() -> resolvedor.de(ana)).isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    @DisplayName("usuario inexistente nao fica em cache e volta vazio")
    void inexistente() {
        UUID fantasma = UUID.randomUUID();
        when(repositorio.contextoDe(fantasma)).thenReturn(Optional.empty());
        assertThat(resolvedor.de(fantasma)).isEmpty();
        assertThat(resolvedor.metricas().usuariosEmCache()).isZero();
    }
}
