package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.domain.TipoConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.infrastructure.ChavesDeCacheConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.infrastructure.ConfiguracaoAutomacaoCacheProperties;

@ExtendWith(MockitoExtension.class)
class ConfiguracaoAutomacaoRepositorioJpaTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    @Mock
    private ConfiguracaoAutomacaoJpaRepository jpa;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valores;

    private ObjectMapper json;
    private ConfiguracaoAutomacaoRepositorioJpa repositorio;

    @BeforeEach
    void preparar() {
        json = new ObjectMapper().registerModule(new JavaTimeModule());
        when(redis.opsForValue()).thenReturn(valores);
        repositorio = new ConfiguracaoAutomacaoRepositorioJpa(
                jpa, redis, json, new ConfiguracaoAutomacaoCacheProperties(TTL));
    }

    @Test
    @DisplayName("listarTodas com banco vazio: devolve vazio e nao escreve no Redis")
    void listarTodas_bancoVazio_naoEscreveNoCache() {
        when(valores.get(ChavesDeCacheConfiguracaoAutomacao.TODAS)).thenReturn(null);
        when(jpa.findAll()).thenReturn(List.of());

        assertThat(repositorio.listarTodas()).isEmpty();

        verify(jpa).findAll();
        verify(valores, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        verify(valores, never()).set(anyString(), anyString());
    }

    @Test
    @DisplayName("listarTodas com banco populado: escreve com TTL e a segunda chamada nao vai ao banco")
    void listarTodas_bancoPopulado_escreveComTtlEUsaCache() throws Exception {
        ConfiguracaoAutomacaoEntity entidade = entidade("janela_horas", "24");
        when(valores.get(ChavesDeCacheConfiguracaoAutomacao.TODAS)).thenReturn(null);
        when(jpa.findAll()).thenReturn(List.of(entidade));

        List<ConfiguracaoAutomacao> primeira = repositorio.listarTodas();
        assertThat(primeira).hasSize(1).first().extracting(ConfiguracaoAutomacao::chave).isEqualTo("janela_horas");

        ArgumentCaptor<String> jsonCapturado = ArgumentCaptor.forClass(String.class);
        verify(valores)
                .set(
                        eq(ChavesDeCacheConfiguracaoAutomacao.TODAS),
                        jsonCapturado.capture(),
                        eq(TTL.toMillis()),
                        eq(TimeUnit.MILLISECONDS));

        when(valores.get(ChavesDeCacheConfiguracaoAutomacao.TODAS)).thenReturn(jsonCapturado.getValue());
        List<ConfiguracaoAutomacao> segunda = repositorio.listarTodas();
        assertThat(segunda).hasSize(1);
        verify(jpa).findAll();
    }

    @Test
    @DisplayName("cache com [] (estado da producao): trata como miss e le o banco")
    void listarTodas_cacheComListaVazia_naoConfiaEConsultaBanco() {
        when(valores.get(ChavesDeCacheConfiguracaoAutomacao.TODAS)).thenReturn("[]");
        when(jpa.findAll()).thenReturn(List.of(entidade("janela_horas", "24")));

        List<ConfiguracaoAutomacao> resultado = repositorio.listarTodas();

        assertThat(resultado).hasSize(1);
        verify(jpa).findAll();
        verify(valores)
                .set(
                        eq(ChavesDeCacheConfiguracaoAutomacao.TODAS),
                        anyString(),
                        eq(TTL.toMillis()),
                        eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("Redis fora do ar na leitura: cai no banco sem quebrar")
    void listarTodas_redisFora_caiNoBanco() {
        when(valores.get(ChavesDeCacheConfiguracaoAutomacao.TODAS))
                .thenThrow(new RuntimeException("redis down"));
        when(jpa.findAll()).thenReturn(List.of(entidade("janela_horas", "24")));

        assertThat(repositorio.listarTodas()).hasSize(1);
        verify(jpa).findAll();
    }

    @Test
    @DisplayName("porChave continua sem cachear ausencia")
    void porChave_ausente_naoEscreveNoCache() {
        String chave = "inexistente";
        when(valores.get(ChavesDeCacheConfiguracaoAutomacao.porChave(chave))).thenReturn(null);
        when(jpa.findById(chave)).thenReturn(Optional.empty());

        assertThat(repositorio.porChave(chave)).isEmpty();

        verify(valores, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        verify(valores, never()).set(anyString(), anyString());
    }

    private static ConfiguracaoAutomacaoEntity entidade(String chave, String valor) {
        ConfiguracaoAutomacaoEntity entidade = new ConfiguracaoAutomacaoEntity();
        entidade.aplicar(new ConfiguracaoAutomacao(
                chave,
                valor,
                "h",
                TipoConfiguracaoAutomacao.INT,
                BigDecimal.ONE,
                BigDecimal.valueOf(48),
                "teste",
                null,
                Instant.parse("2026-09-01T12:00:00Z")));
        return entidade;
    }
}
