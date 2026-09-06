package com.synapse.crm.automacaoconfig.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.crm.automacaoconfig.domain.MarcaDaInstancia;
import com.synapse.crm.automacaoconfig.domain.MarcaDaInstanciaInvalidaException;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.midia.DetectorDeTipoReal;

class MarcaDaInstanciaUseCaseTest {

    private static final UUID USUARIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant AGORA = Instant.parse("2026-09-06T12:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);

    @Test
    @DisplayName("tema sem customizacao usa exatamente o recurso do classpath")
    void temaSemCustomizacao_usaClasspath() {
        var marcas = mock(MarcaDaInstanciaRepositorio.class);
        var recursos = mock(RecursosDeMarcaDaInstancia.class);
        var json = new ObjectMapper();
        var fallback = json.createObjectNode().put("corPrimaria", "#123456");
        when(marcas.obter()).thenReturn(new MarcaDaInstancia(null, null, null, null));
        when(recursos.tema()).thenReturn(fallback);

        assertThat(new ObterTemaDaInstanciaUseCase(marcas, recursos, json).executar()).isSameAs(fallback);
    }

    @Test
    @DisplayName("tema persistido aparece na leitura seguinte sem reinicializacao")
    void temaPersistido_sobrepoeClasspath() {
        var marcas = mock(MarcaDaInstanciaRepositorio.class);
        var recursos = mock(RecursosDeMarcaDaInstancia.class);
        var json = new ObjectMapper();
        when(marcas.obter()).thenReturn(new MarcaDaInstancia("{\"corPrimaria\":\"#abcdef\"}", null, null, null));

        assertThat(new ObterTemaDaInstanciaUseCase(marcas, recursos, json).executar().path("corPrimaria").asText())
                .isEqualTo("#abcdef");
        verifyNoInteractions(recursos);
    }

    @Test
    @DisplayName("logo sem customizacao usa classpath e logo persistida baixa do storage")
    void logo_resolveClasspathOuStorage() {
        var marcas = mock(MarcaDaInstanciaRepositorio.class);
        var recursos = mock(RecursosDeMarcaDaInstancia.class);
        var armazenamento = mock(ArmazenamentoDeMidia.class);
        byte[] fallback = {1};
        when(recursos.logo()).thenReturn(fallback);
        when(marcas.obter()).thenReturn(new MarcaDaInstancia(null, null, null, null));
        assertThat(new ObterLogoDaInstanciaUseCase(marcas, recursos, armazenamento).executar()).isSameAs(fallback);

        byte[] customizada = {2};
        when(marcas.obter()).thenReturn(new MarcaDaInstancia(null, "midia/logo", null, null));
        when(armazenamento.baixar("midia/logo")).thenReturn(customizada);
        assertThat(new ObterLogoDaInstanciaUseCase(marcas, recursos, armazenamento).executar()).isSameAs(customizada);
    }

    @Test
    @DisplayName("troca de logo grava nova referencia e remove a antiga somente depois")
    void logo_trocaReferenciaComCompensacao() {
        var marcas = mock(MarcaDaInstanciaRepositorio.class);
        var armazenamento = mock(ArmazenamentoDeMidia.class);
        var detector = mock(DetectorDeTipoReal.class);
        var usuario = usuarioContext();
        when(marcas.obter()).thenReturn(new MarcaDaInstancia(null, "midia/logo-antiga", null, null));
        byte[] novaLogo = {1, 2};
        when(detector.detectar(novaLogo)).thenReturn("image/png");
        when(armazenamento.salvar(novaLogo, "logo.png", "image/png")).thenReturn("midia/logo-nova");

        new AtualizarLogoDaInstanciaUseCase(marcas, armazenamento, detector, usuario, RELOGIO)
                .executar(novaLogo, "logo.png");

        verify(marcas).salvarLogo("midia/logo-nova", USUARIO, AGORA);
        verify(armazenamento).remover("midia/logo-antiga");
    }

    @Test
    @DisplayName("logo que nao e PNG real e recusada antes do storage")
    void logo_tipoInvalido_naoGrava() {
        var marcas = mock(MarcaDaInstanciaRepositorio.class);
        var armazenamento = mock(ArmazenamentoDeMidia.class);
        var detector = mock(DetectorDeTipoReal.class);
        when(marcas.obter()).thenReturn(new MarcaDaInstancia(null, null, null, null));
        byte[] arquivoInvalido = {9};
        when(detector.detectar(arquivoInvalido)).thenReturn("application/pdf");

        assertThatThrownBy(() -> new AtualizarLogoDaInstanciaUseCase(
                        marcas, armazenamento, detector, usuarioContext(), RELOGIO)
                .executar(arquivoInvalido, "logo.png"))
                .isInstanceOf(MarcaDaInstanciaInvalidaException.class);
        verifyNoInteractions(armazenamento);
    }

    private static UsuarioContext usuarioContext() {
        var contexto = mock(UsuarioContext.class);
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(USUARIO, PapelUsuario.GESTOR, false));
        return contexto;
    }
}
