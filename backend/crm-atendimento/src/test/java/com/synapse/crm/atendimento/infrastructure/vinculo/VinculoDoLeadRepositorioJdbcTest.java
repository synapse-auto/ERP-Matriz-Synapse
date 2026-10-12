package com.synapse.crm.atendimento.infrastructure.vinculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * E231: com a chave desligada o caminho da transferencia nao vai ao banco por chamada. O valor da chave e lido no
 * maximo uma vez por intervalo; desligada, a busca do vinculo (pool do chat) nunca executa.
 */
class VinculoDoLeadRepositorioJdbcTest {

    private static final Duration INTERVALO = Duration.ofSeconds(2);

    private final AtomicReference<Instant> agora = new AtomicReference<>(Instant.parse("2026-10-12T10:00:00Z"));
    private final Clock relogio = new Clock() {
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zona) { return this; }
        @Override public Instant instant() { return agora.get(); }
    };

    private final DataSource chat = mock(DataSource.class);
    private final DataSource geral = mock(DataSource.class);

    @Test
    @DisplayName("chave desligada: 100 buscas e 100 gravacoes pedidas fazem uma leitura da chave e nenhuma busca do vinculo")
    void desligadaNaoVaiAoBancoPorChamada() throws Exception {
        ligar(geral, false, null);
        VinculoDoLeadRepositorioJdbc repositorio = new VinculoDoLeadRepositorioJdbc(chat, geral, relogio, INTERVALO);

        for (int chamada = 0; chamada < 100; chamada++) {
            assertThat(repositorio.vinculadoDoAtendimento(UUID.randomUUID())).isEmpty();
            assertThat(repositorio.habilitado()).isFalse();
        }

        verify(geral, times(1)).getConnection();
        verify(chat, never()).getConnection();
    }

    @Test
    @DisplayName("a leitura vale ate o intervalo acabar; depois, uma nova leitura")
    void relêDepoisDoIntervalo() throws Exception {
        ligar(geral, false, null);
        VinculoDoLeadRepositorioJdbc repositorio = new VinculoDoLeadRepositorioJdbc(chat, geral, relogio, INTERVALO);

        repositorio.habilitado();
        agora.set(agora.get().plusSeconds(1));
        repositorio.habilitado();
        verify(geral, times(1)).getConnection();

        agora.set(agora.get().plusSeconds(2));
        repositorio.habilitado();
        verify(geral, times(2)).getConnection();
    }

    @Test
    @DisplayName("chave ligada: a busca do vinculo executa no pool do chat")
    void ligadaBuscaOVinculo() throws Exception {
        UUID atendente = UUID.randomUUID();
        ligar(geral, true, null);
        ligar(chat, null, atendente);
        VinculoDoLeadRepositorioJdbc repositorio = new VinculoDoLeadRepositorioJdbc(chat, geral, relogio, INTERVALO);

        assertThat(repositorio.vinculadoDoAtendimento(UUID.randomUUID())).contains(atendente);

        verify(chat, times(1)).getConnection();
    }

    @Test
    @DisplayName("falha ao ler a chave: falha fechado (vinculo ignorado) e nao tenta de novo a cada chamada")
    void falhaDeLeituraFechaPorUmIntervalo() throws Exception {
        when(geral.getConnection()).thenThrow(new SQLException("banco fora"));
        VinculoDoLeadRepositorioJdbc repositorio = new VinculoDoLeadRepositorioJdbc(chat, geral, relogio, INTERVALO);

        for (int chamada = 0; chamada < 10; chamada++) {
            assertThat(repositorio.habilitado()).isFalse();
        }

        verify(geral, times(1)).getConnection();
    }

    /** Conexao falsa: {@code booleanDaChave} responde a leitura da chave; {@code atendente} responde a busca do vinculo. */
    private static void ligar(DataSource fonte, Boolean booleanDaChave, UUID atendente) throws SQLException {
        Connection conexao = mock(Connection.class);
        PreparedStatement comando = mock(PreparedStatement.class);
        ResultSet resultado = mock(ResultSet.class);
        when(fonte.getConnection()).thenReturn(conexao);
        when(conexao.prepareStatement(anyString())).thenReturn(comando);
        when(comando.executeQuery()).thenReturn(resultado);
        if (booleanDaChave != null) {
            when(resultado.next()).thenReturn(true, false);
            when(resultado.getBoolean(1)).thenReturn(booleanDaChave);
        } else {
            when(resultado.next()).thenReturn(true, false);
            when(resultado.getObject("atendente_id", UUID.class)).thenReturn(atendente);
        }
    }
}
