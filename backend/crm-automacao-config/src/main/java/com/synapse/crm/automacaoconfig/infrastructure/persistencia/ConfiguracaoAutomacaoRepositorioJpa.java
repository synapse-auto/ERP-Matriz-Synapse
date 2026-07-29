package com.synapse.crm.automacaoconfig.infrastructure.persistencia;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.synapse.crm.automacaoconfig.application.ConfiguracaoAutomacaoRepositorio;
import com.synapse.crm.automacaoconfig.domain.ConfiguracaoAutomacao;
import com.synapse.crm.automacaoconfig.infrastructure.ChavesDeCacheConfiguracaoAutomacao;

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
 */
@Repository
class ConfiguracaoAutomacaoRepositorioJpa implements ConfiguracaoAutomacaoRepositorio {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracaoAutomacaoRepositorioJpa.class);

    private final ConfiguracaoAutomacaoJpaRepository jpa;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    ConfiguracaoAutomacaoRepositorioJpa(
            ConfiguracaoAutomacaoJpaRepository jpa, StringRedisTemplate redis, ObjectMapper json) {
        this.jpa = jpa;
        this.redis = redis;
        this.json = json;
    }

    @Override
    public List<ConfiguracaoAutomacao> listarTodas() {
        List<ConfiguracaoAutomacao> doCache = lerListaDoCache();
        if (doCache != null) {
            return doCache;
        }
        List<ConfiguracaoAutomacao> doBanco =
                jpa.findAll().stream().map(ConfiguracaoAutomacaoEntity::paraDominio).toList();
        escreverNoCache(ChavesDeCacheConfiguracaoAutomacao.TODAS, doBanco);
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
            return bruto == null
                    ? null
                    : json.readValue(bruto, new TypeReference<List<ConfiguracaoAutomacao>>() {});
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
            redis.opsForValue().set(chaveRedis, json.writeValueAsString(valor));
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Falha ao escrever cache de configuracao de automacao ({}).", chaveRedis, e);
        }
    }
}
