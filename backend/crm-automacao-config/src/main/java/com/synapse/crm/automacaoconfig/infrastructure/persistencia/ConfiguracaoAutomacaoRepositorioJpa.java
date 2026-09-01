package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.automacaoconfig.application.ConfiguracaoAutomacaoRepositorio;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.infrastructure.ChavesDeCacheConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.infrastructure.ConfiguracaoAutomacaoCacheProperties;

/**
 * Adaptador de {@code configuracao_automacao}, com leitura em cache (E07 §3).
 *
 * <p>Tabela pequena (poucas dezenas de linhas) e lida a cada requisicao de {@code /internal/v1} —
 * exatamente o perfil que justifica cache na frente do banco. O cache mora no Redis, e nao em
 * memoria local, porque assim a invalidacao de uma instancia (quando {@link
 * com.synapse.crm.automacaoconfig.infrastructure.reacao.CacheDeConfiguracaoAutomacaoListener}
 * reage ao evento apos o commit) vale para todas — nao precisa de um segundo canal de pub/sub como o
 * da E06, porque o proprio dado compartilhado ja e a fonte.
 *
 * <p>Se o Redis estiver fora do ar, a leitura simplesmente ignora o cache e vai direto ao banco — o
 * mesmo principio de resiliencia de {@code RelayDeTempoRealListener}: uma dependencia de otimizacao
 * nunca pode derrubar o caminho principal.
 *
 * <p>E104: ausencia (lista vazia) <b>nao</b> e cacheada — guardar {@code []} envenenava a tela de
 * parametros para sempre, porque a unica invalidacao exige editar um parametro que a tela nao
 * mostra. Toda escrita leva TTL configuravel ({@link ConfiguracaoAutomacaoCacheProperties}).
 */
@Repository
class ConfiguracaoAutomacaoRepositorioJpa implements ConfiguracaoAutomacaoRepositorio {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracaoAutomacaoRepositorioJpa.class);

    private final ConfiguracaoAutomacaoJpaRepository jpa;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final ConfiguracaoAutomacaoCacheProperties propriedades;

    ConfiguracaoAutomacaoRepositorioJpa(
            ConfiguracaoAutomacaoJpaRepository jpa,
            StringRedisTemplate redis,
            ObjectMapper json,
            ConfiguracaoAutomacaoCacheProperties propriedades) {
        this.jpa = jpa;
        this.redis = redis;
        this.json = json;
        this.propriedades = propriedades;
    }

    @Override
    public List<ConfiguracaoAutomacao> listarTodas() {
        List<ConfiguracaoAutomacao> doCache = lerListaDoCache();
        if (doCache != null) {
            return doCache;
        }
        List<ConfiguracaoAutomacao> doBanco =
                jpa.findAll().stream().map(ConfiguracaoAutomacaoEntity::paraDominio).toList();
        if (!doBanco.isEmpty()) {
            escreverNoCache(ChavesDeCacheConfiguracaoAutomacao.TODAS, doBanco);
        }
        return doBanco;
    }

    @Override
    public Optional<ConfiguracaoAutomacao> porChave(String chave) {
        ConfiguracaoAutomacao doCache = lerUmDoCache(chave);
        if (doCache != null) {
            return Optional.of(doCache);
        }
        Optional<ConfiguracaoAutomacao> doBanco = jpa.findById(chave).map(ConfiguracaoAutomacaoEntity::paraDominio);
        doBanco.ifPresent(
                configuracao -> escreverNoCache(ChavesDeCacheConfiguracaoAutomacao.porChave(chave), configuracao));
        return doBanco;
    }

    @Override
    public ConfiguracaoAutomacao salvar(ConfiguracaoAutomacao configuracao) {
        ConfiguracaoAutomacaoEntity entidade =
                jpa.findById(configuracao.chave()).orElseGet(ConfiguracaoAutomacaoEntity::new);
        entidade.aplicar(configuracao);
        return jpa.save(entidade).paraDominio();
    }

    private List<ConfiguracaoAutomacao> lerListaDoCache() {
        try {
            String bruto = redis.opsForValue().get(ChavesDeCacheConfiguracaoAutomacao.TODAS);
            if (bruto == null) {
                return null;
            }
            List<ConfiguracaoAutomacao> lista =
                    json.readValue(bruto, new TypeReference<List<ConfiguracaoAutomacao>>() {});
            // E104: "[]" no Redis e o estado da producao. Tratar como miss — senao a tela
            // continua vazia enquanto o banco esta cheio. Nao e limpeza no boot: e nao
            // confiar em cache negativo nesta chave.
            if (lista.isEmpty()) {
                return null;
            }
            return lista;
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Falha ao ler cache de configuracoes de automacao; indo direto ao banco.", e);
            return null;
        }
    }

    private ConfiguracaoAutomacao lerUmDoCache(String chave) {
        try {
            String bruto = redis.opsForValue().get(ChavesDeCacheConfiguracaoAutomacao.porChave(chave));
            return bruto == null ? null : json.readValue(bruto, ConfiguracaoAutomacao.class);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Falha ao ler cache da configuracao de automacao '{}'; indo direto ao banco.", chave, e);
            return null;
        }
    }

    private void escreverNoCache(String chaveRedis, Object valor) {
        try {
            redis.opsForValue()
                    .set(
                            chaveRedis,
                            json.writeValueAsString(valor),
                            propriedades.cacheTtl().toMillis(),
                            TimeUnit.MILLISECONDS);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Falha ao escrever cache de configuracao de automacao ({}).", chaveRedis, e);
        }
    }
}
