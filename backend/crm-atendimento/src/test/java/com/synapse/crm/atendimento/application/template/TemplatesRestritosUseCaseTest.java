package com.synapse.crm.atendimento.application.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ResultadoDeTemplate;
import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/** Casos de uso de template com a regra real e o provedor mockado: nada restrito chega ao canal. */
class TemplatesRestritosUseCaseTest {

    private static final TemplateDoCanal COMUM = template("meta-comum", "aviso_cliente");
    private static final TemplateDoCanal RESTRITO = template("meta-restrito", "aviso_interno_cliente");

    private CanalGateway canal;

    @BeforeEach
    void preparar() {
        canal = mock(CanalGateway.class);
        when(canal.listarTemplates()).thenReturn(List.of(COMUM, RESTRITO));
        when(canal.editarTemplate(any())).thenReturn(new ResultadoDeTemplate.Aceito(null));
        when(canal.excluirTemplate(any(), any())).thenReturn(new ResultadoDeTemplate.Aceito(null));
        when(canal.criarTemplate(any())).thenReturn(new ResultadoDeTemplate.Aceito(COMUM));
    }

    @ParameterizedTest
    @EnumSource(value = PapelUsuario.class, names = {"ATENDENTE", "SUBGESTOR", "GESTOR"})
    void listagemDeNaoAdministradorOmiteRestrito(PapelUsuario papel) {
        var listar = new ListarTemplatesWhatsAppUseCase(canal, autorizacao(papel));

        assertThat(listar.executar()).containsExactly(COMUM);
    }

    @Test
    void administradorListaTudo() {
        var listar = new ListarTemplatesWhatsAppUseCase(canal, autorizacao(PapelUsuario.ADMINISTRADOR));

        assertThat(listar.executar()).containsExactly(COMUM, RESTRITO);
    }

    @ParameterizedTest
    @EnumSource(value = PapelUsuario.class, names = {"ATENDENTE", "SUBGESTOR", "GESTOR"})
    void criarComNomeRestritoNaoChegaAoProvedor_inclusiveDepoisDaNormalizacao(PapelUsuario papel) {
        var criar = new CriarTemplateWhatsAppUseCase(canal, autorizacao(papel));

        assertThatThrownBy(() -> criar.executar(
                        "Aviso INTERNO cliente", "pt_BR", TemplateDoCanal.Categoria.UTILIDADE, "Ola"))
                .isInstanceOf(TemplateRestritoException.class);
        verify(canal, never()).criarTemplate(any());
    }

    @Test
    void criarNomeComumContinuaPermitidoParaGestor() {
        var criar = new CriarTemplateWhatsAppUseCase(canal, autorizacao(PapelUsuario.GESTOR));

        criar.executar("aviso_cliente", "pt_BR", TemplateDoCanal.Categoria.UTILIDADE, "Ola");

        verify(canal).criarTemplate(any());
    }

    @ParameterizedTest
    @EnumSource(value = PapelUsuario.class, names = {"SUBGESTOR", "GESTOR"})
    void editarVarianteRestritaRespondeComoInexistente(PapelUsuario papel) {
        var editar = new EditarTemplateWhatsAppUseCase(canal, autorizacao(papel));

        assertThatThrownBy(() -> editar.executar("meta-restrito", "Novo"))
                .isInstanceOf(TemplateForaDoAlcanceException.class);
        verify(canal, never()).editarTemplate(any());
    }

    @Test
    void editarIdDesconhecidoDeNaoAdministradorFalhaFechado() {
        var editar = new EditarTemplateWhatsAppUseCase(canal, autorizacao(PapelUsuario.GESTOR));

        assertThatThrownBy(() -> editar.executar("meta-inexistente", "Novo"))
                .isInstanceOf(TemplateForaDoAlcanceException.class);
        verify(canal, never()).editarTemplate(any());
    }

    @Test
    void administradorEditaRestritoSemConsultarListagem() {
        var editar = new EditarTemplateWhatsAppUseCase(canal, autorizacao(PapelUsuario.ADMINISTRADOR));

        editar.executar("meta-restrito", "Novo");

        verify(canal).editarTemplate(any());
        verify(canal, never()).listarTemplates();
    }

    @Test
    void excluirRestritoPorNomeOuPorIdNaoChegaAoProvedor() {
        var excluir = new ExcluirTemplateWhatsAppUseCase(canal, autorizacao(PapelUsuario.GESTOR));

        assertThatThrownBy(() -> excluir.executar("meta-restrito", "aviso_interno_cliente"))
                .isInstanceOf(TemplateRestritoException.class);
        // Nome comum nao acoberta o ID de uma variante restrita.
        assertThatThrownBy(() -> excluir.executar("meta-restrito", "aviso_cliente"))
                .isInstanceOf(TemplateForaDoAlcanceException.class);
        verify(canal, never()).excluirTemplate(any(), any());
    }

    @Test
    void excluirComumContinuaPermitidoParaGestor() {
        var excluir = new ExcluirTemplateWhatsAppUseCase(canal, autorizacao(PapelUsuario.GESTOR));

        excluir.executar("meta-comum", "aviso_cliente");

        verify(canal).excluirTemplate("meta-comum", "aviso_cliente");
    }

    private AutorizacaoDeTemplates autorizacao(PapelUsuario papel) {
        return AutorizacaoDeTemplatesDeTeste.paraPapel(papel, canal);
    }

    private static TemplateDoCanal template(String id, String nome) {
        return new TemplateDoCanal(
                id, nome, "pt_BR", TemplateDoCanal.Categoria.UTILIDADE,
                TemplateDoCanal.Status.APROVADO, "Ola", 0);
    }
}
