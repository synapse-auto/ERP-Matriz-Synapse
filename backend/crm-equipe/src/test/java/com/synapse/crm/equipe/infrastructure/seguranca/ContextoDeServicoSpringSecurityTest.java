package com.synapse.crm.equipe.infrastructure.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

class ContextoDeServicoSpringSecurityTest {

    @BeforeEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
        new ContextoDeServicoSpringSecurity();
    }

    @AfterEach
    void restaurarPonte() {
        SecurityContextHolder.clearContext();
        ContextoDeServico.instalarPonteDeAutoridade(ContextoDeServico.PonteDeAutoridade.NAO_INSTALADA);
    }

    @Test
    void contextoDeServico_publicaRoleERecuperaAutoridadeAoSair() {
        ContextoDeServico.executarComo("teste-agendado", () -> {
            Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
            assertThat(autenticacao).isNotNull();
            assertThat(autenticacao.getName()).isEqualTo("servico:teste-agendado");
            assertThat(autenticacao.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_SERVICO");
        });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
