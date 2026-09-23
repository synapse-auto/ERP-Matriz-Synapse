package com.synapse.crm.relatorios.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.synapse.crm.relatorios.domain.dashboard.FiltroTemporalDashboard;
import com.synapse.crm.relatorios.domain.dashboard.VisaoGeralDashboard;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

@ExtendWith(MockitoExtension.class)
class CacheVisaoGeralDashboardRedisTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valores;

    @Mock
    private UsuarioContext usuario;

    private CacheVisaoGeralDashboardRedis cache;

    @BeforeEach
    void preparar() {
        when(usuario.atual())
                .thenReturn(new UsuarioAutenticado(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"), PapelUsuario.GESTOR, false));
        cache = new CacheVisaoGeralDashboardRedis(
                redis, new ObjectMapper(), usuario, Duration.ofSeconds(30), Duration.ofSeconds(1), 131072);
    }

    @Test
    void falhaDaConsultaNaoEEscritaNoCache() {
        when(redis.opsForValue()).thenReturn(valores);
        when(valores.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        FiltroTemporalDashboard filtro = FiltroTemporalDashboard.de(
                2040, List.of(8), null, null, ZoneId.of("America/Sao_Paulo"));

        assertThatThrownBy(() -> cache.buscarOuCalcular(filtro, () -> {
                    throw new IllegalStateException("banco indisponivel");
                }))
                .isInstanceOf(IllegalStateException.class);

        verify(valores, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void indisponibilidadeDoRedisFazConsultaNormal() {
        when(redis.opsForValue()).thenReturn(valores);
        when(valores.get(anyString())).thenThrow(new IllegalStateException("redis indisponivel"));
        when(valores.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis indisponivel"));
        VisaoGeralDashboard resposta = org.mockito.Mockito.mock(VisaoGeralDashboard.class);
        FiltroTemporalDashboard filtro = FiltroTemporalDashboard.de(
                2040, List.of(8), null, null, ZoneId.of("America/Sao_Paulo"));

        var resultado = cache.buscarOuCalcular(filtro, () -> resposta);

        assertThat(resultado.valor()).isSameAs(resposta);
        assertThat(resultado.encontradoNoCache()).isFalse();
    }

    @Test
    void fusoHorarioParticipaDaChave() {
        when(redis.opsForValue()).thenReturn(valores);
        when(valores.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new IllegalStateException("redis indisponivel"));
        VisaoGeralDashboard resposta = org.mockito.Mockito.mock(VisaoGeralDashboard.class);
        FiltroTemporalDashboard brasilia = FiltroTemporalDashboard.de(
                2040, List.of(8), null, null, ZoneId.of("America/Sao_Paulo"));
        FiltroTemporalDashboard utc = FiltroTemporalDashboard.de(
                2040, List.of(8), null, null, ZoneId.of("UTC"));

        cache.buscarOuCalcular(brasilia, () -> resposta);
        cache.buscarOuCalcular(utc, () -> resposta);

        ArgumentCaptor<String> chaves = ArgumentCaptor.forClass(String.class);
        verify(valores, times(2)).get(chaves.capture());
        assertThat(chaves.getAllValues()).doesNotHaveDuplicates();
    }
}
