package com.synapse.crm.atendimento.infrastructure.midia;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.synapse.crm.atendimento.application.midia.FalhaNaConversaoDeAudioException;
import com.synapse.crm.sharedkernel.midia.ConversorDeAudio;

/**
 * Transcodifica gravações do composer para OGG/Opus, o único formato que a Meta marca como nota de
 * voz. O processo não recebe arquivos nem caminhos controlados pelo cliente: bytes entram por
 * stdin e o resultado sai por stdout.
 */
@Component
final class FfmpegConversorDeAudio implements ConversorDeAudio {

    private static final String MIME_OGG = "audio/ogg";
    private static final byte[] OGG = {'O', 'g', 'g', 'S'};
    private static final byte[] OPUS_HEAD = {'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'};

    private final String executavel;

    FfmpegConversorDeAudio(
            @Value("${synapse.midia.ffmpeg.executavel:ffmpeg}") String executavel) {
        this.executavel = executavel;
    }

    @Override
    public Resultado converterParaOggOpus(byte[] conteudo, String mimetype) {
        if (conteudo == null || conteudo.length == 0) {
            throw new FalhaNaConversaoDeAudioException("gravação de áudio vazia");
        }

        Process processo;
        try {
            processo = new ProcessBuilder(comando()).start();
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
        if (!temAssinatura(convertido, OGG) || !contém(convertido, OPUS_HEAD)) {
            throw new FalhaNaConversaoDeAudioException(
                    "FFmpeg não produziu um contêiner OGG/Opus válido");
        }
        return new Resultado(convertido, MIME_OGG);
    }

    /** Perfil explícito e estável de nota de voz; mantido visível ao teste de contrato do encoder. */
    List<String> comando() {
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

    private static boolean temAssinatura(byte[] bytes, byte[] assinatura) {
        if (bytes.length < assinatura.length) return false;
        for (int i = 0; i < assinatura.length; i++) {
            if (bytes[i] != assinatura[i]) return false;
        }
        return true;
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

    private static final class OutputStreamNulo extends OutputStream {
        private static final OutputStreamNulo INSTANCE = new OutputStreamNulo();

        private OutputStreamNulo() {}

        @Override
        public void write(int b) {}
    }
}
