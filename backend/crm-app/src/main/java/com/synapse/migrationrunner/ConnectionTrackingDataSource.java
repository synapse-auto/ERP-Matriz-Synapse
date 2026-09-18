package com.synapse.migrationrunner;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * DataSource usado somente durante o one-shot para interromper conexões JDBC ativas no timeout.
 * O pool subjacente é fechado pelo runner ao final do processo one-shot.
 */
final class ConnectionTrackingDataSource implements DataSource, AutoCloseable {

    private final DataSource delegate;
    private final Set<Connection> abertas = ConcurrentHashMap.newKeySet();
    private final Object cicloDeVida = new Object();
    private volatile boolean encerrando;

    ConnectionTrackingDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return rastrear(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return rastrear(delegate.getConnection(username, password));
    }

    int conexoesAtivas() {
        return abertas.size();
    }

    /** Cancela consultas e fecha todas as conexões vistas, sem aguardar o worker do Flyway. */
    void fecharConexoesAtivas() {
        Connection[] conexoes;
        synchronized (cicloDeVida) {
            encerrando = true;
            conexoes = abertas.toArray(Connection[]::new);
            abertas.clear();
        }
        for (Connection conexao : conexoes) {
            try {
                conexao.abort(Runnable::run);
            } catch (Throwable ignorado) {
                // Alguns drivers não implementam abort; close abaixo ainda devolve o recurso ao pool.
            }
            try {
                conexao.close();
            } catch (SQLException ignorado) {
                // O processo one-shot será encerrado; não vazar detalhes JDBC no log.
            }
        }
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }

    @Override
    public void close() {
        fecharConexoesAtivas();
    }

    private Connection rastrear(Connection conexao) throws SQLException {
        synchronized (cicloDeVida) {
            if (encerrando) {
                try {
                    conexao.close();
                } catch (SQLException ignorado) {
                    // A conexão já está fora do escopo do processo que será encerrado.
                }
                throw new SQLException("DataSource do runner já está sendo encerrado");
            }
            abertas.add(conexao);
        }
        InvocationHandler handler = new ConnectionHandler(conexao, abertas);
        return (Connection) Proxy.newProxyInstance(
                ConnectionTrackingDataSource.class.getClassLoader(), new Class<?>[] {Connection.class}, handler);
    }

    private static final class ConnectionHandler implements InvocationHandler {

        private final Connection delegate;
        private final Set<Connection> abertas;

        private ConnectionHandler(Connection delegate, Set<Connection> abertas) {
            this.delegate = delegate;
            this.abertas = abertas;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("close") && method.getParameterCount() == 0) {
                abertas.remove(delegate);
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException erro) {
                throw erro.getCause();
            }
        }
    }
}
