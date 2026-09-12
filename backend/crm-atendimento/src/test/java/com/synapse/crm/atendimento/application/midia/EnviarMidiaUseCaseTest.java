package com.synapse.crm.atendimento.application.midia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.sharedkernel.midia.ArmazenamentoDeMidia;
import com.synapse.crm.sharedkernel.midia.CategoriaDeMidia;
import com.synapse.crm.sharedkernel.midia.ConversorDeAudio;
import com.synapse.crm.sharedkernel.midia.DetectorDeTipoReal;
import com.synapse.crm.sharedkernel.midia.LimiteDeAnexoRepositorio;

class EnviarMidiaUseCaseTest {

    private static final byte[] AUDIO_MP4 = {0, 1, 2, 3};
    private static final byte[] AUDIO_OGG = {'O', 'g', 'g', 'S', 'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'};
    private static final byte[] AUDIO_OGG_OPUS = oggOpusValido();

    @Test
    void gravacaoMp4DoComposerEConvertidaParaOggOpusAntesDoStorageEEnvio() {
        DetectorDeTipoReal detector = mock(DetectorDeTipoReal.class);
        ArmazenamentoDeMidia armazenamento = mock(ArmazenamentoDeMidia.class);
        LimiteDeAnexoRepositorio limites = mock(LimiteDeAnexoRepositorio.class);
        EnviarMensagemUseCase enviarMensagem = mock(EnviarMensagemUseCase.class);
        ConversorDeAudio conversor = mock(ConversorDeAudio.class);
        UUID leadId = UUID.randomUUID();

        when(detector.detectar(AUDIO_MP4)).thenReturn("audio/mp4");
        when(limites.limiteEmBytes(CategoriaDeMidia.AUDIO)).thenReturn(Optional.of(1024L));
        when(conversor.converterParaOggOpus(AUDIO_MP4, "audio/mp4"))
                .thenReturn(new ConversorDeAudio.Resultado(AUDIO_OGG_OPUS, "audio/ogg"));
        when(armazenamento.salvar(AUDIO_OGG_OPUS, "gravacao.ogg", "audio/ogg"))
                .thenReturn("midias/gravacao.ogg");

        EnviarMidiaUseCase useCase = new EnviarMidiaUseCase(
                detector, armazenamento, limites, enviarMensagem, new ObjectMapper(), conversor);

        useCase.executar(leadId, AUDIO_MP4, "gravacao.m4a", null, null, true);

        verify(conversor).converterParaOggOpus(AUDIO_MP4, "audio/mp4");
        verify(conversor, never()).converterParaAacAdts(any(), any());
        verify(armazenamento).salvar(AUDIO_OGG_OPUS, "gravacao.ogg", "audio/ogg");
        ArgumentCaptor<ConteudoDeEnvio> envio = ArgumentCaptor.forClass(ConteudoDeEnvio.class);
        verify(enviarMensagem).executar(eq(leadId), envio.capture());
        assertThat(envio.getValue()).isInstanceOf(ConteudoDeEnvio.MensagemMidia.class);
        ConteudoDeEnvio.MensagemMidia midia = (ConteudoDeEnvio.MensagemMidia) envio.getValue();
        assertThat(midia.tipo()).isEqualTo(TipoMensagem.AUDIO);
        assertThat(midia.metadados()).contains("\"mimetype\":\"audio/ogg\"");
        assertThat(midia.metadados()).contains("\"nome\":\"gravacao.ogg\"");
    }

    @Test
    void audioAnexadoManualmenteNaoPassaPeloConversor() {
        DetectorDeTipoReal detector = mock(DetectorDeTipoReal.class);
        ArmazenamentoDeMidia armazenamento = mock(ArmazenamentoDeMidia.class);
        LimiteDeAnexoRepositorio limites = mock(LimiteDeAnexoRepositorio.class);
        EnviarMensagemUseCase enviarMensagem = mock(EnviarMensagemUseCase.class);
        ConversorDeAudio conversor = mock(ConversorDeAudio.class);
        UUID leadId = UUID.randomUUID();

        when(detector.detectar(AUDIO_MP4)).thenReturn("audio/mp4");
        when(limites.limiteEmBytes(CategoriaDeMidia.AUDIO)).thenReturn(Optional.of(1024L));
        when(armazenamento.salvar(AUDIO_MP4, "anexo.m4a", "audio/mp4"))
                .thenReturn("midias/anexo.m4a");

        EnviarMidiaUseCase useCase = new EnviarMidiaUseCase(
                detector, armazenamento, limites, enviarMensagem, new ObjectMapper(), conversor);

        useCase.executar(leadId, AUDIO_MP4, "anexo.m4a", null);

        verify(conversor, never()).converterParaAacAdts(any(), any());
        verify(conversor, never()).converterParaOggOpus(any(), any());
        verify(armazenamento).salvar(AUDIO_MP4, "anexo.m4a", "audio/mp4");
    }

