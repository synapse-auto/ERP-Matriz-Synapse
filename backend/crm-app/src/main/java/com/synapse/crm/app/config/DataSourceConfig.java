package com.synapse.crm.app.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Bulkhead de conexoes: dois pools sobre o mesmo banco.
 *
 * <p>A regra de precedencia do produto e que a aba Atendimentos nao pode ficar indisponivel entre
 * 08:00 e 18:30. Um relatorio pesado que segure todas as conexoes derruba o chat, e um pool unico
 * nao tem como impedir isso. Separar os pools transforma "o relatorio derrubou o chat" em "o
 * relatorio ficou lento", que e uma falha aceitavel.
 *
 * <p>{@code generalDataSource} e o {@code @Primary} de proposito. Se o pool do chat fosse o padrao,
 * todo codigo que esquecesse de qualificar consumiria a reserva do caminho critico e o bulkhead
 * viraria decoracao. Com o padrao no pool geral, o esquecimento degrada o relatorio, nunca o chat:
 * usar o pool do chat exige pedir por ele, com {@code @Qualifier("chatDataSource")}.
 *
 * <p>Nesta etapa existem apenas os beans. A ligacao do caminho de mensagens ao
 * {@code chatDataSource} (EntityManagerFactory e TransactionManager proprios) entra na etapa E09.
 */
@Configuration
public class DataSourceConfig {

    /** Qualificador do pool reservado ao caminho critico de mensagens. */
    public static final String CHAT_DATA_SOURCE = Pools.CHAT_DATA_SOURCE;

    /** Qualificador do pool usado por todo o resto da aplicacao. */
    public static final String GENERAL_DATA_SOURCE = Pools.GENERAL_DATA_SOURCE;

    @Bean
    @Primary
    @ConfigurationProperties("synapse.datasource.general")
    public DataSourceProperties generalDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(GENERAL_DATA_SOURCE)
    @Primary
    @ConfigurationProperties("synapse.datasource.general.hikari")
    public DataSource generalDataSource(
            @Qualifier("generalDataSourceProperties") DataSourceProperties propriedades) {
        return propriedades.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @ConfigurationProperties("synapse.datasource.chat")
    public DataSourceProperties chatDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(CHAT_DATA_SOURCE)
    @ConfigurationProperties("synapse.datasource.chat.hikari")
    public DataSource chatDataSource(
            @Qualifier("chatDataSourceProperties") DataSourceProperties propriedades) {
        return propriedades.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }
}
