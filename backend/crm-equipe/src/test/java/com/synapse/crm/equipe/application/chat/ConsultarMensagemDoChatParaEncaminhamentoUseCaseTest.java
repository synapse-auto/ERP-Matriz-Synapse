package com.synapse.crm.equipe.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.synapse.crm.equipe.application.chat.ChatInternoRepositorio.MensagemResumo;
import com.synapse.crm.equipe.application.chat.MensagemDoChatNaoEncaminhavelException.Motivo;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.identidade.UsuarioAutenticado;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;

/** O que é conteúdo para cliente e o que não é, e que só quem participa da conversa lê a mensagem. */
class ConsultarMensagemDoChatParaEncaminhamentoUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-10-05T12:00:00Z");

    private final ChatInternoRepositorio repositorio = mock(ChatInternoRepositorio.class);
    private final UsuarioContext contexto = mock(UsuarioContext.class);
    private final UUID usuario = UUID.randomUUID();
    private final UUID conversa = UUID.randomUUID();
    private final UUID mensagem = UUID.randomUUID();
    private ConsultarMensagemDoChatParaEncaminhamentoUseCase casoDeUso;

    @BeforeEach
    void preparar() {
        when(contexto.atual()).thenReturn(new UsuarioAutenticado(usuario, PapelUsuario.ATENDENTE, false));
        when(repositorio.participante(conversa, usuario)).thenReturn(true);
        casoDeUso = new ConsultarMensagemDoChatParaEncaminhamentoUseCase(repositorio, contexto, new ObjectMapper());
    }

    @Test
    @DisplayName("quem nao participa da conversa nao le a mensagem, e a existencia dela nao e revelada")
    void naoParticipante() {
        when(repositorio.participante(conversa, usuario)).thenReturn(false);
        // A mensagem existe: a recusa vem da participação, não de a mensagem não ser achada.
        devolve(new MensagemResumo(mensagem, conversa, UUID.randomUUID(), "Bruno", "TEXTO", "reservada", null, null, AGORA));

        assertThatThrownBy(() -> casoDeUso.executar(conversa, mensagem)).isInstanceOf(ChatSemAcessoException.class);
        verify(repositorio, never()).mensagem(conversa, mensagem);
    }

    @Test
    @DisplayName("mensagem que nao e dessa conversa responde como sem acesso")
    void mensagemInexistenteNaConversa() {
        when(repositorio.mensagem(conversa, mensagem)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> casoDeUso.executar(conversa, mensagem)).isInstanceOf(ChatSemAcessoException.class);
    }

    @Test
    @DisplayName("texto: devolve o corpo da linha persistida, sem nada do contexto interno")
    void texto() {
        devolve(new MensagemResumo(mensagem, conversa, UUID.randomUUID(), "Bruno", "TEXTO", "Oi cliente", null, null, AGORA));

        MensagemDoChatParaCliente lida = casoDeUso.executar(conversa, mensagem);

        assertThat(lida.tipo()).isEqualTo("TEXTO");
        assertThat(lida.texto()).isEqualTo("Oi cliente");
        assertThat(lida.ehMidia()).isFalse();
    }

    @Test
    @DisplayName("midia: nome, mimetype, tamanho e legenda saem dos metadados do chat (nome_original, tamanho_bytes)")
    void midia() {
        devolve(new MensagemResumo(mensagem, conversa, UUID.randomUUID(), "Bruno", "DOCUMENTO", "texto solto",
                "midia/abc.pdf",
                "{\"nome_original\":\"orçamento.pdf\",\"mimetype\":\"application/pdf\",\"tamanho_bytes\":4096,\"legenda\":\"segue\"}",
                AGORA));

        MensagemDoChatParaCliente lida = casoDeUso.executar(conversa, mensagem);

        assertThat(lida.ehMidia()).isTrue();
        assertThat(lida.midiaReferencia()).isEqualTo("midia/abc.pdf");
        assertThat(lida.nomeArquivo()).isEqualTo("orçamento.pdf");
        assertThat(lida.mimetype()).isEqualTo("application/pdf");
        assertThat(lida.tamanhoBytes()).isEqualTo(4096L);
        assertThat(lida.legenda()).isEqualTo("segue");
    }

    @Test
    @DisplayName("midia sem legenda nos metadados usa o conteudo da mensagem; sem os dois, fica sem legenda")
    void legendaDoConteudo() {
        devolve(new MensagemResumo(mensagem, conversa, UUID.randomUUID(), "Bruno", "IMAGEM", "veja",
                "midia/a.png", "{\"nome_original\":\"a.png\",\"mimetype\":\"image/png\",\"tamanho_bytes\":10}", AGORA));
        assertThat(casoDeUso.executar(conversa, mensagem).legenda()).isEqualTo("veja");

        devolve(new MensagemResumo(mensagem, conversa, UUID.randomUUID(), "Bruno", "IMAGEM", null,
                "midia/a.png", "{\"nome_original\":\"a.png\",\"mimetype\":\"image/png\",\"tamanho_bytes\":10}", AGORA));
        assertThat(casoDeUso.executar(conversa, mensagem).legenda()).isNull();
    }

    static Stream<Arguments> recusas() {
        return Stream.of(
                Arguments.of("SISTEMA", "{}", null, null, Motivo.EVENTO_DE_SISTEMA),
                Arguments.of("CONTATO", null, null, "{\"contatos\":[]}", Motivo.TIPO_NAO_SUPORTADO),
                Arguments.of("LOCALIZACAO", null, null, null, Motivo.TIPO_NAO_SUPORTADO),
                Arguments.of("TEXTO", "   ", null, null, Motivo.SEM_CONTEUDO),
                Arguments.of("TEXTO", null, null, null, Motivo.SEM_CONTEUDO),
                Arguments.of("IMAGEM", null, null, "{\"mimetype\":\"image/png\"}", Motivo.MIDIA_INDISPONIVEL),
                Arguments.of("IMAGEM", null, "midia/a.png", null, Motivo.MIDIA_INDISPONIVEL),
                Arguments.of("IMAGEM", null, "midia/a.png", "{nao e json", Motivo.MIDIA_INDISPONIVEL),
                Arguments.of("IMAGEM", null, "midia/a.png", "{\"nome_original\":\"a.png\"}", Motivo.MIDIA_INDISPONIVEL));
    }

    @ParameterizedTest(name = "{0} -> {4}")
    @MethodSource("recusas")
    void recusas(String tipo, String conteudo, String midia, String metadados, Motivo motivo) {
        devolve(new MensagemResumo(mensagem, conversa, UUID.randomUUID(), "Bruno", tipo, conteudo, midia, metadados, AGORA));

        assertThatThrownBy(() -> casoDeUso.executar(conversa, mensagem))
                .isInstanceOfSatisfying(MensagemDoChatNaoEncaminhavelException.class,
                        e -> assertThat(e.motivo()).isEqualTo(motivo));
    }

    @Test
    @DisplayName("mensagem apagada nao e encaminhada")
    void removida() {
        devolve(new MensagemResumo(mensagem, conversa, UUID.randomUUID(), "Bruno", "TEXTO", null, null, null, AGORA,
                null, List.of(), true, null));

        assertThatThrownBy(() -> casoDeUso.executar(conversa, mensagem))
                .isInstanceOfSatisfying(MensagemDoChatNaoEncaminhavelException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.REMOVIDA));
    }

    private void devolve(MensagemResumo resumo) {
        when(repositorio.mensagem(conversa, mensagem)).thenReturn(Optional.of(resumo));
    }
}
