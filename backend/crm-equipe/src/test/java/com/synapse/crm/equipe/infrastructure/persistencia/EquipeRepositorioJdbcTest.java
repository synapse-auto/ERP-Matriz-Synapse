package com.synapse.crm.equipe.infrastructure.persistencia;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.synapse.crm.equipe.application.usuario.EmailDeUsuarioEmUsoException;

class EquipeRepositorioJdbcTest {

    @Test
    void indiceUnicoDoBancoViraConflitoDeEmailNoPerfil() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("usuario_email_key"));

        assertThatThrownBy(() -> new EquipeRepositorioJdbc(jdbc)
                .atualizarMeuPerfil(UUID.randomUUID(), "Ana", "ocupado@example.invalid", null, null))
                .isInstanceOf(EmailDeUsuarioEmUsoException.class);
    }
}
