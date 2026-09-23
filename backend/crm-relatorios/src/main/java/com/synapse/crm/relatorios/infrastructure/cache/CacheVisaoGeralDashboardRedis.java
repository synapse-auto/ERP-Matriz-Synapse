package com.synapse.crm.relatorios.infrastructure.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.synapse.crm.relatorios.application.dashboard.CacheVisaoGeralDashboard;
import com.synapse.crm.relatorios.domain.dashboard.FiltroTemporalDashboard;
import com.synapse.crm.relatorios.domain.dashboard.VisaoGeralDashboard;

/**
 * Reaproveita o Redis da instancia para os agregados. A chave inclui sujeito, autoridades e filtro
 * canonico; o status ao vivo nunca e servido diretamente desta entrada.
 */
@Component
class CacheVisaoGeralDashboardRedis implements CacheVisaoGeralDashboard {

    private static final Logger LOG = LoggerFactory.getLogger(CacheVisaoGeralDashboardRedis.class);
    private static final String PREFIXO = "dashboard:visao-geral:v1:";
    private static final DefaultRedisScript<Long> LIBERAR_TRAVA = scriptDeLiberacao();

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration tempoDeVida;
    private final Duration esperaMaxima;
    private final int tamanhoMaximo;

    CacheVisaoGeralDashboardRedis(
            StringRedisTemplate redis,
            ObjectMapper json,
            @Value("${synapse.dashboard.cache-ttl}") Duration tempoDeVida,
            @Value("${synapse.dashboard.cache-wait}") Duration esperaMaxima,
            @Value("${synapse.dashboard.cache-max-bytes}") int tamanhoMaximo) {
        if (tempoDeVida.isNegative()
                || tempoDeVida.isZero()
                || esperaMaxima.isNegative()
                || tamanhoMaximo <= 0) {
            throw new IllegalArgumentException("duracoes do cache do dashboard invalidas");
        }
        this.redis = redis;
        this.json = json;
        this.tempoDeVida = tempoDeVida;
        this.esperaMaxima = esperaMaxima;
        this.tamanhoMaximo = tamanhoMaximo;
    }

    @Override
    public Resultado buscarOuCalcular(
            FiltroTemporalDashboard filtro, Supplier<VisaoGeralDashboard> consulta) {
        String chave = chavePara(filtro);
        VisaoGeralDashboard existente = ler(chave);
        if (existente != null) {
            return new Resultado(existente, true);
        }

        String trava = chave + ":lock";
        String dono = UUID.randomUUID().toString();
        boolean adquiriu;
        try {
            adquiriu = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(trava, dono, tempoDeVida));
        } catch (RuntimeException erro) {
            LOG.warn("Redis indisponivel para reserva do dashboard; consultando banco.");
            return new Resultado(consulta.get(), false);
        }

        if (!adquiriu) {
            VisaoGeralDashboard calculadoPorOutro = aguardar(chave);
            return calculadoPorOutro == null
                    ? new Resultado(consulta.get(), false)
                    : new Resultado(calculadoPorOutro, true);
        }

        try {
            // Outra replica pode ter preenchido a entrada entre o primeiro GET e a reserva.
            existente = ler(chave);
            if (existente != null) {
                return new Resultado(existente, true);
            }
            VisaoGeralDashboard calculado = consulta.get();
            escrever(chave, calculado);
            return new Resultado(calculado, false);
        } finally {
            try {
                redis.execute(LIBERAR_TRAVA, List.of(trava), dono);
            } catch (RuntimeException erro) {
                // A trava expira sozinha; nunca remover a trava de outra requisicao.
                LOG.warn("Falha ao liberar reserva do cache do dashboard.");
            }
        }
    }

    private VisaoGeralDashboard aguardar(String chave) {
        long fim = System.nanoTime() + esperaMaxima.toNanos();
        for (int tentativa = 0; tentativa < 4; tentativa++) {
            VisaoGeralDashboard existente = ler(chave);
            if (existente != null) {
                return existente;
            }
            if (Thread.currentThread().isInterrupted() || System.nanoTime() >= fim) {
                return null;
            }
            LockSupport.parkNanos(esperaMaxima.dividedBy(4).toNanos());
        }
        return ler(chave);
    }

    private VisaoGeralDashboard ler(String chave) {
        try {
            String valor = redis.opsForValue().get(chave);
            return valor == null ? null : json.readValue(valor, VisaoGeralDashboard.class);
        } catch (RuntimeException | JsonProcessingException erro) {
            LOG.warn("Falha ao ler cache do dashboard; consultando banco.");
            return null;
        }
    }

    private void escrever(String chave, VisaoGeralDashboard valor) {
        try {
            String conteudo = json.writeValueAsString(valor);
            if (conteudo.getBytes(StandardCharsets.UTF_8).length <= tamanhoMaximo) {
                redis.opsForValue().set(chave, conteudo, tempoDeVida);
            }
        } catch (RuntimeException | JsonProcessingException erro) {
            LOG.warn("Falha ao escrever cache do dashboard; resultado ja foi obtido do banco.");
        }
    }

    private static String chavePara(FiltroTemporalDashboard filtro) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !autenticacao.isAuthenticated()) {
            throw new AccessDeniedException("autenticacao obrigatoria para o dashboard");
        }
        String papeis = autenticacao.getAuthorities().stream()
                .map(autoridade -> autoridade.getAuthority())
                .sorted()
                .reduce("", (atual, papel) -> atual + "," + papel);
        String escopo = autenticacao.getName() + "|" + papeis + "|" + filtro;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(escopo.getBytes(StandardCharsets.UTF_8));
            return PREFIXO + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException erro) {
            throw new IllegalStateException("SHA-256 indisponivel", erro);
        }
    }

    private static DefaultRedisScript<Long> scriptDeLiberacao() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("if redis.call('get', KEYS[1]) == ARGV[1] then "
                + "return redis.call('del', KEYS[1]) else return 0 end");
        script.setResultType(Long.class);
        return script;
    }
}
