package com.synapse.crm.atendimento.infrastructure.tempo_real;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import com.synapse.crm.equipe.infrastructure.seguranca.SecurityContextPropagationInterceptor;

/**
 * Fiacao do chat em tempo real: endpoint, autenticacao, autorizacao e o bulkhead de threads.
 *
 * <p>O executor de entrada e o de saida sao proprios deste modulo — nunca o pool HTTP do Tomcat, nunca
 * o {@code SimpleAsyncTaskExecutor} implicito que o Spring usaria por omissao. E a mesma logica dos
 * dois {@code DataSource} da E00: um WebSocket sob carga nao pode roubar thread de quem processa
 * relatorio, nem o publisher da outbox pode roubar thread de quem entrega mensagem em tempo real.
 * Cada bulkhead protege um caminho do que os outros fazem sob pressao.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer, DisposableBean {

    /**
     * Duas threads: o pulso e uma tarefa curta e periodica, nao trabalho de aplicacao. Fica fora do
     * agendador de {@code @Scheduled} (AgendamentoConfig) pelo mesmo motivo dos executores acima —
     * um job de manutencao longo nao pode atrasar o pulso que sustenta a deteccao de conexao morta.
     */
    private static final int THREADS_HEARTBEAT = 2;

    /**
     * Agendador so do pulso, iniciado no construtor. Nao e {@code @Bean} de proposito: um
     * {@code TaskScheduler} publicado no contexto faria o Spring Boot desistir do seu proprio e
     * viraria candidato para qualquer injecao de {@code TaskScheduler} da aplicacao — o oposto do
     * bulkhead que este pool existe para manter.
     */
    private final ThreadPoolTaskScheduler agendadorDeHeartbeat = new ThreadPoolTaskScheduler();

    private final JwtHandshakeInterceptor autenticacaoHandshake;
    private final AutenticacaoHandshakeHandler manipuladorDeHandshake;
    private final SecurityContextPropagationInterceptor propagacaoDeContexto;
    private final AutorizacaoDeAssinaturaInterceptor autorizacaoDeAssinatura;
    private final ConfiguracaoDeReconexaoWebSocketDecoratorFactory configuracaoDeReconexao;
    private final TempoRealProperties propriedades;

    WebSocketConfig(
            JwtHandshakeInterceptor autenticacaoHandshake,
            AutenticacaoHandshakeHandler manipuladorDeHandshake,
            SecurityContextPropagationInterceptor propagacaoDeContexto,
            AutorizacaoDeAssinaturaInterceptor autorizacaoDeAssinatura,
            ConfiguracaoDeReconexaoWebSocketDecoratorFactory configuracaoDeReconexao,
            TempoRealProperties propriedades) {
        this.autenticacaoHandshake = autenticacaoHandshake;
        this.manipuladorDeHandshake = manipuladorDeHandshake;
        this.propagacaoDeContexto = propagacaoDeContexto;
        this.autorizacaoDeAssinatura = autorizacaoDeAssinatura;
        this.configuracaoDeReconexao = configuracaoDeReconexao;
        this.propriedades = propriedades;
        agendadorDeHeartbeat.setPoolSize(THREADS_HEARTBEAT);
        agendadorDeHeartbeat.setThreadNamePrefix("ws-heartbeat-");
        agendadorDeHeartbeat.initialize();
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registro) {
        registro.addEndpoint("/ws")
                .setHandshakeHandler(manipuladorDeHandshake)
                .addInterceptors(autenticacaoHandshake)
                .setAllowedOriginPatterns(propriedades.origensPermitidas());
    }

    /**
     * {@code /topic} existe so para presenca — nenhum dado de lead passa por broadcast nativo.
     *
     * <p>{@code /queue} e o que sustenta o destino-por-usuario ({@code /user/queue/...}): o broker
     * entrega numa fila resolvida por sessao, nunca em broadcast para todo assinante do nome bruto.
     *
     * <p>O heartbeat existe porque a tela de Atendimentos nao tem polling (docs/40): ela so
     * atualiza reagindo a um frame. Sem pulso, o {@code SimpleBroker} negocia {@code 0,0} e uma
     * conexao morta em silencio fica de pe para os dois lados ate o TCP desistir — foi o que fez a
     * mensagem recebida demorar cerca de um minuto (ou um F5) para aparecer. Com pulso, o
     * fechamento chega em segundos e o reconector com backoff que ja existe assume.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registro) {
        registro.enableSimpleBroker("/topic", "/queue")
                .setTaskScheduler(agendadorDeHeartbeat)
                .setHeartbeatValue(new long[] {
                    propriedades.heartbeatSaidaMs(), propriedades.heartbeatEntradaMs()
                });
        registro.setApplicationDestinationPrefixes("/app");
        registro.setUserDestinationPrefix("/user");
    }

    /**
     * Ordem importa: o contexto de seguranca precisa estar publicado ANTES do interceptador de
     * autorizacao rodar, porque e dali que ele le quem esta pedindo a assinatura.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registro) {
        registro.taskExecutor(executor(propriedades.threadsEntrada(), "ws-entrada-"));
        registro.interceptors(propagacaoDeContexto, autorizacaoDeAssinatura);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registro) {
        registro.taskExecutor(executor(propriedades.threadsSaida(), "ws-saida-"));
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registro) {
        configuracaoDeReconexao.registrar(registro);
    }

    @Override
    public void destroy() {
        agendadorDeHeartbeat.shutdown();
    }

    private ThreadPoolTaskExecutor executor(int threads, String prefixo) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(propriedades.filaMaxima());
        executor.setThreadNamePrefix(prefixo);
        executor.initialize();
        return executor;
    }
}
