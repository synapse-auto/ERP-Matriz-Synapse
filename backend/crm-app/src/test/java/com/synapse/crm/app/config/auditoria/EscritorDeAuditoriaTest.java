package com.synapse.crm.app.config.auditoria;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Sem contexto Spring de proposito: {@code @Transactional} nao tem efeito quando a classe e
 * instanciada direto (sem proxy), entao o que este teste exercita e exatamente o try/catch interno
 * de {@link EscritorDeAuditoria#registrar}, isolado da infraestrutura de transacao.
 */
@ExtendWith(MockitoExtension.class)
class EscritorDeAuditoriaTest {

    @Mock
    private DataSource dataSource;

    @Test
    @DisplayName("falha ao gravar (ex.: conexao indisponivel) nao propaga — so alarma")
    void registrar_comFalhaDeConexao_naoLanca() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("conexao indisponivel (simulado)"));
        EscritorDeAuditoria escritor = new EscritorDeAuditoria(dataSource);

        RegistroDeAuditoria registro = new RegistroDeAuditoria(
                UUID.randomUUID(),
                "USUARIO",
                "ATUALIZAR_TAG",
                "TAG",
                UUID.randomUUID(),
                null,
                "{\"nome\":\"antes\"}",
                "{\"nome\":\"depois\"}",
                "127.0.0.1",
                Instant.now());

        assertThatCode(() -> escritor.registrar(registro)).doesNotThrowAnyException();
    }
}
