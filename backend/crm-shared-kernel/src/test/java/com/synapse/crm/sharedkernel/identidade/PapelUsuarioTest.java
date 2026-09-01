package com.synapse.crm.sharedkernel.identidade;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PapelUsuarioTest {

    @Test
    void recebeAtendimento_atendenteESubgestorSim_gestorEAdministradorNao() {
        assertThat(PapelUsuario.ATENDENTE.recebeAtendimento()).isTrue();
        assertThat(PapelUsuario.SUBGESTOR.recebeAtendimento()).isTrue();
        assertThat(PapelUsuario.GESTOR.recebeAtendimento()).isFalse();
        assertThat(PapelUsuario.ADMINISTRADOR.recebeAtendimento()).isFalse();
    }

    @Test
    void enxergaTodosOsLeads_subgestorContinuaVendoABaseInteira() {
        assertThat(PapelUsuario.SUBGESTOR.enxergaTodosOsLeads()).isTrue();
        assertThat(PapelUsuario.ATENDENTE.enxergaTodosOsLeads()).isFalse();
        assertThat(PapelUsuario.GESTOR.enxergaTodosOsLeads()).isTrue();
        assertThat(PapelUsuario.ADMINISTRADOR.enxergaTodosOsLeads()).isTrue();
    }
}
