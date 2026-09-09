package com.synapse.crm.atendimento.application.midia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private static final byte[] AUDIO_AAC = {(byte) 0xFF, (byte) 0xF1, 0x50, (byte) 0x80, 0x00, 0x1F, (byte) 0xFC};

    @Test
    void gravacaoMp4DoComposerEConvertidaParaAacAntesDoStorageEEnvio() {
        DetectorDeTipoReal detector = mock(DetectorDeTipoReal.class);
        ArmazenamentoDeMidia armazenamento = mock(ArmazenamentoDeMidia.class);
        LimiteDeAnexoRepositorio limites = mock(LimiteDeAnexoRepositorio.class);
        EnviarMensagemUseCase enviarMensagem = mock(EnviarMensagemUseCase.class);
        ConversorDeAudio conversor = mock(ConversorDeAudio.class);
        UUID leadId = UUID.randomUUID();

        when(detector.detectar(AUDIO_MP4)).thenReturn("audio/mp4");
        when(limites.limiteEmBytes(CategoriaDeMidia.AUDIO)).thenReturn(Optional.of(1024L));
        when(conversor.converterParaAacAdts(AUDIO_MP4, "audio/mp4"))
                .thenReturn(new ConversorDeAudio.Resultado(AUDIO_AAC, "audio/aac"));
        when(armazenamento.salvar(AUDIO_AAC, "gravacao.aac", "audio/aac"))
                .thenReturn("midias/gravacao.aac");

        EnviarMidiaUseCase useCase = new EnviarMidiaUseCase(
                detector, armazenamento, limites, enviarMensagem, new ObjectMapper(), conversor);

        useCase.executar(leadId, AUDIO_MP4, "gravacao.m4a", null, null, true);

        verify(conversor).converterParaAacAdts(AUDIO_MP4, "audio/mp4");
        verify(armazenamento).salvar(AUDIO_AAC, "gravacao.aac", "audio/aac");
        ArgumentCaptor<ConteudoDeEnvio> envio = ArgumentCaptor.forClass(ConteudoDeEnvio.class);
        verify(enviarMensagem).executar(eq(leadId), envio.capture());
        assertThat(envio.getValue()).isInstanceOf(ConteudoDeEnvio.MensagemMidia.class);
        ConteudoDeEnvio.MensagemMidia midia = (ConteudoDeEnvio.MensagemMidia) envio.getValue();
        assertThat(midia.tipo()).isEqualTo(TipoMensagem.AUDIO);
        assertThat(midia.metadados()).contains("\"mimetype\":\"audio/aac\"");
        assertThat(midia.metadados()).contains("\"nome\":\"gravacao.aac\"");
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
        when(conversor.converterParaAacAdts(AUDIO_OGG, "audio/ogg"))
                .thenReturn(new ConversorDeAudio.Resultado(AUDIO_AAC, "audio/aac"));
        when(armazenamento.salvar(AUDIO_AAC, "gravacao.aac", "audio/aac"))
                .thenReturn("midias/gravacao.aac");

        EnviarMidiaUseCase useCase = new EnviarMidiaUseCase(
                detector, armazenamento, limites, enviarMensagem, new ObjectMapper(), conversor);

        useCase.executar(leadId, AUDIO_OGG, "gravacao.ogg", null, null, true);

        verify(conversor).converterParaAacAdts(AUDIO_OGG, "audio/ogg");
        verify(armazenamento).salvar(AUDIO_AAC, "gravacao.aac", "audio/aac");
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

    static Stream<Arguments> documentosBinariosPermitidos() {
        return Stream.of(
                Arguments.of("application/msword"), Arguments.of("application/vnd.ms-excel"));
    }
}
