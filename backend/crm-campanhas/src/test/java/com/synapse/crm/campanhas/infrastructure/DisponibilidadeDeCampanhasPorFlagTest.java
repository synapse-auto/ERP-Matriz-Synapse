package com.synapse.crm.campanhas.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.campanhas.application.CampanhasIndisponiveisException;
import com.synapse.crm.sharedkernel.permissao.ConsultaDeFuncionalidades;

/** Campanhas existem so com a funcionalidade habilitada E um canal que administra templates (por capacidade). */
class DisponibilidadeDeCampanhasPorFlagTest {

    @Test
    @DisplayName("funcionalidade habilitada e canal que administra templates: disponivel")
    void disponivel() {
        assertThat(disponibilidade(Set.of("campanhas", "dashboard"), true).disponivel()).isTrue();
    }

    @Test
    @DisplayName("canal que nao administra templates (nao Meta) esconde campanhas, mesmo com a funcionalidade ligada")
    void canalSemTemplates_indisponivel() {
        assertThat(disponibilidade(Set.of("campanhas"), false).disponivel()).isFalse();
    }

    @Test
    @DisplayName("funcionalidade desligada esconde campanhas, mesmo no canal da Meta")
    void funcionalidadeDesligada_indisponivel() {
        assertThat(disponibilidade(Set.of("dashboard"), true).disponivel()).isFalse();
    }

    @Test
    @DisplayName("exigir() lanca quando indisponivel")
    void exigirLanca() {
        assertThatThrownBy(() -> disponibilidade(Set.of(), true).exigir())
                .isInstanceOf(CampanhasIndisponiveisException.class);
        disponibilidade(Set.of("campanhas"), true).exigir();
    }

    private static DisponibilidadeDeCampanhasPorFlag disponibilidade(
            Set<String> funcionalidades, boolean gerenciaTemplates) {
        ConsultaDeFuncionalidades flags = mock(ConsultaDeFuncionalidades.class);
        when(flags.habilitadas()).thenReturn(funcionalidades);
        CanalGateway canal = mock(CanalGateway.class);
        when(canal.gerenciaTemplates()).thenReturn(gerenciaTemplates);
        return new DisponibilidadeDeCampanhasPorFlag(flags, canal);
    }
}
