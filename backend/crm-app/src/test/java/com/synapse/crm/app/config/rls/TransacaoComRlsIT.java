package com.synapse.crm.app.config.rls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.permissao.ConsultaDeAcessoAClientes;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@SpringBootTest
class TransacaoComRlsIT extends PostgresIT {

    @Autowired private EntityManagerFactory emf;
    @Autowired @Qualifier(Pools.GENERAL_DATA_SOURCE) private DataSource geral;
    @Autowired @Qualifier(Pools.CHAT_DATA_SOURCE) private DataSource chat;

    static Stream<Arguments> falhas() {
        return Stream.of(Arguments.of(true, false), Arguments.of(false, false),
                Arguments.of(true, true), Arguments.of(false, true));
    }

    @ParameterizedTest(name = "JPA={0}, SQL={1}: falha no inicio nega e devolve a conexao")
    @MethodSource("falhas")
    void falhaDePermissaoNaoRetemRecursosDaTransacao(boolean jpa, boolean falhaNoSql) throws Exception {
        UsuarioContext usuarios = mock(UsuarioContext.class);
        UUID usuario = UUID.randomUUID();
        when(usuarios.atualSeHouver()).thenReturn(Optional.of(
                new UsuarioAutenticado(usuario, PapelUsuario.ATENDENTE, false)));
        ConsultaDeAcessoAClientes acesso = mock(ConsultaDeAcessoAClientes.class);
        DataSource fonte = jpa ? geral : chat;
        DataAccessResourceFailureException falha = new DataAccessResourceFailureException("falha controlada");
        when(acesso.permitido(usuario)).thenAnswer(chamada -> {
            assertThat(TransactionSynchronizationManager.hasResource(fonte))
                    .as("consulta de permissoes antes de reservar a conexao").isFalse();
            if (!falhaNoSql) throw falha;
            return false;
        });
        AplicadorDeContextoRls aplicador = spy(new AplicadorDeContextoRls(usuarios, acesso));
        if (falhaNoSql) {
            doAnswer(chamada -> {
                chamada.callRealMethod();
                throw falha;
            }).when(aplicador).aplicar(eq(fonte), any(AplicadorDeContextoRls.Contexto.class));
        }
        PlatformTransactionManager gerente = jpa
                ? new TransacaoComRlsConfig.JpaComRls(emf, fonte, aplicador)
                : new TransacaoComRlsConfig.JdbcComRls(fonte, aplicador);
        try {
            assertThatThrownBy(() -> new TransactionTemplate(gerente).execute(status -> {
                throw new AssertionError("nao pode executar negocio sem contexto RLS");
            })).isSameAs(falha);

            assertThat(TransactionSynchronizationManager.hasResource(fonte)).as("conexao ligada apos falha").isFalse();
            assertThat(TransactionSynchronizationManager.hasResource(emf)).as("EntityManager ligado apos falha").isFalse();
            HikariDataSource pool = fonte.unwrap(HikariDataSource.class);
            if (pool.getHikariPoolMXBean() != null) {
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).as("conexao retida no pool").isZero();
            }
            doReturn(false).when(acesso).permitido(usuario);
            doCallRealMethod().when(aplicador).aplicar(eq(fonte), any(AplicadorDeContextoRls.Contexto.class));
            String permitido = new TransactionTemplate(gerente).execute(status ->
                    new org.springframework.jdbc.core.JdbcTemplate(fonte).queryForObject(
                            "SELECT current_setting('app.acesso_clientes')", String.class));
            assertThat(permitido).isEqualTo("false");
            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
        } finally {
            // Permite executar o negativo contra a implementacao antiga sem contaminar o proximo caso.
            Object entidade = TransactionSynchronizationManager.unbindResourceIfPossible(emf);
            if (entidade instanceof EntityManagerHolder holder) {
                if (holder.getEntityManager().getTransaction().isActive()) holder.getEntityManager().getTransaction().rollback();
                holder.getEntityManager().close();
            }
            Object recurso = TransactionSynchronizationManager.unbindResourceIfPossible(fonte);
            if (recurso instanceof ConnectionHolder holder && !holder.getConnection().isClosed()) {
                holder.getConnection().rollback();
                holder.getConnection().close();
            }
        }
    }
}
