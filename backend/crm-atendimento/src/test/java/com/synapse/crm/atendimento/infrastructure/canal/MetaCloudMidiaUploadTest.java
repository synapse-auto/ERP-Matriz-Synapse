package com.synapse.crm.atendimento.infrastructure.canal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;

class MetaCloudMidiaUploadTest {

    @Test
    void mp4ComCodecsViraAudioMp4SemParametros() {
        String campo = MetaCloudMidiaUpload.tipoDoCampo("audio/mp4;codecs=mp4a.40.2", TipoMensagem.AUDIO);

        assertThat(campo).isEqualTo("audio/mp4");
        assertThat(MetaCloudMidiaUpload.tipoDoArquivo(campo)).isEqualTo("audio/mp4");
        assertThat(MetaCloudMidiaUpload.contentType(campo)).isEqualTo(MediaType.parseMediaType("audio/mp4"));
        assertThat(MetaCloudMidiaUpload.ehNotaDeVoz("audio/mp4;codecs=mp4a.40.2")).isFalse();
    }

    @Test
    void oggGanhaCodecsOpusNoCampoType() {
        String campo = MetaCloudMidiaUpload.tipoDoCampo("audio/ogg", TipoMensagem.AUDIO);

        assertThat(campo).isEqualTo("audio/ogg; codecs=opus");
        assertThat(MetaCloudMidiaUpload.tipoDoArquivo(campo)).isEqualTo("audio/ogg");
        assertThat(MetaCloudMidiaUpload.ehNotaDeVoz("audio/ogg")).isTrue();
        assertThat(MetaCloudMidiaUpload.ehNotaDeVoz("audio/opus")).isTrue();
    }

    @Test
    void nomeSemExtensaoRecebeExtensaoDoMime() {
        assertThat(MetaCloudMidiaUpload.nomeDoArquivo("anexo", "audio/mp4")).isEqualTo("anexo.m4a");
        assertThat(MetaCloudMidiaUpload.nomeDoArquivo(null, "audio/ogg")).isEqualTo("audio.ogg");
        assertThat(MetaCloudMidiaUpload.nomeDoArquivo("gravacao.m4a", "audio/aac")).isEqualTo("gravacao.aac");
        assertThat(MetaCloudMidiaUpload.nomeDoArquivo("foto.PNG", "image/png")).isEqualTo("foto.png");
    }

    @Test
    void mimetypeAusenteEmAudioAssumeMp4() {
        assertThat(MetaCloudMidiaUpload.tipoDoCampo(null, TipoMensagem.AUDIO)).isEqualTo("audio/mp4");
        assertThat(MetaCloudMidiaUpload.tipoDoCampo("  ", TipoMensagem.AUDIO)).isEqualTo("audio/mp4");
    }
}
