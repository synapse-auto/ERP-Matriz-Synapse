package com.synapse.crm.atendimento.infrastructure.tempo_real;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * O backplane: liga as instancias entre si por pub/sub.
 *
 * <p>Sem isto, um atendente conectado a instancia A nunca veria a mensagem de um lead cujo atendimento
 * um colega esta olhando na instancia B — o WebSocket sozinho so alcanca quem esta na mesma instancia
 * que processou o evento.
 *
 * <p>O container recebe seu proprio executor (mesmo motivo do {@link WebSocketConfig}): o trabalho de
 * receber do Redis e entregar localmente nao pode competir por thread com o que entra dos clientes.
 */
@Configuration
class RedisConfig {

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory conexao,
            RedisSubscriberDeAtendimento assinante,
            TempoRealProperties propriedades) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(conexao);
        container.setTaskExecutor(executorDoRedis(propriedades));
        container.addMessageListener(assinante, new PatternTopic(CanaisRedis.PADRAO));
        return container;
    }

    private ThreadPoolTaskExecutor executorDoRedis(TempoRealProperties propriedades) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(propriedades.threadsRedis());
        executor.setMaxPoolSize(propriedades.threadsRedis());
        executor.setQueueCapacity(propriedades.filaMaxima());
        executor.setThreadNamePrefix("ws-redis-");
        executor.initialize();
        return executor;
    }
}
