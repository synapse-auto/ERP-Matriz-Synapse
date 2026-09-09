package com.synapse.crm.atendimento.infrastructure.midia;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.application.midia.FalhaNaConversaoDeAudioException;
import com.synapse.crm.sharedkernel.midia.ConversorDeAudio;
import com.synapse.crm.sharedkernel.midia.ValidadorDeOggOpus;

/**
 * Transcodifica gravações do composer para OGG/Opus no perfil de nota de voz do WhatsApp. O
 * processo não recebe arquivos nem caminhos controlados pelo cliente: bytes entram por stdin e o
 * resultado sai por stdout.
 */
@Component
final class FfmpegConversorDeAudio implements ConversorDeAudio {

    private static final String MIME_OGG = "audio/ogg";
    private static final String MIME_AAC = "audio/aac";

    private final String executavel;

    FfmpegConversorDeAudio(
            @Value("${synapse.midia.ffmpeg.executavel:ffmpeg}") String executavel) {
        this.executavel = executavel;
    }

    @Override
    public Resultado converterParaOggOpus(byte[] conteudo, String mimetype) {
        return converter(conteudo, comando(), MIME_OGG, "OGG/Opus", FfmpegConversorDeAudio::ehOggOpus);
    }

    @Override
    public Resultado converterParaAacAdts(byte[] conteudo, String mimetype) {
        return converter(conteudo, comandoAacAdts(), MIME_AAC, "AAC/ADTS", FfmpegConversorDeAudio::ehAacAdts);
    }

    private Resultado converter(
            byte[] conteudo,
            List<String> comando,
            String mimetypeDeSaida,
            String formatoEsperado,
            Predicate<byte[]> formatoValido) {
        if (conteudo == null || conteudo.length == 0) {
            throw new FalhaNaConversaoDeAudioException("gravação de áudio vazia");
        }

        Process processo;
        try {
            processo = new ProcessBuilder(comando).start();
        } catch (IOException e) {
            throw new FalhaNaConversaoDeAudioException(
                    "FFmpeg não está disponível para converter a gravação de áudio", e);
        }

        var saida = new ByteArrayOutputStream();
        var erro = new AtomicReference<IOException>();
        Thread leitorDaSaida = Thread.startVirtualThread(() -> ler(processo.getInputStream(), saida, erro));
        Thread leitorDoErro = Thread.startVirtualThread(() -> drenar(processo.getErrorStream(), erro));
        try {
            processo.getOutputStream().write(conteudo);
            processo.getOutputStream().close();
            int codigo = processo.waitFor();
            leitorDaSaida.join();
            leitorDoErro.join();
            if (codigo != 0 || erro.get() != null) {
                throw new FalhaNaConversaoDeAudioException(
                        "FFmpeg recusou a gravação de áudio (código " + codigo + ")");
            }
        } catch (InterruptedException e) {
            processo.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new FalhaNaConversaoDeAudioException("conversão de áudio interrompida", e);
        } catch (IOException e) {
            processo.destroyForcibly();
            throw new FalhaNaConversaoDeAudioException("falha ao converter a gravação de áudio", e);
        }

        byte[] convertido = saida.toByteArray();
        if (!formatoValido.test(convertido)) {
            throw new FalhaNaConversaoDeAudioException(
                    "FFmpeg não produziu um contêiner " + formatoEsperado + " válido");
        }
        return new Resultado(convertido, mimetypeDeSaida);
    }

    /** Perfil de nota de voz usado pelas gravações do composer. */
    List<String> comando() {
        return List.of(
                executavel,
                "-hide_banner",
                "-loglevel",
                "error",
                "-nostdin",
                "-fflags",
                "+genpts",
                "-i",
                "pipe:0",
                "-vn",
                "-map_metadata",
                "-1",
                "-af",
                "aresample=async=1:first_pts=0",
                "-c:a",
                "libopus",
                "-application",
                "voip",
                // O cliente mobile do WhatsApp espera o perfil de nota de voz, e não apenas
                // um contêiner OGG com Opus. Não preserve canais/taxa do microfone (que pode
                // ser estéreo ou 44,1 kHz): produza o perfil portátil de voz antes do upload.
                "-ac",
                "1",
                "-ar",
                "48000",
                "-b:a",
                "32k",
                "-f",
                "ogg",
                "pipe:1");
    }

    /** Perfil AAC/ADTS legado, mantido para registros fragmentados antigos no worker. */
    List<String> comandoAacAdts() {
        return List.of(
                executavel,
                "-hide_banner",
                "-loglevel",
                "error",
                "-nostdin",
                "-i",
                "pipe:0",
                "-vn",
                "-map_metadata",
                "-1",
                "-c:a",
                "aac",
                "-profile:a",
                "aac_low",
                "-ac",
                "1",
                "-ar",
                "48000",
                "-b:a",
                "48k",
                "-f",
                "adts",
                "pipe:1");
    }

    private static void ler(
            InputStream fonte, ByteArrayOutputStream destino, AtomicReference<IOException> erro) {
        try (fonte) {
            fonte.transferTo(destino);
        } catch (IOException e) {
            erro.compareAndSet(null, e);
        }
    }

    private static void drenar(InputStream fonte, AtomicReference<IOException> erro) {
        try (fonte) {
            fonte.transferTo(OutputStreamNulo.INSTANCE);
        } catch (IOException e) {
            erro.compareAndSet(null, e);
        }
    }

    /**
     * Verifica o contêiner inteiro, e não apenas as assinaturas iniciais. Uma página EOS com
     * granule position positivo é necessária para que os provedores consigam calcular a duração;
     * um arquivo truncado com {@code OpusHead} não é uma nota de voz válida.
     */
    static boolean ehOggOpus(byte[] bytes) {
        return ValidadorDeOggOpus.ehValido(bytes);
    }

    private static boolean ehAacAdts(byte[] bytes) {
        return bytes.length >= 7
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xF6) == 0xF0;
    }

    private static final class OutputStreamNulo extends OutputStream {
        private static final OutputStreamNulo INSTANCE = new OutputStreamNulo();

        private OutputStreamNulo() {}

        @Override
        public void write(int b) {}
    }
}