    @Test
    void gravacaoOggDoComposerTambemEConvertidaParaOPerfilPortatil() {
        DetectorDeTipoReal detector = mock(DetectorDeTipoReal.class);
        ArmazenamentoDeMidia armazenamento = mock(ArmazenamentoDeMidia.class);
        LimiteDeAnexoRepositorio limites = mock(LimiteDeAnexoRepositorio.class);
        EnviarMensagemUseCase enviarMensagem = mock(EnviarMensagemUseCase.class);
        ConversorDeAudio conversor = mock(ConversorDeAudio.class);
        UUID leadId = UUID.randomUUID();

        when(detector.detectar(AUDIO_OGG)).thenReturn("audio/ogg");
        when(limites.limiteEmBytes(CategoriaDeMidia.AUDIO)).thenReturn(Optional.of(1024L));
        when(conversor.converterParaOggOpus(AUDIO_OGG, "audio/ogg"))
                .thenReturn(new ConversorDeAudio.Resultado(AUDIO_OGG_OPUS, "audio/ogg"));
        when(armazenamento.salvar(AUDIO_OGG_OPUS, "gravacao.ogg", "audio/ogg"))
                .thenReturn("midias/gravacao.ogg");

        EnviarMidiaUseCase useCase = new EnviarMidiaUseCase(
                detector, armazenamento, limites, enviarMensagem, new ObjectMapper(), conversor);

        useCase.executar(leadId, AUDIO_OGG, "gravacao.ogg", null, null, true);

        verify(conversor).converterParaOggOpus(AUDIO_OGG, "audio/ogg");
        verify(conversor, never()).converterParaAacAdts(any(), any());
        verify(armazenamento).salvar(AUDIO_OGG_OPUS, "gravacao.ogg", "audio/ogg");
    }

    @Test
    void gravacaoDoComposerComOggSemEosOuDuracaoERecusadaAntesDoStorage() {
        DetectorDeTipoReal detector = mock(DetectorDeTipoReal.class);
        ArmazenamentoDeMidia armazenamento = mock(ArmazenamentoDeMidia.class);
        LimiteDeAnexoRepositorio limites = mock(LimiteDeAnexoRepositorio.class);
        EnviarMensagemUseCase enviarMensagem = mock(EnviarMensagemUseCase.class);
        ConversorDeAudio conversor = mock(ConversorDeAudio.class);
        UUID leadId = UUID.randomUUID();
        byte[] entrada = {1, 2, 3};
        byte[] oggSemDuracao = paginaOgg(0, 0, new byte[] {'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'});

        when(detector.detectar(entrada)).thenReturn("audio/mp4");
        when(limites.limiteEmBytes(CategoriaDeMidia.AUDIO)).thenReturn(Optional.of(1024L));
        when(conversor.converterParaOggOpus(entrada, "audio/mp4"))
                .thenReturn(new ConversorDeAudio.Resultado(oggSemDuracao, "audio/ogg"));
        EnviarMidiaUseCase useCase = new EnviarMidiaUseCase(
                detector, armazenamento, limites, enviarMensagem, new ObjectMapper(), conversor);

        assertThatThrownBy(() -> useCase.executar(leadId, entrada, "gravacao.webm", null, null, true))
                .isInstanceOf(FalhaNaConversaoDeAudioException.class)
                .hasMessageContaining("OGG/Opus");
        verify(armazenamento, never()).salvar(any(), any(), any());
        verify(enviarMensagem, never()).executar(any(UUID.class), any(ConteudoDeEnvio.class));
    }

