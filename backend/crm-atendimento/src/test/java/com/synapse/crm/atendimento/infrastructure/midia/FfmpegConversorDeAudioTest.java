package com.synapse.crm.atendimento.infrastructure.midia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class FfmpegConversorDeAudioTest {

    @Test
    void gravaçãoMp4AacViraOggOpusComAssinaturasReais() throws Exception {
        Assumptions.assumeTrue(ffmpegDisponivel(), "FFmpeg não instalado neste ambiente");
        byte[] mp4 = gerarMp4Aac();

        var resultado = new FfmpegConversorDeAudio("ffmpeg")
                .converterParaOggOpus(mp4, "audio/mp4;codecs=mp4a.40.2");

        assertThat(resultado.mimetype()).isEqualTo("audio/ogg");
        assertThat(resultado.conteudo()).startsWith(new byte[] {'O', 'g', 'g', 'S'});
        assertThat(contém(resultado.conteudo(), new byte[] {'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'}))
                .isTrue();
    }

    private static boolean ffmpegDisponivel() {
        try {
            Process processo = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true)
                    .start();
            processo.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            return processo.waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static byte[] gerarMp4Aac() throws IOException, InterruptedException {
        Process processo = new ProcessBuilder(List.of(
                        "ffmpeg",
                        "-hide_banner",
                        "-loglevel",
                        "error",
                        "-f",
                        "lavfi",
                        "-i",
                        "sine=frequency=1000:duration=0.2",
                        "-c:a",
                        "aac",
                        "-movflags",
                        "frag_keyframe+empty_moov",
                        "-f",
                        "mp4",
                        "pipe:1"))
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        byte[] saida = processo.getInputStream().readAllBytes();
        int codigo = processo.waitFor();
        assertThat(codigo).isZero();
        return saida;
    }

    private static boolean contém(byte[] bytes, byte[] trecho) {
        for (int inicio = 0; inicio <= bytes.length - trecho.length; inicio++) {
            boolean igual = true;
            for (int deslocamento = 0; deslocamento < trecho.length; deslocamento++) {
                if (bytes[inicio + deslocamento] != trecho[deslocamento]) {
                    igual = false;
                    break;
                }
            }
            if (igual) return true;
        }
        return false;
    }
}
