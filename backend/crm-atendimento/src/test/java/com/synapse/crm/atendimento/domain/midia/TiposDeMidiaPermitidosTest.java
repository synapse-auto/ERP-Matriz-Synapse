package com.synapse.crm.atendimento.domain.midia;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;

/** E215 — video so por trilha + marca; audio nao fica mais frouxo. */
class TiposDeMidiaPermitidosTest {

    @Test
    void mp4ComumQueOTikaChamaDeQuicktimeViraVideoMp4() {
        var classificacao = TiposDeMidiaPermitidos.classificar("video/quicktime", conteiner("isom", "vide"));

        assertThat(classificacao).hasValueSatisfying(c -> {
            assertThat(c.tipo()).isEqualTo(TipoMensagem.VIDEO);
            assertThat(c.mimetype()).isEqualTo("video/mp4");
        });
    }

    @Test
    void conteiner3gpComVideoViraVideo3gpp() {
        assertThat(TiposDeMidiaPermitidos.classificar("video/3gpp", conteiner("3gp4", "vide")))
                .hasValueSatisfying(c -> assertThat(c.mimetype()).isEqualTo("video/3gpp"));
    }

    @Test
    void movDoIphoneComVideoERecusadoPorqueAMetaNaoAceita() {
        assertThat(TiposDeMidiaPermitidos.classificar("video/quicktime", conteiner("qt  ", "vide"))).isEmpty();
    }

    @Test
    void videoDisfarcadoDeM4aERecusadoEmVezDeVirarAudio() {
        assertThat(TiposDeMidiaPermitidos.classificar("audio/mp4", conteiner("M4A ", "vide"))).isEmpty();
        // Mesmo com marca de MP4, rotulado como audio, a trilha de video manda: vira VIDEO, nunca AUDIO.
        assertThat(TiposDeMidiaPermitidos.classificar("audio/mp4", conteiner("isom", "soun", "vide")))
                .hasValueSatisfying(c -> assertThat(c.tipo()).isEqualTo(TipoMensagem.VIDEO));
    }

    @Test
    void audioSemTrilhaDeVideoContinuaComoAntes() {
        assertThat(TiposDeMidiaPermitidos.classificar("audio/mp4", conteiner("M4A ", "soun")))
                .hasValueSatisfying(c -> {
                    assertThat(c.tipo()).isEqualTo(TipoMensagem.AUDIO);
                    assertThat(c.mimetype()).isEqualTo("audio/mp4");
                });
        assertThat(TiposDeMidiaPermitidos.classificar("video/quicktime", conteiner("qt  ", "soun")))
                .hasValueSatisfying(c -> assertThat(c.tipo()).isEqualTo(TipoMensagem.AUDIO));
    }

    @Test
    void conteinerDeVideoSemTrilhaNenhumaNaoViraVideo() {
        assertThat(TiposDeMidiaPermitidos.classificar("video/mp4", conteiner("isom"))).isEmpty();
    }

    @Test
    void mimetypeDeVideoSemConteinerIsoNaoEAceito() {
        assertThat(TiposDeMidiaPermitidos.classificar("video/webm", new byte[32])).isEmpty();
        assertThat(TiposDeMidiaPermitidos.tipoDe("video/mp4")).isEmpty();
    }

    @Test
    void tetoDeVideoEODaMeta() {
        assertThat(TiposDeMidiaPermitidos.tetoDaMetaEmBytes(TipoMensagem.VIDEO)).isEqualTo(16L * 1024 * 1024);
    }

    private static byte[] conteiner(String marca, String... trilhas) {
        byte[] ftyp = box("ftyp", concatenar(ascii(marca), ascii(marca)));
        byte[] traks = new byte[0];
        for (String trilha : trilhas) {
            traks = concatenar(traks, box("trak", box("mdia", box("hdlr", concatenar(new byte[8], ascii(trilha))))));
        }
        return concatenar(ftyp, box("moov", traks));
    }

    private static byte[] ascii(String texto) {
        return texto.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concatenar(byte[] a, byte[] b) {
        byte[] resultado = new byte[a.length + b.length];
        System.arraycopy(a, 0, resultado, 0, a.length);
        System.arraycopy(b, 0, resultado, a.length, b.length);
        return resultado;
    }

    private static byte[] box(String tipo, byte[] payload) {
        byte[] resultado = new byte[8 + payload.length];
        int tamanho = resultado.length;
        resultado[0] = (byte) (tamanho >>> 24);
        resultado[1] = (byte) (tamanho >>> 16);
        resultado[2] = (byte) (tamanho >>> 8);
        resultado[3] = (byte) tamanho;
        System.arraycopy(ascii(tipo), 0, resultado, 4, 4);
        System.arraycopy(payload, 0, resultado, 8, payload.length);
        return resultado;
    }
}