    @ParameterizedTest(name = "mantém documento binário permitido {0}")
    @MethodSource("documentosBinariosPermitidos")
    void documentosBinariosLegadosContinuamNoCaminhoDeDocumento(String mimetype) {
        DetectorDeTipoReal detector = mock(DetectorDeTipoReal.class);
        ArmazenamentoDeMidia armazenamento = mock(ArmazenamentoDeMidia.class);
        LimiteDeAnexoRepositorio limites = mock(LimiteDeAnexoRepositorio.class);
        EnviarMensagemUseCase enviarMensagem = mock(EnviarMensagemUseCase.class);
        byte[] conteudo = {0x01, 0x02, 0x03};
        UUID leadId = UUID.randomUUID();

        when(detector.detectar(conteudo)).thenReturn(mimetype);
        when(limites.limiteEmBytes(CategoriaDeMidia.DOCUMENTO)).thenReturn(Optional.of(1024L));
        when(armazenamento.salvar(any(), any(), eq(mimetype))).thenReturn("midias/documento");

        EnviarMidiaUseCase useCase = new EnviarMidiaUseCase(
                detector, armazenamento, limites, enviarMensagem, new ObjectMapper());

        useCase.executar(leadId, conteudo, "documento", null);

        ArgumentCaptor<ConteudoDeEnvio> envio = ArgumentCaptor.forClass(ConteudoDeEnvio.class);
        verify(enviarMensagem).executar(eq(leadId), envio.capture());
        assertThat(((ConteudoDeEnvio.MensagemMidia) envio.getValue()).tipo()).isEqualTo(TipoMensagem.DOCUMENTO);
    }

    @Test
    void uploadAncoradoDelegaParaOEnvioDaMesmaConversa() {
        DetectorDeTipoReal detector = mock(DetectorDeTipoReal.class);
        ArmazenamentoDeMidia armazenamento = mock(ArmazenamentoDeMidia.class);
        LimiteDeAnexoRepositorio limites = mock(LimiteDeAnexoRepositorio.class);
        EnviarMensagemUseCase enviarMensagem = mock(EnviarMensagemUseCase.class);
        UUID leadId = UUID.randomUUID();
        UUID atendimentoId = UUID.randomUUID();
        byte[] conteudo = {0x01, 0x02, 0x03};

        when(detector.detectar(conteudo)).thenReturn("application/msword");
        when(limites.limiteEmBytes(CategoriaDeMidia.DOCUMENTO)).thenReturn(Optional.of(1024L));
        when(armazenamento.salvar(any(), any(), eq("application/msword"))).thenReturn("midias/documento");
        EnviarMidiaUseCase useCase = new EnviarMidiaUseCase(
                detector, armazenamento, limites, enviarMensagem, new ObjectMapper());

        useCase.executar(
                leadId,
                atendimentoId,
                conteudo,
                "documento.doc",
                null,
                null,
                false,
                "chave-upload");

        ArgumentCaptor<ConteudoDeEnvio> envio = ArgumentCaptor.forClass(ConteudoDeEnvio.class);
        verify(enviarMensagem).executar(
                eq(leadId), eq(atendimentoId), envio.capture(), isNull(), eq("chave-upload"));
        assertThat(((ConteudoDeEnvio.MensagemMidia) envio.getValue()).tipo()).isEqualTo(TipoMensagem.DOCUMENTO);
    }

    static Stream<Arguments> documentosBinariosPermitidos() {
        return Stream.of(
                Arguments.of("application/msword"), Arguments.of("application/vnd.ms-excel"));
    }

    private static byte[] oggOpusValido() {
        byte[] opusHead = {
            'O', 'p', 'u', 's', 'H', 'e', 'a', 'd',
            1, 1, 0, 0, (byte) 0x80, (byte) 0xBB, 0, 0, 0, 0, 0
        };
        return concatenar(paginaOgg(0, 0, opusHead), paginaOgg(0x04, 960, new byte[] {0}));
    }

    private static byte[] paginaOgg(int flags, long granule, byte[] payload) {
        byte[] pagina = new byte[28 + payload.length];
        pagina[0] = 'O';
        pagina[1] = 'g';
        pagina[2] = 'g';
        pagina[3] = 'S';
        pagina[5] = (byte) flags;
        for (int indice = 0; indice < Long.BYTES; indice++) {
            pagina[6 + indice] = (byte) (granule >>> (8 * indice));
        }
        pagina[26] = 1;
        pagina[27] = (byte) payload.length;
        System.arraycopy(payload, 0, pagina, 28, payload.length);
        return pagina;
    }

    private static byte[] concatenar(byte[] primeiro, byte[] segundo) {
        byte[] resultado = java.util.Arrays.copyOf(primeiro, primeiro.length + segundo.length);
        System.arraycopy(segundo, 0, resultado, primeiro.length, segundo.length);
        return resultado;
    }
}
