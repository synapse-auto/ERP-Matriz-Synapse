package com.synapse.crm.app.observabilidade;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.application.painel.EstadoDoPool;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Observabilidade dos pools de conexao Hikari (E225, PR 5). Sem mudar comportamento: so le contadores e escreve log.
 *
 * <ul>
 *   <li><b>{@code [METRICA_POOL_CONEXOES]}</b> (INFO, uma vez por {@code synapse.observabilidade.pool.intervalo-metricas}):
 *       por pool, conexoes ativas, ociosas, threads esperando, total e maximo.</li>
 *   <li><b>{@code [ALERTA_POOL_SATURADO]}</b> (WARN): o pool ficou com 100% das conexoes emprestadas por mais de
 *       {@code synapse.observabilidade.pool.saturado-alerta-apos}. Uma amostra por
 *       {@code synapse.observabilidade.pool.intervalo-amostra} (padrao 1 s); depois do primeiro alerta repete no maximo
 *       uma vez por limiar enquanto durar. Quando o pool alivia, um INFO {@code [POOL_SATURADO_FIM]} com a duracao.</li>
 * </ul>
 * Os dois usam so o nome do pool e numeros. Nenhum usuario, lead ou consulta aparece no log.
 */
@Component
class ObservabilidadeDosPoolsDeConexao {

    static final String MARCADOR_METRICA = "[METRICA_POOL_CONEXOES]";
    static final String MARCADOR_ALERTA = "[ALERTA_POOL_SATURADO]";
    static final String MARCADOR_FIM = "[POOL_SATURADO_FIM]";

    private static final Logger log = LoggerFactory.getLogger(ObservabilidadeDosPoolsDeConexao.class);

    /** Uma fonte de leitura por pool; permite testar sem Hikari. */
    interface LeitorDePool {
        String nome();

        Optional<EstadoDoPool> ler();
    }

    private final List<LeitorDePool> leitores;
    private final Clock relogio;
    private final Duration alertarApos;
    private final Map<String, Instant> saturadoDesde = new ConcurrentHashMap<>();
    private final Map<String, Instant> ultimoAlerta = new ConcurrentHashMap<>();

    @Autowired
    ObservabilidadeDosPoolsDeConexao(
            @Qualifier(Pools.GENERAL_DATA_SOURCE) DataSource geral,
            @Qualifier(Pools.CHAT_DATA_SOURCE) DataSource chat,
            Clock relogio,
            @Value("${synapse.observabilidade.pool.saturado-alerta-apos}") Duration alertarApos) {
        this(List.of(leitorDe("synapse-geral", geral), leitorDe("synapse-chat", chat)), relogio, alertarApos);
    }

    ObservabilidadeDosPoolsDeConexao(List<LeitorDePool> leitores, Clock relogio, Duration alertarApos) {
        if (alertarApos.isNegative() || alertarApos.isZero()) {
            throw new IllegalArgumentException(
                    "synapse.observabilidade.pool.saturado-alerta-apos precisa ser positivo: " + alertarApos);
        }
        this.leitores = List.copyOf(leitores);
        this.relogio = relogio;
        this.alertarApos = alertarApos;
    }

    private static LeitorDePool leitorDe(String nome, DataSource dataSource) {
        return new LeitorDePool() {
            @Override
            public String nome() {
                return nome;
            }

            @Override
            public Optional<EstadoDoPool> ler() {
                return LeituraDePoolHikari.ler(dataSource);
            }
        };
    }

    /** Estado atual de todos os pools que ja subiram. */
    List<EstadoDoPool> medir() {
        return leitores.stream().map(LeitorDePool::ler).flatMap(Optional::stream).toList();
    }

    @Scheduled(fixedDelayString = "${synapse.observabilidade.pool.intervalo-metricas}")
    void registrarMetricas() {
        for (EstadoDoPool estado : medir()) {
            log.info(
                    "{} pool={} ativas={} ociosas={} esperando={} total={} maximo={}",
                    MARCADOR_METRICA,
                    estado.nome(),
                    estado.ativas(),
                    estado.ociosas(),
                    estado.esperando(),
                    estado.total(),
                    estado.maximo());
        }
    }

    @Scheduled(fixedDelayString = "${synapse.observabilidade.pool.intervalo-amostra}")
    void amostrar() {
        Instant agora = relogio.instant();
        for (LeitorDePool leitor : leitores) {
            Optional<EstadoDoPool> estado = leitor.ler();
            if (estado.isPresent() && estado.get().saturado()) {
                registrarSaturacao(estado.get(), agora);
            } else {
                registrarAlivio(leitor.nome(), agora);
            }
        }
    }

    private void registrarSaturacao(EstadoDoPool estado, Instant agora) {
        Instant desde = saturadoDesde.computeIfAbsent(estado.nome(), nome -> agora);
        Duration saturadoHa = Duration.between(desde, agora);
        if (saturadoHa.compareTo(alertarApos) < 0) {
            return;
        }
        Instant ultimo = ultimoAlerta.get(estado.nome());
        if (ultimo != null && Duration.between(ultimo, agora).compareTo(alertarApos) < 0) {
            return;
        }
        ultimoAlerta.put(estado.nome(), agora);
        log.warn(
                "{} pool={} ativas={} maximo={} esperando={} saturadoHaSegundos={} limiarSegundos={}",
                MARCADOR_ALERTA,
                estado.nome(),
                estado.ativas(),
                estado.maximo(),
                estado.esperando(),
                saturadoHa.toSeconds(),
                alertarApos.toSeconds());
    }

    private void registrarAlivio(String nome, Instant agora) {
        Instant desde = saturadoDesde.remove(nome);
        boolean tinhaAlertado = ultimoAlerta.remove(nome) != null;
        if (desde != null && tinhaAlertado) {
            log.info("{} pool={} duracaoSegundos={}", MARCADOR_FIM, nome, Duration.between(desde, agora).toSeconds());
        }
    }
}
