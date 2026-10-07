package com.synapse.crm.app.observabilidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.synapse.crm.app.observabilidade.ObservabilidadeDosPoolsDeConexao.LeitorDePool;
import com.synapse.crm.atendimento.application.painel.EstadoDoPool;

class ObservabilidadeDosPoolsDeConexaoTest {

    private static final Duration LIMIAR = Duration.ofSeconds(5);

    private final RelogioMutavel relogio = new RelogioMutavel(Instant.parse("2026-10-06T12:00:00Z"));
    private final PoolFalso chat = new PoolFalso("synapse-chat", 8);
    private final PoolFalso geral = new PoolFalso("synapse-geral", 12);
    private final ListAppender<ILoggingEvent> coletor = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ObservabilidadeDosPoolsDeConexao.class);

    private ObservabilidadeDosPoolsDeConexao observador;

    @BeforeEach
    void preparar() {
        coletor.start();
        logger.addAppender(coletor);
        observador = new ObservabilidadeDosPoolsDeConexao(List.of(geral, chat), relogio, LIMIAR);
    }

    @AfterEach
    void limpar() {
        logger.detachAppender(coletor);
    }

    @Test
    void registraUmaLinhaInfoPorPoolComAtivasOciosasEsperandoTotalEMaximo() {
        chat.estado(8, 0, 9, 8);
        geral.estado(3, 2, 0, 5);

        observador.registrarMetricas();

        assertThat(mensagens(Level.INFO)).containsExactly(
                "[METRICA_POOL_CONEXOES] pool=synapse-geral ativas=3 ociosas=2 esperando=0 total=5 maximo=12",
                "[METRICA_POOL_CONEXOES] pool=synapse-chat ativas=8 ociosas=0 esperando=9 total=8 maximo=8");
    }

    @Test
    void poolQueAindaNaoSubiuNaoGeraLinhaNemQuebra() {
        chat.indisponivel();
        geral.estado(1, 1, 0, 2);

        observador.registrarMetricas();
        observador.amostrar();

        assertThat(mensagens(Level.INFO)).singleElement().asString().contains("pool=synapse-geral");
        assertThat(mensagens(Level.WARN)).isEmpty();
    }

    @Test
    void naoAlertaAntesDoLimiarDeSaturacao() {
        chat.estado(8, 0, 4, 8);

        observador.amostrar();
        relogio.avancar(Duration.ofSeconds(4));
        observador.amostrar();

        assertThat(mensagens(Level.WARN)).isEmpty();
    }

    @Test
    void alertaUmaVezAoPassarDoLimiarComPoolAtivasMaximoEsperandoEDuracao() {
        chat.estado(8, 0, 12, 8);

        observador.amostrar();
        relogio.avancar(Duration.ofSeconds(5));
        observador.amostrar();

        assertThat(mensagens(Level.WARN)).containsExactly(
                "[ALERTA_POOL_SATURADO] pool=synapse-chat ativas=8 maximo=8 esperando=12 saturadoHaSegundos=5 limiarSegundos=5");
    }

    @Test
    void repeteOAlertaNoMaximoUmaVezPorLimiarEnquantoDurar() {
        chat.estado(8, 0, 12, 8);
        observador.amostrar();

        for (int segundo = 1; segundo <= 14; segundo++) {
            relogio.avancar(Duration.ofSeconds(1));
            observador.amostrar();
        }

        // Alertas em t=5 e t=10 (nao em 6, 7, 8, 9, 11...); em t=14 ainda nao completou outro limiar.
        assertThat(mensagens(Level.WARN)).hasSize(2);
    }

    @Test
    void quandoAliviaGravaOFimComADuracaoEReiniciaAContagem() {
        chat.estado(8, 0, 12, 8);
        observador.amostrar();
        relogio.avancar(Duration.ofSeconds(7));
        observador.amostrar();
        chat.estado(6, 2, 0, 8);
        relogio.avancar(Duration.ofSeconds(1));
        observador.amostrar();

        assertThat(mensagens(Level.INFO)).containsExactly("[POOL_SATURADO_FIM] pool=synapse-chat duracaoSegundos=8");

        // Nova saturacao curta depois do alivio nao herda o tempo da anterior.
        chat.estado(8, 0, 1, 8);
        relogio.avancar(Duration.ofSeconds(1));
        observador.amostrar();
        relogio.avancar(Duration.ofSeconds(2));
        observador.amostrar();

        assertThat(mensagens(Level.WARN)).hasSize(1);
    }

    @Test
    void saturacaoCurtaQueNuncaAlertouNaoGravaFim() {
        chat.estado(8, 0, 2, 8);
        observador.amostrar();
        relogio.avancar(Duration.ofSeconds(2));
        chat.estado(1, 7, 0, 8);
        observador.amostrar();

        assertThat(mensagens(Level.INFO)).isEmpty();
        assertThat(mensagens(Level.WARN)).isEmpty();
    }

    @Test
    void poolsSaturadosEmSeparadoSaoContadosCadaUmPorSi() {
        chat.estado(8, 0, 3, 8);
        geral.estado(2, 3, 0, 5);
        observador.amostrar();
        relogio.avancar(Duration.ofSeconds(6));
        observador.amostrar();

        assertThat(mensagens(Level.WARN)).singleElement().asString().contains("pool=synapse-chat");
    }

    @Test
    void limiarNaoPositivoERecusado() {
        assertThatThrownBy(() -> new ObservabilidadeDosPoolsDeConexao(List.of(chat), relogio, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private List<String> mensagens(Level nivel) {
        return coletor.list.stream()
                .filter(evento -> evento.getLevel() == nivel)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    /** Pool de mentira: o teste define o que o MXBean "diria". */
    private static final class PoolFalso implements LeitorDePool {
        private final String nome;
        private final int maximo;
        private EstadoDoPool atual;

        private PoolFalso(String nome, int maximo) {
            this.nome = nome;
            this.maximo = maximo;
            this.atual = new EstadoDoPool(nome, 0, 0, 0, 0, maximo);
        }

        private void estado(int ativas, int ociosas, int esperando, int total) {
            this.atual = new EstadoDoPool(nome, ativas, ociosas, esperando, total, maximo);
        }

        private void indisponivel() {
            this.atual = null;
        }

        @Override
        public String nome() {
            return nome;
        }

        @Override
        public Optional<EstadoDoPool> ler() {
            return Optional.ofNullable(atual);
        }
    }

    /** Relogio controlado: o tempo so anda quando se pede. */
    private static final class RelogioMutavel extends Clock {
        private Instant agora;

        private RelogioMutavel(Instant inicio) {
            this.agora = inicio;
        }

        private void avancar(Duration quanto) {
            agora = agora.plus(quanto);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
