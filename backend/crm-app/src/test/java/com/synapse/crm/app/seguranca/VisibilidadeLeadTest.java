package com.synapse.crm.app.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.synapse.crm.core.domain.lead.Lead;
import com.synapse.crm.core.domain.lead.StatusBasicoLead;
import com.synapse.crm.core.domain.lead.VisibilidadeLead;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;

/**
 * RN-CRM-01 sem banco e sem Spring.
 *
 * <p>A regra e Java puro justamente para poder ser testada assim: em milissegundos, sem container,
 * cobrindo todas as combinacoes de papel. O teste de integracao confere que o SQL concorda com isto.
 */
class VisibilidadeLeadTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BRUNO = UUID.randomUUID();

    private static Lead leadDe(UUID dono, StatusBasicoLead status) {
        return Lead.apenasParaVisibilidade(UUID.randomUUID(), "Lead", status, dono);
    }

    @Test
    @DisplayName("atendente ve o proprio lead")
    void atendente_leadProprio_enxerga() {
        VisibilidadeLead visao = VisibilidadeLead.de(new UsuarioAutenticado(ANA, PapelUsuario.ATENDENTE, false));

        assertThat(visao.permite(leadDe(ANA, StatusBasicoLead.EM_ATENDIMENTO))).isTrue();
    }

    @Test
    @DisplayName("atendente NAO ve o lead EM_ATENDIMENTO de outro atendente")
    void atendente_leadDeColega_naoEnxerga() {
        VisibilidadeLead visao = VisibilidadeLead.de(new UsuarioAutenticado(ANA, PapelUsuario.ATENDENTE, false));

        assertThat(visao.permite(leadDe(BRUNO, StatusBasicoLead.EM_ATENDIMENTO))).isFalse();
    }

    @Test
    @DisplayName("atendente ve lead FINALIZADO de colega (E145 — balcao)")
    void atendente_leadFinalizadoDeColega_enxerga() {
        VisibilidadeLead visao = VisibilidadeLead.de(new UsuarioAutenticado(ANA, PapelUsuario.ATENDENTE, false));

        assertThat(visao.permite(leadDe(BRUNO, StatusBasicoLead.FINALIZADO))).isTrue();
    }

    @Test
    @DisplayName("atendente ve os leads em IA, que sao de todos")
    void atendente_leadEmIa_enxerga() {
        VisibilidadeLead visao = VisibilidadeLead.de(new UsuarioAutenticado(ANA, PapelUsuario.ATENDENTE, false));

        assertThat(visao.permite(leadDe(null, StatusBasicoLead.IA))).isTrue();
        // Mesmo com dono, enquanto estiver em IA continua no grupo "Potenciais".
        assertThat(visao.permite(leadDe(BRUNO, StatusBasicoLead.IA))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = PapelUsuario.class, names = {"SUBGESTOR", "GESTOR", "ADMINISTRADOR"})
    @DisplayName("gestao enxerga qualquer lead")
    void gestao_qualquerLead_enxerga(PapelUsuario papel) {
        VisibilidadeLead visao = VisibilidadeLead.de(new UsuarioAutenticado(ANA, papel, false));

        assertThat(visao).isInstanceOf(VisibilidadeLead.Ampla.class);
        assertThat(visao.permite(leadDe(BRUNO, StatusBasicoLead.EM_ATENDIMENTO))).isTrue();
        assertThat(visao.permite(leadDe(BRUNO, StatusBasicoLead.FINALIZADO))).isTrue();
    }

    @Test
    @DisplayName("so o atendente recebe visao restrita")
    void papel_atendente_recebeVisaoRestrita() {
        VisibilidadeLead visao = VisibilidadeLead.de(new UsuarioAutenticado(ANA, PapelUsuario.ATENDENTE, false));

        assertThat(visao).isEqualTo(new VisibilidadeLead.DoAtendente(ANA));
    }
}
