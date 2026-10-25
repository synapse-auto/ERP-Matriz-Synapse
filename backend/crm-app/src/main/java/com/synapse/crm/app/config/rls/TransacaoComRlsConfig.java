package com.synapse.crm.app.config.rls;

import java.util.function.Consumer;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;

import com.synapse.crm.app.config.DataSourceConfig;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Gerentes de transacao que publicam o contexto RLS antes de qualquer consulta.
 *
 * <p>O gancho e {@code doBegin}: quando ele retorna, a conexao ja esta ligada a transacao e com
 * autocommit desligado — que e a condicao para {@code SET LOCAL} valer. Fazer isso ao pegar a
 * conexao do pool seria cedo demais: ainda em autocommit, o escopo "local" nao existe e o comando
 * viraria no-op silencioso.
 *
 * <p>Substituir o gerente de transacao, em vez de usar um aspecto sobre {@code @Transactional},
 * evita a briga de ordenacao com o interceptador de transacao — e garante que <b>toda</b>
 * transacao passe por aqui, inclusive as abertas por {@code TransactionTemplate} ou por codigo que
 * ninguem lembrou de anotar.
 *
 * <p>Os dois pools tem o seu. O do chat ainda nao e usado por ninguem — a ligacao do caminho de
 * mensagens a ele e a E09 —, mas o encanamento fica pronto agora, que e barato, em vez de depois,
 * quando houver dezenas de casos de uso para revisar.
 */
@Configuration
public class TransacaoComRlsConfig {

    /** Qualificador do gerente de transacao do pool reservado ao caminho critico. */
    public static final String CHAT_TRANSACTION_MANAGER = Pools.CHAT_TRANSACTION_MANAGER;

    @Bean
    @Primary
    PlatformTransactionManager transactionManager(
            EntityManagerFactory emf,
            @Qualifier(DataSourceConfig.GENERAL_DATA_SOURCE) DataSource geral,
            AplicadorDeContextoRls aplicador) {
        return new JpaComRls(emf, geral, aplicador);
    }

    @Bean(CHAT_TRANSACTION_MANAGER)
    PlatformTransactionManager chatTransactionManager(
            @Qualifier(DataSourceConfig.CHAT_DATA_SOURCE) DataSource chat,
            AplicadorDeContextoRls aplicador) {
        return new JdbcComRls(chat, aplicador);
    }

    /** Transacoes JPA do pool geral. */
    static final class JpaComRls extends JpaTransactionManager {

        private static final long serialVersionUID = 1L;

        private final transient AplicadorDeContextoRls aplicador;

        JpaComRls(EntityManagerFactory emf, DataSource dataSource, AplicadorDeContextoRls aplicador) {
            super(emf);
            // Sem isto, a conexao nao fica registrada sob este DataSource e o
            // aplicador nao teria como alcanca-la.
            setDataSource(dataSource);
            this.aplicador = aplicador;
        }

        @Override
        protected void doBegin(Object transacao, TransactionDefinition definicao) {
            AplicadorDeContextoRls.Contexto contexto = aplicador.preparar();
            super.doBegin(transacao, definicao);
            try {
                aplicador.aplicar(getDataSource(), contexto);
            } catch (RuntimeException | Error falha) {
                limparInicioFalho(transacao, definicao, falha, super::doRollback, super::doCleanupAfterCompletion);
                throw falha;
            }
        }
    }

    /** Transacoes JDBC do pool do chat. */
    static final class JdbcComRls extends DataSourceTransactionManager {

        private static final long serialVersionUID = 1L;

        private final transient AplicadorDeContextoRls aplicador;

        JdbcComRls(DataSource dataSource, AplicadorDeContextoRls aplicador) {
            super(dataSource);
            this.aplicador = aplicador;
        }

        @Override
        protected void doBegin(Object transacao, TransactionDefinition definicao) {
            AplicadorDeContextoRls.Contexto contexto = aplicador.preparar();
            super.doBegin(transacao, definicao);
            try {
                aplicador.aplicar(obtainDataSource(), contexto);
            } catch (RuntimeException | Error falha) {
                limparInicioFalho(transacao, definicao, falha, super::doRollback, super::doCleanupAfterCompletion);
                throw falha;
            }
        }
    }

    /**
     * O Spring so conhece um begin concluido quando doBegin retorna. Se nosso SQL posterior
     * falhar, cabe a este gancho desfazer o begin ja feito pelo super e devolver seus recursos.
     * Nao publica sincronizacoes nem callbacks de commit para uma transacao que nao comecou.
     */
    private static void limparInicioFalho(Object transacao, TransactionDefinition definicao, Throwable falha,
            Consumer<DefaultTransactionStatus> rollback, Consumer<Object> limpeza) {
        try {
            rollback.accept(new DefaultTransactionStatus(
                    definicao.getName(), transacao, true, false, false, definicao.isReadOnly(), false, null));
        } catch (RuntimeException | Error erroDeRollback) {
            falha.addSuppressed(erroDeRollback);
        } finally {
            try {
                limpeza.accept(transacao);
            } catch (RuntimeException | Error erroDeLimpeza) {
                falha.addSuppressed(erroDeLimpeza);
            }
        }
    }
}
