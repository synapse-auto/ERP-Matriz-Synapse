package com.synapse.crm.app.config.rls;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/**
 * Publica o contexto da transacao para as politicas RLS do banco.
 *
 * <p>Roda no inicio de toda transacao, nos dois pools. Existem tres contextos possiveis e cada um
 * tem um comportamento deliberado:
 *
 * <ul>
 *   <li><b>Requisicao autenticada</b> — grava {@code app.usuario_id} e {@code app.papel}; a
 *       politica filtra pelo papel.
 *   <li><b>Servico</b> ({@link ContextoDeServico}) — grava {@code app.papel = 'SERVICO'}; jobs e
 *       consumidores de fila enxergam tudo, sem se passar por um usuario real.
 *   <li><b>Sem contexto</b> — nao grava nada. As variaveis ficam NULL e a politica nega tudo.
 * </ul>
 *
 * <p>O terceiro caso e o mais importante: uma transacao que escapou dos dois primeiros e bug, e a
 * resposta certa para bug e tela vazia — visivel e diagnosticavel — e nao lead do colega.
 *
 * <p>Usamos {@code set_config(..., is_local => true)}, que e o equivalente parametrizavel de
 * {@code SET LOCAL}: escopo de transacao (nao vaza para a proxima transacao da mesma conexao do
 * pool) e sem concatenar valor em SQL.
 */
@Component
public class AplicadorDeContextoRls {

    private static final Logger log = LoggerFactory.getLogger(AplicadorDeContextoRls.class);

    /**
     * Assumida em toda transacao. Sem isso a aplicacao roda como dona das tabelas — e, em
     * ambiente de teste, como superusuario — e o Postgres ignora as politicas. A protecao passa
     * a nao depender de como a string de conexao foi provisionada.
     */
    private static final String SQL_ASSUMIR_ROLE = "SET LOCAL ROLE synapse_app";

    private static final String SQL_DEFINIR = "SELECT set_config(?, ?, TRUE)";
    private static final String CHAVE_USUARIO = "app.usuario_id";
    private static final String CHAVE_PAPEL = "app.papel";

    private final UsuarioContext usuarioContext;

    public AplicadorDeContextoRls(UsuarioContext usuarioContext) {
        this.usuarioContext = usuarioContext;
    }

    /** Chamado logo apos o inicio da transacao, com a conexao ja ligada ao {@code dataSource}. */
    public void aplicar(DataSource dataSource) {
        if (ContextoDeServico.ativo()) {
            definir(dataSource, "", ContextoDeServico.PAPEL);
            return;
        }

        Optional<UsuarioAutenticado> usuario = usuarioSeHouver();
        if (usuario.isEmpty()) {
            // Transacao sem contexto e normal no login e em tudo que nao toca tabela
            // protegida. Ainda assim assumimos a role: sem isso, uma transacao sem
            // contexto rodaria como dona das tabelas e enxergaria TUDO — o oposto de
            // falhar fechado.
            definir(dataSource, "", "");
            return;
        }

        definir(dataSource, usuario.get().id().toString(), usuario.get().papel().name());
    }

    private Optional<UsuarioAutenticado> usuarioSeHouver() {
        try {
            return usuarioContext.atualSeHouver();
        } catch (RuntimeException e) {
            // Nunca deixamos a leitura do contexto derrubar a transacao: falhar aqui
            // resultaria em contexto ausente, que o banco ja trata negando.
            log.warn("Falha ao ler o contexto de usuario para RLS. Seguindo sem contexto.", e);
            return Optional.empty();
        }
    }

    private void definir(DataSource dataSource, String usuarioId, String papel) {
        Connection conexao = DataSourceUtils.getConnection(dataSource);

        // Primeiro deixa de ser dono das tabelas. Enquanto a transacao roda como
        // dono — ou como superusuario, que e o caso no ambiente de teste — o
        // Postgres ignora as politicas e o RLS inteiro vira decoracao.
        try (Statement troca = conexao.createStatement()) {
            troca.execute(SQL_ASSUMIR_ROLE);
        } catch (SQLException e) {
            throw new ContextoRlsIndisponivelException(papel, e);
        }

        try (PreparedStatement comando = conexao.prepareStatement(SQL_DEFINIR)) {
            executar(comando, CHAVE_USUARIO, usuarioId);
            executar(comando, CHAVE_PAPEL, papel);
        } catch (SQLException e) {
            // Sem contexto publicado, as politicas negam tudo. Preferimos abortar a
            // transacao com erro claro a deixar a aplicacao rodando com telas vazias
            // inexplicaveis.
            throw new ContextoRlsIndisponivelException(papel, e);
        }
    }

    private static void executar(PreparedStatement comando, String chave, String valor)
            throws SQLException {
        comando.setString(1, chave);
        comando.setString(2, valor);
        comando.execute();
    }
}
