package com.synapse.crm.atendimento.infrastructure.midia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class FfmpegConversorDeAudioTest {

    @Test
    void usaPerfilMonoEm48kParaNotaDeVozNoMobile() {
        assertThat(new FfmpegConversorDeAudio("ffmpeg").comando())
                .containsSubsequence("-c:a", "libopus", "-application", "voip")
                .containsSubsequence("-ac", "1", "-ar", "48000", "-b:a", "32k")
                .containsSubsequence("-f", "ogg", "pipe:1");
    }

    @Test
    void mantémPerfilAacAdtsMonoParaRegistrosFragmentadosLegados() {
        assertThat(new FfmpegConversorDeAudio("ffmpeg").comandoAacAdts())
                .containsSubsequence("-c:a", "aac", "-profile:a", "aac_low")
                .containsSubsequence("-ac", "1", "-ar", "48000", "-b:a", "48k")
                .containsSubsequence("-f", "adts", "pipe:1");
    }

    @Test
    void gravaçãoMp4AacEstereoViraNotaDeVozOggOpusMonoCompativelComMobile() throws Exception {
        Assumptions.assumeTrue(ffmpegDisponivel(), "FFmpeg não instalado neste ambiente");
        byte[] mp4 = gerarMp4Aac();

        var resultado = new FfmpegConversorDeAudio("ffmpeg")
                .converterParaOggOpus(mp4, "audio/mp4;codecs=mp4a.40.2");

        assertThat(resultado.mimetype()).isEqualTo("audio/ogg");
        assertThat(resultado.conteudo()).startsWith(new byte[] {'O', 'g', 'g', 'S'});
        assertThat(contém(resultado.conteudo(), new byte[] {'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'}))
                .isTrue();
        String detalhes = inspecionar(resultado.conteudo());
        assertThat(detalhes)
                .contains("codec_name=opus", "channels=1", "sample_rate=48000");
        assertThat(duracao(detalhes)).isPositive();
    }

    @Test
    void gravaçãoMp4AacViraAudioAacAdtsMonoCompativelComMobile() throws Exception {
        Assumptions.assumeTrue(ffmpegDisponivel(), "FFmpeg não instalado neste ambiente");
        byte[] mp4 = gerarMp4Aac();

        var resultado = new FfmpegConversorDeAudio("ffmpeg")
                .converterParaAacAdts(mp4, "audio/mp4;codecs=mp4a.40.2");

        assertThat(resultado.mimetype()).isEqualTo("audio/aac");
        assertThat(resultado.conteudo()).startsWith(new byte[] {(byte) 0xFF, (byte) 0xF1});
        assertThat(inspecionar(resultado.conteudo()))
                .contains("codec_name=aac", "channels=1", "sample_rate=48000");
    }

    @Test
    void oggComCabecalhoOpusMasSemEosOuDuracaoERecusado() {
        byte[] opusHead = {'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'};

        assertThat(FfmpegConversorDeAudio.ehOggOpus(paginaOgg(0, 0, opusHead))).isFalse();
        assertThat(FfmpegConversorDeAudio.ehOggOpus(paginaOgg(0x04, 0, opusHead))).isFalse();
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
                        "-ac",
                        "2",
                        "-ar",
                        "44100",
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

    private static String inspecionar(byte[] audio) throws IOException, InterruptedException {
        Path arquivo = Files.createTempFile("ffprobe-audio-", ".ogg");
        try {
            Files.write(arquivo, audio);
            Process processo = new ProcessBuilder(List.of(
                            "ffprobe",
                            "-v",
                            "error",
                            "-select_streams",
                            "a:0",
                            "-show_entries",
                            "stream=codec_name,channels,sample_rate,duration",
                            "-of",
                            "default=noprint_wrappers=1",
                            "-i",
                            arquivo.toString()))
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            String saida = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int codigo = processo.waitFor();
            assertThat(codigo).isZero();
            return saida;
        } finally {
            Files.deleteIfExists(arquivo);
        }
    }

    private static double duracao(String detalhes) {
        return java.util.Arrays.stream(detalhes.split("\\R"))
                .filter(linha -> linha.startsWith("duration="))
                .mapToDouble(linha -> Double.parseDouble(linha.substring("duration=".length())))
                .findFirst()
                .orElse(0);
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
