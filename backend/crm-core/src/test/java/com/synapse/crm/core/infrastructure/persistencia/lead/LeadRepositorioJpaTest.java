package com.synapse.crm.core.infrastructure.persistencia.lead;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;

import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;
import com.synapse.crm.core.domain.lead.TelefoneIndisponivelException;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

class LeadRepositorioJpaTest {
    @Test
    void traduzSomenteUnicidadeDoTelefone() {
        var erro = conflito("23505", "ux_lead_telefone");
        assertThatThrownBy(() -> salvarComFalha(erro)).isInstanceOf(TelefoneIndisponivelException.class)
                .hasMessageNotContaining("SQL");
    }

    @Test
    void outraConstraintNaoViraConflitoDeTelefone() {
        var erro = conflito("23505", "outra_constraint");
        assertThatThrownBy(() -> salvarComFalha(erro)).isSameAs(erro);
    }

    @Test
    void outroEstadoSqlNaoViraConflitoDeTelefone() {
        var erro = conflito("23514", "ux_lead_telefone");
        assertThatThrownBy(() -> salvarComFalha(erro)).isSameAs(erro);
    }

    private static DataIntegrityViolationException conflito(String estado, String nome) {
        return new DataIntegrityViolationException("falha SQL", new ConstraintViolationException(
                "detalhe interno", new SQLException("nao expor", estado), nome));
    }

    private static void salvarComFalha(DataIntegrityViolationException erro) {
        var jpa = mock(LeadJpaRepository.class);
        var usuario = mock(UsuarioContext.class);
        when(usuario.atual()).thenReturn(new UsuarioAutenticado(UUID.randomUUID(), PapelUsuario.GESTOR, false));
        when(jpa.findOne(org.mockito.ArgumentMatchers.<Specification<LeadEntity>>any()))
                .thenReturn(Optional.of(new LeadEntity()));
        when(jpa.saveAndFlush(any(LeadEntity.class))).thenThrow(erro);
        var repositorio = new LeadRepositorioJpa(jpa, mock(EntityManager.class), usuario, Clock.systemUTC());
        repositorio.salvar(Lead.apenasParaVisibilidade(UUID.randomUUID(), "Sintetico", StatusBasicoLead.IA, null));
    }
}
